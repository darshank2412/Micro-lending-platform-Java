package com.darshan.lending.service;

import com.darshan.lending.dto.BankAccountResponse;
import com.darshan.lending.entity.BankAccount;
import com.darshan.lending.entity.LoanProduct;
import com.darshan.lending.entity.SavingsProduct;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.AccountStatus;
import com.darshan.lending.entity.enums.AccountType;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final UserService           userService;
    private final SavingsProductService savingsProductService;
    private final LoanProductService    loanProductService;
    private final AuditLogService       auditLogService;

    // ── Open savings account ──────────────────────────────────────────────────

    @Transactional
    public BankAccountResponse openSavingsAccount(Long userId, Long savingsProductId) {
        User user             = userService.findUserById(userId);
        SavingsProduct product = savingsProductService.findById(savingsProductId);

        if (bankAccountRepository.findByUserIdAndAccountType(userId, AccountType.SAVINGS).isPresent()) {
            throw new BusinessException("User already has a savings account");
        }

        String accountNumber = "SAV" + String.format("%08d", userId);
        BankAccount account = BankAccount.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .user(user)
                .savingsProduct(product)
                .build();

        BankAccount saved = bankAccountRepository.save(account);

        // ── FIX 3: Reload so Hibernate fetches savingsProduct / loanProduct ───
        BankAccount reloaded = bankAccountRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found after save: " + saved.getId()));

        log.info("Savings account opened: {} userId={} productId={}",
                accountNumber, userId, savingsProductId);
        return toResponse(reloaded);
    }

    // ── Open loan account ─────────────────────────────────────────────────────

    @Transactional
    public BankAccountResponse openLoanAccount(Long userId, Long loanProductId) {
        User user           = userService.findUserById(userId);
        LoanProduct product = loanProductService.findById(loanProductId);

        if (bankAccountRepository.findByUserIdAndAccountType(userId, AccountType.LOAN).isPresent()) {
            throw new BusinessException("User already has a loan account");
        }

        String accountNumber = "LOA" + String.format("%08d", userId);
        BankAccount account = BankAccount.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.LOAN)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .user(user)
                .loanProduct(product)
                .build();

        BankAccount saved = bankAccountRepository.save(account);

        // ── FIX 3: Reload so Hibernate fetches loanProduct ───────────────────
        BankAccount reloaded = bankAccountRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found after save: " + saved.getId()));

        log.info("Loan account opened: {} userId={} productId={}",
                accountNumber, userId, loanProductId);
        return toResponse(reloaded);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankAccountResponse> getAccountsByUser(Long userId) {
        return bankAccountRepository.findByUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BankAccountResponse getById(Long accountId) {
        return toResponse(findById(accountId));
    }

    @Transactional(readOnly = true)
    public BankAccountResponse getSavingsAccount(Long userId) {
        BankAccount account = bankAccountRepository
                .findByUserIdAndAccountType(userId, AccountType.SAVINGS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No savings account for user: " + userId));
        return toResponse(account);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Transactional
    public BankAccountResponse deposit(Long accountId, BigDecimal amount) {
        BankAccount account = findById(accountId);
        if (account.getAccountType() != AccountType.SAVINGS) {
            throw new BusinessException("Deposits only allowed on savings accounts");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active: " + account.getStatus());
        }

        account.setBalance(account.getBalance().add(amount));
        BankAccount saved = bankAccountRepository.save(account);

        log.info("Deposit: accountId={} amount={} newBalance={}",
                accountId, amount, saved.getBalance());

        auditLogService.log(
                account.getUser().getId(),
                account.getUser().getRole().name(),
                account.getUser().getFullName(),
                AuditLogService.ACTION_DEPOSIT,
                AuditLogService.RESOURCE_ACCOUNT,
                accountId,
                "Amount: " + amount + " | New balance: " + saved.getBalance(),
                "SUCCESS"
        );

        return toResponse(saved);
    }

    // ── Withdraw ──────────────────────────────────────────────────────────────

    @Transactional
    public BankAccountResponse withdraw(Long accountId, BigDecimal amount) {
        BankAccount account = findById(accountId);
        if (account.getAccountType() != AccountType.SAVINGS) {
            throw new BusinessException("Withdrawals only allowed on savings accounts");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active: " + account.getStatus());
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Insufficient balance. Available: " + account.getBalance());
        }

        account.setBalance(account.getBalance().subtract(amount));
        BankAccount saved = bankAccountRepository.save(account);

        log.info("Withdrawal: accountId={} amount={} newBalance={}",
                accountId, amount, saved.getBalance());

        auditLogService.log(
                account.getUser().getId(),
                account.getUser().getRole().name(),
                account.getUser().getFullName(),
                AuditLogService.ACTION_WITHDRAWAL,
                AuditLogService.RESOURCE_ACCOUNT,
                accountId,
                "Amount: " + amount + " | New balance: " + saved.getBalance(),
                "SUCCESS"
        );

        return toResponse(saved);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    public BankAccount findById(Long accountId) {
        return bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));
    }

    private BankAccountResponse toResponse(BankAccount a) {
        return BankAccountResponse.builder()
                .id(a.getId())
                .accountNumber(a.getAccountNumber())
                .accountType(a.getAccountType())
                .balance(a.getBalance())
                .status(a.getStatus())
                .userId(a.getUser().getId())
                .savingsProductId(a.getSavingsProduct() != null
                        ? a.getSavingsProduct().getId() : null)
                .savingsProductName(a.getSavingsProduct() != null
                        ? a.getSavingsProduct().getName() : null)
                .loanProductId(a.getLoanProduct() != null
                        ? a.getLoanProduct().getId() : null)
                .loanProductName(a.getLoanProduct() != null
                        ? a.getLoanProduct().getName() : null)
                .createdAt(a.getCreatedAt())
                .build();
    }
}