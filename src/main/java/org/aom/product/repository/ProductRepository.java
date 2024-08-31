package org.aom.product.repository;

import org.aom.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Query("SELECT p FROM Product p where p.skuCode = 'SAM2024'")
    List<Product> findAllSamsungProducts();
}
