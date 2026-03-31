package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.enums.ProductStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class LoanProductServiceTest {

    @Autowired LoanProductService loanProductService;

    private Long loanProductId;

    @BeforeEach
    void setUp() {
        loanProductId = loanProductService.create(LoanProductRequest.builder()
                .name("Personal Loan").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minTenure(6).maxTenure(60)
                .minInterest(new BigDecimal("8")).maxInterest(new BigDecimal("24"))
                .build()).getId();
    }

    @ParameterizedTest
    @CsvSource({
            "500000, 100000, 8,  24, 6,  60",
            "100000, 500000, 24, 8,  6,  60",
            "100000, 500000, 8,  24, 60, 6"
    })
    void create_shouldFailForInvalidRanges(
            BigDecimal minAmount, BigDecimal maxAmount,
            BigDecimal minInterest, BigDecimal maxInterest,
            Integer minTenure, Integer maxTenure) {
        LoanProductRequest req = LoanProductRequest.builder()
                .name("Test Loan").minAmount(minAmount).maxAmount(maxAmount)
                .minInterest(minInterest).maxInterest(maxInterest)
                .minTenure(minTenure).maxTenure(maxTenure).build();
        assertThrows(BusinessException.class, () -> loanProductService.create(req));
    }

    @Test
    void create_shouldFailForDuplicateName() {
        LoanProductRequest req = LoanProductRequest.builder()
                .name("Personal Loan").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minInterest(new BigDecimal("8"))
                .maxInterest(new BigDecimal("24")).minTenure(6).maxTenure(60).build();
        assertThrows(BusinessException.class, () -> loanProductService.create(req));
    }

    @Test
    void getAll_shouldReturnActiveProducts() {
        List<LoanProductResponse> all = loanProductService.getAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(p -> p.getStatus() == ProductStatus.ACTIVE));
    }

    @Test
    void getById_shouldReturnCorrectProduct() {
        LoanProductResponse found = loanProductService.getById(loanProductId);
        assertEquals("Personal Loan", found.getName());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> loanProductService.getById(999999L));
    }

    @Test
    void update_shouldUpdateFields() {
        LoanProductRequest req = LoanProductRequest.builder()
                .name("Updated Loan").minAmount(new BigDecimal("5000"))
                .maxAmount(new BigDecimal("200000")).minInterest(new BigDecimal("9"))
                .maxInterest(new BigDecimal("20")).minTenure(3).maxTenure(48).build();
        LoanProductResponse updated = loanProductService.update(loanProductId, req);
        assertEquals("Updated Loan", updated.getName());
        assertEquals(new BigDecimal("5000"), updated.getMinAmount());
    }

    @Test
    void delete_shouldDeactivate() {
        loanProductService.delete(loanProductId);
        List<LoanProductResponse> all = loanProductService.getAll();
        assertTrue(all.stream().noneMatch(p -> p.getId().equals(loanProductId)));
    }

    @Test
    void getById_notFound_throwsException() {
        assertThrows(ResourceNotFoundException.class,
                () -> loanProductService.findById(99999L));
    }
}
