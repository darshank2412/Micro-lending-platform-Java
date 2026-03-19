package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 200)
    @Schema(example = "Personal Loan")
    private String name;

    @NotNull(message = "Min amount is required")
    @DecimalMin(value = "1000.00", message = "Min loan amount must be at least Rs.1,000")
    @DecimalMax(value = "5000000.00", message = "Max loan amount cannot exceed Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Amount supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "10000.00")
    private BigDecimal minAmount;

    @NotNull(message = "Max amount is required")
    @DecimalMin(value = "1000.00", message = "Min loan amount must be at least Rs.1,000")
    @DecimalMax(value = "5000000.00", message = "Max loan amount cannot exceed Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Amount supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "500000.00")
    private BigDecimal maxAmount;

    @NotNull(message = "Min interest is required")
    @DecimalMin(value = "8.00", message = "Interest rate cannot be less than 8%")
    @DecimalMax(value = "24.00", message = "Interest rate cannot exceed 24%")
    @Digits(integer = 3, fraction = 2, message = "Interest rate supports up to 3 integer digits and 2 decimal places")
    @Schema(example = "8.00")
    private BigDecimal minInterest;

    @NotNull(message = "Max interest is required")
    @DecimalMin(value = "8.00", message = "Interest rate cannot be less than 8%")
    @DecimalMax(value = "24.00", message = "Interest rate cannot exceed 24%")
    @Digits(integer = 3, fraction = 2, message = "Interest rate supports up to 3 integer digits and 2 decimal places")
    @Schema(example = "24.00")
    private BigDecimal maxInterest;

    @NotNull(message = "Min tenure is required")
    @Min(value = 6, message = "Min tenure must be at least 6 months")
    @Max(value = 60, message = "Max tenure cannot exceed 60 months")
    @Schema(example = "6")
    private Integer minTenure;

    @NotNull(message = "Max tenure is required")
    @Min(value = 6, message = "Min tenure must be at least 6 months")
    @Max(value = 60, message = "Max tenure cannot exceed 60 months")
    @Schema(example = "60")
    private Integer maxTenure;
}
