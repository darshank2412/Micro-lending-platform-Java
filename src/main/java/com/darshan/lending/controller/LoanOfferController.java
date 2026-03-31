package com.darshan.lending.controller;

import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.service.LoanOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/loan-offers")
@RequiredArgsConstructor
@Tag(name = "11.Loan Offer", description = "Loan offer lifecycle — view, accept and reject matched loan offers")
public class LoanOfferController {

    private final LoanOfferService loanOfferService;

    @Operation(
            summary = "ADMIN — View all ranked offers for a loan request",
            description = "Get all loan offers generated for a specific loan request " +
                    "by the matchmaking engine — ordered by interest rate ascending (best offer first)"
    )
    @GetMapping("/request/{requestId}")
    public ResponseEntity<List<LoanOfferResponse>> adminGetOffersForRequest(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(
                loanOfferService.adminGetOffersForRequest(requestId));
    }

    @Operation(
            summary = "LENDER — View all offers matched to me",
            description = "Get all loan offers matched to this lender by the matchmaking engine — " +
                    "shows offer status, interest rate and rank for each matched loan request"
    )
    @GetMapping("/my")
    public ResponseEntity<List<LoanOfferResponse>> getMyOffers(
            @RequestParam Long lenderId) {
        return ResponseEntity.ok(
                loanOfferService.getMyOffers(lenderId));
    }

    @Operation(
            summary = "BORROWER — View all ranked offers on my loan request",
            description = "Get all loan offers received on a specific loan request — " +
                    "ordered by interest rate ascending so the best offer appears first"
    )
    @GetMapping("/borrower/{requestId}")
    public ResponseEntity<List<LoanOfferResponse>> getOffersForRequest(
            @RequestParam Long borrowerId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(
                loanOfferService.getOffersForRequest(borrowerId, requestId));
    }

    @Operation(
            summary = "BORROWER — Accept a loan offer",
            description = "Accept a specific loan offer — transitions the offer to ACCEPTED, " +
                    "loan request to FUNDED, and auto-rejects all other PENDING offers " +
                    "for the same loan request in a single transaction"
    )
    @PostMapping("/{offerId}/accept")
    public ResponseEntity<LoanOfferResponse> acceptOffer(
            @PathVariable Long offerId,
            @RequestParam Long borrowerId) {
        return ResponseEntity.ok(
                loanOfferService.acceptOffer(borrowerId, offerId));
    }

    @Operation(
            summary = "BORROWER — Reject a single loan offer",
            description = "Reject a specific loan offer — only this offer is rejected, " +
                    "all remaining PENDING offers for the same request stay active " +
                    "and the borrower can still accept another offer"
    )
    @PostMapping("/{offerId}/reject")
    public ResponseEntity<LoanOfferResponse> rejectOffer(
            @PathVariable Long offerId,
            @RequestParam Long borrowerId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.getOrDefault("reason", null) : null;
        return ResponseEntity.ok(
                loanOfferService.rejectOffer(borrowerId, offerId, reason));
    }
}