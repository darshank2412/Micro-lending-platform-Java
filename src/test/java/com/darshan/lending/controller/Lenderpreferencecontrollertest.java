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
class LenderPreferenceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired LoanProductService loanProductService;
    @Autowired LenderPreferenceService lenderPreferenceService;

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

    // ── POST /lender-preferences ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /lender-preferences → 200 and preference saved for LENDER")
    void savePreference_shouldReturn200() throws Exception {
        mockMvc.perform(post("/lender-preferences")
                        .param("lenderId", lender.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody(loanProductId, "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lenderId").value(lender.getId()))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("POST /lender-preferences → 400 when BORROWER tries to set preferences")
    void savePreference_shouldReturn400ForBorrower() throws Exception {
        mockMvc.perform(post("/lender-preferences")
                        .param("lenderId", borrower.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody(loanProductId, "LOW")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /lender-preferences → 404 when user not found")
    void savePreference_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/lender-preferences")
                        .param("lenderId", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody(loanProductId, "LOW")))
                .andExpect(status().isNotFound());
    }

    // ── GET /lender-preferences ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /lender-preferences → 200 with lender's active preferences")
    void getMyPreferences_shouldReturn200() throws Exception {
        lenderPreferenceService.savePreference(lender.getId(), buildPreferenceDto(loanProductId, RiskAppetite.HIGH));
        mockMvc.perform(get("/lender-preferences")
                        .param("lenderId", lender.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /lender-preferences → 200 with empty list when no preferences saved")
    void getMyPreferences_noPreferences_shouldReturn200() throws Exception {
        mockMvc.perform(get("/lender-preferences")
                        .param("lenderId", lender.getId().toString()))
                .andExpect(status().isOk());
    }

    // ── PATCH /lender-preferences/deactivate ─────────────────────────────────

    @Test
    @DisplayName("PATCH /lender-preferences/deactivate → 200 and isActive false")
    void deactivatePreference_shouldReturn200() throws Exception {
        lenderPreferenceService.savePreference(lender.getId(), buildPreferenceDto(loanProductId, RiskAppetite.MEDIUM));
        mockMvc.perform(patch("/lender-preferences/deactivate")
                        .param("lenderId", lender.getId().toString())
                        .param("loanProductId", loanProductId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    @DisplayName("PATCH /lender-preferences/deactivate → 404 when no preference exists")
    void deactivatePreference_shouldReturn400WhenNotFound() throws Exception {
        mockMvc.perform(patch("/lender-preferences/deactivate")
                        .param("lenderId", lender.getId().toString())
                        .param("loanProductId", loanProductId.toString()))
                .andExpect(status().isNotFound()); // ← change this line
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String preferenceBody(Long productId, String riskAppetite) {
        return String.format(
                "{\"loanProductId\":%d,\"minInterestRate\":8,\"maxInterestRate\":20," +
                        "\"minTenureMonths\":6,\"maxTenureMonths\":48," +
                        "\"minLoanAmount\":10000,\"maxLoanAmount\":200000," +
                        "\"riskAppetite\":\"%s\"}", productId, riskAppetite);
    }

    private LenderPreferenceDto buildPreferenceDto(Long productId, RiskAppetite riskAppetite) {
        return LenderPreferenceDto.builder()
                .loanProductId(productId)
                .minInterestRate(new BigDecimal("8")).maxInterestRate(new BigDecimal("20"))
                .minTenureMonths(6).maxTenureMonths(48)
                .minLoanAmount(new BigDecimal("10000")).maxLoanAmount(new BigDecimal("200000"))
                .riskAppetite(riskAppetite).build();
    }
}