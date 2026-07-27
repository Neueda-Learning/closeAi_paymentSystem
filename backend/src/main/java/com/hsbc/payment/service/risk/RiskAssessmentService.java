package com.hsbc.payment.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.enums.RiskDecision;
import com.hsbc.payment.enums.RiskLevel;
import com.hsbc.payment.entity.AccountStats;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.entity.RiskAssessment;
import com.hsbc.payment.mapper.AccountStatsMapper;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final List<RiskRule> rules;
    private final StatisticalDetector statisticalDetector;
    private final RiskConfig riskConfig;
    private final AccountStatsMapper accountStatsMapper;
    private final PaymentMapper paymentMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;

    public RiskAssessmentResult assess(Payment payment) {
        RiskContext context = buildContext(payment);

        // Layer 1: Rule engine
        int totalScore = 0;
        List<TriggeredRule> triggeredRules = new ArrayList<>();
        if (riskConfig.getLayer1().isEnabled()) {
            for (RiskRule rule : rules) {
                int score = rule.evaluate(payment, context);
                if (score > 0) triggeredRules.add(new TriggeredRule(rule.ruleName(), score, rule.reason(payment, context)));
                totalScore += score;
            }
            log.info("Layer 1: score={}, rules={}", totalScore, triggeredRules.size());
        }

        RiskDecision decision = decide(totalScore);
        if (decision == RiskDecision.BLOCK)
            return persist(payment, totalScore, decision, triggeredRules, StatisticalDetector.StatisticalResult.noAnomaly(), null);

        // Layer 2: Statistical
        StatisticalDetector.StatisticalResult statResult = StatisticalDetector.StatisticalResult.noAnomaly();
        if (riskConfig.getLayer2().isEnabled()) {
            statResult = statisticalDetector.detect(payment, context);
            totalScore += statResult.additionalScore();
            decision = decide(totalScore);
            log.info("Layer 2: +{} = {} → {}", statResult.additionalScore(), totalScore, decision);
        }
        if (decision == RiskDecision.BLOCK)
            return persist(payment, totalScore, decision, triggeredRules, statResult, null);

        // Layer 3: AI Agent (deferred, requires LLM API key)
        log.info("Layer 3: {} (decision={}, layer3.enabled={})",
                riskConfig.getLayer3().isEnabled() ? "would evaluate" : "skipped (disabled)",
                decision, riskConfig.getLayer3().isEnabled());

        return persist(payment, totalScore, decision, triggeredRules, statResult, null);
    }

    private RiskDecision decide(int score) {
        if (score >= riskConfig.getThresholds().getBlock()) return RiskDecision.BLOCK;
        if (score >= riskConfig.getThresholds().getReview()) return RiskDecision.REVIEW;
        return RiskDecision.APPROVE;
    }

    private RiskContext buildContext(Payment payment) {
        AccountStats stats = accountStatsMapper.selectById(payment.getSourceAccount());
        int hour = payment.getCreatedAt() != null ? payment.getCreatedAt().getHour() : LocalDateTime.now().getHour();

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getSourceAccount, payment.getSourceAccount()).ge(Payment::getCreatedAt, cutoff);
        int recentCount = paymentMapper.selectCount(wrapper).intValue();

        Set<String> knownPayees = Set.of();
        BigDecimal avg = BigDecimal.ZERO, std = BigDecimal.ZERO, med = BigDecimal.ZERO, q3 = BigDecimal.ZERO;
        if (stats != null) {
            avg = stats.getAvgAmount(); std = stats.getStdAmount();
            med = stats.getMedianAmount(); q3 = stats.getQ3Amount();
            if (stats.getKnownPayees() != null && !stats.getKnownPayees().isEmpty())
                knownPayees = parsePayees(stats.getKnownPayees());
        }

        return RiskContext.builder().transactionHour(hour).accountAvgAmount(avg)
                .accountStdAmount(std).accountMedian(med).accountQ3(q3)
                .knownPayees(knownPayees).recentTransactionCount(recentCount).accountStats(stats).build();
    }

    private Set<String> parsePayees(String json) {
        if (json == null || json.isBlank()) return Set.of();
        json = json.trim().replace("[","").replace("]","").replace("\"","");
        if (json.isEmpty()) return Set.of();
        return Arrays.stream(json.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    private RiskAssessmentResult persist(Payment payment, int score, RiskDecision decision,
            List<TriggeredRule> rules, StatisticalDetector.StatisticalResult statResult, Object aiResult) {
        RiskLevel level = score >= 80 ? RiskLevel.CRITICAL : score >= 60 ? RiskLevel.HIGH : score >= 30 ? RiskLevel.MEDIUM : RiskLevel.LOW;

        RiskAssessment entity = new RiskAssessment();
        entity.setPaymentId(payment.getId()); entity.setRiskScore(score);
        entity.setRiskLevel(level.name()); entity.setRiskDecision(decision.name());
        entity.setTriggeredRules(toJson(rules)); entity.setStatisticalFlags(toJson(statResult.flags()));
        riskAssessmentMapper.insert(entity);

        log.info("Risk persisted: paymentId={}, score={}, level={}, decision={}", payment.getId(), score, level, decision);
        return new RiskAssessmentResult(score, level, decision, rules, statResult, null);
    }

    private String toJson(List<?> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<items.size();i++) { if(i>0)sb.append(","); sb.append(items.get(i).toString()); }
        sb.append("]"); return sb.toString();
    }

    public record TriggeredRule(String ruleName, int score, String reason) {}
    public record RiskAssessmentResult(int totalScore, RiskLevel riskLevel, RiskDecision riskDecision,
            List<TriggeredRule> triggeredRules, StatisticalDetector.StatisticalResult statisticalResult, Object aiAgentResult) {}
}
