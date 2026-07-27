package com.hsbc.payment.service;

import com.hsbc.payment.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineServiceTest {

    private StateMachineService stateMachineService;

    @BeforeEach
    void setUp() {
        stateMachineService = new StateMachineService();
    }

    // ===== Valid transitions =====

    @Test @DisplayName("CREATED → VALIDATED: valid")
    void createdToValidated() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.VALIDATED));
    }

    @Test @DisplayName("CREATED → FAILED: valid")
    void createdToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.FAILED));
    }

    @Test @DisplayName("VALIDATED → SENT: valid")
    void validatedToSent() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.VALIDATED, PaymentStatus.SENT));
    }

    @Test @DisplayName("VALIDATED → FAILED: valid")
    void validatedToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.VALIDATED, PaymentStatus.FAILED));
    }

    @Test @DisplayName("SENT → COMPLETED: valid")
    void sentToCompleted() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.SENT, PaymentStatus.COMPLETED));
    }

    @Test @DisplayName("SENT → FAILED: valid")
    void sentToFailed() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.SENT, PaymentStatus.FAILED));
    }

    @Test @DisplayName("FAILED → VALIDATED (retry): valid")
    void failedToValidated() {
        assertTrue(stateMachineService.canTransition(PaymentStatus.FAILED, PaymentStatus.VALIDATED));
    }

    // ===== Invalid transitions =====

    @Test @DisplayName("COMPLETED is terminal — no transition out")
    void completedIsTerminal() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertFalse(stateMachineService.canTransition(PaymentStatus.COMPLETED, target),
                    "COMPLETED → " + target + " should be invalid");
        }
    }

    @Test @DisplayName("CREATED → SENT: skip VALIDATED — invalid")
    void createdToSentInvalid() {
        assertFalse(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.SENT));
    }

    @Test @DisplayName("CREATED → COMPLETED: skip steps — invalid")
    void createdToCompletedInvalid() {
        assertFalse(stateMachineService.canTransition(PaymentStatus.CREATED, PaymentStatus.COMPLETED));
    }

    @Test @DisplayName("VALIDATED → COMPLETED: skip SENT — invalid")
    void validatedToCompletedInvalid() {
        assertFalse(stateMachineService.canTransition(PaymentStatus.VALIDATED, PaymentStatus.COMPLETED));
    }

    // ===== Edge cases =====

    @Test @DisplayName("null fromStatus returns false")
    void nullFromStatus() {
        assertFalse(stateMachineService.canTransition((PaymentStatus) null, PaymentStatus.VALIDATED));
    }

    @Test @DisplayName("null toStatus returns false")
    void nullToStatus() {
        assertFalse(stateMachineService.canTransition(PaymentStatus.CREATED, (PaymentStatus) null));
    }

    @Test @DisplayName("null both returns false")
    void nullBoth() {
        assertFalse(stateMachineService.canTransition((PaymentStatus) null, (PaymentStatus) null));
    }

    @Test @DisplayName("String-based canTransition matches enum-based")
    void stringBasedTransition() {
        assertTrue(stateMachineService.canTransition("CREATED", "VALIDATED"));
        assertFalse(stateMachineService.canTransition("COMPLETED", "CREATED"));
        assertFalse(stateMachineService.canTransition("INVALID", "CREATED"));
    }
}
