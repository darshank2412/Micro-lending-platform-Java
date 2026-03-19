package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.LoanPurpose;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRequestResponse {

    private Long id;
    private Long borrowerId;
    private String borrowerName;
    private Long loanProductId;
    private String loanProductName;
    private BigDecimal amount;
    private Integer tenureMonths;
    private LoanPurpose purpose;
    private String purposeDescription;
    private LoanRequestStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}