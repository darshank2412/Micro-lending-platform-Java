package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Request to verify OTP")
public class OtpVerifyRequest {

    @NotBlank(message = "Identifier is required")
    @Size(max = 100, message = "Identifier must not exceed 100 characters")
    @Pattern(
            regexp = "^([6-9][0-9]{9}|[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})$",
            message = "Identifier must be a valid 10-digit Indian mobile number or email"
    )
    @Schema(example = "9876543210")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
    @Schema(example = "123456")
    private String otpCode;
}