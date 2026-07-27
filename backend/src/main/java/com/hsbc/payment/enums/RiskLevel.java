package com.hsbc.payment.enums;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static RiskLevel fromString(String value) {
        if (value == null) return null;
        try { return RiskLevel.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return LOW; }
    }
}
