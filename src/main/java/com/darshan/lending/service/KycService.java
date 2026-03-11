package com.darshan.lending.service;

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

import java.util.List;

/**
 * KYC Service - manages document submission and status tracking.
 *
 * Verification flow:
 *   1. User submits documents via submitDocument()
 *   2. Admin reviews and calls approveDocument() or rejectDocument()
 *   3. Once all required docs (AADHAAR + PAN) are VERIFIED,
 *      the user's kycStatus is updated to VERIFIED automatically.
 *
 * NOTE: Integrate with a KYC provider (e.g., Digio, Signzy, IDfy) to automate verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final KycDocumentRepository kycDocRepo;
    private final UserRepository userRepo;

    @Transactional
    public KycDocument submitDocument(Long userId, KycSubmitRequest request) {
        User user = findUser(userId);

        if (kycDocRepo.existsByUserIdAndDocumentType(userId, request.getDocumentType())) {
            throw new BusinessException("Document of type " + request.getDocumentType() + " already submitted.");
        }

        KycDocument doc = KycDocument.builder()
                .user(user)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .documentUrl(request.getDocumentUrl())
                .status(KycStatus.PENDING)
                .build();

        KycDocument saved = kycDocRepo.save(doc);
        log.info("KYC document submitted: type={} userId={}", request.getDocumentType(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<KycDocument> getDocuments(Long userId) {
        findUser(userId); // validate user exists
        return kycDocRepo.findByUserId(userId);
    }

    /** Admin: approve a KYC document */
    @Transactional
    public KycDocument approveDocument(Long docId) {
        KycDocument doc = findDoc(docId);
        doc.setStatus(KycStatus.VERIFIED);
        doc.setReviewedAt(java.time.LocalDateTime.now());
        kycDocRepo.save(doc);

        checkAndUpdateUserKycStatus(doc.getUser().getId());
        return doc;
    }

    /** Admin: reject a KYC document */
    @Transactional
    public KycDocument rejectDocument(Long docId, String reason) {
        KycDocument doc = findDoc(docId);
        doc.setStatus(KycStatus.REJECTED);
        doc.setRejectionNote(reason);
        doc.setReviewedAt(java.time.LocalDateTime.now());
        return kycDocRepo.save(doc);
    }

    /** Automatically updates user kyc_status when required docs are verified */
    private void checkAndUpdateUserKycStatus(Long userId) {
        List<KycDocument> docs = kycDocRepo.findByUserId(userId);
        boolean aadhaarVerified = docs.stream()
                .anyMatch(d -> d.getDocumentType().name().equals("AADHAAR") && d.getStatus() == KycStatus.VERIFIED);
        boolean panVerified = docs.stream()
                .anyMatch(d -> d.getDocumentType().name().equals("PAN") && d.getStatus() == KycStatus.VERIFIED);

        if (aadhaarVerified && panVerified) {
            User user = findUser(userId);
            user.setKycStatus(KycStatus.VERIFIED);
            userRepo.save(user);
            log.info("User KYC fully verified: userId={}", userId);
        }
    }

    private KycDocument findDoc(Long docId) {
        return kycDocRepo.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC document not found: " + docId));
    }

    private User findUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
