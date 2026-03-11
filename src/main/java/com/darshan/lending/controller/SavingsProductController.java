package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.SavingsProductRequest;
import com.darshan.lending.dto.SavingsProductResponse;
import com.darshan.lending.service.SavingsProductService;
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
@RequestMapping("/savings-products")
@RequiredArgsConstructor
@Tag(name = "Savings Product APIs", description = "Savings product CRUD — minBalance must be less than maxBalance")
public class SavingsProductController {

    private final SavingsProductService savingsProductService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Create savings product (ADMIN only)")
    public ResponseEntity<ApiResponse<SavingsProductResponse>> create(
            @Valid @RequestBody SavingsProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Savings product created",
                savingsProductService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all active savings products (any authenticated user)")
    public ResponseEntity<ApiResponse<List<SavingsProductResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Savings products retrieved",
                savingsProductService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get savings product by ID (any authenticated user)")
    public ResponseEntity<ApiResponse<SavingsProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Savings product found",
                savingsProductService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Update savings product (ADMIN only)")
    public ResponseEntity<ApiResponse<SavingsProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SavingsProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Savings product updated",
                savingsProductService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Deactivate savings product (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        savingsProductService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Savings product deactivated", null));
    }
}