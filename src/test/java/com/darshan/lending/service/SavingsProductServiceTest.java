package com.darshan.lending.service;

import com.darshan.lending.dto.*;
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
class SavingsProductServiceTest {

    @Autowired SavingsProductService savingsProductService;

    private Long savingsProductId;

    @BeforeEach
    void setUp() {
        savingsProductId = savingsProductService.create(SavingsProductRequest.builder()
                .name("Basic Savings").minBalance(new BigDecimal("500"))
                .maxBalance(new BigDecimal("1000000")).interestRate(new BigDecimal("4.50"))
                .build()).getId();
    }

    @ParameterizedTest
    @CsvSource({
            "100000, 500,   4.50",
            "500,    500,   4.50",
            "500,    10000, 25.00"
    })
    void create_shouldFailForInvalidRanges(
            BigDecimal minBalance, BigDecimal maxBalance, BigDecimal interestRate) {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("Bad Savings").minBalance(minBalance)
                .maxBalance(maxBalance).interestRate(interestRate).build();
        assertThrows(Exception.class, () -> savingsProductService.create(req));
    }

    @Test
    void create_shouldFailForDuplicateName() {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("Basic Savings").minBalance(new BigDecimal("100"))
                .maxBalance(new BigDecimal("50000")).interestRate(new BigDecimal("3.00")).build();
        assertThrows(BusinessException.class, () -> savingsProductService.create(req));
    }

    @Test
    void getAll_shouldReturnActiveProducts() {
        List<SavingsProductResponse> all = savingsProductService.getAll();
        assertFalse(all.isEmpty());
    }

    @Test
    void getById_shouldReturnCorrectProduct() {
        SavingsProductResponse found = savingsProductService.getById(savingsProductId);
        assertEquals("Basic Savings", found.getName());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> savingsProductService.getById(999999L));
    }

    @Test
    void update_shouldUpdateFields() {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("Premium Savings").minBalance(new BigDecimal("1000"))
                .maxBalance(new BigDecimal("2000000")).interestRate(new BigDecimal("6.00")).build();
        SavingsProductResponse updated = savingsProductService.update(savingsProductId, req);
        assertEquals("Premium Savings", updated.getName());
        assertEquals(new BigDecimal("6.00"), updated.getInterestRate());
    }

    @Test
    void update_shouldFailWhenInterestTooHigh() {
        SavingsProductRequest req = SavingsProductRequest.builder()
                .name("High Interest").minBalance(new BigDecimal("500"))
                .maxBalance(new BigDecimal("100000")).interestRate(new BigDecimal("25.00")).build();
        assertThrows(BusinessException.class, () -> savingsProductService.update(savingsProductId, req));
    }

    @Test
    void deactivate_shouldWork() {
        savingsProductService.deactivate(savingsProductId);
        List<SavingsProductResponse> all = savingsProductService.getAll();
        assertTrue(all.stream().noneMatch(p -> p.getId().equals(savingsProductId)));
    }
}
