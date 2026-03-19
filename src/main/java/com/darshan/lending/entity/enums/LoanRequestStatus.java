package com.darshan.lending.entity.enums;

public enum LoanRequestStatus {
    PENDING,       // just submitted by borrower
    MATCHED,       // lender matched
    ACCEPTED,      // lender accepted
    REJECTED,      // lender rejected
    CANCELLED,     // borrower cancelled
    DISBURSED      // loan disbursed (Week 4)
}