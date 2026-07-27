package com.hsbc.payment.enums;

public enum RiskDecision {
    APPROVE,
    REVIEW,
    BLOCK;

    public static RiskDecision fromString(String value) {
        if (value == null) return null;
        try { return RiskDecision.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return REVIEW; }
    }
}
