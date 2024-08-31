package org.aom.product.repository;

import jakarta.persistence.EntityManager;
import org.aom.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllSamsungProducts() {

        entityManager.persist(new Product("HUW2024", "HUW2024"));
        entityManager.persist(new Product("Samsung Majestic", "SAM2024"));

        List<Product> allSamsungProducts = productRepository.findAllSamsungProducts();
        assertThat(allSamsungProducts).hasSize(1);
    }
}