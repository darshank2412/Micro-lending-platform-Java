package com.darshan.lending.repository;

import com.darshan.lending.entity.SavingsProduct;
import com.darshan.lending.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsProductRepository extends JpaRepository<SavingsProduct, Long> {
    List<SavingsProduct> findByStatus(ProductStatus status);
    boolean existsByName(String name);
}