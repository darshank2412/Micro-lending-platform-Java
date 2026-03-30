package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.LoanStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanSummaryResponse {
    private Long id;
    private Long loanOfferId;
    private Long borrowerId;
    private String borrowerName;
    private Long lenderId;
    private String lenderName;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private BigDecimal totalRepaymentAmount;
    private BigDecimal totalInterestAmount;
    private BigDecimal outstandingPrincipal;
    private LocalDate disbursementDate;
    private LocalDate firstEmiDate;
    private LocalDate lastEmiDate;
    private Integer emisPaid;
    private Integer emisRemaining;
    private LoanStatus status;
    private LocalDateTime createdAt;
}