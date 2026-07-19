package org.aom.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    private Integer prodNum;

    // @NotBlank: validation constraint — rejects null, empty strings, and whitespace-only strings.
    // Triggered when @Valid is placed on a @RequestBody parameter in the controller.
    // If the value is blank, Spring returns 400 Bad Request automatically (via GlobalExceptionHandler).
    @NotBlank(message = "SKU code must not be blank")
    private String skuCode;

    @NotBlank(message = "Product name must not be blank")
    private String pName;

    public Product() {
    }

    public Product(Integer prodNum, String pName, String skuCode) {
        this.prodNum = prodNum;
        this.pName = pName;
        this.skuCode = skuCode;
    }

    public Product(String pName, String skuCode) {
        this.pName = pName;
        this.skuCode = skuCode;
    }

    public Integer getProdNum() {
        return prodNum;
    }

    public void setProdNum(Integer prodNum) {
        this.prodNum = prodNum;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getPName() {
        return pName;
    }

    public void setPName(String pName) {
        this.pName = pName;
    }

    @Override
    public String toString() {
        return "Product{" +
                "prodNum=" + prodNum +
                ", skuCode='" + skuCode + '\'' +
                ", pName='" + pName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(prodNum, product.prodNum)
                && Objects.equals(skuCode, product.skuCode)
                && Objects.equals(pName, product.pName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prodNum, skuCode, pName);
    }
}
