package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.BankAccountResponse;
import com.darshan.lending.dto.DepositWithdrawRequest;
import com.darshan.lending.dto.OpenAccountRequest;
import com.darshan.lending.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Bank Account APIs", description = "Open savings/loan accounts linked to products, deposit and withdraw")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @PostMapping("/savings")
    @Operation(summary = "Open savings account",
            description = "Links account to a SavingsProduct by productId. One savings account per user.")
    public ResponseEntity<ApiResponse<BankAccountResponse>> openSavings(
            @Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Savings account opened",
                bankAccountService.openSavingsAccount(request.getUserId(), request.getProductId())));
    }

    @PostMapping("/loan")
    @Operation(summary = "Open loan account",
            description = "Links account to a LoanProduct by productId. One loan account per user.")
    public ResponseEntity<ApiResponse<BankAccountResponse>> openLoan(
            @Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan account opened",
                bankAccountService.openLoanAccount(request.getUserId(), request.getProductId())));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all accounts for a user")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved",
                bankAccountService.getAccountsByUser(userId)));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<BankAccountResponse>> getById(@PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success("Account found",
                bankAccountService.getById(accountId)));
    }

    @PostMapping("/{accountId}/deposit")
    @Operation(summary = "Deposit into savings account")
    public ResponseEntity<ApiResponse<BankAccountResponse>> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositWithdrawRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Deposit successful",
                bankAccountService.deposit(accountId, request.getAmount())));
    }

    @PostMapping("/{accountId}/withdraw")
    @Operation(summary = "Withdraw from savings account")
    public ResponseEntity<ApiResponse<BankAccountResponse>> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositWithdrawRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful",
                bankAccountService.withdraw(accountId, request.getAmount())));
    }
}