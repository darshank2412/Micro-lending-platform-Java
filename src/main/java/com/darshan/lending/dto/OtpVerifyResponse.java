package com.darshan.lending.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpVerifyResponse {
    private Long   userId;
    private String token;    // only for LOGIN
    private String role;     // only for LOGIN
    private String fullName; // only for LOGIN
    private String message;
}