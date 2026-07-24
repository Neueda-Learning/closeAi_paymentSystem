package com.hsbc.payment.enums;

public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED;

    public static PaymentStatus fromString(String value) {
        if (value == null) return null;
        try {
            return PaymentStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
