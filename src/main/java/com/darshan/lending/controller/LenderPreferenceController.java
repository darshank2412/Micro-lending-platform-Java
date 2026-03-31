package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.LenderPreferenceDto;
import com.darshan.lending.dto.LenderPreferenceResponse;
import com.darshan.lending.service.LenderPreferenceService;
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
@RequestMapping("/lender-preferences")
@RequiredArgsConstructor
@Tag(name = "08.Lender Preference APIs", description = "Lender sets preferences per loan product for matchmaking")
//@SecurityRequirement(name = "basicAuth")
public class LenderPreferenceController {

    private final LenderPreferenceService lenderPreferenceService;

    // ── LENDER: Save or update preference for a specific loan product ─────────
    @PostMapping
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Save or update preference for a loan product")
    public ResponseEntity<ApiResponse<LenderPreferenceResponse>> savePreference(
            @RequestParam Long lenderId,
            @Valid @RequestBody LenderPreferenceDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Preference saved",
                lenderPreferenceService.savePreference(lenderId, dto)));
    }

    // ── LENDER: Get all my preferences ────────────────────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Get all my preferences (one per loan product)")
    public ResponseEntity<ApiResponse<List<LenderPreferenceResponse>>> getMyPreferences(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(ApiResponse.success("Preferences retrieved",
                lenderPreferenceService.getMyPreferences(lenderId)));
    }

    // ── LENDER: Deactivate preference for a specific loan product ─────────────
    @PatchMapping("/deactivate")
    @PreAuthorize("hasRole('LENDER')")
    @Operation(summary = "LENDER — Deactivate preference for a specific loan product")
    public ResponseEntity<ApiResponse<LenderPreferenceResponse>> deactivate(
            @RequestParam Long lenderId,
            @RequestParam Long loanProductId) {
        return ResponseEntity.ok(ApiResponse.success("Preference deactivated",
                lenderPreferenceService.deactivate(lenderId, loanProductId)));
    }

    // ── ADMIN: Get all active preferences ─────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN — Get all active lender preferences")
    public ResponseEntity<ApiResponse<List<LenderPreferenceResponse>>> getAllActive() {
        return ResponseEntity.ok(ApiResponse.success("Active preferences retrieved",
                lenderPreferenceService.getAllActive()));
    }
}