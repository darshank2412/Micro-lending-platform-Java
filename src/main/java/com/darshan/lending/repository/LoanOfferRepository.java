package com.darshan.lending.repository;

import com.darshan.lending.entity.LoanOffer;
import com.darshan.lending.entity.enums.LoanOfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanOfferRepository extends JpaRepository<LoanOffer, Long> {

    List<LoanOffer> findByLoanRequestId(Long loanRequestId);

    List<LoanOffer> findByLenderId(Long lenderId);

    List<LoanOffer> findByLoanRequestIdAndStatus(Long loanRequestId, LoanOfferStatus status);

    boolean existsByLoanRequestIdAndLenderId(Long loanRequestId, Long lenderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM LoanOffer o WHERE o.id = :id")
    Optional<LoanOffer> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query("UPDATE LoanOffer o SET o.status = 'REJECTED', " +
            "o.rejectionReason = 'Another offer was accepted for this loan request' " +
            "WHERE o.loanRequest.id = :loanRequestId " +
            "AND o.status = 'PENDING' " +
            "AND o.id <> :acceptedOfferId")
    int bulkRejectOtherOffers(@Param("loanRequestId") Long loanRequestId,
                              @Param("acceptedOfferId") Long acceptedOfferId);

    @Query("SELECT o FROM LoanOffer o " +
            "WHERE o.loanRequest.id = :loanRequestId " +
            "ORDER BY o.offeredInterestRate ASC, o.createdAt ASC")
    List<LoanOffer> findRankedOffersForRequest(@Param("loanRequestId") Long loanRequestId);

    @Query("SELECT COUNT(o) FROM LoanOffer o " +
            "WHERE o.loanRequest.id = :loanRequestId AND o.status = 'PENDING'")
    long countPendingOffers(@Param("loanRequestId") Long loanRequestId);
}