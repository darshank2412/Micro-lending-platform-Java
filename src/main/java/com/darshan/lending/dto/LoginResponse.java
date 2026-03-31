package com.darshan.lending.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long   userId;
    private String role;
    private String fullName;
}