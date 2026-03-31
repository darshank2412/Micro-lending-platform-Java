package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.LoanProductRequest;
import com.darshan.lending.dto.LoanProductResponse;
import com.darshan.lending.service.LoanProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/loan-products")
@RequiredArgsConstructor
@Tag(name = "06.Loan Product APIs", description = "Loan product CRUD — minAmount < maxAmount, minInterest < maxInterest, minTenure < maxTenure")
public class LoanProductController {

    private final LoanProductService loanProductService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
//    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Create loan product (ADMIN only)")
    public ResponseEntity<ApiResponse<LoanProductResponse>> create(
            @Valid @RequestBody LoanProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan product created",
                loanProductService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all active loan products (any authenticated user)")
    public ResponseEntity<ApiResponse<List<LoanProductResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Loan products retrieved",
                loanProductService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get loan product by ID (any authenticated user)")
    public ResponseEntity<ApiResponse<LoanProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Loan product found",
                loanProductService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Update loan product (ADMIN only)")
    public ResponseEntity<ApiResponse<LoanProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody LoanProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan product updated",
                loanProductService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Deactivate loan product (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        loanProductService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Loan product deactivated", null));
    }

    @Operation(summary = "Get all active loan products (paginated)")
    @GetMapping("/loan-products/paged")
    public ResponseEntity<Page<LoanProductResponse>> getLoanProductsPaged(Pageable pageable) {
        return ResponseEntity.ok(loanProductService.findAllPaged(pageable));
    }
}