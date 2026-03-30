package com.darshan.lending.entity.enums;

public enum EmiStatus {
    PENDING,    // not yet due or not paid
    PAID,       // paid on time
    OVERDUE     // due date passed, not paid
}