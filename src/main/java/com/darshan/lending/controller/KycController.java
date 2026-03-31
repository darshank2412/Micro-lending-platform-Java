package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.darshan.lending.dto.KycSubmitRequest;
import java.util.List;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Tag(name = "04.KYC APIs", description = "KYC document submission and verification")
//@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;

    @PostMapping("/submit")
    @Operation(summary = "Submit a KYC document",
            description = "Submit AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, or VOTER_ID for verification. " +
                    "Both AADHAAR and PAN are required for full KYC approval.")
    public ResponseEntity<ApiResponse<KycDocument>> submitDocument(
            @RequestParam Long userId,
            @Valid @RequestBody KycSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("KYC document submitted successfully",
                kycService.submitDocument(userId, request)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get KYC documents for a user")
    public ResponseEntity<ApiResponse<List<KycDocument>>> getDocuments(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("KYC documents retrieved",
                kycService.getDocuments(userId)));
    }

    @PatchMapping("/approve/{docId}")
    @Operation(summary = "Admin: Approve a KYC document")
    public ResponseEntity<ApiResponse<KycDocument>> approveDocument(
            @PathVariable Long docId) {
        return ResponseEntity.ok(ApiResponse.success("KYC document approved",
                kycService.approveDocument(docId)));
    }

    @PatchMapping("/reject/{docId}")
    @Operation(summary = "Admin: Reject a KYC document")
    public ResponseEntity<ApiResponse<KycDocument>> rejectDocument(
            @PathVariable Long docId,
            @Parameter(description = "Reason for rejection") @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("KYC document rejected",
                kycService.rejectDocument(docId, reason)));
    }
}
