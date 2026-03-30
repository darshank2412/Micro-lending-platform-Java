package com.darshan.lending.entity.enums;

public enum LoanStatus {
    ACTIVE,       // disbursed, EMIs running
    COMPLETED,    // all EMIs paid
    DEFAULTED     // overdue beyond threshold
}