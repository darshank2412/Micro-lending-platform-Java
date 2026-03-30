package com.darshan.lending.entity;

import com.darshan.lending.entity.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_summary")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The accepted loan offer this disbursement is based on */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_offer_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_loan_summary_offer"))
    private LoanOffer loanOffer;

    /** Borrower */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_summary_borrower"))
    private User borrower;

    /** Lender */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_summary_lender"))
    private User lender;

    /** Principal amount disbursed */
    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    /** Annual interest rate locked at disbursement time */
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    /** Tenure in months */
    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    /** Fixed EMI amount per month */
    @Column(name = "emi_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal emiAmount;

    /** Total amount to be repaid (EMI x tenure) */
    @Column(name = "total_repayment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRepaymentAmount;

    /** Total interest to be paid */
    @Column(name = "total_interest_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInterestAmount;

    /** Outstanding principal remaining */
    @Column(name = "outstanding_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal;

    /** Date loan was disbursed */
    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    /** Date first EMI is due */
    @Column(name = "first_emi_date", nullable = false)
    private LocalDate firstEmiDate;

    /** Date last EMI is due */
    @Column(name = "last_emi_date", nullable = false)
    private LocalDate lastEmiDate;

    /** EMIs paid so far */
    @Column(name = "emis_paid", nullable = false)
    @Builder.Default
    private Integer emisPaid = 0;

    /** Total EMIs remaining */
    @Column(name = "emis_remaining", nullable = false)
    private Integer emisRemaining;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}