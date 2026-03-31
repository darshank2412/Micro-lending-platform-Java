package com.darshan.lending.util;

import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * Valid loan request state transitions:
 *
 * PENDING → MATCHED, CANCELLED
 * MATCHED → ACCEPTED, CANCELLED
 * ACCEPTED → DISBURSED, CANCELLED
 * DISBURSED → (terminal)
 * REJECTED → (terminal)
 * CANCELLED → (terminal)
 */
public class LoanStateMachine {

    private static final Map<LoanRequestStatus, Set<LoanRequestStatus>> TRANSITIONS = Map.of(
            LoanRequestStatus.PENDING,   Set.of(LoanRequestStatus.MATCHED,   LoanRequestStatus.CANCELLED, LoanRequestStatus.REJECTED),
            LoanRequestStatus.MATCHED,   Set.of(LoanRequestStatus.ACCEPTED,  LoanRequestStatus.CANCELLED),
            LoanRequestStatus.ACCEPTED,  Set.of(LoanRequestStatus.DISBURSED, LoanRequestStatus.CANCELLED),
            LoanRequestStatus.DISBURSED, Set.of(),
            LoanRequestStatus.REJECTED,  Set.of(),
            LoanRequestStatus.CANCELLED, Set.of()
    );

    public static void validateTransition(LoanRequestStatus from, LoanRequestStatus to) {
        Set<LoanRequestStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    "Invalid state transition: " + from + " → " + to +
                            ". Allowed: " + allowed);
        }
    }

    public static boolean canTransition(LoanRequestStatus from, LoanRequestStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}