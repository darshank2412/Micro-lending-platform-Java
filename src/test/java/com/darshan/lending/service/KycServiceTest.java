package com.darshan.lending.service;

import com.darshan.lending.dto.KycSubmitRequest;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KycServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired KycService kycService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password(".4qFITYhSKOYryHuMBQOuRXpqOOoGBTJyZHmCJKYANmFz6dyT4Oy")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
    }

    @Test
    void submitDocument_shouldSucceed() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR)
                .documentNumber("1234 5678 9012")
                .documentUrl("https://s3.example.com/doc.pdf").build());
        assertNotNull(doc.getId());
        assertEquals(KycStatus.PENDING, doc.getStatus());
    }

    @Test
    void submitDocument_shouldFailOnDuplicate() {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("1234 5678 9012").build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                        .documentType(DocumentType.AADHAAR).documentNumber("1234 5678 9012").build()));
        assertTrue(ex.getMessage().contains("already submitted"));
    }

    @Test
    void submitDocument_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> kycService.submitDocument(999999L, KycSubmitRequest.builder()
                        .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build()));
    }

    @Test
    void getDocuments_shouldReturnList() {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        List<KycDocument> docs = kycService.getDocuments(testUser.getId());
        assertEquals(2, docs.size());
    }

    @Test
    void getDocuments_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> kycService.getDocuments(999999L));
    }

    @Test
    void approveDocument_shouldSetVerified() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PASSPORT).documentNumber("P1234567").build());
        KycDocument approved = kycService.approveDocument(doc.getId());
        assertEquals(KycStatus.VERIFIED, approved.getStatus());
        assertNotNull(approved.getReviewedAt());
    }

    @Test
    void approveDocument_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> kycService.approveDocument(999999L));
    }

    @Test
    void rejectDocument_shouldSetRejected() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.DRIVING_LICENSE).documentNumber("DL1234").build());
        KycDocument rejected = kycService.rejectDocument(doc.getId(), "Document unclear");
        assertEquals(KycStatus.REJECTED, rejected.getStatus());
        assertEquals("Document unclear", rejected.getRejectionNote());
    }

    @Test
    void rejectDocument_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> kycService.rejectDocument(999999L, "reason"));
    }

    @Test
    void approveAllRequired_shouldUpdateUserKycToVerified() {
        KycDocument aadhaar = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        KycDocument pan = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        kycService.approveDocument(aadhaar.getId());
        kycService.approveDocument(pan.getId());
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(KycStatus.VERIFIED, updated.getKycStatus());
    }
}
