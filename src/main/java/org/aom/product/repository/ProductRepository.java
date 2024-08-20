package org.aom.product.repository;

import org.aom.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
}
