package com.darshan.lending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OpenAccountRequest {

    @NotNull(message = "User ID is required")
    @Schema(example = "1")
    private Long userId;

    @NotNull(message = "Product ID is required")
    @Schema(example = "1", description = "ID of SavingsProduct or LoanProduct")
    private Long productId;
}