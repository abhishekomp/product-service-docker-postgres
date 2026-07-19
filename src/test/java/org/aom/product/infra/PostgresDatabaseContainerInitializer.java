package org.aom.product.infra;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Testcontainers Approach 4 — Shared Custom ApplicationContextInitializer.
 *
 * <p>This class is NOT a test itself — it is a reusable helper that any test class
 * can reference via @ContextConfiguration(initializers = {PostgresDatabaseContainerInitializer.class}).
 *
 * <p>Problem it solves: Approaches 1, 2, and 3 define the container inside each test class.
 * If you have 10 test classes, you would duplicate the container setup 10 times.
 * This class defines the container ONCE and shares it across all test classes that need it.
 *
 * <p>How it works:
 *   - The container is a static field — it is shared at the JVM level across all test classes.
 *   - A static initializer block starts the container once when this class is first loaded.
 *   - The initialize() method is called by Spring for each test context that uses this initializer,
 *     injecting the container's connection details into that context's environment.
 */

// ApplicationContextInitializer<ConfigurableApplicationContext>:
//   A Spring callback interface. Spring calls initialize() during application context creation,
//   before any beans are loaded. This is the earliest point where you can inject properties.
//   It is the alternative to @DynamicPropertySource when you want the logic in a separate class.
public class PostgresDatabaseContainerInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // The container is private static final — one instance, shared across all test classes
    // that use this initializer within the same JVM (test run).
    //
    // .withDatabaseName(), .withUsername(), .withPassword() configure the Postgres container.
    //
    // .withReuse(true):
    //   Instructs Testcontainers to keep this container alive between test runs instead of
    //   stopping it when the JVM exits. On the next run, Testcontainers checks if a matching
    //   container is already running and reuses it — avoiding the startup time.
    //   Useful for fast local development. In CI pipelines, containers are typically not reused
    //   so each build starts fresh and clean.
    //   Note: reuse requires enabling it in ~/.testcontainers.properties: testcontainers.reuse.enable=true
    private static final PostgreSQLContainer<?> sqlContainer =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("sa")
                    .withPassword("sa")
                    .withReuse(true);

    // Static initializer block:
    //   Runs ONCE when this class is first loaded by the JVM.
    //   Starts the container before any test class can call initialize().
    //   Because it is static, there is no risk of starting the container multiple times
    //   even when many test classes use this initializer.
    static {
        sqlContainer.start();
    }

    // initialize() is called by Spring for every test ApplicationContext that declares
    // this class as an initializer via @ContextConfiguration(initializers = {...}).
    //
    // TestPropertyValues.of(...).applyTo(context.getEnvironment()):
    //   Injects key=value pairs into the Spring Environment of the given context.
    //   This is the equivalent of @DynamicPropertySource but done via an initializer
    //   instead of a static method on the test class.
    //   The values are taken from the already-running container (getJdbcUrl(), etc.)
    //   so the random port is already known by the time this runs.
    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertyValues.of(
                "spring.datasource.url="      + sqlContainer.getJdbcUrl(),
                "spring.datasource.username=" + sqlContainer.getUsername(),
                "spring.datasource.password=" + sqlContainer.getPassword()
        ).applyTo(configurableApplicationContext.getEnvironment());
    }
}
