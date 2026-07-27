package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfTransferRule implements RiskRule {
    private final RiskConfig riskConfig;

    @Override public String ruleName() { return "SelfTransferRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (payment.getSourceAccount().equalsIgnoreCase(payment.getDestinationAccount())) return riskConfig.getLayer1().getSelfTransferScore();
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (payment.getSourceAccount().equalsIgnoreCase(payment.getDestinationAccount()))
            return "Source and destination are the same: " + payment.getSourceAccount();
        return null;
    }
}
