package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.LoanRequestDto;
import com.darshan.lending.dto.LoanRequestResponse;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.service.LoanRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loan-requests")
@RequiredArgsConstructor
@Tag(name = "08.Loan Request APIs", description = "Borrower submits loan requests; Lender discovers and accepts")
@SecurityRequirement(name = "basicAuth")
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

    @PatchMapping("/{requestId}/match")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN — Mark loan request as matched")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> match(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Loan request matched",
                loanRequestService.markAsMatched(requestId)));
    }

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

    @PatchMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Accept a matched loan request")
    public ResponseEntity<ApiResponse<LoanRequestResponse>> accept(
            @PathVariable Long requestId,
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(ApiResponse.success("Loan request accepted",
                loanRequestService.acceptRequest(lenderId, requestId)));
    }
}