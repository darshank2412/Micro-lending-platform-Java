package com.darshan.lending.service;

import com.darshan.lending.dto.KycDocumentResponse;
import com.darshan.lending.dto.KycSubmitRequest;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.KycStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.KycDocumentRepository;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FIX 5 — All public methods now return KycDocumentResponse instead of the raw
 * KycDocument entity, preventing accidental exposure of password hashes,
 * resetTokens, and other sensitive User fields through the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final KycDocumentRepository kycDocumentRepository;
    private final UserRepository        userRepository;

    // ── Submit document ───────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse submitDocument(Long userId, KycSubmitRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Prevent duplicate submissions for same document type
        kycDocumentRepository.findByUserIdAndDocumentType(userId, request.getDocumentType())
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "A " + request.getDocumentType() + " document already exists for this user. "
                                    + "Current status: " + existing.getStatus());
                });

        KycDocument doc = KycDocument.builder()
                .user(user)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .documentUrl(request.getDocumentUrl())
                .status(KycStatus.PENDING)
                .build();

        KycDocument saved = kycDocumentRepository.save(doc);
        log.info("KYC document submitted: userId={} type={}", userId, request.getDocumentType());
        return toResponse(saved);
    }

    // ── Get all documents for a user ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KycDocumentResponse> getDocuments(Long userId) {
        return kycDocumentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin: approve ────────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse approveDocument(Long docId) {
        KycDocument doc = findDoc(docId);

        if (doc.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessException("Document is already approved");
        }

        doc.setStatus(KycStatus.VERIFIED);
        doc.setReviewedAt(LocalDateTime.now());
        KycDocument saved = kycDocumentRepository.save(doc);

        // Update parent User's kycStatus if both AADHAAR and PAN are approved
        updateUserKycStatus(doc.getUser());

        log.info("KYC document approved: docId={}", docId);
        return toResponse(saved);
    }

    // ── Admin: reject ─────────────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse rejectDocument(Long docId, String reason) {
        KycDocument doc = findDoc(docId);

        if (doc.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessException("Cannot reject an already-approved document");
        }

        doc.setStatus(KycStatus.REJECTED);
        doc.setRejectionNote(reason);
        doc.setReviewedAt(LocalDateTime.now());
        KycDocument saved = kycDocumentRepository.save(doc);

        log.info("KYC document rejected: docId={} reason={}", docId, reason);
        return toResponse(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private KycDocument findDoc(Long docId) {
        return kycDocumentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "KYC document not found: " + docId));
    }

    /**
     * Marks the user as VERIFIED once all required documents are APPROVED.
     * Adjust the required document types to match your DocumentType enum.
     */
    private void updateUserKycStatus(User user) {
        List<KycDocument> docs = kycDocumentRepository.findByUserId(user.getId());
        boolean allApproved = docs.stream()
                .allMatch(d -> d.getStatus() == KycStatus.VERIFIED);
        if (allApproved && !docs.isEmpty()) {
            user.setKycStatus(KycStatus.VERIFIED);
            userRepository.save(user);
            log.info("User KYC status updated to VERIFIED: userId={}", user.getId());
        }
    }

    /**
     * Safe mapping: only exposes the fields defined in KycDocumentResponse.
     * Never touches User beyond user.getId().
     */
    private KycDocumentResponse toResponse(KycDocument doc) {
        return KycDocumentResponse.builder()
                .id(doc.getId())
                .userId(doc.getUser().getId())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber())
                .documentUrl(doc.getDocumentUrl())
                .status(doc.getStatus())
                .rejectionNote(doc.getRejectionNote())
                .submittedAt(doc.getSubmittedAt())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }
}