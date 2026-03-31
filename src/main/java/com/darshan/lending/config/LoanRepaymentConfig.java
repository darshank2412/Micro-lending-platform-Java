package com.darshan.lending.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * All loan repayment business rule values loaded from application.properties.
 * Change values in properties file — no code change needed.
 *
 * Properties prefix: loan.repayment
 */
@Component
@ConfigurationProperties(prefix = "loan.repayment")
@Getter
@Setter
public class LoanRepaymentConfig {

    /**
     * Number of days after EMI due date within which payment has no penalty.
     * Loan disbursed on 20th → EMI due 20th of next month → grace ends 23rd.
     * Default: 3
     */
    private int gracePeriodDays = 3;

    /**
     * Annual penal interest rate applied on overdue EMI amount after grace period.
     * Penalty = emiAmount × penalInterestRate × (daysLate / 365)
     * Default: 0.24 (24% per annum)
     */
    private BigDecimal penalInterestRate = new BigDecimal("0.24");

    /**
     * Foreclosure penalty as a fraction of outstanding principal.
     * Foreclosure charge = outstandingPrincipal × foreclosurePenaltyRate
     * Default: 0.02 (2%)
     */
    private BigDecimal foreclosurePenaltyRate = new BigDecimal("0.02");
}