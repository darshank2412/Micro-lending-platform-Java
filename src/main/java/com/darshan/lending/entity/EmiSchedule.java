package com.darshan.lending.entity;

import com.darshan.lending.entity.enums.EmiStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "emi_schedule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmiSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_summary_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_emi_loan_summary"))
    private LoanSummary loanSummary;

    /** EMI number — 1 to tenureMonths */
    @Column(name = "emi_number", nullable = false)
    private Integer emiNumber;

    /** Date this EMI is due */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Date this EMI was actually paid */
    @Column(name = "paid_date")
    private LocalDate paidDate;

    /** Total EMI amount = principal component + interest component */
    @Column(name = "emi_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal emiAmount;

    /** Principal portion of this EMI */
    @Column(name = "principal_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalComponent;

    /** Interest portion of this EMI */
    @Column(name = "interest_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestComponent;

    /** Outstanding principal after this EMI is paid */
    @Column(name = "outstanding_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmiStatus status = EmiStatus.PENDING;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}