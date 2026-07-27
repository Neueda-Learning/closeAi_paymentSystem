package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LargeAmountRule implements RiskRule {
    private final RiskConfig riskConfig;

    @Override public String ruleName() { return "LargeAmountRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountBlock()) >= 0) return 100;
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountWarning()) >= 0) return 30;
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountBlock()) >= 0)
            return "Amount " + payment.getAmount() + " exceeds block threshold " + riskConfig.getLayer1().getLargeAmountBlock();
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountWarning()) >= 0)
            return "Amount " + payment.getAmount() + " exceeds warning threshold " + riskConfig.getLayer1().getLargeAmountWarning();
        return null;
    }
}
