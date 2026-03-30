package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = {"BORROWER", "LENDER", "ADMIN"})
class LoanRequestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired LoanProductService loanProductService;
    @Autowired LoanRequestService loanRequestService;

    private User borrower;
    private User lender;
    private Long loanProductId;

    @BeforeEach
    void setUp() {
        borrower = userRepository.save(User.builder()
                .phoneNumber("9111111111").countryCode("+91")
                .email("borrower@test.com").password("encoded")
                .fullName("Test Borrower").role(Role.BORROWER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        lender = userRepository.save(User.builder()
                .phoneNumber("9222222222").countryCode("+91")
                .email("lender@test.com").password("encoded")
                .fullName("Test Lender").role(Role.LENDER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        loanProductId = loanProductService.create(LoanProductRequest.builder()
                .name("Test Loan Product").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minInterest(new BigDecimal("8"))
                .maxInterest(new BigDecimal("24")).minTenure(6).maxTenure(60)
                .build()).getId();
    }

    // ── POST /loan-requests ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /loan-requests → 200 and status PENDING for BORROWER")
    void createLoanRequest_shouldReturn200() throws Exception {
        mockMvc.perform(post("/loan-requests")
                        .param("borrowerId", borrower.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequestBody(loanProductId, "EDUCATION", "College fees")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.borrowerId").value(borrower.getId()));
    }

    @Test
    @DisplayName("POST /loan-requests → 400 when LENDER attempts to create a request")
    void createLoanRequest_shouldReturn400ForLender() throws Exception {
        mockMvc.perform(post("/loan-requests")
                        .param("borrowerId", lender.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequestBody(loanProductId, "EDUCATION", "College fees")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /loan-requests → 400 when borrower already has a PENDING request")
    void createLoanRequest_shouldReturn400WhenDuplicatePending() throws Exception {
        createRequestViaService(LoanPurpose.EDUCATION);
        mockMvc.perform(post("/loan-requests")
                        .param("borrowerId", borrower.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequestBody(loanProductId, "MEDICAL", "Hospital")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /loan-requests → 404 when user not found")
    void createLoanRequest_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/loan-requests")
                        .param("borrowerId", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanRequestBody(loanProductId, "EDUCATION", "College fees")))
                .andExpect(status().isNotFound());
    }

    // ── GET /loan-requests/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /loan-requests/{id} → 200 with correct request data")
    void getLoanRequestById_shouldReturn200() throws Exception {
        LoanRequestResponse req = createRequestViaService(LoanPurpose.SMALL_BUSINESS);
        mockMvc.perform(get("/loan-requests/{id}", req.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(req.getId()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /loan-requests/{id} → 404 when not found")
    void getLoanRequestById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/loan-requests/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /loan-requests/{id}/cancel ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /loan-requests/{id}/cancel → 200 and status CANCELLED")
    void cancelLoanRequest_shouldReturn200() throws Exception {
        LoanRequestResponse req = createRequestViaService(LoanPurpose.EMERGENCY);
        mockMvc.perform(patch("/loan-requests/{id}/cancel", req.getId())
                        .param("borrowerId", borrower.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /loan-requests/{id}/cancel → 400 when request is already CANCELLED")
    void cancelLoanRequest_shouldReturn400WhenAlreadyCancelled() throws Exception {
        LoanRequestResponse req = createRequestViaService(LoanPurpose.MEDICAL);
        loanRequestService.cancelRequest(borrower.getId(), req.getId());
        mockMvc.perform(patch("/loan-requests/{id}/cancel", req.getId())
                        .param("borrowerId", borrower.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /loan-requests/{id}/cancel → 404 when request not found")
    void cancelLoanRequest_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(patch("/loan-requests/{id}/cancel", 999999L)
                        .param("borrowerId", borrower.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private LoanRequestResponse createRequestViaService(LoanPurpose purpose) {
        return loanRequestService.createRequest(borrower.getId(), LoanRequestDto.builder()
                .loanProductId(loanProductId).amount(new BigDecimal("50000"))
                .tenureMonths(12).purpose(purpose).build());
    }

    private String loanRequestBody(Long productId, String purpose, String description) {
        return String.format(
                "{\"loanProductId\":%d,\"amount\":50000,\"tenureMonths\":12," +
                        "\"purpose\":\"%s\",\"purposeDescription\":\"%s\"}",
                productId, purpose, description);
    }
}