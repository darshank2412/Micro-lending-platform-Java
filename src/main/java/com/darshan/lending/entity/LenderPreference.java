package com.darshan.lending.entity;

import com.darshan.lending.entity.enums.RiskAppetite;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lender_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lender_preference_user_product",
                columnNames = {"lender_id", "loan_product_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LenderPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One lender → many preferences (one per loan product)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lender_preference_user"))
    private User lender;

    // Each preference is tied to a specific loan product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lender_preference_loan_product"))
    private LoanProduct loanProduct;

    @Column(name = "min_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal minInterestRate;

    @Column(name = "max_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxInterestRate;

    @Column(name = "min_tenure_months", nullable = false)
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months", nullable = false)
    private Integer maxTenureMonths;

    @Column(name = "min_loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minLoanAmount;

    @Column(name = "max_loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxLoanAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_appetite", nullable = false, length = 10)
    @Builder.Default
    private RiskAppetite riskAppetite = RiskAppetite.MEDIUM;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}