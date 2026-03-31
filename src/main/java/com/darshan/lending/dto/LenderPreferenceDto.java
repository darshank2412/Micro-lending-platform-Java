package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.RiskAppetite;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Lender preference request for a specific loan product")
public class LenderPreferenceDto {

    @NotNull(message = "Loan product ID is required")
    @Schema(example = "1", description = "ID of the loan product to set preference for")
    private Long loanProductId;

    @NotNull(message = "Min interest rate is required")
    @DecimalMin(value = "8.0", message = "Min interest rate cannot be less than 8%")
    @DecimalMax(value = "24.0", message = "Min interest rate cannot exceed 24%")
    @Digits(integer = 3, fraction = 2, message = "Interest rate supports up to 3 integer digits and 2 decimal places")
    @Schema(example = "10.00", description = "Minimum interest rate (8% to 24%)")
    private BigDecimal minInterestRate;

    @NotNull(message = "Max interest rate is required")
    @DecimalMin(value = "8.0", message = "Max interest rate cannot be less than 8%")
    @DecimalMax(value = "24.0", message = "Max interest rate cannot exceed 24%")
    @Digits(integer = 3, fraction = 2, message = "Interest rate supports up to 3 integer digits and 2 decimal places")
    @Schema(example = "20.00", description = "Maximum interest rate (8% to 24%)")
    private BigDecimal maxInterestRate;

    @NotNull(message = "Min tenure is required")
    @Min(value = 6, message = "Minimum tenure is 6 months")
    @Max(value = 60, message = "Maximum tenure is 60 months")
    @Schema(example = "6", description = "Minimum tenure in months (6 to 60)")
    private Integer minTenureMonths;

    @NotNull(message = "Max tenure is required")
    @Min(value = 6, message = "Minimum tenure is 6 months")
    @Max(value = 60, message = "Maximum tenure is 60 months")
    @Schema(example = "48", description = "Maximum tenure in months (6 to 60)")
    private Integer maxTenureMonths;

    @NotNull(message = "Min loan amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is Rs.1,000")
    @DecimalMax(value = "5000000.00", message = "Maximum loan amount is Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Loan amount supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "10000.00", description = "Minimum amount willing to lend")
    private BigDecimal minLoanAmount;

    @NotNull(message = "Max loan amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum loan amount is Rs.1,000")
    @DecimalMax(value = "5000000.00", message = "Maximum loan amount is Rs.50,00,000")
    @Digits(integer = 13, fraction = 2, message = "Loan amount supports up to 13 integer digits and 2 decimal places")
    @Schema(example = "200000.00", description = "Maximum amount willing to lend")
    private BigDecimal maxLoanAmount;

    @NotNull(message = "Risk appetite is required")
    @Schema(example = "MEDIUM", description = "LOW, MEDIUM, or HIGH")
    private RiskAppetite riskAppetite;


    @Min(value = 1, message = "Payment day must be between 1 and 28")
    @Max(value = 28, message = "Payment day must be between 1 and 28")
    @Schema(
            example = "5",
            description = "Preferred day of month for EMI payment (1-28). " +
                    "E.g. 5 means borrower should pay on 5th of every month. " +
                    "Leave null for no preference."
    )
    private Integer preferredPaymentDay;
}
