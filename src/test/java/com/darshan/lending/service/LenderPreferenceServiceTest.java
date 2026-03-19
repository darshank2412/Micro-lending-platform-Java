package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LenderPreferenceServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired LenderPreferenceService lenderPreferenceService;
    @Autowired LoanProductService loanProductService;

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
                .name("Test Loan Product")
                .minAmount(new BigDecimal("10000")).maxAmount(new BigDecimal("500000"))
                .minInterest(new BigDecimal("8")).maxInterest(new BigDecimal("24"))
                .minTenure(6).maxTenure(60).build()).getId();
    }

    @Test
    void lender_canSavePreferences() {
        LenderPreferenceResponse response = lenderPreferenceService.savePreference(
                lender.getId(), buildPreferenceDto(RiskAppetite.MEDIUM));
        assertNotNull(response.getId());
        assertEquals(lender.getId(), response.getLenderId());
        assertEquals(loanProductId, response.getLoanProductId());
        assertTrue(response.getIsActive());
    }

    @Test
    void lender_canSavePreferencesForMultipleProducts() {
        Long secondProductId = loanProductService.create(LoanProductRequest.builder()
                .name("Home Loan").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minInterest(new BigDecimal("8"))
                .maxInterest(new BigDecimal("24")).minTenure(6).maxTenure(60)
                .build()).getId();

        lenderPreferenceService.savePreference(lender.getId(), buildPreferenceDto(RiskAppetite.MEDIUM));
        lenderPreferenceService.savePreference(lender.getId(),
                buildPreferenceDtoForProduct(secondProductId, RiskAppetite.LOW));

        List<LenderPreferenceResponse> prefs = lenderPreferenceService.getMyPreferences(lender.getId());
        assertEquals(2, prefs.size());
    }

    @Test
    void borrower_cannotSetLenderPreferences() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> lenderPreferenceService.savePreference(
                        borrower.getId(), buildPreferenceDto(RiskAppetite.LOW)));
        assertTrue(ex.getMessage().contains("Only LENDER"));
    }

    @Test
    void lender_canDeactivatePreference() {
        lenderPreferenceService.savePreference(lender.getId(), buildPreferenceDto(RiskAppetite.MEDIUM));
        LenderPreferenceResponse deactivated = lenderPreferenceService
                .deactivate(lender.getId(), loanProductId);
        assertFalse(deactivated.getIsActive());
    }

    private LenderPreferenceDto buildPreferenceDto(RiskAppetite riskAppetite) {
        return buildPreferenceDtoForProduct(loanProductId, riskAppetite);
    }

    private LenderPreferenceDto buildPreferenceDtoForProduct(Long productId, RiskAppetite riskAppetite) {
        return LenderPreferenceDto.builder()
                .loanProductId(productId)
                .minInterestRate(new BigDecimal("8"))
                .maxInterestRate(new BigDecimal("20"))
                .minTenureMonths(6).maxTenureMonths(48)
                .minLoanAmount(new BigDecimal("10000"))
                .maxLoanAmount(new BigDecimal("200000"))
                .riskAppetite(riskAppetite).build();
    }
}
