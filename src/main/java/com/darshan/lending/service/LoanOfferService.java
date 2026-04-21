package com.darshan.lending.service;

import com.darshan.lending.dto.BorrowerSummary;
import com.darshan.lending.dto.CreditScoreResponse;
import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.entity.LoanOffer;
import com.darshan.lending.entity.LoanRequest;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.LoanOfferStatus;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LoanOfferRepository;
import com.darshan.lending.repository.LoanRequestRepository;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanOfferService {

    private final LoanOfferRepository   loanOfferRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final UserRepository        userRepository;
    private final CreditScoreService    creditScoreService;   // ← NEW: for borrowerSummary

    // ── Borrower: view offers for their request ───────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanOfferResponse> getOffersForRequest(Long borrowerId, Long requestId) {
        User borrower = findUser(borrowerId);
        if (borrower.getRole() != Role.BORROWER) {
            throw new BusinessException("Only BORROWER users can view their loan offers");
        }
        LoanOffer sample = loanOfferRepository.findByLoanRequestId(requestId)
                .stream().findFirst().orElse(null);
        if (sample != null &&
                !sample.getLoanRequest().getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException("You can only view offers on your own loan requests");
        }
        return loanOfferRepository.findRankedOffersForRequest(requestId)
                .stream().map(o -> toResponse(o, false)).collect(Collectors.toList());
    }

    // ── Borrower: accept an offer ─────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public LoanOfferResponse acceptOffer(Long borrowerId, Long offerId) {

        User borrower = findUser(borrowerId);
        if (borrower.getRole() != Role.BORROWER) {
            throw new BusinessException("Only BORROWER users can accept loan offers");
        }

        LoanOffer offer = loanOfferRepository.findByIdWithLock(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan offer not found: " + offerId));

        LoanRequest request = offer.getLoanRequest();
        if (!request.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException(
                    "You can only accept offers on your own loan requests");
        }

        if (offer.getStatus() != LoanOfferStatus.PENDING) {
            throw new BusinessException(
                    "Only PENDING offers can be accepted. Current: " + offer.getStatus());
        }

        if (request.getStatus() != LoanRequestStatus.MATCHED) {
            throw new BusinessException(
                    "Loan request is not in MATCHED state. Current: " + request.getStatus());
        }

        offer.setStatus(LoanOfferStatus.ACCEPTED);
        loanOfferRepository.save(offer);

        // ── FIX 4: Status must be ACCEPTED here, NOT DISBURSED.
        //    DISBURSED is only set by the admin disburse endpoint. ─────────────
        request.setStatus(LoanRequestStatus.ACCEPTED);
        loanRequestRepository.save(request);

        int rejected = loanOfferRepository.bulkRejectOtherOffers(
                request.getId(), offerId);
        log.info("Auto-rejected {} other offers for request id={}", rejected, request.getId());

        log.info("Offer id={} ACCEPTED by borrower={}, request={} → ACCEPTED",
                offerId, borrowerId, request.getId());

        return toResponse(offer, false);
    }

    // ── Borrower: reject an offer ─────────────────────────────────────────────

    @Transactional
    public LoanOfferResponse rejectOffer(Long borrowerId, Long offerId, String reason) {

        User borrower = findUser(borrowerId);
        if (borrower.getRole() != Role.BORROWER) {
            throw new BusinessException("Only BORROWER users can reject loan offers");
        }

        LoanOffer offer = loanOfferRepository.findByIdWithLock(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan offer not found: " + offerId));

        LoanRequest request = offer.getLoanRequest();
        if (!request.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException(
                    "You can only reject offers on your own loan requests");
        }

        if (offer.getStatus() != LoanOfferStatus.PENDING) {
            throw new BusinessException(
                    "Only PENDING offers can be rejected. Current: " + offer.getStatus());
        }

        offer.setStatus(LoanOfferStatus.REJECTED);
        offer.setRejectionReason(reason);
        loanOfferRepository.save(offer);

        log.info("Offer id={} REJECTED by borrower={}", offerId, borrowerId);
        return toResponse(offer, false);
    }

    // ── Lender: view my offers (with borrowerSummary) ─────────────────────────

    @Transactional(readOnly = true)
    public List<LoanOfferResponse> getMyOffers(Long lenderId) {
        User lender = findUser(lenderId);
        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException("Only LENDER users can view their offers");
        }
        // ── ADDITION: pass includeBorrowerSummary=true for lender-facing calls ─
        return loanOfferRepository.findByLenderId(lenderId)
                .stream().map(o -> toResponse(o, true)).collect(Collectors.toList());
    }

    // ── Admin: view all offers for a request ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanOfferResponse> adminGetOffersForRequest(Long requestId) {
        return loanOfferRepository.findRankedOffersForRequest(requestId)
                .stream().map(o -> toResponse(o, false)).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
    }

    /**
     * Maps a LoanOffer to the response DTO.
     *
     * @param includeBorrowerSummary true when called from lender-facing endpoints —
     *                               populates the nested borrowerSummary block.
     */
    public LoanOfferResponse toResponse(LoanOffer o, boolean includeBorrowerSummary) {

        LoanOfferResponse.LoanOfferResponseBuilder builder = LoanOfferResponse.builder()
                .id(o.getId())
                .loanRequestId(o.getLoanRequest().getId())
                .loanAmount(o.getLoanAmount())
                .lenderId(o.getLender().getId())
                .lenderName(o.getLender().getFullName())
                .offeredInterestRate(o.getOfferedInterestRate())
                .status(o.getStatus())
                .matchRank(o.getMatchRank())
                .rejectionReason(o.getRejectionReason())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt());

        // ── ADDITION: populate borrowerSummary for lender ─────────────────────
        if (includeBorrowerSummary) {
            Long borrowerId = o.getLoanRequest().getBorrower().getId();
            LoanRequest req = o.getLoanRequest();

            CreditScoreResponse credit = creditScoreService.calculateScore(borrowerId);

            BorrowerSummary summary = BorrowerSummary.builder()
                    .creditScore(credit.getScore())
                    .grade(credit.getGrade())
                    .kycStatus(o.getLoanRequest().getBorrower().getKycStatus().name())
                    .loanPurpose(req.getPurpose() != null ? req.getPurpose().name() : null)
                    .build();

            builder.borrowerSummary(summary);
        }

        return builder.build();
    }

    /**
     * Convenience overload — no borrower summary (used by older callers).
     */
    public LoanOfferResponse toResponse(LoanOffer o) {
        return toResponse(o, false);
    }
}