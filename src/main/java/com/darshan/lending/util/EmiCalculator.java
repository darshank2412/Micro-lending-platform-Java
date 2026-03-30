package com.darshan.lending.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * EMI Calculation using standard amortization formula:
 *
 *        P × r × (1 + r)^n
 * EMI = ─────────────────────
 *           (1 + r)^n - 1
 *
 * Where:
 *   P = principal amount
 *   r = monthly interest rate (annual rate / 12 / 100)
 *   n = tenure in months
 */
@Component
public class EmiCalculator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 2;

    /**
     * Calculate fixed monthly EMI
     */
    public BigDecimal calculateEmi(BigDecimal principal,
                                   BigDecimal annualRatePercent,
                                   int tenureMonths) {

        // monthly rate r = annualRate / 12 / 100
        BigDecimal monthlyRate = annualRatePercent
                .divide(BigDecimal.valueOf(12), MC)
                .divide(BigDecimal.valueOf(100), MC);

        // (1 + r)^n
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate, MC);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths, MC);

        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal numerator = principal.multiply(monthlyRate, MC)
                .multiply(onePlusRPowN, MC);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE, MC);

        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate monthly interest component
     * Interest = outstandingPrincipal × monthlyRate
     */
    public BigDecimal calculateInterestComponent(BigDecimal outstandingPrincipal,
                                                 BigDecimal annualRatePercent) {
        BigDecimal monthlyRate = annualRatePercent
                .divide(BigDecimal.valueOf(12), MC)
                .divide(BigDecimal.valueOf(100), MC);

        return outstandingPrincipal.multiply(monthlyRate, MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate principal component
     * Principal = EMI - Interest
     */
    public BigDecimal calculatePrincipalComponent(BigDecimal emiAmount,
                                                  BigDecimal interestComponent) {
        return emiAmount.subtract(interestComponent)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}