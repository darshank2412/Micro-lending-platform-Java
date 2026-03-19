package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new admin user")
public class CreateAdminRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[6-9][0-9]{9}$",
            message = "Phone number must be 10 digits and start with 6, 7, 8, or 9"
    )
    @Schema(example = "9999999999")
    private String phoneNumber;

    @NotBlank(message = "Country code is required")
    @Pattern(
            regexp = "^\\+\\d{1,3}$",
            message = "Country code must be in format like +91"
    )
    @Schema(example = "+91")
    private String countryCode;

    @NotBlank(message = "Full name is required")
    @Pattern(
            regexp = "^[A-Z][a-zA-Z ]*$",
            message = "Full name must start with a capital letter and contain only alphabets"
    )
    @Size(max = 200, message = "Full name cannot exceed 200 characters")
    @Schema(example = "Admin User")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(example = "admin@lending.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[@$!%*?&]).{8,}$",
            message = "Password must contain at least 8 characters, one uppercase, one lowercase and one special character"
    )
    @Schema(example = "Admin@123", description = "Min 8 chars, must contain uppercase, lowercase, special character")
    private String password;
}