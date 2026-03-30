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

    /**
     * All unpaid EMIs for a loan — PENDING or OVERDUE — ordered by EMI number.
     * Used by pay-emi and foreclosure to find what's left to pay.
     */
    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status IN (com.darshan.lending.entity.enums.EmiStatus.PENDING, " +
            "                 com.darshan.lending.entity.enums.EmiStatus.OVERDUE) " +
            "ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingOrOverdueEmis(
            @Param("loanSummaryId") Long loanSummaryId);

    /**
     * Only PENDING EMIs — used internally when overdue marking hasn't run yet.
     * Kept for backward compatibility.
     */
    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status = :status " +
            "ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);

    /** All overdue EMIs system-wide — used by the scheduler */
    @Query("SELECT e FROM EmiSchedule e " +
            "WHERE e.dueDate < :today AND e.status = :status")
    List<EmiSchedule> findOverdueEmis(
            @Param("today") LocalDate today,
            @Param("status") EmiStatus status);

    /** Bulk mark overdue — called daily by OverdueMonitoringJob */
    @Modifying
    @Query("UPDATE EmiSchedule e SET e.status = :newStatus " +
            "WHERE e.dueDate < :today AND e.status = :oldStatus")
    int markOverdueEmis(
            @Param("today") LocalDate today,
            @Param("oldStatus") EmiStatus oldStatus,
            @Param("newStatus") EmiStatus newStatus);

    /** Count paid EMIs for a loan */
    @Query("SELECT COUNT(e) FROM EmiSchedule e " +
            "WHERE e.loanSummary.id = :loanSummaryId AND e.status = :status")
    int countPaidEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);
}