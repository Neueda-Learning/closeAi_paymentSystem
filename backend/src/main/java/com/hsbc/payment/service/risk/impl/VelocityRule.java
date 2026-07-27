package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VelocityRule implements RiskRule {
    private final RiskConfig riskConfig;

    @Override public String ruleName() { return "VelocityRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation()) return riskConfig.getLayer1().getVelocityScore();
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation())
            return "High velocity: " + context.getRecentTransactionCount() + " txns in 10min";
        return null;
    }
}
