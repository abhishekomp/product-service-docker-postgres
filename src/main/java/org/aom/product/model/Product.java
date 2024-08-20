package org.aom.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Objects;

/**
 * @author : Abhishek
 * @since : 2024-08-16, Friday
 **/
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int prodNum;
    private String skuCode;
    private String pName;

    public Product() {
    }

    public Product(int prodNum, String pName, String skuCode) {
        this.prodNum = prodNum;
        this.pName = pName;
        this.skuCode = skuCode;
    }

    public String getpName() {
        return pName;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public int getProdNum() {
        return prodNum;
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
        return prodNum == product.prodNum && skuCode.equals(product.skuCode) && pName.equals(product.pName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prodNum, skuCode, pName);
    }
}
