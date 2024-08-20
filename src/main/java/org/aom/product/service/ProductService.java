package org.aom.product.service;

import org.aom.product.model.Product;
import org.aom.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Service
public class ProductService {
    private ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product saveProduct(Product product){
        return this.productRepository.save(product);
    }

    public Product getSingleProduct(int prodNum){
        return this.productRepository.findById(prodNum).get();
    }

    public List<Product> getAllProducts() {
        return this.productRepository.findAll();
    }
}
