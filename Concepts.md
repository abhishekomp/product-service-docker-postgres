# Concepts Guide — Connecting the Dots

This document explains every key term and technology used in this project.
It is written for learners who want to understand not just *what* the code does,
but *why* each piece exists and how they all fit together.

---

## Table of Contents

1. [The Big Picture](#1-the-big-picture)
2. [Spring Boot](#2-spring-boot)
3. [Layered Architecture](#3-layered-architecture)
4. [JPA and Hibernate](#4-jpa-and-hibernate)
5. [Spring Data JPA — Repository Pattern](#5-spring-data-jpa--repository-pattern)
6. [REST API and Spring MVC](#6-rest-api-and-spring-mvc)
7. [Input Validation — @Valid and @NotBlank](#7-input-validation---valid-and-notblank)
8. [Error Handling — @RestControllerAdvice](#8-error-handling---restcontrolleradvice)
9. [PostgreSQL and the Datasource](#9-postgresql-and-the-datasource)
10. [Docker and Docker Compose](#10-docker-and-docker-compose)
11. [Managing Secrets — .env and .env.example](#11-managing-secrets---env-and-envexample)
12. [Testing Concepts](#12-testing-concepts)
13. [Testcontainers — All Four Approaches](#13-testcontainers--all-four-approaches)
14. [AssertJ](#14-assertj)
15. [How Everything Connects on Startup](#15-how-everything-connects-on-startup)

---

## 1. The Big Picture

```
                    ┌──────────────────────────────────────────────┐
                    │              Spring Boot App                 │
                    │                                              │
 HTTP Request  ───► │  ProductController                          │
                    │       │                                      │
                    │       ▼                                      │
                    │  ProductService                              │
                    │       │                                      │
                    │       ▼                                      │
                    │  ProductRepository  ──►  PostgreSQL DB       │
                    │                                              │
                    └──────────────────────────────────────────────┘
                    
          Everything above runs inside a Docker container.
          PostgreSQL also runs in its own Docker container.
          Docker Compose wires them together.
```

**The flow of a request:**
1. A client (Postman, browser, test) sends an HTTP request.
2. `ProductController` receives it and calls the appropriate service method.
3. `ProductService` contains the business logic and calls the repository.
4. `ProductRepository` translates the call into SQL and queries PostgreSQL.
5. The result travels back up the chain and is returned as JSON.

---

## 2. Spring Boot

Spring Boot is a framework that makes it fast to build production-ready Java applications.

**What it does for you:**
- **Auto-configuration**: detects which libraries are on the classpath and configures them automatically. For example, it sees `spring-boot-starter-data-jpa` and sets up Hibernate, EntityManager, and transaction management without any XML or Java config.
- **Embedded server**: packages Tomcat inside the `.jar` file. There is no separate server to install — you just run `java -jar app.jar`.
- **Starter dependencies**: curated collections of dependencies that work well together. `spring-boot-starter-web` brings in everything needed for REST APIs; `spring-boot-starter-data-jpa` brings in everything for database access.

**Entry point — `ProductServiceApplication.java`:**
```java
@SpringBootApplication  // enables component scanning, auto-configuration, and @Configuration support
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```
`@SpringBootApplication` is a shortcut for three annotations:
- `@Configuration` — marks this class as a source of Spring beans
- `@EnableAutoConfiguration` — triggers Spring Boot's auto-configuration mechanism
- `@ComponentScan` — scans the package and sub-packages for `@Component`, `@Service`, `@Repository`, `@Controller` beans

---

## 3. Layered Architecture

This project follows the standard Spring Boot three-layer architecture.
Each layer has a single responsibility and only talks to the layer directly below it.

```
┌─────────────────────────────────────────────────┐
│  Controller layer  (@RestController)            │  ← handles HTTP: input/output
│  ProductController                              │
├─────────────────────────────────────────────────┤
│  Service layer  (@Service)                      │  ← business logic
│  ProductService                                 │
├─────────────────────────────────────────────────┤
│  Repository layer  (@Repository)               │  ← data access
│  ProductRepository                              │
├─────────────────────────────────────────────────┤
│  Database  (PostgreSQL)                        │  ← persistence
└─────────────────────────────────────────────────┘
```

**Why separate layers?**
- Each layer can be tested in isolation (see the test section).
- Business logic does not leak into HTTP handling or database code.
- You can swap the database (e.g. from Postgres to MySQL) without touching the controller.

**Dependency Injection:**
Each layer receives its dependency via the constructor — Spring creates and injects the object automatically. This is called *constructor injection* and is the recommended style.

```java
// Spring sees @Service and creates a ProductService bean.
// When creating ProductController, Spring sees the constructor requires
// a ProductService, so it injects the one it already created.
@RestController
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService; // injected by Spring
    }
}
```

---

## 4. JPA and Hibernate

**JPA (Jakarta Persistence API)** is a specification — a set of Java interfaces and annotations that describe how to map Java objects to database tables.

**Hibernate** is the most popular *implementation* of JPA. When you add `spring-boot-starter-data-jpa` to your project, Hibernate is included automatically.

**The `Product` entity:**
```java
@Entity  // tells JPA: this class maps to a database table
public class Product {

    @Id  // marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    // GenerationType.SEQUENCE: uses a PostgreSQL SEQUENCE object to generate unique IDs.
    // allocationSize = 1: fetch one ID at a time from the sequence (no pre-allocation).
    private Integer prodNum;

    private String skuCode;  // maps to column: sku_code (snake_case by default)
    private String pName;    // maps to column: p_name
}
```

**What Hibernate does automatically:**
- Creates the `product` table in the database (because `spring.jpa.hibernate.ddl-auto=update`).
- Translates `productRepository.findAll()` into `SELECT * FROM product`.
- Maps result rows back into `Product` Java objects.

**DDL Auto strategies** (set in `application.properties`):
| Strategy | Behaviour |
|----------|-----------|
| `create-drop` | Creates tables on startup, drops them on shutdown. Data lost on restart. |
| `update` | Creates tables if missing, alters columns if changed. Data is preserved. ✅ (used here) |
| `validate` | Checks the schema matches the entities but makes no changes. |
| `none` | Does nothing — you manage the schema yourself. |

---

## 5. Spring Data JPA — Repository Pattern

Instead of writing SQL yourself, Spring Data JPA generates the database queries for you.

**`ProductRepository`:**
```java
// Extending JpaRepository<Product, Integer> gives you these methods for free:
//   save(product)         — INSERT or UPDATE
//   findById(id)          — SELECT WHERE id = ?
//   findAll()             — SELECT *
//   deleteById(id)        — DELETE WHERE id = ?
//   count()               — SELECT COUNT(*)
//   ... and many more
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    // Custom query using JPQL (Java Persistence Query Language).
    // JPQL looks like SQL but operates on Java class names and field names,
    // not table names and column names.
    // Hibernate translates this into the actual SQL for Postgres.
    @Query("SELECT p FROM Product p WHERE p.skuCode = 'SAM2024'")
    List<Product> findAllSamsungProducts();

    // Parameterised version — accepts any skuCode at runtime.
    // @Param("skuCode") binds the method argument to the :skuCode placeholder in the query.
    @Query("SELECT p FROM Product p WHERE p.skuCode = :skuCode")
    List<Product> findAllBySkuCode(@Param("skuCode") String skuCode);
}
```

**JPQL vs SQL:**
```
JPQL:  SELECT p FROM Product p WHERE p.skuCode = 'SAM2024'
SQL:   SELECT * FROM product WHERE sku_code = 'SAM2024'
```
JPQL uses the Java class name (`Product`) and field name (`skuCode`).
Hibernate translates it into the correct SQL for whichever database is configured.

**`Optional` and `orElseThrow`:**
`findById()` returns `Optional<Product>` — a container that may or may not contain a value.
```java
// BAD — will throw NoSuchElementException with no useful message if not found:
return repository.findById(id).get();

// GOOD — throws a meaningful exception if the product does not exist:
return repository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + id));
```

---

## 6. REST API and Spring MVC

**REST (Representational State Transfer)** is a style for designing HTTP APIs.
Each URL represents a resource. HTTP methods express what action to take:

| HTTP Method | Action | Endpoint in this project |
|-------------|--------|--------------------------|
| `GET` | Read data | `/product-service/getAllProducts` |
| `GET` | Read one item | `/product-service/getProduct/{id}` |
| `POST` | Create new item | `/product-service/addProduct` |

**Spring MVC annotations in `ProductController`:**
```java
@RestController
// Combines @Controller (marks it as a Spring MVC controller)
// and @ResponseBody (all return values are serialised to JSON automatically).

@RequestMapping("/product-service")
// All endpoints in this class start with /product-service.

@GetMapping("/getAllProducts")
// Handles GET requests to /product-service/getAllProducts.

@PostMapping("/addProduct")
// Handles POST requests to /product-service/addProduct.

@RequestBody Product product
// Deserialises the JSON body of the POST request into a Product object automatically.

@PathVariable("id") int prodNum
// Extracts the {id} segment from the URL path (e.g. /getProduct/1 → prodNum = 1).
```

**Jackson** (included with `spring-boot-starter-web`) handles the JSON serialisation automatically. When your method returns a `Product` object, Spring calls Jackson to convert it to:
```json
{
  "prodNum": 1,
  "skuCode": "APP2024",
  "pName": "Apple iPhone 16"
}
```

---

## 7. Input Validation — `@Valid` and `@NotBlank`

Input validation ensures that data coming into your API is well-formed before your business logic or database ever touches it. Without it, a blank product name could be saved to the database, or a missing field could cause a confusing NullPointerException deep in the code.

### How it works in this project

**Step 1 — Declare constraints on the model** (`Product.java`):
```java
import jakarta.validation.constraints.NotBlank;

@NotBlank(message = "SKU code must not be blank")
private String skuCode;

@NotBlank(message = "Product name must not be blank")
private String pName;
```

`@NotBlank` rejects three kinds of bad input:
| Input | Rejected? |
|-------|-----------|
| `null` | ✅ Yes |
| `""` (empty string) | ✅ Yes |
| `"   "` (whitespace only) | ✅ Yes |
| `"Apple"` | ❌ No — valid |

Other useful Jakarta validation annotations (not used here, but worth knowing):
- `@NotNull` — rejects null only (allows empty strings)
- `@Size(min=2, max=50)` — validates string/collection length
- `@Min(1)` / `@Max(100)` — validates numeric ranges
- `@Email` — validates email format
- `@Pattern(regexp="...")` — validates against a regular expression

**Step 2 — Trigger validation on the controller** (`ProductController.java`):
```java
@PostMapping("/addProduct")
public ResponseEntity<Product> addProduct(@Valid @RequestBody Product product) {
    // If @NotBlank is violated, this method is NEVER called.
    // Spring throws MethodArgumentNotValidException before reaching here.
}
```

`@Valid` tells Spring: before passing this object to the method, run all the validation constraints declared on it. If any fail, throw `MethodArgumentNotValidException`.

**Step 3 — Handle the validation failure** (`GlobalExceptionHandler.java`):
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ErrorResponse handleValidation(MethodArgumentNotValidException ex, ...) {
    // Collects all violated fields into one readable message
}
```

Without the exception handler, Spring would return a generic 400 with a long internal stack trace. With it, the client gets:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "pName: Product name must not be blank",
  "path": "/product-service/addProduct"
}
```

**The dependency** — in Spring Boot 3, validation is NOT included in the web starter. It must be added explicitly to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## 8. Error Handling — `@RestControllerAdvice`

Without a global exception handler, Spring Boot returns a generic `500 Internal Server Error` for most exceptions — even when the real problem is a missing resource (which should be `404`) or bad input (which should be `400`). A REST API should always return the correct HTTP status code and a consistent, parseable error body.

### `@RestControllerAdvice`

```java
@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {
    // handles exceptions from ALL @RestController classes in the application
}
```

`@RestControllerAdvice` is a global "catch" that sits between your controllers and Spring's default error handling. When an exception propagates out of a controller, Spring checks this class before showing any default error page.

### Exception → HTTP status mapping

| Exception | HTTP Status | When it happens |
|-----------|------------|-----------------|
| `NoSuchElementException` | `404 Not Found` | `getProduct(99)` when ID 99 doesn't exist |
| `MethodArgumentNotValidException` | `400 Bad Request` | `@Valid` fails on the request body |

### The `ErrorResponse` Java Record

```java
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {}
```

A **Java Record** (Java 16+) is a concise, immutable data class. The compiler generates the constructor, getters, `equals()`, `hashCode()`, and `toString()` automatically. It is perfect for simple data transfer objects like this error response.

Every error from this API returns the same JSON shape:
```json
{
  "timestamp": "2024-08-16T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 99",
  "path": "/product-service/getProduct/99"
}
```

This consistency is important — client applications can always parse errors the same way regardless of what went wrong.

### `ResponseEntity` — controlling the status code

Some endpoints need to return a specific HTTP status that differs from the default `200 OK`:

```java
// POST → 201 Created (not 200 OK — a new resource was created)
return ResponseEntity.status(HttpStatus.CREATED).body(saved);

// DELETE → 204 No Content (success, but nothing to return)
return ResponseEntity.noContent().build();
```

`ResponseEntity<T>` wraps your return value and lets you set the status code, headers, and body independently. It is the standard way to return non-200 responses from a Spring MVC controller.

---

## 9. PostgreSQL and the Datasource

**PostgreSQL** is the relational database used by this project. It stores the `product` table.

**Datasource configuration** in `application.properties`:
```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/postgres}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:admin}
```

**The `${ENV_VAR:default}` syntax:**
- Spring reads the environment variable first.
- If the environment variable is not set, the value after `:` is used as the default.
- This means when running with Docker Compose, the `SPRING_DATASOURCE_URL` environment variable overrides the localhost default automatically — no code change needed.

**The JDBC URL format:**
```
jdbc:postgresql://localhost:5432/postgres
                 ^^^^^^^^^^ ^^^^ ^^^^^^^
                 host       port  database name
```

**`data.sql` — Seed data:**
```sql
INSERT INTO product(prod_num, p_name, sku_code) VALUES (1, 'Apple iPhone 16', 'APP2024') ON CONFLICT (prod_num) DO NOTHING;
INSERT INTO product(prod_num, p_name, sku_code) VALUES (2, 'Samsung S24', 'SAM2024') ON CONFLICT (prod_num) DO NOTHING;
ALTER SEQUENCE product_seq RESTART WITH 3;
```
- Runs automatically on startup because `spring.sql.init.mode=always`.
- `ON CONFLICT DO NOTHING` makes it safe to run repeatedly — if the row already exists, it is skipped.
- `ALTER SEQUENCE ... RESTART WITH 3` ensures the next auto-generated ID starts at 3, not 1, since rows 1 and 2 were inserted manually.

---

## 10. Docker and Docker Compose

### Docker

Docker packages an application and all its dependencies into a **container** — a lightweight, isolated environment that runs consistently on any machine.

**Multi-stage `Dockerfile`:**
```dockerfile
# Stage 1: Build — needs Maven + full JDK to compile the source code
FROM maven:3.9.8-eclipse-temurin-21 AS buildstage
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests   # produces target/product-service-docker-postgres-0.0.1-SNAPSHOT.jar

# Stage 2: Runtime — only needs a JRE to run the compiled JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=buildstage /app/target/*.jar ./app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage?**
The build image is ~600 MB (Maven + JDK + source code + all downloaded dependencies).
The runtime image only needs the JRE and the final `.jar` file, resulting in a ~200 MB image.
A smaller image is faster to pull, uses less disk space, and has a smaller attack surface.

### Docker Compose

Docker Compose defines and runs multiple containers together using a single `docker-compose.yaml` file.

```yaml
services:
  db:                              # the PostgreSQL container
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_DB_PWD}  # read from your shell environment
      POSTGRES_DB: productDb

  backend:                         # the Spring Boot app container
    depends_on:
      - db                         # starts db first (but see FAQ about readiness)
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/productDb
      #                                         ^^
      #                                         "db" is the service name above.
      #                                         Docker Compose creates an internal DNS
      #                                         so containers can find each other by name.
    build:
      dockerfile: Dockerfile       # builds the image from the Dockerfile in this project
    restart: always                # restarts the app if it crashes (e.g. DB not ready yet)
```

**Key concept — Docker networking:**
Inside Docker Compose, containers communicate using their **service name** as the hostname.
The Spring Boot app connects to `jdbc:postgresql://db:5432/productDb` — `db` resolves to
the PostgreSQL container's IP address automatically. From your Mac, you still use `localhost`.

---

## 11. Managing Secrets — `.env` and `.env.example`

### The problem: secrets should never live in code

Imagine you hardcode the database password directly in `application.properties`:
```properties
spring.datasource.password=mySecretPassword123
```

You commit this file. Now:
- The password is visible to **everyone** who has access to the repository — teammates, GitHub, CI systems.
- It is in git history **forever**, even if you delete it later.
- Every environment (local, staging, production) shares the same password — you cannot change one without changing all.

This is one of the most common and serious security mistakes in software development.

---

### The solution: environment variables

Instead of putting the value in code, you reference a variable name:
```properties
# application.properties
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:admin}
#                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
#                            "read from the environment variable SPRING_DATASOURCE_PASSWORD,
#                             and use 'admin' as the fallback if the variable is not set"
```

The actual value lives outside the codebase — in the **environment** — and is injected at runtime. This is the core idea behind [The 12-Factor App](https://12factor.net/config), a widely adopted set of principles for building modern applications.

---

### The `.env` file — local convenience

Typing `export POSTGRES_DB_PWD=secret` in your terminal every time you work on the project is tedious. A `.env` file is the standard solution: it stores your local variable values in one place, and Docker Compose reads it automatically.

```dotenv
# .env  (your local machine — NEVER committed to git)
POSTGRES_DB_PWD=myLocalPassword
```

When you run `docker compose up`, Docker Compose reads `.env` in the same directory and injects those values as if you had exported them in your shell.

**Critical rule:** `.env` is listed in `.gitignore` so it is never committed:
```
# .gitignore
.env
.env.*
```

---

### The `.env.example` file — the documented template

If `.env` is never committed, how does a new team member know what variables they need to set?

That is exactly what `.env.example` solves. It is committed to the repository with **placeholder values** (not real secrets), documenting every required variable:

```dotenv
# .env.example  (committed to git — safe because it has no real secrets)
POSTGRES_DB_PWD=changeme
```

The workflow for anyone cloning the project:
```bash
# Step 1: copy the template
cp .env.example .env

# Step 2: open .env and replace the placeholder with a real value
# POSTGRES_DB_PWD=changeme  →  POSTGRES_DB_PWD=myActualPassword

# Step 3: run the project — Docker Compose picks up .env automatically
docker compose up
```

---

### Why `changeme` and not `admin`?

The placeholder value matters. If you use `admin`:
- A developer might forget to change it and run with `admin` in a staging or production environment.
- If it ever leaks, `admin` is the first thing an attacker tries.

`changeme` (or `your-secret-here`, `replace-me`) is unambiguous — it signals this is a template, not a working value.

---

### The full picture in this project

```
.env.example   ← committed to git, placeholder values, documents what is needed
.env           ← git-ignored, real values, only on your local machine

docker-compose.yaml reads .env:
  POSTGRES_PASSWORD: ${POSTGRES_DB_PWD}      ← Postgres container
  SPRING_DATASOURCE_PASSWORD: ${POSTGRES_DB_PWD}  ← Spring Boot container

application.properties has a fallback for running without Docker:
  spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:admin}
```

---

### Is `.env.example` industry standard?

Yes — you will find this exact pattern in:
- Most open-source projects on GitHub (Rails, Django, Laravel, Node.js, Go)
- The official Docker documentation
- Every major CI/CD platform (GitHub Actions, GitLab CI, CircleCI)
- Enterprise Java projects using Spring Boot

It is one of those conventions that, once you know it, you will see everywhere.

---

## 12. Testing Concepts

### Test slices vs full context

Spring Boot provides *test slices* — annotations that load only part of the application context. This makes tests faster because fewer beans need to be created.

| Annotation | What is loaded | Typical use |
|------------|---------------|-------------|
| `@DataJpaTest` | JPA layer only (entities, repositories, Hibernate) | Testing repositories and custom queries |
| `@WebMvcTest` | Web layer only (controllers, filters) — no DB | Testing controller logic with mocked services |
| `@SpringBootTest` | Everything — full application context | End-to-end integration tests |

### `@DataJpaTest` in detail

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MyRepositoryTest {
    // - Only JPA beans are loaded (fast).
    // - Each @Test runs in a transaction that is rolled back at the end.
    //   This means test data never accumulates or affects other tests.
    // - replace = NONE: do not swap the datasource for H2.
    //   Use the real PostgreSQL provided by Testcontainers instead.
}
```

### `@SpringBootTest` in detail

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyIntegrationTest {
    // - Full application context: controllers + services + repositories + JPA + web server.
    // - RANDOM_PORT: starts Tomcat on a random free port.
    // - Slower than @DataJpaTest but tests the complete stack end-to-end.
}
```

### MockMvc

MockMvc is a Spring test utility that lets you test HTTP endpoints without starting a real server.
It talks directly to Spring's `DispatcherServlet` — the front controller that routes requests.

```java
@Autowired
private MockMvc mockMvc;

@Test
void shouldReturnOk() throws Exception {
    mockMvc
        .perform(get("/product-service/getAllProducts"))  // build and send a GET request
        .andExpect(status().isOk())                      // assert HTTP 200
        .andExpect(jsonPath("$").isArray());              // assert response is a JSON array
}
```

The call chain: `mockMvc.perform()` → `DispatcherServlet` → `ProductController` → `ProductService` → `ProductRepository` → database.
Everything is real except the HTTP transport layer — the request is made in-process.

### `@BeforeAll` and `@AfterAll`

```java
@BeforeAll
static void setup() {
    // Runs ONCE before the first test method in the class.
    // Must be static — the test instance does not exist yet.
    // Use for expensive setup like starting a container.
}

@AfterAll
static void teardown() {
    // Runs ONCE after the last test method in the class.
    // Use for cleanup like stopping a container.
}
```

### Transaction rollback in tests

`@DataJpaTest` wraps each test method in a transaction and rolls it back afterwards.
This means data inserted in one test is never visible to another test.

```java
@Test
void testA() {
    entityManager.persist(new Product("Test", "SKU1")); // inserted
    // test runs...
    // transaction rolls back — product is deleted
}

@Test
void testB() {
    // the product from testA does not exist here
}
```

---

## 13. Testcontainers — All Four Approaches

> See [Testcontainers.md](./Testcontainers.md) for the full deep dive.
> This section is a quick reference connecting each approach to its key annotations.

### Why not use H2?

H2 is an in-memory database often used in tests. The problem:
- H2 is not PostgreSQL. SQL syntax differences can cause tests to pass with H2 but fail in production with Postgres.
- The whole point of integration tests is to test against *real* infrastructure.

Testcontainers starts a **real PostgreSQL Docker container** for each test run. Your tests connect to the same database engine as production.

### The four approaches at a glance

**Approach 1 — Manual (`ProductRepositoryTestContainerManualStartTest`)**
```
Container declared → started in @BeforeAll → stopped in @AfterAll
@DynamicPropertySource injects the random port URL into Spring
```

**Approach 2 — Automatic (`ProductRepositoryTestContainerTest`)**
```
@Testcontainers + @Container → start/stop automated by the JUnit 5 extension
@DynamicPropertySource injects the random port URL into Spring
```

**Approach 3 — TC JDBC URL (`ProductRepositoryWithTCJdbcUrlTest`)**
```
No container declared. The jdbc:tc: URL tells the Testcontainers JDBC driver
to start and manage the container transparently. No @DynamicPropertySource needed.
```

**Approach 4 — Shared Initializer (`ProductControllerIntegrationUsesInitializerTest`)**
```
PostgresDatabaseContainerInitializer starts the container once.
Any test class using @ContextConfiguration(initializers = {...}) gets the same container.
TestPropertyValues injects connection details into each test's Spring Environment.
.withReuse(true) keeps the container alive between test runs on the same machine.
```

### `@DynamicPropertySource` — why it must be static

The Postgres container starts on a random port. Spring's context is created before
the test instance is created. To inject the random port into Spring's environment,
the method must be `static` — it runs during context initialization, before any
instance methods can be called.

```java
@DynamicPropertySource  // must be static — runs at context creation time
static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgresqlContainer::getJdbcUrl);
    //                                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //                                     method reference — resolved lazily
    //                                     after container starts
}
```

### `.withReuse(true)` — speed optimisation

Without reuse, Testcontainers stops the container when the JVM exits. The next time you run
tests, a new container starts from scratch (a few seconds).

With `.withReuse(true)`, Testcontainers keeps the container alive after the JVM exits.
The next test run detects the existing container and connects to it immediately.

To enable reuse on your machine, add this to `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

---

## 14. AssertJ

AssertJ is a fluent assertion library included with `spring-boot-starter-test`.
It produces readable test code and clear error messages when assertions fail.

```java
import static org.assertj.core.api.Assertions.assertThat;

// Basic assertions
assertThat(result).isNotNull();
assertThat(result).isEqualTo("expected");
assertThat(list).hasSize(2);
assertThat(list).isEmpty();
assertThat(list).contains(item1, item2);
assertThat(value).isGreaterThan(0);
assertThat(text).startsWith("Hello");
```

**Why not JUnit's built-in `assertEquals`?**
AssertJ's error messages are far more descriptive:

```
// JUnit assertEquals failure:
expected: <2> but was: <3>

// AssertJ failure:
Expected size: 2 but was: 3
List: [Product{prodNum=1, skuCode='SAM2024', ...},
       Product{prodNum=2, skuCode='SAM2024', ...},
       Product{prodNum=3, skuCode='SAM2024', ...}]
```

---

## 15. How Everything Connects on Startup

Here is the complete sequence from `docker compose up` to a working API:

```
1. Docker Compose reads docker-compose.yaml

2. "db" service starts:
   - Pulls postgres:16 image from Docker Hub (first time only)
   - Creates a container running PostgreSQL
   - Creates the "productDb" database
   - Postgres listens on port 5432 inside the Docker network

3. "backend" service starts (after db):
   - Docker builds the image using the Dockerfile:
     a. Stage 1: Maven compiles the source code → creates the .jar file
     b. Stage 2: copies the .jar into a lean JRE image
   - Container starts and runs: java -jar app.jar

4. Spring Boot initialises:
   a. @SpringBootApplication triggers component scanning
   b. Auto-configuration detects spring-boot-starter-data-jpa and spring-boot-starter-web
   c. Reads application.properties — SPRING_DATASOURCE_URL env var overrides the localhost default
   d. Creates a connection pool to Postgres at jdbc:postgresql://db:5432/productDb
   e. Hibernate reads the @Entity classes and runs DDL (creates the "product" table if missing)
   f. data.sql runs — inserts the two seed rows if they do not already exist
   g. Embedded Tomcat starts on port 8081
   h. All @RestController beans are registered with Spring MVC

5. API is ready:
   GET  http://localhost:8081/product-service/getAllProducts  → returns seeded products as JSON
   POST http://localhost:8081/product-service/addProduct     → saves a new product to Postgres
   GET  http://localhost:8081/product-service/getProduct/1   → returns product with id 1
```

