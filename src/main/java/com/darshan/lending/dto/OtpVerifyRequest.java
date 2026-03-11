package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.OtpPurpose;
import com.darshan.lending.entity.enums.OtpType;
import com.darshan.lending.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            message = "Identifier must be a valid 10-digit Indian mobile number (starting with 6-9) or a valid email address"
    )
    @Schema(example = "9876543210")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Pattern(
            regexp = "^[0-9]{6}$",
            message = "OTP must be exactly 6 digits"
    )
    @Schema(example = "123456")
    private String otpCode;

    @NotNull(message = "OTP type is required")
    @Schema(example = "PHONE", description = "PHONE or EMAIL")
    private OtpType otpType;

    @NotNull(message = "Purpose is required")
    @Schema(example = "REGISTRATION", description = "REGISTRATION, LOGIN, or RESET")
    private OtpPurpose purpose;

    @NotBlank(message = "Country code is required")
    @Pattern(
            regexp = "^\\+[1-9][0-9]{0,3}$",
            message = "Country code must be in format +91, +1, +44 etc."
    )
    @Schema(example = "+91")
    private String countryCode;

    @NotNull(message = "Role is required")
    @Schema(example = "BORROWER", description = "BORROWER or LENDER")
    private Role role;
}