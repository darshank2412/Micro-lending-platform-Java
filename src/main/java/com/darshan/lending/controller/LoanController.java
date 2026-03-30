package com.darshan.lending.controller;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.Foreclosureresponse;
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

    // ─────────────────────────────────────────────────────────────────────
    // ADMIN ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "ADMIN — Disburse a loan",
            description = """
//                Disburses an ACCEPTED loan offer:
//                - Debits lender savings account
//                - Credits borrower savings account
//                - Generates full EMI amortization schedule
//                - Transitions loan request to DISBURSED
            """
    )
    @PostMapping("/{offerId}/disburse")
    public ResponseEntity<LoanSummaryResponse> disburseLoan(
            @PathVariable Long offerId) {
        return ResponseEntity.ok(loanDisbursementService.disburseLoan(offerId));
    }

    @Operation(
            summary = "ADMIN — View all disbursed loans",
            description = ""
    )
    @GetMapping
    public ResponseEntity<List<LoanSummaryResponse>> getAllLoans() {
        return ResponseEntity.ok(loanDisbursementService.getAllLoans());
    }

    // ─────────────────────────────────────────────────────────────────────
    // SHARED ENDPOINTS (ADMIN / BORROWER / LENDER)
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "ADMIN/BORROWER/LENDER — Get loan details",
            description = ""
//                    +
//                    "Get full loan summary: EMI amount, outstanding principal, " +
//                    "total repayment, and current status"
    )
    @GetMapping("/{loanSummaryId}")
    public ResponseEntity<LoanSummaryResponse> getLoanSummary(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getLoanSummary(loanSummaryId));
    }

    @Operation(
            summary = "BORROWER/ADMIN — View full EMI repayment schedule",
            description = """
//                Get the complete amortization table for a loan:
//                - Each EMI row shows principal component, interest component
//                - Outstanding principal after payment
//                - Due date, paid date, and status (PENDING / PAID / OVERDUE)
            """
    )
    @GetMapping("/{loanSummaryId}/schedule")
    public ResponseEntity<List<EmiScheduleResponse>> getEmiSchedule(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getEmiSchedule(loanSummaryId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // BORROWER ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "BORROWER — View my active loans",
            description = ""
//                    +
//                    "Get all loans disbursed to this borrower — " +
//                    "shows outstanding principal, EMIs paid and remaining"
    )
    @GetMapping("/my")
    public ResponseEntity<List<LoanSummaryResponse>> getMyLoans(
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(loanDisbursementService.getMyLoans(borrowerId));
    }

    @Operation(
            summary = "BORROWER — Pay next EMI",
            description = """
//                Pay the next pending or overdue EMI.
//
//                Rules enforced:
//                - Current EMI must be paid before paying an advance EMI
//                - Maximum 1 EMI can be paid in advance
//                - If paid after the 5th of the due month, late penalty is charged:
//                  Penalty = EMI amount × 24% p.a. × (days late / 365)
//                - Debits borrower savings, credits lender savings
//                - Automatically marks loan as COMPLETED when all EMIs are paid
            """
    )
    @PostMapping("/{loanSummaryId}/pay-emi")
    public ResponseEntity<EmiScheduleResponse> payNextEmi(
            @PathVariable Long loanSummaryId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(emiPaymentService.payNextEmi(loanSummaryId, borrowerId));
    }

    @Operation(
            summary = "BORROWER — Foreclose loan (pay off early)",
            description = """
//                Pay off the entire outstanding loan balance before tenure ends.
//
//                Total payable =
//                  Outstanding principal
//                  + Accrued interest (since last EMI paid / disbursement date)
//                  + 2% foreclosure charge on outstanding principal
//
//                All remaining EMIs are marked PAID. Loan status → COMPLETED.
            """
    )
    @PostMapping("/{loanSummaryId}/foreclose")
    public ResponseEntity<Foreclosureresponse> foreclose(
            @PathVariable Long loanSummaryId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(emiPaymentService.foreclose(loanSummaryId, borrowerId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LENDER ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "LENDER — View loans I have funded",
            description = ""
//            +
//                    "Get all loans this lender has funded — " +
//                    "shows repayment progress and outstanding amounts"
    )
    @GetMapping("/funded")
    public ResponseEntity<List<LoanSummaryResponse>> getLoansFunded(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(loanDisbursementService.getLoansFunded(lenderId));
    }
}