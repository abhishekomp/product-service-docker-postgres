package org.aom.product.repository;

import jakarta.persistence.EntityManager;
import org.aom.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Approach 2 — AUTOMATIC container lifecycle via @Testcontainers + @Container.
 *
 * <p>This approach is identical in effect to Approach 1 (ManualStartTest) but the
 * @Testcontainers JUnit 5 extension automates the container start/stop for you.
 * You no longer need @BeforeAll or @AfterAll.
 *
 * <p>This is the most commonly recommended approach for most use cases.
 */

// @DataJpaTest:
//   Loads only the JPA slice of the Spring application context.
//   - Scans for @Entity classes and configures the JPA layer (Hibernate, EntityManager)
//   - Does NOT load @Service, @Controller, or any other beans
//   - Creates the database schema (DDL) via Hibernate based on your @Entity classes
//   - Runs each test in a transaction that is rolled back after the test finishes,
//     so test data never pollutes other tests
@DataJpaTest

// @AutoConfigureTestDatabase(replace = NONE):
//   @DataJpaTest by default swaps your real database for an embedded H2 in-memory database.
//   replace = NONE disables this swap so our real PostgreSQL container is used instead.
//   This is critical — without it, our @Container setup below would be ignored.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)

// @Testcontainers:
//   Registers the Testcontainers JUnit 5 extension.
//   The extension scans the test class for fields annotated with @Container and
//   automatically calls .start() before the tests run and .stop() after.
//   This is the ONLY difference from Approach 1 — it replaces the manual @BeforeAll/@AfterAll.
@Testcontainers
class ProductRepositoryTestContainerTest {

    // @Container:
    //   Marks this field for automatic lifecycle management by the @Testcontainers extension.
    //   The extension will call postgresqlContainer.start() before tests and .stop() after.
    //
    //   The field is static — this means ONE container is shared across all test methods
    //   in this class (started once before all tests, stopped once after all tests).
    //
    //   If this were a non-static (instance) field, a NEW container would start and stop
    //   for EVERY single test method — very slow with many tests.
    @Container
    static PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    // Spring injects the ProductRepository bean — wired to the Testcontainers database.
    @Autowired
    private ProductRepository productRepository;

    // EntityManager gives direct access to the JPA persistence context.
    // Using it here to insert test data without going through the repository,
    // keeping the test focused purely on the query being tested.
    @Autowired
    private EntityManager entityManager;

    // @DynamicPropertySource:
    //   Injects the container's connection details (URL, username, password) into
    //   Spring's Environment at context-creation time — after the container starts
    //   and its random port is known.
    //
    //   Must be static because Spring's context is set up before test instances exist.
    //   Uses method references (postgresqlContainer::getJdbcUrl) so the value is
    //   resolved lazily, after the container is fully started.
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
    }

    @Test
    void findAllSamsungProducts() {
        // Insert three products into the real Postgres container.
        // entityManager.persist() writes the object into the JPA persistence context
        // (and flushes it to the DB within the current transaction).
        entityManager.persist(new Product("HUW2024", "HUW2024"));        // Huawei — NOT Samsung
        entityManager.persist(new Product("Samsung Majestic", "SAM2024")); // Samsung ✓
        entityManager.persist(new Product("Samsung Fold",     "SAM2024")); // Samsung ✓

        // findAllSamsungProducts() runs the JPQL: SELECT p FROM Product p WHERE p.skuCode = 'SAM2024'
        // Only the 2 Samsung products match — the Huawei is excluded.
        List<Product> allSamsungProducts = productRepository.findAllSamsungProducts();
        assertThat(allSamsungProducts).hasSize(2);
    }
}