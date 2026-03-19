package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Partial profile update - only fullName, email, gender allowed")
public class UserProfileUpdateRequest {

    @Pattern(
            regexp = "^[A-Z][a-zA-Z ]*$",
            message = "Full name must start with a capital letter and contain only alphabets"
    )
    @Size(max = 200, message = "Full name must not exceed 200 characters")
    @Schema(example = "Darshan Kumar")
    private String fullName;

    @Email(message = "Please provide a valid email address")
    @Schema(example = "darshan@example.com")
    private String email;

    @Schema(example = "MALE")
    private Gender gender;
}