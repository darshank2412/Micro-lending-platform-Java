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
    @DecimalMax(value = "5000000.00", message = "Minimum balance cannot exceed Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Balance supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "500.00")
    private BigDecimal minBalance;

    @NotNull(message = "Maximum balance is required")
    @DecimalMin(value = "1.00", message = "Maximum balance must be at least Rs.1")
    @DecimalMax(value = "5000000.00", message = "Maximum balance cannot exceed Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Balance supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "1000000.00")
    private BigDecimal maxBalance;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "2.00", message = "Interest rate cannot be less than 2%")
    @DecimalMax(value = "12.00", message = "Interest rate cannot exceed 12%")
    @Digits(integer = 3, fraction = 2, message = "Interest rate supports up to 3 integer digits and 2 decimal places")
    @Schema(example = "4.50", description = "Annual interest rate %")
    private BigDecimal interestRate;
}
