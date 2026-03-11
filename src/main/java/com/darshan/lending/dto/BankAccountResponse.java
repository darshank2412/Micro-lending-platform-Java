package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.AccountStatus;
import com.darshan.lending.entity.enums.AccountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccountResponse {
    private Long id;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private AccountStatus status;
    private Long userId;
    private Long savingsProductId;
    private String savingsProductName;
    private Long loanProductId;
    private String loanProductName;
    private LocalDateTime createdAt;
}