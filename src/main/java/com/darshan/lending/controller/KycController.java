package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.KycDocumentResponse;
import com.darshan.lending.dto.KycSubmitRequest;
import com.darshan.lending.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FIX 5 — All endpoints now return KycDocumentResponse (safe DTO) instead of
 * the raw KycDocument entity, which was exposing the full User object including
 * password hash, resetToken, bank accounts, etc.
 *
 * Note: KycService methods must also be updated to return KycDocumentResponse.
 * See the mapping helper below for the conversion pattern.
 */
@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Tag(name = "03.KYC APIs", description = "KYC document submission and verification")
public class KycController {

    private final KycService kycService;

    @PostMapping("/submit")
    @Operation(
            summary = "Submit a KYC document",
            description = "Submit AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, or VOTER_ID for verification. "
                    + "Both AADHAAR and PAN are required for full KYC approval."
    )
    public ResponseEntity<ApiResponse<KycDocumentResponse>> submitDocument(
            @RequestParam Long userId,
            @Valid @RequestBody KycSubmitRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC document submitted successfully",
                kycService.submitDocument(userId, request)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get KYC documents for a user")
    public ResponseEntity<ApiResponse<List<KycDocumentResponse>>> getDocuments(
            @PathVariable Long userId) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC documents retrieved",
                kycService.getDocuments(userId)));
    }

    @PatchMapping("/approve/{docId}")
    @Operation(summary = "Admin: Approve a KYC document")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> approveDocument(
            @PathVariable Long docId) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC document approved",
                kycService.approveDocument(docId)));
    }

    @PatchMapping("/reject/{docId}")
    @Operation(summary = "Admin: Reject a KYC document")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> rejectDocument(
            @PathVariable Long docId,
            @Parameter(description = "Reason for rejection")
            @RequestParam String reason) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC document rejected",
                kycService.rejectDocument(docId, reason)));
    }
}