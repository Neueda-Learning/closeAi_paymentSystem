package com.hsbc.payment.service.risk;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticalDetector {
    private final RiskConfig riskConfig;

    public StatisticalResult detect(Payment payment, RiskContext context) {
        if (!riskConfig.getLayer2().isEnabled()) return StatisticalResult.noAnomaly();
        if (context.getAccountStats() == null || context.getAccountStats().getTotalCount() < 5) {
            log.info("Insufficient baseline data for account {}, skipping Layer 2", payment.getSourceAccount());
            return StatisticalResult.insufficientData();
        }

        List<StatisticalFlag> flags = new ArrayList<>();
        int totalScore = 0;

        // z-score
        if (context.getAccountAvgAmount() != null && context.getAccountStdAmount() != null
                && context.getAccountStdAmount().compareTo(BigDecimal.ZERO) > 0) {
            double zscore = Math.abs((payment.getAmount().doubleValue() - context.getAccountAvgAmount().doubleValue())
                    / context.getAccountStdAmount().doubleValue());
            if (zscore >= riskConfig.getLayer2().getZscoreThreshold()) {
                int score = (int) Math.min(zscore * 8, 25);
                totalScore += score;
                flags.add(new StatisticalFlag("ZSCORE_ANOMALY", zscore, score,
                        "z-score=" + String.format("%.2f", zscore)));
            }
        }

        // IQR
        if (context.getAccountQ3() != null && context.getAccountMedian() != null) {
            BigDecimal iqr = context.getAccountQ3().subtract(context.getAccountMedian());
            BigDecimal upperBound = context.getAccountQ3().add(iqr.multiply(BigDecimal.valueOf(riskConfig.getLayer2().getIqrMultiplier())));
            if (payment.getAmount().compareTo(upperBound) > 0) {
                totalScore += 20;
                flags.add(new StatisticalFlag("IQR_ANOMALY", upperBound.doubleValue(), 20,
                        "Amount " + payment.getAmount() + " exceeds IQR upper bound " + String.format("%.2f", upperBound.doubleValue())));
            }
        }

        // Velocity
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation()) {
            totalScore += 25;
            flags.add(new StatisticalFlag("VELOCITY_ANOMALY", context.getRecentTransactionCount(), 25,
                    "Velocity " + context.getRecentTransactionCount() + "x in 10min"));
        }

        return new StatisticalResult(totalScore, flags);
    }

    public record StatisticalResult(int additionalScore, List<StatisticalFlag> flags) {
        public static StatisticalResult noAnomaly() { return new StatisticalResult(0, List.of()); }
        public static StatisticalResult insufficientData() { return new StatisticalResult(0, List.of()); }
    }
    public record StatisticalFlag(String type, double value, int score, String description) {}
}
