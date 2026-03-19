package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.dto.BankAccountResponse;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.service.*;
import org.junit.jupiter.api.BeforeEach;
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
class ControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountService bankAccountService;
    @Autowired SavingsProductService savingsProductService;
    @Autowired LoanProductService loanProductService;
    @Autowired OtpService otpService;
    @Autowired KycService kycService;

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

    @Test
    void sendOtp_shouldReturn200() throws Exception {
        String body = "{\"identifier\":\"9111122233\",\"otpType\":\"PHONE\",\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\"}";
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void verifyOtp_shouldReturn200AndUserId() throws Exception {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9222233344").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION).countryCode("+91").build());
        String body = String.format(
                "{\"identifier\":\"9222233344\",\"otpCode\":\"%s\",\"otpType\":\"PHONE\",\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\",\"role\":\"BORROWER\"}",
                otp);
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void sendOtp_shouldReturn400OnValidationFailure() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savingsProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void savingsProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", savingsProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Basic Savings"));
    }

    @Test
    void savingsProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void loanProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void loanProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", loanProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Personal Loan"));
    }

    @Test
    void loanProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void openSavingsAccount_shouldReturn200() throws Exception {
        String body = String.format("{\"userId\":%d,\"productId\":%d}", testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/savings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"));
    }

    @Test
    void openLoanAccount_shouldReturn200() throws Exception {
        String body = String.format("{\"userId\":%d,\"productId\":%d}", testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/loan")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("LOAN"));
    }

    @Test
    void getAccountsByUser_shouldReturn200() throws Exception {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(get("/accounts/user/{userId}", testUser.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getAccountById_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(get("/accounts/{accountId}", acc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(acc.getId()));
    }

    @Test
    void deposit_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2000));
    }

    @Test
    void withdraw_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("5000"));
        mockMvc.perform(post("/accounts/{accountId}/withdraw", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isOk());
    }

    @Test
    void deposit_shouldReturn400OnBusinessException() throws Exception {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_shouldReturn200() throws Exception {
        mockMvc.perform(get("/users/{id}", testUser.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/users/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerUser_shouldReturn200() throws Exception {
        String body = "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"testuser@example.com\"," +
                "\"phoneNumber\":\"9876543210\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\"," +
                "\"role\":\"BORROWER\",\"pan\":\"TESTX1234Y\",\"incomeBracket\":\"5-10 LPA\"," +
                "\"p2pExperience\":\"BEGINNER\",\"password\":\"Test@1234\"," +
                "\"address\":{\"line1\":\"123 Street\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}}";
        mockMvc.perform(post("/register")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLATFORM_ACCOUNT_CREATED"));
    }

    @Test
    void updateProfile_shouldReturn200() throws Exception {
        String body = "{\"fullName\":\"New Name\",\"email\":\"new@email.com\",\"gender\":\"FEMALE\"}";
        mockMvc.perform(put("/users/profile")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("New Name"));
    }

    @Test
    void submitKyc_shouldReturn200() throws Exception {
        String body = "{\"documentType\":\"AADHAAR\",\"documentNumber\":\"1234 5678 9012\",\"documentUrl\":\"https://s3.example.com/doc.pdf\"}";
        mockMvc.perform(post("/kyc/submit")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getKycDocuments_shouldReturn200() throws Exception {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        mockMvc.perform(get("/kyc/{userId}", testUser.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void approveKyc_shouldReturn200() throws Exception {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        mockMvc.perform(patch("/kyc/approve/{docId}", doc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void rejectKyc_shouldReturn200() throws Exception {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        mockMvc.perform(patch("/kyc/reject/{docId}", doc.getId())
                        .param("reason", "Blurry image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void getKycDocuments_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(get("/kyc/{userId}", 999999L))
                .andExpect(status().isNotFound());
    }
}
