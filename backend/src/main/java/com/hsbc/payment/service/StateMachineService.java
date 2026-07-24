package com.hsbc.payment.service;

import com.hsbc.payment.enums.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class StateMachineService {

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
        PaymentStatus.CREATED,   Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
        PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
        PaymentStatus.SENT,      Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
        PaymentStatus.COMPLETED, Set.of(),
        PaymentStatus.FAILED,    Set.of(PaymentStatus.VALIDATED)
    );

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) return false;
        Set<PaymentStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }

    public boolean canTransition(String fromStr, String toStr) {
        PaymentStatus from = PaymentStatus.fromString(fromStr);
        PaymentStatus to = PaymentStatus.fromString(toStr);
        return canTransition(from, to);
    }
}
