package com.darshan.lending;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import com.darshan.lending.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class LendingApplicationTests {

    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired OtpVerificationRepository otpVerificationRepository;

    @Autowired BankAccountService bankAccountService;
    @Autowired LoanProductService loanProductService;
    @Autowired SavingsProductService savingsProductService;
    @Autowired UserService userService;
    @Autowired KycService kycService;
    @Autowired OtpService otpService;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private User testUser;
    private Long savingsProductId;
    private Long loanProductId;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210")
                .countryCode("+91")
                .email("test@lending.com")
                .password("$2a$10$N.4qFITYhSKOYryHuMBQOuRXpqOOoGBTJyZHmCJKYANmFz6dyT4Oy")
                .role(Role.BORROWER)
                .status(UserStatus.MOBILE_VERIFIED)
                .build());

        savingsProductId = savingsProductService.create(
                SavingsProductRequest.builder()
                        .name("Basic Savings")
                        .minBalance(new BigDecimal("500"))
                        .maxBalance(new BigDecimal("1000000"))
                        .interestRate(new BigDecimal("4.50"))
                        .build()).getId();

        loanProductId = loanProductService.create(
                LoanProductRequest.builder()
                        .name("Personal Loan")
                        .minAmount(new BigDecimal("10000"))
                        .maxAmount(new BigDecimal("500000"))
                        .minTenure(6).maxTenure(60)
                        .minInterest(new BigDecimal("8"))
                        .maxInterest(new BigDecimal("24"))
                        .build()).getId();
    }

    // =========================================================================
    // BANK ACCOUNT SERVICE TESTS
    // =========================================================================

    @Test
    void openSavingsAccount_shouldLinkToSavingsProduct() {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        assertTrue(acc.getAccountNumber().startsWith("SAV"));
        assertEquals(AccountType.SAVINGS, acc.getAccountType());
        assertEquals("Basic Savings", acc.getSavingsProductName());
        assertEquals(BigDecimal.ZERO, acc.getBalance());
    }

    @Test
    void openLoanAccount_shouldLinkToLoanProduct() {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        assertTrue(acc.getAccountNumber().startsWith("LOA"));
        assertEquals(AccountType.LOAN, acc.getAccountType());
        assertEquals("Personal Loan", acc.getLoanProductName());
    }

    @Test
    void deposit_shouldIncreaseBalance() {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        BankAccountResponse result = bankAccountService.deposit(acc.getId(), new BigDecimal("5000"));
        assertEquals(new BigDecimal("5000"), result.getBalance());
    }

    @Test
    void withdraw_shouldDecreaseBalance() {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("3000"));
        BankAccountResponse result = bankAccountService.withdraw(acc.getId(), new BigDecimal("1000"));
        assertEquals(new BigDecimal("2000"), result.getBalance());
    }

    @Test
    void withdraw_shouldFailWhenInsufficientBalance() {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("1000"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.withdraw(acc.getId(), new BigDecimal("5000")));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
    }

    @Test
    void openSavingsAccount_shouldFailIfAlreadyExists() {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId));
        assertTrue(ex.getMessage().contains("already has a savings account"));
    }

    @Test
    void openLoanAccount_shouldFailIfAlreadyExists() {
        bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.openLoanAccount(testUser.getId(), loanProductId));
        assertTrue(ex.getMessage().contains("already has a loan account"));
    }

    @Test
    void deposit_shouldFailOnLoanAccount() {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.deposit(acc.getId(), new BigDecimal("1000")));
        assertTrue(ex.getMessage().contains("savings accounts"));
    }

    @Test
    void withdraw_shouldFailOnLoanAccount() {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bankAccountService.withdraw(acc.getId(), new BigDecimal("1000")));
        assertTrue(ex.getMessage().contains("savings accounts"));
    }

    @Test
    void getAccountsByUser_shouldReturnAllAccounts() {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        List<BankAccountResponse> accounts = bankAccountService.getAccountsByUser(testUser.getId());
        assertTrue(accounts.size() >= 2);
    }

    @Test
    void getById_shouldReturnAccount() {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        BankAccountResponse found = bankAccountService.getById(acc.getId());
        assertEquals(acc.getId(), found.getId());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> bankAccountService.findById(999999L));
    }

    @Test
    void openSavingsAccount_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> bankAccountService.openSavingsAccount(999999L, savingsProductId));
    }

    @Test
    void openLoanAccount_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> bankAccountService.openLoanAccount(999999L, loanProductId));
    }

    // =========================================================================
    // LOAN PRODUCT SERVICE TESTS
    // =========================================================================

    @ParameterizedTest
    @CsvSource({
            "500000, 100000, 8,  24, 6,  60",
            "100000, 500000, 24, 8,  6,  60",
            "100000, 500000, 8,  24, 60, 6"
    })
    void loanProduct_shouldFailForInvalidRanges(
            BigDecimal minAmount, BigDecimal maxAmount,
            BigDecimal minInterest, BigDecimal maxInterest,
            Integer minTenure, Integer maxTenure) {
        LoanProductRequest req = LoanProductRequest.builder()
                .name("Test Loan " + minAmount)
                .minAmount(minAmount).maxAmount(maxAmount)
                .minInterest(minInterest).maxInterest(maxInterest)
                .minTenure(minTenure).maxTenure(maxTenure)
                .build();
        assertThrows(BusinessException.class, () -> loanProductService.create(req));
    }

    @Test
    void loanProduct_shouldFailForDuplicateName() {
        LoanProductRequest req = LoanProductRequest.builder()
                .name("Personal Loan")
                .minAmount(new BigDecimal("10000")).maxAmount(new BigDecimal("500000"))
                .minInterest(new BigDecimal("8")).maxInterest(new BigDecimal("24"))
                .minTenure(6).maxTenure(60)
                .build();
        assertThrows(BusinessException.class, () -> loanProductService.create(req));
    }

    @Test
    void loanProduct_getAll_shouldReturnActiveProducts() {
        List<LoanProductResponse> all = loanProductService.getAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(p -> p.getStatus() == ProductStatus.ACTIVE));
    }

    @Test
    void loanProduct_getById_shouldReturnCorrectProduct() {
        LoanProductResponse found = loanProductService.getById(loanProductId);
        assertEquals("Personal Loan", found.getName());
    }

    @Test
    void loanProduct_getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> loanProductService.getById(999999L));
    }

    @Test
    void loanProduct_update_shouldUpdateFields() {
        LoanProductRequest updateReq = LoanProductRequest.builder()
                .name("Updated Loan")
                .minAmount(new BigDecimal("5000")).maxAmount(new BigDecimal("200000"))
                .minInterest(new BigDecimal("9")).maxInterest(new BigDecimal("20"))
                .minTenure(3).maxTenure(48)
                .build();
        LoanProductResponse updated = loanProductService.update(loanProductId, updateReq);
        assertEquals("Updated Loan", updated.getName());
        assertEquals(new BigDecimal("5000"), updated.getMinAmount());
    }

    @Test
    void loanProduct_update_shouldFailForInvalidRanges() {
        LoanProductRequest badReq = LoanProductRequest.builder()
                .name("Bad Loan")
                .minAmount(new BigDecimal("500000")).maxAmount(new BigDecimal("10000"))
                .minInterest(new BigDecimal("8")).maxInterest(new BigDecimal("24"))
                .minTenure(6).maxTenure(60)
                .build();
        assertThrows(BusinessException.class, () -> loanProductService.update(loanProductId, badReq));
    }

    @Test
    void loanProduct_delete_shouldDeactivate() {
        loanProductService.delete(loanProductId);
        List<LoanProductResponse> all = loanProductService.getAll();
        assertTrue(all.stream().noneMatch(p -> p.getId().equals(loanProductId)));
    }

    // =========================================================================
    // SAVINGS PRODUCT SERVICE TESTS
    // =========================================================================

    @ParameterizedTest
    @CsvSource({
            "100000, 500,    4.50",
            "500,    500,    4.50",
            "500,    10000,  25.00"
    })
    void savingsProduct_shouldFailForInvalidRanges(
            BigDecimal minBalance, BigDecimal maxBalance, BigDecimal interestRate) {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("Bad Savings " + minBalance)
                .minBalance(minBalance).maxBalance(maxBalance)
                .interestRate(interestRate)
                .build();
        assertThrows(Exception.class, () -> savingsProductService.create(req));
    }

    @Test
    void savingsProduct_shouldFailForDuplicateName() {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("Basic Savings")
                .minBalance(new BigDecimal("100")).maxBalance(new BigDecimal("50000"))
                .interestRate(new BigDecimal("3.00"))
                .build();
        assertThrows(BusinessException.class, () -> savingsProductService.create(req));
    }

    @Test
    void savingsProduct_getAll_shouldReturnActiveProducts() {
        List<SavingsProductResponse> all = savingsProductService.getAll();
        assertFalse(all.isEmpty());
    }

    @Test
    void savingsProduct_getById_shouldReturnCorrectProduct() {
        SavingsProductResponse found = savingsProductService.getById(savingsProductId);
        assertEquals("Basic Savings", found.getName());
    }

    @Test
    void savingsProduct_getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> savingsProductService.getById(999999L));
    }

    @Test
    void savingsProduct_update_shouldUpdateFields() {
        SavingsProductRequest updateReq = SavingsProductRequest.builder()
                .name("Premium Savings")
                .minBalance(new BigDecimal("1000")).maxBalance(new BigDecimal("2000000"))
                .interestRate(new BigDecimal("6.00"))
                .build();
        SavingsProductResponse updated = savingsProductService.update(savingsProductId, updateReq);
        assertEquals("Premium Savings", updated.getName());
        assertEquals(new BigDecimal("6.00"), updated.getInterestRate());
    }

    @Test
    void savingsProduct_update_shouldFailWhenInterestTooHigh() {
        SavingsProductRequest badReq = SavingsProductRequest.builder()
                .name("High Interest")
                .minBalance(new BigDecimal("500")).maxBalance(new BigDecimal("100000"))
                .interestRate(new BigDecimal("25.00"))
                .build();
        assertThrows(BusinessException.class, () -> savingsProductService.update(savingsProductId, badReq));
    }

    @Test
    void savingsProduct_deactivate_shouldWork() {
        savingsProductService.deactivate(savingsProductId);
        List<SavingsProductResponse> all = savingsProductService.getAll();
        assertTrue(all.stream().noneMatch(p -> p.getId().equals(savingsProductId)));
    }

    // =========================================================================
    // USER SERVICE TESTS
    // =========================================================================

    @Test
    void userService_register_shouldCompleteRegistration() {
        UserResponse response = userService.register(testUser.getId(), buildValidRegistrationRequest("ABCDE1234F"));
        assertEquals(UserStatus.PLATFORM_ACCOUNT_CREATED, response.getStatus());
        assertEquals("Darshan Kumar", response.getFullName());
        assertNotNull(response.getPlatformAccountNumber());
        assertTrue(response.getPlatformAccountNumber().startsWith("MLP"));
    }

    @Test
    void userService_register_shouldFailWhenNotMobileVerified() {
        testUser.setStatus(UserStatus.REGISTRATION_COMPLETE);
        userRepository.save(testUser);
        assertThrows(BusinessException.class,
                () -> userService.register(testUser.getId(), buildValidRegistrationRequest("ZZZZZ9999Z")));
    }

    @Test
    void userService_register_shouldFailOnDuplicatePan() {
        userService.register(testUser.getId(), buildValidRegistrationRequest("ABCDE1234F"));

        User secondUser = userRepository.save(User.builder()
                .phoneNumber("9999988888")
                .countryCode("+91")
                .email("second@lending.com")
                .password("password")
                .role(Role.BORROWER)
                .status(UserStatus.MOBILE_VERIFIED)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(secondUser.getId(), buildValidRegistrationRequest("ABCDE1234F")));
        assertTrue(ex.getMessage().contains("PAN already registered"));
    }

    @Test
    void userService_getById_shouldReturnUser() {
        UserResponse response = userService.getById(testUser.getId());
        assertEquals(testUser.getId(), response.getId());
    }

    @Test
    void userService_getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(999999L));
    }

    @Test
    void userService_updateProfile_shouldUpdateAllFields() {
        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Updated Name")
                .email("updated@example.com")
                .gender(Gender.FEMALE)
                .build();
        UserResponse response = userService.updateProfile(testUser.getId(), req);
        assertEquals("Updated Name", response.getFullName());
        assertEquals("updated@example.com", response.getEmail());
        assertEquals(Gender.FEMALE, response.getGender());
    }

    @Test
    void userService_updateProfile_withNullFields_shouldNotOverwrite() {
        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Only Name Changed")
                .build();
        UserResponse response = userService.updateProfile(testUser.getId(), req);
        assertEquals("Only Name Changed", response.getFullName());
    }

    @Test
    void userService_toResponse_withNoAddress_shouldReturnNullAddress() {
        UserResponse response = userService.getById(testUser.getId());
        assertNull(response.getAddress());
    }

    // =========================================================================
    // KYC SERVICE TESTS
    // =========================================================================

    @Test
    void kyc_submitDocument_shouldSucceed() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR)
                .documentNumber("1234 5678 9012")
                .documentUrl("https://s3.example.com/doc.pdf")
                .build());
        assertNotNull(doc.getId());
        assertEquals(KycStatus.PENDING, doc.getStatus());
    }

    @Test
    void kyc_submitDocument_shouldFailOnDuplicate() {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("1234 5678 9012").build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                        .documentType(DocumentType.AADHAAR).documentNumber("1234 5678 9012").build()));
        assertTrue(ex.getMessage().contains("already submitted"));
    }

    @Test
    void kyc_submitDocument_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> kycService.submitDocument(999999L, KycSubmitRequest.builder()
                        .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build()));
    }

    @Test
    void kyc_getDocuments_shouldReturnList() {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        List<KycDocument> docs = kycService.getDocuments(testUser.getId());
        assertEquals(2, docs.size());
    }

    @Test
    void kyc_getDocuments_shouldThrowWhenUserNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> kycService.getDocuments(999999L));
    }

    @Test
    void kyc_approveDocument_shouldSetVerified() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PASSPORT).documentNumber("P1234567").build());
        KycDocument approved = kycService.approveDocument(doc.getId());
        assertEquals(KycStatus.VERIFIED, approved.getStatus());
        assertNotNull(approved.getReviewedAt());
    }

    @Test
    void kyc_approveDocument_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> kycService.approveDocument(999999L));
    }

    @Test
    void kyc_rejectDocument_shouldSetRejected() {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.DRIVING_LICENSE).documentNumber("DL1234").build());
        KycDocument rejected = kycService.rejectDocument(doc.getId(), "Document unclear");
        assertEquals(KycStatus.REJECTED, rejected.getStatus());
        assertEquals("Document unclear", rejected.getRejectionNote());
    }

    @Test
    void kyc_rejectDocument_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> kycService.rejectDocument(999999L, "reason"));
    }

    @Test
    void kyc_approveAllRequired_shouldUpdateUserKycToVerified() {
        KycDocument aadhaar = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        KycDocument pan = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        kycService.approveDocument(aadhaar.getId());
        kycService.approveDocument(pan.getId());
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(KycStatus.VERIFIED, updated.getKycStatus());
    }

    // =========================================================================
    // OTP SERVICE TESTS
    // =========================================================================

    @Test
    void otp_sendOtp_shouldReturnSixDigitCode() {
        OtpRequest req = OtpRequest.builder()
                .identifier("9876543210")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .build();
        String otp = otpService.sendOtp(req);
        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("[0-9]{6}"));
    }

    @Test
    void otp_verifyOtp_shouldCreateNewUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("8888877777")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .build());
        Long userId = otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                .identifier("8888877777")
                .otpCode(otp)
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .role(Role.BORROWER)
                .build());
        assertNotNull(userId);
        assertTrue(userRepository.findById(userId).isPresent());
    }

    @Test
    void otp_verifyOtp_shouldReturnExistingUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9876543210")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN)
                .countryCode("+91")
                .build());
        Long userId = otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                .identifier("9876543210")
                .otpCode(otp)
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN)
                .countryCode("+91")
                .role(Role.BORROWER)
                .build());
        assertEquals(testUser.getId(), userId);
    }

    @Test
    void otp_verifyOtp_shouldFailWhenNoPendingOtp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                        .identifier("0000000000")
                        .otpCode("123456")
                        .otpType(OtpType.PHONE)
                        .purpose(OtpPurpose.REGISTRATION)
                        .countryCode("+91")
                        .role(Role.BORROWER)
                        .build()));
        assertTrue(ex.getMessage().contains("No pending OTP found"));
    }

    @Test
    void otp_verifyOtp_shouldFailWhenWrongCode() {
        otpService.sendOtp(OtpRequest.builder()
                .identifier("7777766666")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                        .identifier("7777766666")
                        .otpCode("000000")
                        .otpType(OtpType.PHONE)
                        .purpose(OtpPurpose.REGISTRATION)
                        .countryCode("+91")
                        .role(Role.BORROWER)
                        .build()));
        assertTrue(ex.getMessage().contains("Invalid OTP"));
    }

    // =========================================================================
    // CONTROLLER LAYER — MockMvc Tests
    // =========================================================================

    @Test
    void controller_sendOtp_shouldReturn200() throws Exception {
        String body = "{\"identifier\":\"9111122233\",\"otpType\":\"PHONE\",\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\"}";
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void controller_verifyOtp_shouldReturn200AndUserId() throws Exception {
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
    void controller_sendOtp_shouldReturn400OnValidationFailure() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void controller_savingsProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void controller_savingsProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", savingsProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Basic Savings"));
    }

    @Test
    void controller_savingsProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/savings-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void controller_loanProducts_getAll_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void controller_loanProducts_getById_shouldReturn200() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", loanProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Personal Loan"));
    }

    @Test
    void controller_loanProducts_getById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/loan-products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void controller_openSavingsAccount_shouldReturn200() throws Exception {
        String body = String.format("{\"userId\":%d,\"productId\":%d}", testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/savings/open")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("SAVINGS"));
    }

    @Test
    void controller_openLoanAccount_shouldReturn200() throws Exception {
        String body = String.format("{\"userId\":%d,\"productId\":%d}", testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/loan/open")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("LOAN"));
    }

    @Test
    void controller_getAccountsByUser_shouldReturn200() throws Exception {
        bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(get("/accounts/user/{userId}", testUser.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void controller_getAccountById_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(get("/accounts/{accountId}", acc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(acc.getId()));
    }

    @Test
    void controller_deposit_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2000));
    }

    @Test
    void controller_withdraw_shouldReturn200() throws Exception {
        BankAccountResponse acc = bankAccountService.openSavingsAccount(testUser.getId(), savingsProductId);
        bankAccountService.deposit(acc.getId(), new BigDecimal("5000"));
        mockMvc.perform(post("/accounts/{accountId}/withdraw", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isOk());
    }

    @Test
    void controller_deposit_shouldReturn400OnBusinessException() throws Exception {
        BankAccountResponse acc = bankAccountService.openLoanAccount(testUser.getId(), loanProductId);
        mockMvc.perform(post("/accounts/{accountId}/deposit", acc.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void controller_getUser_shouldReturn200() throws Exception {
        mockMvc.perform(get("/users/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testUser.getId()));
    }

    @Test
    void controller_getUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/users/{userId}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void controller_registerUser_shouldReturn200() throws Exception {
        String body = "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"testuser@example.com\"," +
                "\"phoneNumber\":\"9876543210\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\"," +
                "\"role\":\"BORROWER\",\"pan\":\"TESTX1234Y\",\"incomeBracket\":\"5-10 LPA\"," +
                "\"p2pExperience\":\"BEGINNER\",\"password\":\"pass123\"," +
                "\"address\":{\"line1\":\"123 Street\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pincode\":\"560001\"}}";
        mockMvc.perform(post("/users/register")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLATFORM_ACCOUNT_CREATED"));
    }

    @Test
    void controller_updateProfile_shouldReturn200() throws Exception {
        String body = "{\"fullName\":\"New Name\",\"email\":\"new@email.com\",\"gender\":\"FEMALE\"}";
        mockMvc.perform(put("/users/profile")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("New Name"));
    }

    @Test
    void controller_submitKyc_shouldReturn200() throws Exception {
        String body = "{\"documentType\":\"AADHAAR\",\"documentNumber\":\"1234 5678 9012\",\"documentUrl\":\"https://s3.example.com/doc.pdf\"}";
        mockMvc.perform(post("/kyc/submit")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void controller_getKycDocuments_shouldReturn200() throws Exception {
        kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        mockMvc.perform(get("/kyc/{userId}", testUser.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void controller_approveKyc_shouldReturn200() throws Exception {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.AADHAAR).documentNumber("111122223333").build());
        mockMvc.perform(patch("/kyc/approve/{docId}", doc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void controller_rejectKyc_shouldReturn200() throws Exception {
        KycDocument doc = kycService.submitDocument(testUser.getId(), KycSubmitRequest.builder()
                .documentType(DocumentType.PAN).documentNumber("ABCDE1234F").build());
        mockMvc.perform(patch("/kyc/reject/{docId}", doc.getId())
                        .param("reason", "Blurry image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void controller_kycGetDocuments_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(get("/kyc/{userId}", 999999L))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private UserRegistrationRequest buildValidRegistrationRequest(String pan) {
        return UserRegistrationRequest.builder()
                .firstName("Darshan")
                .lastName("Kumar")
                .email("user@example.com")
                .phoneNumber("9876543210")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender(Gender.MALE)
                .role(Role.BORROWER)
                .pan(pan)
                .incomeBracket("5-10 LPA")
                .p2pExperience(P2pExperience.BEGINNER)
                .password("password123")
                .address(AddressDto.builder()
                        .line1("123 Main St")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .build();
    }
}