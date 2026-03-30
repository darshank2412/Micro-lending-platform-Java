package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class KycControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired KycService kycService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password("hash")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
    }

    // ── POST /kyc/submit ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /kyc/submit → 200 and status PENDING for AADHAAR")
    void submitKyc_aadhaar_shouldReturn200() throws Exception {
        mockMvc.perform(post("/kyc/submit")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"AADHAAR\",\"documentNumber\":\"1234 5678 9012\"," +
                                "\"documentUrl\":\"https://s3.example.com/doc.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.documentType").value("AADHAAR"));
    }

    @Test
    @DisplayName("POST /kyc/submit → 200 and status PENDING for PAN")
    void submitKyc_pan_shouldReturn200() throws Exception {
        mockMvc.perform(post("/kyc/submit")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"PAN\",\"documentNumber\":\"ABCDE1234F\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.documentType").value("PAN"));
    }

    @Test
    @DisplayName("POST /kyc/submit → 404 when user not found")
    void submitKyc_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/kyc/submit")
                        .param("userId", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"AADHAAR\",\"documentNumber\":\"1234 5678 9012\"}"))
                .andExpect(status().isNotFound());
    }

    // ── GET /kyc/{userId} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /kyc/{userId} → 200 with list of submitted documents")
    void getKycDocuments_shouldReturn200() throws Exception {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        mockMvc.perform(get("/kyc/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /kyc/{userId} → 200 with empty list when no documents submitted")
    void getKycDocuments_shouldReturn200WithEmptyList() throws Exception {
        mockMvc.perform(get("/kyc/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /kyc/{userId} → 404 when user not found")
    void getKycDocuments_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(get("/kyc/{userId}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /kyc/approve/{docId} ────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /kyc/approve/{docId} → 200 and status VERIFIED")
    void approveKyc_shouldReturn200() throws Exception {
        KycDocument doc = submitAadhaar("111122223333");
        mockMvc.perform(patch("/kyc/approve/{docId}", doc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("PATCH /kyc/approve/{docId} → 404 when document not found")
    void approveKyc_shouldReturn404WhenDocNotFound() throws Exception {
        mockMvc.perform(patch("/kyc/approve/{docId}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /kyc/reject/{docId} ─────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /kyc/reject/{docId} → 200 and status REJECTED")
    void rejectKyc_shouldReturn200() throws Exception {
        KycDocument doc = submitPan("ABCDE1234F");
        mockMvc.perform(patch("/kyc/reject/{docId}", doc.getId())
                        .param("reason", "Blurry image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /kyc/reject/{docId} → 404 when document not found")
    void rejectKyc_shouldReturn404WhenDocNotFound() throws Exception {
        mockMvc.perform(patch("/kyc/reject/{docId}", 999999L)
                        .param("reason", "Does not exist"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private KycDocument submitAadhaar(String number) {
        return kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber(number).build());
    }

    private KycDocument submitPan(String number) {
        return kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber(number).build());
    }
}