package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "KYC document submission request")
public class KycSubmitRequest {

    @NotNull(message = "Document type is required")
    @Schema(example = "AADHAAR", description = "AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, VOTER_ID")
    private DocumentType documentType;

    @NotBlank(message = "Document number is required")
    @Schema(example = "1234 5678 9012")
    private String documentNumber;

    @Schema(example = "https://s3.example.com/kyc/doc.pdf", description = "URL of the uploaded document image/PDF")
    private String documentUrl;
}
