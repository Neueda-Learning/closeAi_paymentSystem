package com.hsbc.payment.service.risk;

import com.hsbc.payment.entity.Payment;

public interface RiskRule {
    String ruleName();
    int evaluate(Payment payment, RiskContext context);
    String reason(Payment payment, RiskContext context);
}
