package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.CreditScoreResponse;
import com.darshan.lending.service.CreditScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/credit-score")
@RequiredArgsConstructor
@Tag(name = "12. Credit Scoring", description = "Credit risk scoring model")
public class CreditScoreController {

    private final CreditScoreService creditScoreService;

    @Operation(summary = "ADMIN/LENDER — Calculate credit score for a borrower")
    @GetMapping("/{borrowerId}")
    public ResponseEntity<ApiResponse<CreditScoreResponse>> getCreditScore(
            @PathVariable Long borrowerId) {
        return ResponseEntity.ok(ApiResponse.<CreditScoreResponse>builder()
                .success(true)
                .message("Credit score calculated")
                .data(creditScoreService.calculateScore(borrowerId))
                .build());
    }
}