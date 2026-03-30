package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.LoanOfferStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanOfferResponse {
    private Long id;
    private Long loanRequestId;
    private BigDecimal loanAmount;
    private Long lenderId;
    private String lenderName;
    private BigDecimal offeredInterestRate;
    private LoanOfferStatus status;
    private Integer matchRank;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}