package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewPayeeRule implements RiskRule {
    private final RiskConfig riskConfig;

    @Override public String ruleName() { return "NewPayeeRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        Set<String> known = context.getKnownPayees();
        if (known == null || known.isEmpty()) return 0;
        if (!known.contains(payment.getDestinationAccount())) return riskConfig.getLayer1().getNewPayeeScore();
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        Set<String> known = context.getKnownPayees();
        if (known != null && !known.isEmpty() && !known.contains(payment.getDestinationAccount()))
            return "Destination " + payment.getDestinationAccount() + " not in known payees";
        return null;
    }
}
