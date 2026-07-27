package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NightTimeRule implements RiskRule {
    private final RiskConfig riskConfig;

    @Override public String ruleName() { return "NightTimeRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        int start = riskConfig.getLayer1().getNightTimeStart(), end = riskConfig.getLayer1().getNightTimeEnd();
        if (context.getTransactionHour() >= start && context.getTransactionHour() < end) return 25;
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        int start = riskConfig.getLayer1().getNightTimeStart(), end = riskConfig.getLayer1().getNightTimeEnd();
        if (context.getTransactionHour() >= start && context.getTransactionHour() < end)
            return "Transaction at " + context.getTransactionHour() + ":00 (night time " + start + "-" + end + ")";
        return null;
    }
}
