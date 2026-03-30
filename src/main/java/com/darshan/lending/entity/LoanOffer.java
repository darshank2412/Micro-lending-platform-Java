package com.darshan.lending.entity;

import com.darshan.lending.entity.enums.LoanOfferStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_offer",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_loan_offer_request_lender",
                columnNames = {"loan_request_id", "lender_id"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_offer_request"))
    private LoanRequest loanRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_offer_lender"))
    private User lender;

    @Column(name = "offered_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal offeredInterestRate;

    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoanOfferStatus status = LoanOfferStatus.PENDING;

    @Column(name = "match_rank", nullable = false)
    private Integer matchRank;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}