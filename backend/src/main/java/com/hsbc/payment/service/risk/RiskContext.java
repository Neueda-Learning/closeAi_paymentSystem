package com.hsbc.payment.service.risk;

import com.hsbc.payment.entity.AccountStats;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Builder
public class RiskContext {
    private int transactionHour;
    private BigDecimal accountAvgAmount;
    private BigDecimal accountStdAmount;
    private BigDecimal accountMedian;
    private BigDecimal accountQ3;
    private Set<String> knownPayees;
    private int recentTransactionCount;
    private AccountStats accountStats;
}
