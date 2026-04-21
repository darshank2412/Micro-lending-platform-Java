package com.darshan.lending.repository;

import com.darshan.lending.entity.EmiSchedule;
import com.darshan.lending.entity.enums.EmiStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, Long> {

    List<EmiSchedule> findByLoanSummaryIdOrderByEmiNumberAsc(Long loanSummaryId);

    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status IN (com.darshan.lending.entity.enums.EmiStatus.PENDING, " +
            "                 com.darshan.lending.entity.enums.EmiStatus.OVERDUE, " +
            "                 com.darshan.lending.entity.enums.EmiStatus.PARTIAL) " +
            "ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingOrOverdueEmis(@Param("loanSummaryId") Long loanSummaryId);

    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status = :status " +
            "ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);

    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.dueDate < :today AND e.status = :status")
    List<EmiSchedule> findOverdueEmis(
            @Param("today") LocalDate today,
            @Param("status") EmiStatus status);

    @Modifying
    @Query("UPDATE EmiSchedule e SET e.status = :newStatus " +
            "WHERE e.dueDate < :today AND e.status = :oldStatus")
    int markOverdueEmis(
            @Param("today") LocalDate today,
            @Param("oldStatus") EmiStatus oldStatus,
            @Param("newStatus") EmiStatus newStatus);

    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = :status")
    int countPaidEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);

    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "JOIN e.loanSummary s WHERE s.borrower.id = :borrowerId AND e.status = :status")
    int countPaidEmisByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("status") EmiStatus status);

    // ── Used by CreditScoreService ────────────────────────────────────────────

    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "JOIN e.loanSummary s WHERE s.borrower.id = :borrowerId")
    long countTotalEmisByBorrower(@Param("borrowerId") Long borrowerId);

    long countByStatus(EmiStatus status);

    // ── ADDITION: Loan statement aggregation ──────────────────────────────────

    /**
     * Sum of totalPaid across all PAID EMIs for a loan — the actual amount
     * debited from the borrower so far (includes penalties).
     */
    @Query("SELECT COALESCE(SUM(e.totalPaid), 0) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = 'PAID'")
    java.math.BigDecimal sumTotalPaidByLoan(@Param("loanSummaryId") Long loanSummaryId);

    /**
     * Sum of interestComponent across all PAID EMIs for a loan.
     */
    @Query("SELECT COALESCE(SUM(e.interestComponent), 0) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = 'PAID'")
    java.math.BigDecimal sumInterestPaidByLoan(@Param("loanSummaryId") Long loanSummaryId);

    /**
     * Sum of principalComponent across all PAID EMIs for a loan.
     */
    @Query("SELECT COALESCE(SUM(e.principalComponent), 0) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = 'PAID'")
    java.math.BigDecimal sumPrincipalPaidByLoan(@Param("loanSummaryId") Long loanSummaryId);

    // ── ADDITION: Upcoming EMIs for borrower (next 7 days) ───────────────────

    /**
     * Returns all PENDING EMIs for a given borrower where the due date falls
     * within the next 7 days (inclusive of today and the cutoff date).
     * Used by GET /loans/upcoming-emis?borrowerId=X
     */
    @Query("SELECT e FROM EmiSchedule e " +
            "JOIN e.loanSummary s " +
            "WHERE s.borrower.id = :borrowerId " +
            "AND e.status = com.darshan.lending.entity.enums.EmiStatus.PENDING " +
            "AND e.dueDate >= :today " +
            "AND e.dueDate <= :cutoff " +
            "ORDER BY e.dueDate ASC")
    List<EmiSchedule> findUpcomingEmisByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("today") LocalDate today,
            @Param("cutoff") LocalDate cutoff);

    // ── ADDITION: Upcoming collections for lender (next 7 days) ──────────────

    /**
     * Returns all PENDING EMIs for a given lender where the due date falls
     * within the next 7 days (inclusive of today and the cutoff date).
     * Used by GET /loans/upcoming-collections?lenderId=X
     */
    @Query("SELECT e FROM EmiSchedule e " +
            "JOIN e.loanSummary s " +
            "WHERE s.lender.id = :lenderId " +
            "AND e.status = com.darshan.lending.entity.enums.EmiStatus.PENDING " +
            "AND e.dueDate >= :today " +
            "AND e.dueDate <= :cutoff " +
            "ORDER BY e.dueDate ASC")
    List<EmiSchedule> findUpcomingCollectionsByLender(
            @Param("lenderId") Long lenderId,
            @Param("today") LocalDate today,
            @Param("cutoff") LocalDate cutoff);
}