package com.darshan.lending.repository;

import com.darshan.lending.entity.LoanSummary;
import com.darshan.lending.entity.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT ls FROM LoanSummary ls WHERE ls.borrower.id = :borrowerId " +
            "AND ls.status = :status")
    List<LoanSummary> findByBorrowerIdAndStatus(
            @Param("borrowerId") Long borrowerId,
            @Param("status") LoanStatus status);
}