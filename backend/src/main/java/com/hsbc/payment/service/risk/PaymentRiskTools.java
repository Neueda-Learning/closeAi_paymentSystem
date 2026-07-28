package com.hsbc.payment.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hsbc.payment.entity.Account;
import com.hsbc.payment.entity.AccountStats;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.StatusHistory;
import com.hsbc.payment.mapper.AccountMapper;
import com.hsbc.payment.mapper.AccountStatsMapper;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.StatusHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data query methods for AI Agent context.
 * Pre-fetches real data from the backend, providing the LLM with
 * factual information to base its risk assessment on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRiskTools {

    private final PaymentMapper paymentMapper;
    private final AccountMapper accountMapper;
    private final AccountStatsMapper accountStatsMapper;
    private final StatusHistoryMapper statusHistoryMapper;

    public String getPaymentDetails(String paymentId) {
        Payment p = paymentMapper.selectById(paymentId);
        if (p == null) return "Payment not found: " + paymentId;
        return String.format("Payment[id=%s, amount=%s %s, from=%s, to=%s, status=%s, description=%s, createdAt=%s]",
                p.getId(), p.getAmount(), p.getCurrency(), p.getSourceAccount(),
                p.getDestinationAccount(), p.getStatus(),
                p.getDescription() != null ? p.getDescription() : "N/A", p.getCreatedAt());
    }

    public String getAccountProfile(String accountNumber) {
        Account a = accountMapper.selectById(accountNumber);
        if (a == null) return "Account not found: " + accountNumber;
        return String.format("Account[number=%s, name=%s, balance=%s %s]",
                a.getAccountNumber(), a.getAccountName(), a.getBalance(), a.getCurrency());
    }

    public String getAccountStatistics(String accountNumber) {
        AccountStats s = accountStatsMapper.selectById(accountNumber);
        if (s == null) return "No statistics for account: " + accountNumber;
        return String.format("AccountStats[account=%s, avgAmount=%s, stdAmount=%s, median=%s, Q3=%s, totalCount=%d, knownPayees=%s]",
                s.getAccountNumber(), s.getAvgAmount(), s.getStdAmount(),
                s.getMedianAmount(), s.getQ3Amount(), s.getTotalCount(),
                s.getKnownPayees() != null ? s.getKnownPayees() : "[]");
    }

    public String getRecentPayments(String sourceAccount, int limit) {
        LambdaQueryWrapper<Payment> w = new LambdaQueryWrapper<>();
        w.eq(Payment::getSourceAccount, sourceAccount).orderByDesc(Payment::getCreatedAt).last("LIMIT " + Math.min(limit, 20));
        List<Payment> list = paymentMapper.selectList(w);
        if (list.isEmpty()) return "No recent payments for: " + sourceAccount;
        return list.stream().map(p -> String.format("[%s] %s %s -> %s (%s)",
                p.getCreatedAt(), p.getAmount(), p.getCurrency(), p.getDestinationAccount(), p.getStatus()))
                .collect(Collectors.joining("\n"));
    }

    public String getPaymentStatusHistory(String paymentId) {
        List<StatusHistory> h = statusHistoryMapper.findByPaymentId(paymentId);
        if (h.isEmpty()) return "No history for: " + paymentId;
        return h.stream().map(e -> String.format("[%s] %s -> %s (reason: %s)",
                e.getChangedAt(), e.getFromStatus(), e.getToStatus(),
                e.getReason() != null ? e.getReason() : "N/A"))
                .collect(Collectors.joining("\n"));
    }

    public int countRecentTransactions(String sourceAccount, int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        LambdaQueryWrapper<Payment> w = new LambdaQueryWrapper<>();
        w.eq(Payment::getSourceAccount, sourceAccount).ge(Payment::getCreatedAt, cutoff);
        return paymentMapper.selectCount(w).intValue();
    }
}
