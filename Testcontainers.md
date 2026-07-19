# Testcontainers — A Deep Dive

This document explains **what Testcontainers is**, **why it matters**, and walks through all **four different approaches** used in this project, one by one.

---

## 🤔 What is Testcontainers?

When you write integration tests for a Spring Boot application that uses a database, you need a real database to test against. The traditional options are not great:

| Option | Problem |
|--------|---------|
| Use H2 (in-memory database) | H2 is not PostgreSQL. SQL that works in Postgres may fail in H2. You are not testing reality. |
| Use a shared dev database | Tests can interfere with each other. The database state is unpredictable. |
| Manually install and run Postgres on every machine | Works on your Mac, fails on CI, fails on a colleague's machine. |

**Testcontainers solves all of this.**

> Testcontainers is a Java library that starts real Docker containers — such as a real PostgreSQL database — programmatically from inside your test code, and stops them automatically when the test is done.

You get a real, isolated, throw-away Postgres database for every test run. No installation needed. No shared state. No surprises.

---

## 🧰 Prerequisites

Testcontainers needs a Docker-compatible container runtime to be running. That is the only requirement.

Any of the following work on macOS:

| Runtime | Notes |
|---------|-------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Most common, works out of the box |
| [Colima](https://github.com/abiosoft/colima) | Lightweight, open-source alternative — `brew install colima && colima start` |
| [Rancher Desktop](https://rancherdesktop.io/) | Open-source, bundles containerd or dockerd |
| [Podman Desktop](https://podman-desktop.io/) | Rootless containers, requires a compatibility socket |

Testcontainers detects the Docker socket automatically regardless of which runtime you use.
If you use Colima, the socket is usually at `/var/run/docker.sock` — the default Testcontainers looks for.

- ✅ A Docker-compatible runtime running on your machine (see above)
- ✅ The Testcontainers dependencies in `pom.xml` (already added)
- ❌ No local PostgreSQL installation required

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 🏷️ Key Annotations to Know

Before looking at the four approaches, understand these two Spring Boot test annotations that appear in every test class:

### `@DataJpaTest`
Loads only the JPA layer (entities, repositories, `EntityManager`). It does **not** start the full Spring application context. This makes the test faster because it only loads what the database layer needs.

### `@AutoConfigureTestDatabase(replace = NONE)`
By default, `@DataJpaTest` replaces your configured database with an in-memory H2 database. `replace = NONE` tells Spring: **do not replace it** — use whatever datasource we configure. This is essential so our Testcontainers PostgreSQL container gets used instead of H2.

---

## 🔬 The Four Approaches

This project contains four test classes that all test the same thing (`findAllSamsungProducts()`) but each demonstrates a different way to wire up Testcontainers. Read them in order — each one builds on the previous.

---

### Approach 1 — Manual Start/Stop
**File:** `ProductRepositoryTestContainerManualStartTest.java`

This is the most explicit approach. You are fully in control of the container lifecycle.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTestContainerManualStartTest {

    // 1. Declare the container — but do NOT annotate it with @Container
    static PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    // 2. Start it manually before any tests run
    @BeforeAll
    static void beforeAll() {
        postgresqlContainer.start();
    }

    // 3. Stop it manually after all tests are done
    @AfterAll
    static void afterAll() {
        postgresqlContainer.stop();
    }

    // 4. Tell Spring to use the container's connection details
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
    }

    @Test
    void findAllSamsungProducts() { ... }
}
```

**What `@DynamicPropertySource` does:**  
The Postgres container starts on a **random port** chosen by Docker. Spring needs to know the URL, username, and password to connect. `@DynamicPropertySource` lets you inject those values into Spring's environment *at runtime*, after the container has started and its port is known. Without this, Spring would try to use the URL from `application.properties` which points to your local machine, not the container.

**When to use this approach:**  
When you need maximum control — for example, if you want to start the container only under certain conditions, or configure it in a special way before starting.

---

### Approach 2 — Automatic Lifecycle with `@Testcontainers` + `@Container`
**File:** `ProductRepositoryTestContainerTest.java`

This is the same as Approach 1 but with the lifecycle management automated by the Testcontainers JUnit 5 extension. You remove `@BeforeAll` / `@AfterAll` and replace them with two annotations.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers  // ← activates the Testcontainers JUnit 5 extension
class ProductRepositoryTestContainerTest {

    @Container  // ← the extension will start/stop this automatically
    static PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
    }

    @Test
    void findAllSamsungProducts() { ... }
}
```

**What changed vs Approach 1:**
- Added `@Testcontainers` on the class — this activates the extension that watches for `@Container` fields.
- Added `@Container` on the field — the extension calls `.start()` before tests and `.stop()` after automatically.
- Removed `@BeforeAll` and `@AfterAll` — no longer needed.

**`static` vs instance field:**  
When the container field is `static`, one container is shared across all tests in the class (started once, stopped once). If it were an instance field (non-static), a fresh container would be started and stopped for *every single test method* — much slower.

**When to use this approach:**  
This is the most common and recommended approach. It is clean, readable, and lets Testcontainers manage the lifecycle for you.

---

### Approach 3 — TC JDBC URL
**File:** `ProductRepositoryWithTCJdbcUrlTest.java`

This approach is the most minimal. You do not declare a container at all. Instead, you use a special JDBC URL format that Testcontainers recognises and uses to start a container automatically.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:16:///demodb"  // ← the magic URL
})
class ProductRepositoryWithTCJdbcUrlTest {

