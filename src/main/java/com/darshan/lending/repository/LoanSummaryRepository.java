package com.darshan.lending.repository;

import com.darshan.lending.entity.LoanSummary;
import com.darshan.lending.entity.enums.EmiStatus;
import com.darshan.lending.entity.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanSummaryRepository extends JpaRepository<LoanSummary, Long> {

    List<LoanSummary> findByBorrowerId(Long borrowerId);

    List<LoanSummary> findByLenderId(Long lenderId);

    Optional<LoanSummary> findByLoanOfferId(Long loanOfferId);

    List<LoanSummary> findByStatus(LoanStatus status);

    @Query("SELECT ls FROM LoanSummary ls " +
            "WHERE ls.borrower.id = :borrowerId AND ls.status = :status")
    List<LoanSummary> findByBorrowerIdAndStatus(
            @Param("borrowerId") Long borrowerId,
            @Param("status") LoanStatus status);


    @Query("SELECT COUNT(s) FROM LoanSummary s WHERE s.borrower.id = :borrowerId")
    long countByBorrowerId(@Param("borrowerId") Long borrowerId);

    @Query("SELECT COUNT(s) FROM LoanSummary s WHERE s.borrower.id = :borrowerId AND s.status = 'COMPLETED'")
    long countCompletedByBorrowerId(@Param("borrowerId") Long borrowerId);

    @Query("SELECT COUNT(s) FROM LoanSummary s WHERE s.borrower.id = :borrowerId AND s.status = 'ACTIVE'")
    long countActiveByBorrowerId(@Param("borrowerId") Long borrowerId);
    /**
     * Bulk mark ACTIVE loans as DEFAULTED when they have at least one OVERDUE EMI.
     * Single JOIN query — avoids the N+1 loop in the original scheduler.
     */
    @Modifying
    @Query("UPDATE LoanSummary ls SET ls.status = :newStatus " +
            "WHERE ls.status = :currentStatus " +
            "AND EXISTS (" +
            "  SELECT 1 FROM EmiSchedule e " +
            "  WHERE e.loanSummary.id = ls.id AND e.status = :overdueStatus" +
            ")")
    int markLoansWithOverdueEmisAsDefaulted(
            @Param("currentStatus") LoanStatus currentStatus,
            @Param("newStatus") LoanStatus newStatus,
            @Param("overdueStatus") EmiStatus overdueStatus);
}