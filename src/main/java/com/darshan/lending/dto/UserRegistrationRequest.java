package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.Gender;
import com.darshan.lending.entity.enums.P2pExperience;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.validation.MinAge;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "User registration request - Step 2 after OTP verification")
public class UserRegistrationRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "First name can only contain letters, spaces, hyphens, and apostrophes")
    @Schema(example = "Darshan")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Last name can only contain letters, spaces, hyphens, and apostrophes")
    @Schema(example = "Kumar")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(example = "darshan@example.com")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    @Schema(example = "9876543210")
    private String phoneNumber;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @MinAge(value = 18)
    @Schema(example = "1990-05-15")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    @Schema(example = "MALE")
    private Gender gender;

    @NotNull(message = "Role is required")
    @Schema(example = "BORROWER", description = "BORROWER or LENDER")
    private Role role;

    @NotBlank(message = "PAN is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format. Expected: ABCDE1234F")
    @Schema(example = "ABCDE1234F")
    private String pan;

    @NotBlank(message = "Income bracket is required")
    @Schema(example = "5-10 LPA",
            description = "One of: BELOW_2_LPA, 2_5_LPA, 5_10_LPA, 10_20_LPA, 20_50_LPA, ABOVE_50_LPA")
    private String incomeBracket;

    @Schema(example = "BEGINNER")
    private P2pExperience p2pExperience;

    @Valid
    @NotNull(message = "Address is required")
    private AddressDto address;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?`~])[^\\s]{8,18}$",
            message = "Password must be at least 8 characters and upto 18 characters, contain at least one uppercase letter, one lowercase letter, one digit, one special character, and no spaces"
    )
    @Schema(
            example = "Secure@123",
            description = "Min 8 chars, must contain uppercase, lowercase, digit, special character, no spaces"
    )
    private String password;
}