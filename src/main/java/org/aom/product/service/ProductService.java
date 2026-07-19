package org.aom.product.service;

import org.aom.product.model.Product;
import org.aom.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product saveProduct(Product product){
        return this.productRepository.save(product);
    }

    public Product getSingleProduct(int prodNum){
        return this.productRepository.findById(prodNum)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + prodNum));
    }

    public List<Product> getAllProducts() {
        return this.productRepository.findAll();
    }

    /**
     * Updates an existing product's name and SKU code.
     *
     * <p>The ID in the URL path is authoritative — the ID inside the request body
     * is ignored. This is the standard REST convention for PUT: the URL identifies
     * the resource, the body describes the new state.
     *
     * <p>Throws NoSuchElementException if no product exists with the given ID,
     * which GlobalExceptionHandler converts to a 404 response.
     *
     * @param prodNum the ID of the product to update (from the URL path)
     * @param updated the new field values (from the request body)
     * @return the saved, updated product
     */
    public Product updateProduct(int prodNum, Product updated) {
        // First verify the product exists — throws 404 via GlobalExceptionHandler if not.
        Product existing = this.productRepository.findById(prodNum)
                .orElseThrow(() -> new NoSuchElementException("Product not found with id: " + prodNum));

        // Apply the new values onto the existing managed entity.
        existing.setPName(updated.getPName());
        existing.setSkuCode(updated.getSkuCode());

        // save() on an entity with an existing ID performs an SQL UPDATE.
        return this.productRepository.save(existing);
    }

    /**
     * Deletes the product with the given ID.
     *
     * <p>Throws NoSuchElementException if no product exists with that ID,
     * which GlobalExceptionHandler converts to a 404 response.
     * This prevents silent deletes of non-existent resources.
     *
     * @param prodNum the ID of the product to delete
     */
    public void deleteProduct(int prodNum) {
        // Verify it exists before deleting — deleteById() on a missing ID would silently do nothing.
        if (!this.productRepository.existsById(prodNum)) {
            throw new NoSuchElementException("Product not found with id: " + prodNum);
        }
        this.productRepository.deleteById(prodNum);
    }
}
