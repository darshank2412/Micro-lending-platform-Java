package com.darshan.lending.controller;

import com.darshan.lending.dto.ApiResponse;
import com.darshan.lending.dto.OtpRequest;
import com.darshan.lending.dto.OtpVerifyRequest;
import com.darshan.lending.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Tag(name = "01.OTP APIs", description = "OTP generation and verification")
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    @Operation(summary = "Send OTP")
    public ResponseEntity<ApiResponse<String>> sendOtp(
            @Valid @RequestBody OtpRequest request) {

        String otp = otpService.sendOtp(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "OTP sent successfully to " + request.getIdentifier(),
                        "[DEV-ONLY] OTP: " + otp
                )
        );
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify OTP & Create User")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request) {

        Long userId = otpService.verifyOtpAndCreateUser(request);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);

        return ResponseEntity.ok(
                ApiResponse.success("OTP verified successfully", data)
        );
    }
}