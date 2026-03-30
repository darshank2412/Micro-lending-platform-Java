package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.dto.BankAccountResponse;
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
class BankAccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountService bankAccountService;
    @Autowired SavingsProductService savingsProductService;
    @Autowired LoanProductService loanProductService;

    private User testUser;
    private Long savingsProductId;
    private Long loanProductId;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password("hash")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());

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

    // ── POST /accounts/savings ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /accounts/savings → 200 and accountType SAVINGS")
    void openSavingsAccount_shouldReturn200() throws Exception {
        mockMvc.perform(post("/accounts/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(testUser.getId(), savingsProductId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.data.accountNumber").isString());
    }

    @Test
    @DisplayName("POST /accounts/savings → 400 when savings account already exists")
    void openSavingsAccount_shouldReturn400WhenAlreadyExists() throws Exception {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(testUser.getId(), savingsProductId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /accounts/savings → 404 when user not found")
    void openSavingsAccount_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/accounts/savings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(999999L, savingsProductId)))
                .andExpect(status().isNotFound());
    }

    // ── POST /accounts/loan ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /accounts/loan → 200 and accountType LOAN")
    void openLoanAccount_shouldReturn200() throws Exception {
        mockMvc.perform(post("/accounts/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(testUser.getId(), loanProductId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("LOAN"))
                .andExpect(jsonPath("$.data.accountNumber").isString());
    }

    @Test
    @DisplayName("POST /accounts/loan → 400 when loan account already exists")
    void openLoanAccount_shouldReturn400WhenAlreadyExists() throws Exception {
        bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(testUser.getId(), loanProductId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /accounts/loan → 404 when user not found")
    void openLoanAccount_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/accounts/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(999999L, loanProductId)))
                .andExpect(status().isNotFound());
    }

    // ── GET /accounts/user/{userId} ───────────────────────────────────────────

    @Test
    @DisplayName("GET /accounts/user/{userId} → 200 with all accounts for user")
    void getAccountsByUser_shouldReturn200() throws Exception {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(get("/accounts/user/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /accounts/user/{userId} → 200 with empty list when no accounts")
    void getAccountsByUser_noAccounts_shouldReturn200() throws Exception {
        mockMvc.perform(get("/accounts/user/{userId}", testUser.getId()))
                .andExpect(status().isOk());
    }

    // ── GET /accounts/{accountId} ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /accounts/{accountId} → 200 with correct account data")
    void getAccountById_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(get("/accounts/{accountId}", acc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(acc.getId()))
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"));
    }

    @Test
    @DisplayName("GET /accounts/{accountId} → 404 when account not found")
    void getAccountById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── POST /accounts/{accountId}/deposit ────────────────────────────────────

    @Test
    @DisplayName("POST /accounts/{accountId}/deposit → 200 and updated balance")
    void deposit_shouldReturn200AndUpdatedBalance() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2000));
    }

    @Test
    @DisplayName("POST /accounts/{accountId}/deposit → 400 when depositing to a LOAN account")
    void deposit_shouldReturn400OnLoanAccount() throws Exception {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /accounts/{accountId}/deposit → 404 when account not found")
    void deposit_shouldReturn404WhenAccountNotFound() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/deposit", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isNotFound());
    }

    // ── POST /accounts/{accountId}/withdraw ───────────────────────────────────

    @Test
    @DisplayName("POST /accounts/{accountId}/withdraw → 200 and decreased balance")
    void withdraw_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("5000"));
        mockMvc.perform(post("/accounts/{accountId}/withdraw", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(4000));
    }

    @Test
    @DisplayName("POST /accounts/{accountId}/withdraw → 400 when insufficient balance")
    void withdraw_shouldReturn400WhenInsufficientBalance() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("500"));
        mockMvc.perform(post("/accounts/{accountId}/withdraw", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":9999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /accounts/{accountId}/withdraw → 400 when withdrawing from a LOAN account")
    void withdraw_shouldReturn400OnLoanAccount() throws Exception {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/{accountId}/withdraw", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String accountBody(Long userId, Long productId) {
        return String.format("{\"userId\":%d,\"productId\":%d}", userId, productId);
    }
}