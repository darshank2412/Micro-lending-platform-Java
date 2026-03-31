package com.darshan.lending.entity;

import com.darshan.lending.entity.enums.LoanPurpose;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_request")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Borrower who raised this request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_request_borrower"))
    private User borrower;

    // Loan product this request is based on
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_request_product"))
    private LoanProduct loanProduct;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private LoanPurpose purpose;

    @Column(name = "purpose_description", length = 500)
    private String purposeDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoanRequestStatus status = LoanRequestStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    @Column(name = "preferred_emi_day")
    private Integer preferredEmiDay;
}