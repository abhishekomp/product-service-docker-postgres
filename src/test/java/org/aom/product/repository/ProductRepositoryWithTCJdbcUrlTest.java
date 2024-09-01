package org.aom.product.repository;

import jakarta.persistence.EntityManager;
import org.aom.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Testcontainers
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///demodb"
})
class ProductRepositoryWithTCJdbcUrlTest {

    //static PostgreSQLContainer<?> postgresqlContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"));
//    @Container
//    static PostgreSQLContainer<?> postgresqlContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

//    @BeforeAll
//    static void beforeAll() {
//        postgresqlContainer.start();
//    }
//
//    @AfterAll
//    static void afterAll() {
//        postgresqlContainer.stop();
//    }

//    @DynamicPropertySource
//    static void overrideProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgresqlContainer::getJdbcUrl);
//        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
//        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
//    }

    @Test
    void findAllSamsungProducts() {

        entityManager.persist(new Product("HUW2024", "HUW2024"));
        entityManager.persist(new Product("Samsung Majestic", "SAM2024"));
        entityManager.persist(new Product("Samsung Fold", "SAM2024"));

        List<Product> allSamsungProducts = productRepository.findAllSamsungProducts();
        assertThat(allSamsungProducts).hasSize(3);
    }
}