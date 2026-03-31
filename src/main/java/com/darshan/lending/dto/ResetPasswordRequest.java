package com.darshan.lending.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String phoneNumber;
    private String newPassword;
}