    // No container field declared at all!
    // No @DynamicPropertySource needed!

    @Test
    void findAllSamsungProducts() { ... }
}
```

**The TC JDBC URL format:**
```
jdbc:tc:postgresql:16:///demodb
      ^^                 ^^^^^^
      ||                 database name (arbitrary)
      ||
      tc = Testcontainers will handle this
```

When the Testcontainers JDBC driver sees `jdbc:tc:` in the URL, it:
1. Pulls and starts a `postgres:16` Docker container
2. Waits for it to be ready
3. Rewrites the URL to the real `localhost:RANDOM_PORT/demodb` connection string
4. Returns the connection to Spring

**Why no `@DynamicPropertySource` is needed:**  
The Testcontainers JDBC driver intercepts the connection at the driver level, before Spring even tries to connect. The datasource URL IS the instruction — there is nothing dynamic to inject.

**When to use this approach:**  
When you want the absolute minimum amount of test code and you only need to swap the database. It is elegant but less flexible — you cannot easily configure the container (e.g. set custom environment variables or mount files).

---

### Approach 4 — Custom `ApplicationContextInitializer`
**Files:** `ProductControllerIntegrationUsesInitializerTest.java` and `PostgresDatabaseContainerInitializer.java`

All previous approaches defined the container *inside* the test class. If you have many test classes that all need the same Postgres container, that is a lot of duplication. This approach extracts the container setup into a **reusable shared class**.

**Step 1 — The shared initializer:**

```java
// src/test/java/org/aom/product/infra/PostgresDatabaseContainerInitializer.java

public class PostgresDatabaseContainerInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // Container is static — shared across all test classes that use this initializer
    private static final PostgreSQLContainer<?> sqlContainer =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("sa")
                    .withPassword("sa")
                    .withReuse(true);  // ← reuses the container across test classes

    static {
        sqlContainer.start();  // starts once when the class is first loaded
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // Injects the container's connection details into Spring's environment
        TestPropertyValues.of(
            "spring.datasource.url="      + sqlContainer.getJdbcUrl(),
            "spring.datasource.username=" + sqlContainer.getUsername(),
            "spring.datasource.password=" + sqlContainer.getPassword()
        ).applyTo(context.getEnvironment());
    }
}
```

**Step 2 — Any test class just references it:**

```java
// No container declaration here at all
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {PostgresDatabaseContainerInitializer.class})
class ProductControllerIntegrationUsesInitializerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllProducts() throws Exception {
        mockMvc.perform(get("/product-service/getAllProducts"))
                .andExpect(status().isOk());
    }
}
```

**Key differences from the repository tests:**
- Uses `@SpringBootTest` instead of `@DataJpaTest` — this starts the **full** Spring application context including the web layer.
- Uses `MockMvc` to make HTTP requests to the running controller — this is a true end-to-end integration test through all layers.
- `.withReuse(true)` — tells Testcontainers to reuse the same container if it is already running, instead of creating a new one. This speeds up test suites significantly when multiple test classes use the same initializer.

**When to use this approach:**  
When you have many test classes that all need the same container, or when you want full integration tests that test the HTTP layer together with the database.

---

## 📊 Side-by-Side Comparison

| | Approach 1 | Approach 2 | Approach 3 | Approach 4 |
|-|-----------|-----------|-----------|-----------|
| **File** | `ManualStartTest` | `TestContainerTest` | `WithTCJdbcUrlTest` | `UsesInitializerTest` |
| **Container declared in** | Test class | Test class | URL string | Separate shared class |
| **Lifecycle managed by** | You (`@BeforeAll`/`@AfterAll`) | Testcontainers extension | Testcontainers JDBC driver | Static initializer block |
| **`@DynamicPropertySource` needed** | ✅ Yes | ✅ Yes | ❌ No | ❌ No (uses `TestPropertyValues`) |
| **Reusable across classes** | ❌ No | ❌ No | ❌ No | ✅ Yes |
| **Spring context loaded** | JPA only (`@DataJpaTest`) | JPA only (`@DataJpaTest`) | JPA only (`@DataJpaTest`) | Full app (`@SpringBootTest`) |
| **Tests via** | Repository directly | Repository directly | Repository directly | HTTP / MockMvc |
| **Verbosity** | High | Medium | Low | Low (in test class) |

---

## 🔄 What Happens When You Run `./mvnw test`

Here is the sequence of events so you understand what Docker is doing in the background:

```
1. Maven starts the test phase
2. JUnit 5 discovers the test classes
3. For each test class:
   a. Testcontainers checks if Docker is running
   b. Testcontainers pulls postgres:16 image (only on first run — cached after that)
   c. A new PostgreSQL container starts on a random available port
   d. Testcontainers waits until Postgres is ready to accept connections
   e. @DynamicPropertySource (or initializer) injects the real connection URL into Spring
   f. Spring context loads and connects to the container
   g. Hibernate creates the schema (DDL)
   h. data.sql seeds initial rows
   i. Test methods execute
   j. Container stops (or is reused if .withReuse(true))
4. Test results reported
```

---

## 💡 Tips & Things to Know

**Why is the first test run slow?**  
Docker has to pull the `postgres:16` image the first time. After that it is cached locally and subsequent runs are fast.

**Can two containers run at the same time?**  
Yes. Testcontainers assigns a random port to each container so there are no conflicts. You can have multiple test classes running in parallel, each with its own isolated container.

**What is `withReuse(true)`?**  
This is an optimisation. When enabled, Testcontainers checks if a matching container is already running (from a previous test run) and reuses it instead of starting a new one. Useful for speeding up local development, but generally disabled in CI so each build gets a clean state.

**Why `@AutoConfigureTestDatabase(replace = NONE)`?**  
Without this, Spring Boot's test auto-configuration will swap your datasource for H2. Since we want to test against real Postgres, we tell it: hands off, we are providing the datasource ourselves.

**Why `static` containers and `@DynamicPropertySource`?**  
The Spring context is created *before* the test instance is created. A `static` container can be started before the context is built. `@DynamicPropertySource` is also `static` for the same reason — it runs during context creation, before any instance exists.

