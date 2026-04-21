package com.darshan.lending.controller;

import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.dto.MatchRequest;
import com.darshan.lending.service.MatchmakingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/loan-requests")
@RequiredArgsConstructor
@Tag(name = "10.Matchmaking",
        description = "Admin triggers matchmaking engine to match loan requests with lenders")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "ADMIN — Trigger matchmaking for a loan request",
            description = "Finds all lenders whose preferences match the loan amount and tenure, " +
                    "ranks them by lowest interest rate, creates ranked loan offers and " +
                    "transitions the loan request to MATCHED"
    )
    @PostMapping("/{requestId}/trigger-match")   // ← was /match, conflicted with PATCH /match
    public ResponseEntity<List<LoanOfferResponse>> matchRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) MatchRequest matchRequest) {

        int max = (matchRequest != null && matchRequest.getMaxOffers() != null)
                ? matchRequest.getMaxOffers()
                : 5;

        return ResponseEntity.ok(matchmakingService.matchLoanRequest(requestId, max));
    }
}