package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.EmiStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmiScheduleResponse {

    private Long        id;
    private Long        loanSummaryId;
    private Integer     emiNumber;
    private LocalDate   dueDate;
    private LocalDate   paidDate;
    private BigDecimal  emiAmount;
    private BigDecimal  principalComponent;
    private BigDecimal  interestComponent;
    private BigDecimal  outstandingPrincipal;
    private EmiStatus   status;

    // ── Populated only on pay-emi response ───────────────────────────────
    /** Late penalty charged (0 if paid within grace period) */
    private BigDecimal  penaltyAmount;

    /** emiAmount + penaltyAmount actually debited */
    private BigDecimal  totalPaid;


    private BigDecimal shortfall;
    private String message;
}