package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.EmiStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmiScheduleResponse {

    private Long      id;
    private Long      loanSummaryId;
    private Integer   emiNumber;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private BigDecimal paidAmount;
    private BigDecimal emiAmount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal outstandingPrincipal;
    private EmiStatus status;

    /** Late penalty charged (0 if paid within grace period). */
    private BigDecimal penaltyAmount;

    /**
     * FIX 1 — Was always null; now populated by EmiPaymentService.
     * Total actually debited from borrower account this payment cycle.
     */
    private BigDecimal totalPaid;

    /**
     * FIX 1 — Was always null; now populated by EmiPaymentService.
     * Non-zero only when status = PARTIAL.
     */
    private BigDecimal shortfall;

    /**
     * ADDITION — Answers "how much do I owe right now on this EMI?"
     *  - PARTIAL : shortfall (how much is still remaining)
     *  - PENDING  : full emiAmount
     *  - PAID     : 0
     */
    private BigDecimal amountStillDue;

    private String message;
}