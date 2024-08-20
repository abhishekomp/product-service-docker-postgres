package org.aom.product.controller;

import org.aom.product.model.Product;
import org.aom.product.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@RestController
@RequestMapping("/product-service")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/addProduct")
    public Product addProduct(@RequestBody Product product){
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
