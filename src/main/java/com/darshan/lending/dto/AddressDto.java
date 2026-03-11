package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Address details")
public class AddressDto {

    @NotBlank(message = "Address line1 is required")
    @Schema(example = "123 MG Road")
    private String line1;

    @NotBlank(message = "City is required")
    @Schema(example = "Mumbai")
    private String city;

    @NotBlank(message = "State is required")
    @Schema(example = "Maharashtra")
    private String state;

    @NotBlank(message = "Pincode is required")
    @Schema(example = "400001")
    private String pincode;
}
