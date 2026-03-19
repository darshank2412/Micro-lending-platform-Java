package com.darshan.lending.repository;

import com.darshan.lending.entity.LoanRequest;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    // All requests by a borrower
    List<LoanRequest> findByBorrowerId(Long borrowerId);

    // All requests with a specific status
    List<LoanRequest> findByStatus(LoanRequestStatus status);

    // All requests by borrower and status
    List<LoanRequest> findByBorrowerIdAndStatus(Long borrowerId, LoanRequestStatus status);

    // Check if borrower already has a pending request
    boolean existsByBorrowerIdAndStatus(Long borrowerId, LoanRequestStatus status);

    // Pending requests filtered by lender preference ranges
    @Query("SELECT r FROM LoanRequest r WHERE r.status = 'PENDING' " +
            "AND r.amount >= :minAmount AND r.amount <= :maxAmount " +
            "AND r.tenureMonths >= :minTenure AND r.tenureMonths <= :maxTenure")
    List<LoanRequest> findPendingMatchingPreference(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("minTenure") Integer minTenure,
            @Param("maxTenure") Integer maxTenure);
}