package com.darshan.lending.controller;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.Foreclosureresponse;
import com.darshan.lending.dto.LoanSummaryResponse;
import com.darshan.lending.service.EmiPaymentService;
import com.darshan.lending.service.LoanDisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "13. Loan Disbursement & EMI",
        description = "Loan disbursement, EMI repayment schedule and payment processing")
public class LoanController {

    private final LoanDisbursementService loanDisbursementService;
    private final EmiPaymentService emiPaymentService;

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    @Operation(
            summary = "ADMIN — Disburse a loan",
            description = """
                Disburses an ACCEPTED loan offer:
                - Debits lender savings account
                - Credits borrower savings account
                - Generates full EMI amortization schedule
                - Transitions loan request to DISBURSED
                - EMI due dates respect lender's preferred payment day if set
            """
    )
    @PostMapping("/{offerId}/disburse")
    public ResponseEntity<LoanSummaryResponse> disburseLoan(
            @PathVariable Long offerId,
    @RequestHeader(value = "Idempotency-Key", required = false)
    String idempotencyKey)
    {
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
            summary = "ADMIN — Get all loans (paginated)",
            description = "Returns paginated loan list. Use ?page=0&size=10&sort=createdAt,desc"
    )
    @GetMapping("/paged")
    public ResponseEntity<Page<LoanSummaryResponse>> getAllLoansPaged(Pageable pageable) {
        return ResponseEntity.ok(loanDisbursementService.getAllLoansPaged(pageable));
    }

    // ── SHARED (ADMIN / BORROWER / LENDER) ───────────────────────────────────

    @Operation(
            summary = "ADMIN/BORROWER/LENDER — Get loan details",
            description = "Get full loan summary: EMI amount, outstanding principal, " +
                    "total repayment, and current status"
    )
    @GetMapping("/{loanSummaryId}")
    public ResponseEntity<LoanSummaryResponse> getLoanSummary(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getLoanSummary(loanSummaryId));
    }

    @Operation(
            summary = "BORROWER/ADMIN — View full EMI repayment schedule",
            description = """
                Get the complete amortization table for a loan:
                - Each EMI row shows principal component, interest component
                - Outstanding principal after payment
                - Due date, paid date, and status (PENDING / PAID / OVERDUE / PARTIAL)
            """
    )
    @GetMapping("/{loanSummaryId}/schedule")
    public ResponseEntity<List<EmiScheduleResponse>> getEmiSchedule(
            @PathVariable Long loanSummaryId) {
        return ResponseEntity.ok(loanDisbursementService.getEmiSchedule(loanSummaryId));
    }

    // ── BORROWER ──────────────────────────────────────────────────────────────

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
            summary = "BORROWER — Pay EMI (full or partial)",
            description = """
                Pay any amount toward your next EMI.

                Rules:
                - Enter any amount you can afford
                - If amount < EMI due: EMI marked PARTIAL, shortfall added to next EMI
                - If amount = EMI due: EMI marked PAID normally
                - If amount > EMI due: EMI marked PAID, excess reduces next EMI
                - Late penalty applied automatically if paying after grace period
                - Debits borrower savings, credits lender savings
                - Loan marked COMPLETED when all EMIs fully paid
            """
    )
    @PostMapping("/{loanSummaryId}/pay-emi")
    public ResponseEntity<EmiScheduleResponse> payEmi(
            @PathVariable Long loanSummaryId,
            @RequestParam Long borrowerId,
            @RequestParam BigDecimal amount,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey){
        return ResponseEntity.ok(emiPaymentService.payEmi(loanSummaryId, borrowerId, amount));
    }

    @Operation(
            summary = "BORROWER — Foreclose loan (pay off early)",
            description = """
                Pay off the entire outstanding loan balance before tenure ends.

                Total payable =
                  Outstanding principal
                  + Accrued interest (since last EMI paid / disbursement date)
                  + 2% foreclosure charge on outstanding principal

                All remaining EMIs are marked PAID. Loan status → COMPLETED.
            """
    )
    @PostMapping("/{loanSummaryId}/foreclose")
    public ResponseEntity<Foreclosureresponse> foreclose(
            @PathVariable Long loanSummaryId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(emiPaymentService.foreclose(loanSummaryId, borrowerId));
    }

    // ── LENDER ────────────────────────────────────────────────────────────────

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
}