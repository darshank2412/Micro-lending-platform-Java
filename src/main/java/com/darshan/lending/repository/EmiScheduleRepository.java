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
            "                 com.darshan.lending.entity.enums.EmiStatus.OVERDUE) " +
            "ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingOrOverdueEmis(
            @Param("loanSummaryId") Long loanSummaryId);

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

    // ── Per loan ──────────────────────────────────────────────────────────
    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = :status")
    int countPaidEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);

    // ── Per borrower (used by CreditScoreService) ─────────────────────────
    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "JOIN e.loanSummary s WHERE s.borrower.id = :borrowerId AND e.status = :status")
    int countPaidEmisByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("status") EmiStatus status);

    @Query("SELECT COUNT(e) FROM EmiSchedule e JOIN e.loanSummary s WHERE s.borrower.id = :borrowerId")
    long countTotalEmisByBorrower(@Param("borrowerId") Long borrowerId);
}