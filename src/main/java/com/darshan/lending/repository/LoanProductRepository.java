package com.darshan.lending.repository;

import com.darshan.lending.entity.LoanProduct;
import com.darshan.lending.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {
    List<LoanProduct> findByStatus(ProductStatus status);
    boolean existsByName(String name);
}