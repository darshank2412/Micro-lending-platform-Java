package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavingsProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 200, message = "Name must be between 3 and 200 characters")
    @Schema(example = "Basic Savings Account")
    private String name;

    @NotNull(message = "Minimum balance is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Minimum balance cannot be negative")
    @Schema(example = "500.00")
    private BigDecimal minBalance;

    @NotNull(message = "Maximum balance is required")
    @DecimalMin(value = "1.00", message = "Maximum balance must be at least 1")
    @Schema(example = "1000000.00")
    private BigDecimal maxBalance;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Interest rate cannot be negative")
    @DecimalMax(value = "20.00", message = "Interest rate cannot exceed 20%")
    @Schema(example = "4.50", description = "Annual interest rate %")
    private BigDecimal interestRate;
}