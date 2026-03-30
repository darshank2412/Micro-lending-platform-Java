package com.darshan.lending.service;

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
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

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

        request.setStatus(LoanRequestStatus. DISBURSED );
        loanRequestRepository.save(request);

        int rejected = loanOfferRepository.bulkRejectOtherOffers(
                request.getId(), offerId);
        log.info("Auto-rejected {} other offers for request id={}", rejected, request.getId());

        log.info("Offer id={} ACCEPTED by borrower={}, request={} → FUNDED",
                offerId, borrowerId, request.getId());

        return toResponse(offer);
    }

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
        return toResponse(offer);
    }

    @Transactional(readOnly = true)
    public List<LoanOfferResponse> getMyOffers(Long lenderId) {
        User lender = findUser(lenderId);
        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException("Only LENDER users can view their offers");
        }
        return loanOfferRepository.findByLenderId(lenderId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LoanOfferResponse> adminGetOffersForRequest(Long requestId) {
        return loanOfferRepository.findRankedOffersForRequest(requestId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
    }

    public LoanOfferResponse toResponse(LoanOffer o) {
        return LoanOfferResponse.builder()
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
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}