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
    @DecimalMin(value = "1000.00", message = "Min loan amount must be at least 1000")
    @Schema(example = "10000.00")
    private BigDecimal minAmount;

    @NotNull(message = "Max amount is required")
    @Schema(example = "500000.00")
    private BigDecimal maxAmount;

    @NotNull(message = "Min interest is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Interest rate cannot be negative")
    @Schema(example = "8.50")
    private BigDecimal minInterest;

    @NotNull(message = "Max interest is required")
    @DecimalMax(value = "36.00", message = "Interest rate cannot exceed 36%")
    @Schema(example = "24.00")
    private BigDecimal maxInterest;

    @NotNull(message = "Min tenure is required")
    @Min(value = 1, message = "Min tenure must be at least 1 month")
    @Schema(example = "6")
    private Integer minTenure;

    @NotNull(message = "Max tenure is required")
    @Max(value = 360, message = "Max tenure cannot exceed 360 months")
    @Schema(example = "60")
    private Integer maxTenure;
}