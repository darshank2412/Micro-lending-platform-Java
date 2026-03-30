package com.darshan.lending.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Foreclosureresponse {

    private Long        loanSummaryId;

    /** Outstanding principal at time of foreclosure */
    private BigDecimal  outstandingPrincipal;

    /** Interest accrued since last EMI payment / disbursement */
    private BigDecimal  accruedInterest;

    /** 2% of outstanding principal */
    private BigDecimal  foreclosureCharge;

    /** outstandingPrincipal + accruedInterest + foreclosureCharge */
    private BigDecimal  totalPaid;

    private LocalDate   foreclosureDate;

    private String      message;
}