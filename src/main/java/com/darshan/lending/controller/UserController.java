package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.UserProfileUpdateRequest;
import com.darshan.lending.dto.UserRegistrationRequest;
import com.darshan.lending.dto.UserResponse;
import com.darshan.lending.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User APIs", description = "User onboarding and profile management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Complete registration after OTP verification",
            description = "Saves profile (firstName, lastName, DOB, phone, gender, role, PAN, income), " +
                    "address, and auto-creates a platform wallet. Returns the account number.")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @RequestParam Long userId,
            @Valid @RequestBody UserRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration successful", userService.register(userId, request)));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile (partial)", description = "Only fullName, email, gender are updatable")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @RequestParam Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", userService.updateProfile(userId, request)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User found", userService.getById(userId)));
    }
}
