package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.RiskAppetite;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LenderPreferenceResponse {
    private Long id;
    private Long lenderId;
    private String lenderName;
    private Long loanProductId;
    private String loanProductName;
    private BigDecimal minInterestRate;
    private BigDecimal maxInterestRate;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;
    private BigDecimal minLoanAmount;
    private BigDecimal maxLoanAmount;
    private RiskAppetite riskAppetite;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Schema(example = "5",
            description = "Lender prefers EMI payment on this day of month. Null = flexible.")
    private Integer preferredPaymentDay;
}