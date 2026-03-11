package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Partial profile update - only fullName, email, gender allowed")
public class UserProfileUpdateRequest {

    @Size(max = 200, message = "Full name must not exceed 200 characters")
    @Schema(example = "Darshan Kumar Updated")
    private String fullName;

    @Email(message = "Invalid email format")
    @Schema(example = "updated@example.com")
    private String email;

    @Schema(example = "FEMALE")
    private Gender gender;
}
