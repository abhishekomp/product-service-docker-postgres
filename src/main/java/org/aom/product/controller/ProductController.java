package org.aom.product.controller;

import jakarta.validation.Valid;
import org.aom.product.model.Product;
import org.aom.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the full CRUD API for products.
 *
 * <p>All endpoints are prefixed with /product-service.
 * Error responses (404, 400) are handled centrally by GlobalExceptionHandler.
 *
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 */
@RestController
@RequestMapping("/product-service")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;

    // @Value: injects a property from application.properties into this field.
    // The value is resolved at startup from the key "myproperty.greeting".
    @Value("${myproperty.greeting}")
    private String myGreeting;

    // Constructor injection — Spring automatically provides the ProductService bean.
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Creates a new product.
     * POST /product-service/addProduct
     *
     * <p>Returns HTTP 201 Created with the saved product (including the generated ID).
     *
     * @param product the product to create — must have non-blank pName and skuCode
     * @return the persisted product with its generated prodNum
     */
    @PostMapping("/addProduct")
    // ResponseEntity<Product>: gives explicit control over the HTTP status code returned.
    // HttpStatus.CREATED = 201 — the standard REST code for a successful resource creation.
    public ResponseEntity<Product> addProduct(
            // @Valid: triggers Jakarta Bean Validation on the Product object.
            // Spring checks all @NotBlank constraints on the fields.
            // If any fail, MethodArgumentNotValidException is thrown BEFORE this method runs,
            // and GlobalExceptionHandler converts it to a 400 Bad Request response.
            @Valid @RequestBody Product product) {
        logger.info("Fetched my greeting from properties file: {}", myGreeting);
        Product saved = this.productService.saveProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Retrieves a single product by its ID.
     * GET /product-service/getProduct/{id}
     *
     * <p>Returns HTTP 200 OK with the product, or HTTP 404 if not found (via GlobalExceptionHandler).
     *
     * @param prodNum the product ID from the URL path
     * @return the matching product
     */
    @GetMapping("/getProduct/{id}")
    public Product getProduct(@PathVariable("id") int prodNum) {
        return this.productService.getSingleProduct(prodNum);
    }

    /**
     * Retrieves all products.
     * GET /product-service/getAllProducts
     *
     * <p>Returns HTTP 200 OK with a (possibly empty) JSON array.
     *
     * @return list of all products
     */
    @GetMapping("/getAllProducts")
    public List<Product> getAllProducts() {
        logger.info("controller method getAllProducts was invoked");
        return productService.getAllProducts();
    }

    /**
     * Updates an existing product's name and SKU code.
     * PUT /product-service/updateProduct/{id}
     *
     * <p>The ID in the URL is authoritative — the body describes the new field values.
     * Returns HTTP 200 OK with the updated product, or HTTP 404 if not found.
     *
     * @param prodNum the ID of the product to update (from the URL path)
     * @param product the new field values — must have non-blank pName and skuCode
     * @return the updated product
     */
    @PutMapping("/updateProduct/{id}")
    public Product updateProduct(
            @PathVariable("id") int prodNum,
            @Valid @RequestBody Product product) {
        logger.info("Updating product with id: {}", prodNum);
        return this.productService.updateProduct(prodNum, product);
    }

    /**
     * Deletes a product by its ID.
     * DELETE /product-service/deleteProduct/{id}
     *
     * <p>Returns HTTP 204 No Content on success — the standard REST response for a
     * successful DELETE (operation succeeded, nothing to return in the body).
     * Returns HTTP 404 if no product exists with the given ID.
     *
     * @param prodNum the ID of the product to delete
     * @return empty 204 response
     */
    @DeleteMapping("/deleteProduct/{id}")
    // ResponseEntity<Void>: Void signals there is no response body.
    // HttpStatus.NO_CONTENT = 204 — the REST standard for a successful DELETE.
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") int prodNum) {
        logger.info("Deleting product with id: {}", prodNum);
        this.productService.deleteProduct(prodNum);
        return ResponseEntity.noContent().build();
    }
}
