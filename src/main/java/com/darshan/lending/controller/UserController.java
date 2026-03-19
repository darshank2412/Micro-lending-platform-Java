package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "User APIs", description = "User onboarding and profile management")
@SecurityRequirement(name = "basicAuth")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Complete registration")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @RequestParam Long userId,
            @Valid @RequestBody UserRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration successful",
                userService.register(userId, request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved",
                userService.getByPhone(userDetails.getUsername())));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                userService.updateProfileByPhone(userDetails.getUsername(), request)));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new admin")
    public ResponseEntity<ApiResponse<UserResponse>> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Admin created",
                userService.createAdmin(request)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all admins")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllAdmins() {
        return ResponseEntity.ok(ApiResponse.success("Admins retrieved",
                userService.getAllAdmins()));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an admin")
    public ResponseEntity<ApiResponse<Void>> deleteAdmin(@PathVariable Long id) {
        userService.deleteAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Admin deleted", null));
    }
}