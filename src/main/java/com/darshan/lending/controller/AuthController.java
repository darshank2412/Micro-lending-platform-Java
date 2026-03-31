package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.LoginRequest;
import com.darshan.lending.dto.LoginResponse;
import com.darshan.lending.dto.ResetPasswordRequest;
import com.darshan.lending.entity.User;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.security.JwtUtil;
import com.darshan.lending.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "0. Authentication", description = "Login and get JWT token")
public class AuthController {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil         jwtUtil;
    // BUG FIX 1: inject OtpService as a Spring bean instead of calling it statically
    private final OtpService      otpService;

    @Operation(summary = "Login — get JWT token",
            description = "Send phone number + password, get back a Bearer token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request) {

        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid credentials"));
        }

        String token = jwtUtil.generateToken(
                user.getPhoneNumber(),
                user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .success(true)
                .message("Login successful")
                .data(LoginResponse.builder()
                        .token(token)
                        .userId(user.getId())
                        .role(user.getRole().name())
                        .fullName(user.getFullName())
                        .build())
                .build());
    }

    @Operation(summary = "Reset password after OTP verified")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        // BUG FIX 1: call on injected instance, not as static method
        otpService.resetPassword(request.getPhoneNumber(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Password reset successful. Please login.")
                .data(null)
                .build());
    }
}