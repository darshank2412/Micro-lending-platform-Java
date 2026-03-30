package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired SavingsProductService savingsProductService;
    @Autowired LoanProductService loanProductService;

    private Long savingsProductId;
    private Long loanProductId;

    @BeforeEach
    void setUp() {
        savingsProductId = savingsProductService.create(SavingsProductRequest.builder()
                .name("Basic Savings").minBalance(new BigDecimal("500"))
                .maxBalance(new BigDecimal("1000000")).interestRate(new BigDecimal("4.50"))
                .build()).getId();

        loanProductId = loanProductService.create(LoanProductRequest.builder()
                .name("Personal Loan").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minTenure(6).maxTenure(60)
                .minInterest(new BigDecimal("8")).maxInterest(new BigDecimal("24"))
                .build()).getId();
    }

    // ── GET /savings-products ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /savings-products → 200 with list of active products")
    void savingsProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /savings-products/{id} → 200 with correct product name")
    void savingsProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", savingsProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Basic Savings"))
                .andExpect(jsonPath("$.data.interestRate").value(4.50));
    }

    @Test
    @DisplayName("GET /savings-products/{id} → 404 when not found")
    void savingsProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── GET /loan-products ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /loan-products → 200 with list of active products")
    void loanProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /loan-products/{id} → 200 with correct product name")
    void loanProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", loanProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Personal Loan"));
    }

    @Test
    @DisplayName("GET /loan-products/{id} → 404 when not found")
    void loanProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }
}