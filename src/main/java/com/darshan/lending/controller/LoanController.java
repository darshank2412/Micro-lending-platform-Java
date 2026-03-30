package com.darshan.lending.controller;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.LoanSummaryResponse;
import com.darshan.lending.service.EmiPaymentService;
import com.darshan.lending.service.LoanDisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "11. Loan Disbursement & EMI",
        description = "Loan disbursement, EMI repayment schedule and payment processing")
public class LoanController {

    private final LoanDisbursementService loanDisbursementService;
    private final EmiPaymentService       emiPaymentService;

    @Operation(
            summary = "ADMIN — Disburse a loan",
            description = "Disburses an ACCEPTED loan offer — debits lender account, " +
                    "credits borrower account, generates full EMI repayment schedule " +
                    "and transitions loan request to DISBURSED"
    )
    @PostMapping("/{offerId}/disburse")
    public ResponseEntity<LoanSummaryResponse> disburseLoan(
            @PathVariable Long offerId) {
        return ResponseEntity.ok(loanDisbursementService.disburseLoan(offerId));
    }

    @Operation(
            summary = "ADMIN — View all disbursed loans",
            description = "Get all active, completed and defaulted loans in the system"
    )
    @GetMapping
    public ResponseEntity<List<LoanSummaryResponse>> getAllLoans() {
        return ResponseEntity.ok(loanDisbursementService.getAllLoans());
    }

    @Operation(
            summary = "ADMIN/BORROWER/LENDER — Get loan details",
            description = "Get full loan summary including EMI amount, outstanding principal, " +
                    "total repayment, and current status"
    )
    @GetMapping("/{loanSummaryId}")
    public ResponseEntity<LoanSummaryResponse> getLoanSummary(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getLoanSummary(loanSummaryId));
    }

    @Operation(
            summary = "BORROWER — View my active loans",
            description = "Get all loans disbursed to this borrower — " +
                    "shows outstanding principal, EMIs paid and remaining"
    )
    @GetMapping("/my")
    public ResponseEntity<List<LoanSummaryResponse>> getMyLoans(
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(loanDisbursementService.getMyLoans(borrowerId));
    }

    @Operation(
            summary = "LENDER — View loans I have funded",
            description = "Get all loans this lender has funded — " +
                    "shows repayment progress and outstanding amounts"
    )
    @GetMapping("/funded")
    public ResponseEntity<List<LoanSummaryResponse>> getLoansFunded(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(loanDisbursementService.getLoansFunded(lenderId));
    }

    @Operation(
            summary = "BORROWER/ADMIN — View full EMI repayment schedule",
            description = "Get the complete amortization table for a loan — " +
                    "shows each EMI with principal component, interest component " +
                    "and outstanding principal after payment"
    )
    @GetMapping("/{loanSummaryId}/schedule")
    public ResponseEntity<List<EmiScheduleResponse>> getEmiSchedule(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getEmiSchedule(loanSummaryId));
    }

    @Operation(
            summary = "BORROWER — Pay next EMI",
            description = "Pay the next pending EMI — debits borrower savings account, " +
                    "credits lender savings account, updates outstanding principal. " +
                    "Automatically marks loan as COMPLETED when all EMIs are paid"
    )
    @PostMapping("/{loanSummaryId}/pay-emi")
    public ResponseEntity<EmiScheduleResponse> payNextEmi(
            @PathVariable Long loanSummaryId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(emiPaymentService.payNextEmi(loanSummaryId, borrowerId));
    }
}