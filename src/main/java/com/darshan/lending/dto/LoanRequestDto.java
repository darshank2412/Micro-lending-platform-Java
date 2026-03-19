package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.LoanPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Request to submit a loan application")
public class LoanRequestDto {

    @NotNull(message = "Loan product ID is required")
    @Schema(example = "1", description = "ID of the loan product to apply for")
    private Long loanProductId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is Rs.1,000")
    @DecimalMax(value = "5000000.00", message = "Maximum loan amount is Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Amount supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "50000.00", description = "Loan amount between Rs.1,000 and Rs.50,00,000")
    private BigDecimal amount;

    @NotNull(message = "Tenure is required")
    @Min(value = 6, message = "Minimum tenure is 6 months")
    @Max(value = 60, message = "Maximum tenure is 60 months")
    @Schema(example = "12", description = "Loan tenure in months (6 to 60)")
    private Integer tenureMonths;

    @NotNull(message = "Purpose is required")
    @Schema(example = "EDUCATION", description = "EDUCATION, MEDICAL, TRAVEL, SMALL_BUSINESS, EMERGENCY, OTHER")
    private LoanPurpose purpose;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(example = "College tuition fees for 2026", description = "Optional description of loan purpose")
    private String purposeDescription;
}
