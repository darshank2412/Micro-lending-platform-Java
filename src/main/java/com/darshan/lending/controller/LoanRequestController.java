package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.LoanRequestDto;
import com.darshan.lending.dto.LoanRequestResponse;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.service.LoanRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REMOVAL — Two endpoints have been removed from this controller:
 *
 *   ✗  PATCH /{requestId}/accept   (LENDER)
 *      Removed because the system uses auto-matchmaking via LenderPreferences.
 *      The lender's acceptance is implicit in their saved preferences.
 *      Flow: Admin triggers matchmaking → LoanOffers created → Borrower accepts → Admin disburses.
 *
 *   ✗  PATCH /{requestId}/match    (ADMIN)
 *      Removed because it only flipped the status to MATCHED without running
 *      any matching logic — a meaningless manual override.
 *      Use POST /{requestId}/trigger-match instead, which both runs the engine
 *      AND transitions the status correctly.
 *
 * Everything else below is unchanged from your original controller.
 */
@RestController
@RequestMapping("/loan-requests")
@RequiredArgsConstructor
@Tag(name = "09.Loan Request APIs", description = "Borrower submits loan requests; Admin manages matchmaking")
public class LoanRequestController {

    private final LoanRequestService loanRequestService;

    // ── BORROWER ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "BORROWER — Submit a loan request")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> create(
            @RequestParam Long borrowerId,
            @Valid @RequestBody LoanRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Loan request submitted",
                loanRequestService.createRequest(borrowerId, dto)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "BORROWER — View my loan requests")
    public ResponseEntity<ApiResponse<List<LoanRequestResponse>>> getMyRequests(
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(ApiResponse.success("Loan requests retrieved",
                loanRequestService.getMyRequests(borrowerId)));
    }

    @PatchMapping("/{requestId}/cancel")
    @PreAuthorize("hasRole('BORROWER')")
    @Operation(summary = "BORROWER — Cancel a pending loan request")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> cancel(
            @PathVariable Long requestId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(ApiResponse.success("Loan request cancelled",
                loanRequestService.cancelRequest(borrowerId, requestId)));
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN — Get all loan requests (filter by status)")
    public ResponseEntity<ApiResponse<List<LoanRequestResponse>>> getAll(
            @RequestParam(required = false) LoanRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Loan requests retrieved",
                loanRequestService.getAllRequests(status)));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "ADMIN — Get loan request by ID")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> getById(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Loan request found",
                loanRequestService.getById(requestId)));
    }

    // ✗ REMOVED: PATCH /{requestId}/match
    //   This was manually flipping status to MATCHED with no engine logic.
    //   Use POST /{requestId}/trigger-match (in your matchmaking controller) instead.

    @PatchMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN — Reject a loan request")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> reject(
            @PathVariable Long requestId,
            @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("Loan request rejected",
                loanRequestService.rejectRequest(requestId, reason)));
    }

    // ── LENDER ────────────────────────────────────────────────────────────────

    @GetMapping("/open")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Browse all open (PENDING) loan requests")
    public ResponseEntity<ApiResponse<List<LoanRequestResponse>>> getOpenRequests(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(ApiResponse.success("Open loan requests retrieved",
                loanRequestService.getOpenRequests(lenderId)));
    }

    @GetMapping("/open/matching")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Browse requests matching my preferences")
    public ResponseEntity<ApiResponse<List<LoanRequestResponse>>> getMatchingRequests(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(ApiResponse.success("Matching loan requests retrieved",
                loanRequestService.getRequestsMatchingLenderPreference(lenderId)));
    }

    @GetMapping("/matched")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Get all matched loan requests")
    public ResponseEntity<ApiResponse<List<LoanRequestResponse>>> getMatchedRequests() {
        return ResponseEntity.ok(ApiResponse.success("Matched loan requests retrieved",
                loanRequestService.getAllRequests(LoanRequestStatus.MATCHED)));
    }

    // ✗ REMOVED: PATCH /{requestId}/accept (LENDER)
    //   Removed because lender acceptance is already implicit in LenderPreferences.
    //   The matchmaking engine selects lenders automatically. A separate accept
    //   endpoint created a broken hybrid model. Borrowers accept via LoanOfferController.
}