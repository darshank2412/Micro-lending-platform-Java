package com.darshan.lending.service;

import com.darshan.lending.dto.LoanRequestDto;
import com.darshan.lending.dto.LoanRequestResponse;
import com.darshan.lending.entity.LenderPreference;
import com.darshan.lending.entity.LoanProduct;
import com.darshan.lending.entity.LoanRequest;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.entity.enums.ProductStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LenderPreferenceRepository;
import com.darshan.lending.repository.LoanProductRepository;
import com.darshan.lending.repository.LoanRequestRepository;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.util.LoanStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRequestService {

    private final LoanRequestRepository      loanRequestRepository;
    private final UserRepository             userRepository;
    private final LoanProductRepository      loanProductRepository;
    private final LenderPreferenceRepository lenderPreferenceRepository;
    private final AuditLogService            auditLogService;

    // ── BORROWER: Submit a loan request ──────────────────────────────────────

    @Transactional
    public LoanRequestResponse createRequest(Long borrowerId, LoanRequestDto dto) {

        User borrower = findUser(borrowerId);

        if (borrower.getRole() != Role.BORROWER) {
            throw new BusinessException(
                    "Only BORROWER users can submit loan requests");
        }

        if (loanRequestRepository.existsByBorrowerIdAndStatus(
                borrowerId, LoanRequestStatus.PENDING)) {
            throw new BusinessException(
                    "You already have a PENDING loan request. "
                            + "Cancel it before raising a new one.");
        }

        LoanProduct product = loanProductRepository.findById(dto.getLoanProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan product not found: " + dto.getLoanProductId()));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Loan product is not active");
        }

        if (dto.getAmount().compareTo(product.getMinAmount()) < 0 ||
                dto.getAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new BusinessException(
                    "Amount must be between " + product.getMinAmount()
                            + " and " + product.getMaxAmount());
        }

        if (dto.getTenureMonths() < product.getMinTenure() ||
                dto.getTenureMonths() > product.getMaxTenure()) {
            throw new BusinessException(
                    "Tenure must be between " + product.getMinTenure()
                            + " and " + product.getMaxTenure() + " months");
        }

        LoanRequest request = LoanRequest.builder()
                .borrower(borrower)
                .loanProduct(product)
                .amount(dto.getAmount())
                .tenureMonths(dto.getTenureMonths())
                .purpose(dto.getPurpose())
                .purposeDescription(dto.getPurposeDescription())
                .preferredEmiDay(dto.getPreferredEmiDay())
                .status(LoanRequestStatus.PENDING)
                .build();

        LoanRequest saved = loanRequestRepository.save(request);

        // ── Audit: loan request created ───────────────────────────────────
        auditLogService.log(
                borrowerId,
                "BORROWER",
                borrower.getFullName(),
                AuditLogService.ACTION_LOAN_REQUEST_CREATED,
                AuditLogService.RESOURCE_REQUEST,
                saved.getId(),
                "Amount: " + dto.getAmount()
                        + " | Tenure: " + dto.getTenureMonths() + " months"
                        + " | Purpose: " + dto.getPurpose()
                        + " | Product: " + product.getName(),
                "SUCCESS"
        );

        log.info("Loan request created: id={} borrower={}",
                saved.getId(), borrowerId);
        return toResponse(saved);
    }

    // ── BORROWER: Get my loan requests ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanRequestResponse> getMyRequests(Long borrowerId) {
        User borrower = findUser(borrowerId);
        if (borrower.getRole() != Role.BORROWER) {
            throw new BusinessException(
                    "Only BORROWER users can view loan requests");
        }
        return loanRequestRepository.findByBorrowerId(borrowerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── BORROWER: Cancel a pending request ───────────────────────────────────

    @Transactional
    public LoanRequestResponse cancelRequest(Long borrowerId, Long requestId) {

        LoanRequest request = findRequest(requestId);

        if (!request.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException(
                    "You can only cancel your own loan requests");
        }

        LoanStateMachine.validateTransition(
                request.getStatus(), LoanRequestStatus.CANCELLED);

        request.setStatus(LoanRequestStatus.CANCELLED);

        LoanRequest saved = loanRequestRepository.save(request);

        // ── Audit: loan request cancelled ─────────────────────────────────
        auditLogService.log(
                borrowerId,
                "BORROWER",
                request.getBorrower().getFullName(),
                AuditLogService.ACTION_LOAN_REQUEST_CANCELLED,
                AuditLogService.RESOURCE_REQUEST,
                requestId,
                "Request cancelled by borrower",
                "CANCELLED"
        );

        log.info("Loan request cancelled: id={}", requestId);
        return toResponse(saved);
    }

    // ── ADMIN: Get all requests (optionally by status) ────────────────────────

    @Transactional(readOnly = true)
    public List<LoanRequestResponse> getAllRequests(LoanRequestStatus status) {
        List<LoanRequest> requests = (status != null)
                ? loanRequestRepository.findByStatus(status)
                : loanRequestRepository.findAll();
        return requests.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── ADMIN: Mark a request as MATCHED ─────────────────────────────────────

    @Transactional
    public LoanRequestResponse markAsMatched(Long requestId) {

        LoanRequest request = findRequest(requestId);

        LoanStateMachine.validateTransition(
                request.getStatus(), LoanRequestStatus.MATCHED);

        request.setStatus(LoanRequestStatus.MATCHED);
        log.info("Loan request matched: id={}", requestId);
        return toResponse(loanRequestRepository.save(request));
    }

    // ── ADMIN: Reject a loan request ─────────────────────────────────────────

    @Transactional
    public LoanRequestResponse rejectRequest(Long requestId, String reason) {

        LoanRequest request = findRequest(requestId);

        LoanStateMachine.validateTransition(
                request.getStatus(), LoanRequestStatus.REJECTED);

        request.setStatus(LoanRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        log.info("Loan request rejected: id={}", requestId);
        return toResponse(loanRequestRepository.save(request));
    }

    // ── LENDER: Browse all PENDING loan requests ──────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanRequestResponse> getOpenRequests(Long lenderId) {
        User lender = findUser(lenderId);
        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException(
                    "Only LENDER users can browse loan requests");
        }
        return loanRequestRepository.findByStatus(LoanRequestStatus.PENDING)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── LENDER: Browse PENDING requests matching preferences ─────────────────

    @Transactional(readOnly = true)
    public List<LoanRequestResponse> getRequestsMatchingLenderPreference(
            Long lenderId) {

        User lender = findUser(lenderId);
        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException(
                    "Only LENDER users can browse loan requests");
        }

        List<LenderPreference> preferences = lenderPreferenceRepository
                .findByLenderIdAndIsActiveTrue(lenderId);

        if (preferences.isEmpty()) {
            throw new BusinessException(
                    "No active preferences found. "
                            + "Please set your lending preferences first.");
        }

        return preferences.stream()
                .flatMap(preference -> loanRequestRepository
                        .findPendingMatchingPreference(
                                preference.getMinLoanAmount(),
                                preference.getMaxLoanAmount(),
                                preference.getMinTenureMonths(),
                                preference.getMaxTenureMonths()).stream())
                .distinct()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── LENDER: Accept a matched loan request ────────────────────────────────

    @Transactional
    public LoanRequestResponse acceptRequest(Long lenderId, Long requestId) {

        User lender = findUser(lenderId);

        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException(
                    "Only LENDER users can accept loan requests");
        }

        LoanRequest request = findRequest(requestId);

        LoanStateMachine.validateTransition(
                request.getStatus(), LoanRequestStatus.ACCEPTED);

        request.setStatus(LoanRequestStatus.ACCEPTED);
        log.info("Loan request accepted: id={} lender={}", requestId, lenderId);
        return toResponse(loanRequestRepository.save(request));
    }

    // ── Get single request by ID ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoanRequestResponse getById(Long requestId) {
        return toResponse(findRequest(requestId));
    }

    // ── NEW: Get raw entity (used by tests and other services) ────────────────

    public LoanRequest getRequestEntity(Long requestId) {
        return loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan request not found: " + requestId));
    }

    // ── NEW: Mark request as DISBURSED ────────────────────────────────────────

    @Transactional
    public void markDisbursed(Long requestId) {
        LoanRequest request = getRequestEntity(requestId);
        LoanStateMachine.validateTransition(
                request.getStatus(), LoanRequestStatus.DISBURSED);
        request.setStatus(LoanRequestStatus.DISBURSED);
        loanRequestRepository.save(request);
        log.info("Loan request marked as DISBURSED: id={}", requestId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
    }

    private LoanRequest findRequest(Long requestId) {
        return loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan request not found: " + requestId));
    }

    public LoanRequestResponse toResponse(LoanRequest r) {
        return LoanRequestResponse.builder()
                .id(r.getId())
                .borrowerId(r.getBorrower().getId())
                .borrowerName(r.getBorrower().getFullName())
                .loanProductId(r.getLoanProduct().getId())
                .loanProductName(r.getLoanProduct().getName())
                .amount(r.getAmount())
                .tenureMonths(r.getTenureMonths())
                .purpose(r.getPurpose())
                .purposeDescription(r.getPurposeDescription())
                .status(r.getStatus())
                .preferredEmiDay(r.getPreferredEmiDay())
                .rejectionReason(r.getRejectionReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}