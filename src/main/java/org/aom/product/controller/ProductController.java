package org.aom.product.controller;

import org.aom.product.model.Product;
import org.aom.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@RestController
@RequestMapping("/product-service")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;

    @Value("${myproperty.greeting}")
    private String myGreeting;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/addProduct")
    public Product addProduct(@RequestBody Product product){
        //System.out.println("received product: " + product);
        logger.info("Fetched my greeting from properties file: {}", myGreeting);
        return this.productService.saveProduct(product);
    }

    @GetMapping("/getProduct/{id}")
    public Product getProduct(@PathVariable("id") int prodNum){
        return this.productService.getSingleProduct(prodNum);
    }

    @GetMapping("/getAllProducts")
    public List<Product> getAllProducts() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>> controller method getAllProducts was invoked");
        return productService.getAllProducts();
    }

}
