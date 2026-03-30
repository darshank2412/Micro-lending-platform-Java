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
import java.util.Optional;

@Repository
public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, Long> {

    List<EmiSchedule> findByLoanSummaryIdOrderByEmiNumberAsc(Long loanSummaryId);

    /** Next unpaid EMI for a loan */
    @Query(value = "SELECT e FROM EmiSchedule e WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status = :status ORDER BY e.emiNumber ASC")
    List<EmiSchedule> findPendingEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);

    /** All overdue EMIs — due date passed and still PENDING */
    @Query("SELECT e FROM EmiSchedule e WHERE e.dueDate < :today " +
            "AND e.status = :status")
    List<EmiSchedule> findOverdueEmis(
            @Param("today") LocalDate today,
            @Param("status") EmiStatus status);

    /** Bulk mark overdue */
    @Modifying
    @Query("UPDATE EmiSchedule e SET e.status = :newStatus " +
            "WHERE e.dueDate < :today AND e.status = :oldStatus")
    int markOverdueEmis(
            @Param("today") LocalDate today,
            @Param("oldStatus") EmiStatus oldStatus,
            @Param("newStatus") EmiStatus newStatus);

    /** Count paid EMIs for a loan */
    @Query("SELECT COUNT(e) FROM EmiSchedule e WHERE e.loanSummary.id = :loanSummaryId " +
            "AND e.status = :status")
    int countPaidEmis(
            @Param("loanSummaryId") Long loanSummaryId,
            @Param("status") EmiStatus status);
}