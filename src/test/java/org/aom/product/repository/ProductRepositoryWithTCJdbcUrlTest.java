package org.aom.product.repository;

import jakarta.persistence.EntityManager;
import org.aom.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Approach 3 — TC JDBC URL.
 *
 * <p>This is the most minimal approach. There is no container object declared anywhere.
 * Instead, a special Testcontainers JDBC URL format tells the Testcontainers JDBC driver
 * to start a Docker container automatically and transparently.
 *
 * <p>The trade-off: less code, but less control over container configuration.
 */

// @DataJpaTest: loads only the JPA slice (entities + repositories), not the full app context.
@DataJpaTest

// @AutoConfigureTestDatabase(replace = NONE):
//   Prevents @DataJpaTest from replacing the datasource with H2.
//   Our TC JDBC URL (set below) must reach the actual datasource configuration.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)

// @TestPropertySource:
//   Overrides specific Spring properties for this test class only.
//   Here it replaces the datasource URL with a special "TC JDBC URL".
//
//   The TC JDBC URL format:
//     jdbc:tc:postgresql:16:///demodb
//          ^^              ^^^^^^^^^
//          ||              database name (arbitrary — Testcontainers creates it)
//          ||
//          "tc" — the Testcontainers JDBC driver intercepts any URL starting with jdbc:tc:
//
//   When the Testcontainers JDBC driver sees this URL it:
//     1. Pulls the postgres:16 Docker image (if not already cached)
//     2. Starts a container on a random free port
//     3. Waits until Postgres is ready
//     4. Rewrites the URL internally to the real jdbc:postgresql://localhost:RANDOM_PORT/demodb
//     5. Returns a connection to Spring as if nothing special happened
//
//   Because this all happens at the JDBC driver level, no @DynamicPropertySource is needed —
//   the URL itself IS the instruction.
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///demodb"
})
class ProductRepositoryWithTCJdbcUrlTest {

    // No container field — the TC JDBC URL handles everything automatically.

    @Autowired
    private ProductRepository productRepository;

    // EntityManager: the low-level JPA API used here to insert test data directly,
    // ensuring the data is visible within the same transaction as the query under test.
    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllSamsungProducts() {
        // Three products inserted: 1 Huawei (should be excluded) + 2 Samsung (should be returned).
        entityManager.persist(new Product("HUW2024", "HUW2024"));
        entityManager.persist(new Product("Samsung Majestic", "SAM2024"));
        entityManager.persist(new Product("Samsung Fold",     "SAM2024"));

        // Executes JPQL: SELECT p FROM Product p WHERE p.skuCode = 'SAM2024'
        List<Product> allSamsungProducts = productRepository.findAllSamsungProducts();

        // AssertJ assertion: exactly 2 Samsung products should be returned.
        assertThat(allSamsungProducts).hasSize(2);
    }
}