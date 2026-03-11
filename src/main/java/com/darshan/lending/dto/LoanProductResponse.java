package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.ProductStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanProductResponse {
    private Long id;
    private String name;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal minInterest;
    private BigDecimal maxInterest;
    private Integer minTenure;
    private Integer maxTenure;
    private ProductStatus status;
    private LocalDateTime createdAt;
}