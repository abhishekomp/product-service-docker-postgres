package org.aom.product.repository;

import jakarta.persistence.EntityManager;
import org.aom.product.model.Product;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Approach 1 — MANUAL container lifecycle.
 *
 * <p>This is the most explicit approach. You are fully responsible for starting
 * and stopping the Docker container yourself using @BeforeAll and @AfterAll.
 *
 * <p>The goal: test the JPA/database layer in isolation against a real PostgreSQL
 * database that lives inside a Docker container, without needing Postgres installed locally.
 *
 * <p>Compare with ProductRepositoryTestContainerTest (Approach 2) to see how
 * @Testcontainers + @Container automates exactly what is done manually here.
 */

// @DataJpaTest:
//   Loads only the JPA/database slice of the Spring context.
//   This means only @Entity classes, @Repository beans, and JPA configuration are loaded.
//   The web layer (controllers) and service layer are NOT loaded — making the test faster.
//   Hibernate will create the schema (DDL) based on the @Entity classes.
@DataJpaTest

// @AutoConfigureTestDatabase(replace = NONE):
//   By default, @DataJpaTest replaces your configured datasource with an embedded
//   in-memory H2 database. This is convenient but dangerous — H2 is not PostgreSQL
//   and SQL dialects can differ. replace = NONE says: do NOT replace the datasource.
//   Use whatever datasource we configure ourselves (the Testcontainers container below).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTestContainerManualStartTest {

    // Declare the PostgreSQL container.
    // PostgreSQLContainer is a Testcontainers class that knows how to start a Postgres Docker image.
    // DockerImageName.parse("postgres:16") specifies the exact Docker image to use.
    // The container is declared static so it is shared across all test methods in this class
    // (started once, not once per test method — much faster).
    // NOTE: No @Container annotation here — lifecycle is managed manually below.
    static PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private ProductRepository productRepository;

    // EntityManager is the low-level JPA API for interacting with the persistence context.
    // Here it is used to insert test data directly (bypassing the repository layer)
    // so the test data is within the same transaction as the test.
    @Autowired
    private EntityManager entityManager;

    // @BeforeAll: JUnit 5 annotation — this method runs ONCE before any test method in the class.
    // It must be static because it runs before the test instance is created.
    // We manually call .start() to tell Docker to pull and start the postgres:16 container.
    // Testcontainers will block here until the container is fully ready to accept connections.
    @BeforeAll
    static void beforeAll() {
        postgresqlContainer.start();
    }

    // @AfterAll: JUnit 5 annotation — this method runs ONCE after all test methods have finished.
    // We manually call .stop() to shut down the Docker container and free resources.
    @AfterAll
    static void afterAll() {
        postgresqlContainer.stop();
    }

    // @DynamicPropertySource:
    //   The Postgres container starts on a RANDOM port chosen by Docker at runtime.
    //   Spring needs to know the URL, username, and password to connect.
    //   @DynamicPropertySource lets us inject those values into Spring's Environment
    //   AFTER the container has started and its port is known, but BEFORE the Spring
    //   context finishes building.
    //
    //   This method must be static because it runs during context creation,
    //   before any test instance exists.
    //
    //   postgresqlContainer::getJdbcUrl is a method reference — it calls getJdbcUrl()
    //   on the container object lazily, at the moment Spring resolves the property.
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
    }

    @Test
    void findAllSamsungProducts() {
        // Persist three products directly via EntityManager.
        // The first product has skuCode "HUW2024" (Huawei) — should NOT be returned.
        // The next two have skuCode "SAM2024" (Samsung) — SHOULD be returned.
        entityManager.persist(new Product("HUW2024", "HUW2024"));
        entityManager.persist(new Product("Samsung Majestic", "SAM2024"));
        entityManager.persist(new Product("Samsung Fold", "SAM2024"));

        // Call the custom JPQL query defined in ProductRepository.
        // It filters by skuCode = 'SAM2024' so only the 2 Samsung products are returned.
        List<Product> allSamsungProducts = productRepository.findAllSamsungProducts();

        // assertThat is from AssertJ — a fluent assertion library included with Spring Boot Test.
        // hasSize(2) verifies exactly 2 products were returned (not 3 — the Huawei is excluded).
        assertThat(allSamsungProducts).hasSize(2);
    }
}