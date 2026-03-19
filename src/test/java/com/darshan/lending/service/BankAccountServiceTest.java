package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
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
class BankAccountServiceTest {

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
                .password(".4qFITYhSKOYryHuMBQOuRXpqOOoGBTJyZHmCJKYANmFz6dyT4Oy")
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
        assertThrows(ResourceNotFoundException.class, () -> bankAccountService.findById(999999L));
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
}
