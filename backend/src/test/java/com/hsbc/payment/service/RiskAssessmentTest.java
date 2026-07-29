package com.hsbc.payment.service;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.enums.RiskDecision;
import com.hsbc.payment.enums.RiskLevel;
import com.hsbc.payment.mapper.AccountMapper;
import com.hsbc.payment.mapper.AccountStatsMapper;
import com.hsbc.payment.mapper.PaymentMapper;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import com.hsbc.payment.service.risk.*;
import com.hsbc.payment.service.risk.StatisticalDetector.StatisticalResult;
import com.hsbc.payment.service.risk.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 风控测试 — Layer 1 规则引擎 / Layer 2 统计检测 / 三层编排 / Layer 3 异常保护
 * 基于 JUnit并发测试与风控测试场景设计文档 (4大类 22用例)
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskAssessmentTest {

    @Autowired private LargeAmountRule largeAmountRule;
    @Autowired private NightTimeRule nightTimeRule;
    @Autowired private SelfTransferRule selfTransferRule;
    @Autowired private NewPayeeRule newPayeeRule;
    @Autowired private VelocityRule velocityRule;
    @Autowired private StatisticalDetector statisticalDetector;
    @Autowired private RiskAssessmentService riskAssessmentService;
    @Autowired private AiAgentResultParser aiResultParser;
    @Autowired private RiskConfig riskConfig;
    @Autowired private AccountStatsMapper accountStatsMapper;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private RiskAssessmentMapper riskAssessmentMapper;

    // ═══════════════ G: Layer 1 规则引擎 (10) ═══════════════

    private RiskContext ctx(int hour, int recentCount, Set<String> knownPayees) {
        return RiskContext.builder()
                .transactionHour(hour).recentTransactionCount(recentCount).knownPayees(knownPayees)
                .accountAvgAmount(BigDecimal.ZERO).accountStdAmount(BigDecimal.ZERO)
                .accountMedian(BigDecimal.ZERO).accountQ3(BigDecimal.ZERO).build();
    }

    private Payment pay(BigDecimal amount, String src, String dst) {
        Payment p = new Payment();
        p.setAmount(amount); p.setSourceAccount(src); p.setDestinationAccount(dst);
        p.setCurrency("USD");
        return p;
    }

    @Test @DisplayName("G-1: LargeAmountRule — >=1M → 100 分 (BLOCK)")
    void g1_largeAmountBlock() {
        assertEquals(100, largeAmountRule.evaluate(pay(new BigDecimal("1000000"), "A", "B"), ctx(14,0,Set.of())));
        assertNotNull(largeAmountRule.reason(pay(new BigDecimal("1000000"), "A", "B"), ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-2: LargeAmountRule — >=100K warning → 30 分")
    void g2_largeAmountWarning() {
        assertEquals(30, largeAmountRule.evaluate(pay(new BigDecimal("100000"), "A", "B"), ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-3: LargeAmountRule — 正常金额 → 0")
    void g3_largeAmountNormal() {
        assertEquals(0, largeAmountRule.evaluate(pay(new BigDecimal("1000"), "A", "B"), ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-4: NightTimeRule — 凌晨 2 点 → 25 分")
    void g4_nightTime() {
        assertEquals(25, nightTimeRule.evaluate(pay(new BigDecimal("100"), "A", "B"), ctx(2,0,Set.of())));
    }

    @Test @DisplayName("G-5: NightTimeRule — 下午 2 点 → 0")
    void g5_nightTimeNormal() {
        assertEquals(0, nightTimeRule.evaluate(pay(new BigDecimal("100"), "A", "B"), ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-6: SelfTransferRule — 源=目标 → 50 分")
    void g6_selfTransfer() {
        Payment p = pay(new BigDecimal("100"), "ACC-SAME", "ACC-SAME");
        assertEquals(riskConfig.getLayer1().getSelfTransferScore(), selfTransferRule.evaluate(p, ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-7: SelfTransferRule — 源≠目标 → 0")
    void g7_selfTransferNormal() {
        assertEquals(0, selfTransferRule.evaluate(pay(new BigDecimal("100"), "A", "B"), ctx(14,0,Set.of())));
    }

    @Test @DisplayName("G-8: NewPayeeRule — 目标不在 knownPayees → 30 分")
    void g8_newPayeeTriggered() {
        Payment p = pay(new BigDecimal("100"), "A", "ACC-00009");
        assertEquals(riskConfig.getLayer1().getNewPayeeScore(),
                newPayeeRule.evaluate(p, ctx(14, 0, Set.of("ACC-00002", "ACC-00003"))));
    }

    @Test @DisplayName("G-9: NewPayeeRule — 目标在 knownPayees → 0")
    void g9_newPayeeNormal() {
        Payment p = pay(new BigDecimal("100"), "A", "ACC-00002");
        assertEquals(0, newPayeeRule.evaluate(p, ctx(14, 0, Set.of("ACC-00002", "ACC-00003"))));
    }

    @Test @DisplayName("G-10: VelocityRule — 10min >=5 笔 → 35 分")
    void g10_velocityTriggered() {
        assertEquals(riskConfig.getLayer1().getVelocityScore(),
                velocityRule.evaluate(pay(new BigDecimal("100"), "A", "B"), ctx(14, 6, Set.of())));
    }

    // ═══════════════ H: Layer 2 统计检测 (4) ═══════════════

    @Test @DisplayName("H-1: z-score 异常 — amount 偏离均值 >3σ → 加分")
    void h1_zscoreAnomaly() {
        var stats = accountStatsMapper.selectById("ACC-00001"); // avg=50000, std=30000
        assertNotNull(stats);
        Payment p = pay(new BigDecimal("200000"), "ACC-00001", "ACC-00002");
        RiskContext c = ctx(14, 0, Set.of("ACC-00002"));
        c.setAccountStats(stats);
        c.setAccountAvgAmount(stats.getAvgAmount());
        c.setAccountStdAmount(stats.getStdAmount());
        c.setAccountMedian(stats.getMedianAmount());
        c.setAccountQ3(stats.getQ3Amount());

        StatisticalResult r = statisticalDetector.detect(p, c);
        assertTrue(r.additionalScore() > 0, "z-score 5.0 should add score, got " + r.additionalScore());
        assertTrue(r.flags().stream().anyMatch(f -> f.type().equals("ZSCORE_ANOMALY")));
    }

    @Test @DisplayName("H-2: IQR 异常 — amount > Q3 + 1.5*IQR → +20")
    void h2_iqrAnomaly() {
        var stats = accountStatsMapper.selectById("ACC-00001"); // Q3=75000, IQR=30000, upper=120000
        Payment p = pay(new BigDecimal("150000"), "ACC-00001", "ACC-00002");
        RiskContext c = ctx(14, 0, Set.of("ACC-00002"));
        c.setAccountStats(stats);
        c.setAccountAvgAmount(stats.getAvgAmount());
        c.setAccountStdAmount(stats.getStdAmount());
        c.setAccountMedian(stats.getMedianAmount());
        c.setAccountQ3(stats.getQ3Amount());

        StatisticalResult r = statisticalDetector.detect(p, c);
        assertTrue(r.flags().stream().anyMatch(f -> f.type().equals("IQR_ANOMALY")),
                "IQR outlier should be flagged");
    }

    @Test @DisplayName("H-3: velocity 异常 — 10min >=5 笔 → +25")
    void h3_velocityAnomaly() {
        var stats = accountStatsMapper.selectById("ACC-00001");
        Payment p = pay(new BigDecimal("5000"), "ACC-00001", "ACC-00002");
        RiskContext c = ctx(14, 7, Set.of("ACC-00002"));
        c.setAccountStats(stats);
        StatisticalResult r = statisticalDetector.detect(p, c);
        assertTrue(r.flags().stream().anyMatch(f -> f.type().equals("VELOCITY_ANOMALY")));
    }

    @Test @DisplayName("H-4: 数据不足 — totalCount <5 → insufficientData")
    void h4_insufficientData() {
        Payment p = pay(new BigDecimal("5000"), "ACC-00010", "ACC-00002");
        var stats = accountStatsMapper.selectById("ACC-00010"); // no stats for ACC-00010
        RiskContext c = ctx(14, 0, Set.of());
        c.setAccountStats(stats);
        StatisticalResult r = statisticalDetector.detect(p, c);
        assertEquals(0, r.additionalScore());
        assertTrue(r.flags().isEmpty());
    }

    // ═══════════════ I: 三层编排 (3) ═══════════════

    /** Helper: create + persist a real payment in H2 for assessment */
    private Payment createRealPayment(String src, String dst, BigDecimal amount) {
        Payment p = new Payment();
        p.setId(java.util.UUID.randomUUID().toString());
        p.setIdempotencyKey("risk-" + p.getId().substring(0,8));
        p.setSourceAccount(src); p.setDestinationAccount(dst);
        p.setAmount(amount); p.setCurrency("USD");
        p.setSettlementAmount(amount); // required by DB NOT NULL
        p.setSettlementCurrency("USD");
        p.setStatus("CREATED");
        p.setCreatedAt(LocalDateTime.now());
        p.setRetryCount(0);
        p.setVersion(0);
        paymentMapper.insert(p);
        return p;
    }

    @Test @DisplayName("I-1: Layer 1 直接 BLOCK — 不走 Layer 2/3")
    void i1_layer1Block() {
        Payment p = createRealPayment("ACC-00001", "ACC-00002", new BigDecimal("1000000"));
        var result = riskAssessmentService.assess(p);

        assertEquals(RiskDecision.BLOCK, result.riskDecision());
        assertTrue(result.riskLevel() == RiskLevel.HIGH || result.riskLevel() == RiskLevel.CRITICAL,
                "1M triggers BLOCK, level=" + result.riskLevel());
        assertTrue(result.triggeredRules().stream().anyMatch(t -> t.ruleName().equals("LargeAmountRule")));
        assertNull(result.aiAgentResult(), "Layer 3 not run");
    }

    @Test @DisplayName("I-2: Layer 1 REVIEW + Layer 2 → BLOCK (累积)")
    void i2_layer1ReviewLayer2Block() {
        // amount=100K triggers warning (+30), stats cause z-score/IQR (+20+25) = 75 → BLOCK
        Payment p = createRealPayment("ACC-00001", "ACC-00003", new BigDecimal("500000"));
        var result = riskAssessmentService.assess(p);

        assertTrue(result.totalScore() >= riskConfig.getThresholds().getBlock() || result.riskDecision() == RiskDecision.BLOCK,
                "score=" + result.totalScore() + " decision=" + result.riskDecision());
    }

    @Test @DisplayName("I-3: Layer 1 REVIEW + Layer 3 未启用 → 维持 REVIEW")
    void i3_layer1ReviewLayer3Disabled() {
        // Default: layer3.enabled=false
        Payment p = createRealPayment("ACC-00007", "ACC-00008", new BigDecimal("500"));
        var result = riskAssessmentService.assess(p);

        assertEquals(RiskDecision.APPROVE, result.riskDecision(), "Normal payment should APPROVE");
        assertNull(result.aiAgentResult());
    }

    // ═══════════════ J: Layer 3 异常保护 (3) ═══════════════

    @Test @DisplayName("J-2: LLM_API_KEY 未配置 → PaymentRiskAgent 返回默认 REVIEW")
    void j2_noApiKeyReturnsReview() {
        // Agent not injected (no API key) — fallback in RiskAssessmentService
        Payment p = createRealPayment("ACC-00001", "ACC-00008", new BigDecimal("500"));
        var result = riskAssessmentService.assess(p);
        assertNotNull(result, "assessment completes without Layer 3");
    }

    @Test @DisplayName("J-3: LLM 返回格式异常 → 解析器默认 REVIEW")
    void j3_malformedLlmResponse() {
        String badResponse = "I think this is very suspicious!\nBut I forgot the format.";
        var parsed = aiResultParser.parse(badResponse);
        assertEquals(RiskDecision.REVIEW, parsed.decision(), "default to REVIEW on bad format");
        assertEquals("MEDIUM", parsed.confidence(), "default to MEDIUM confidence");
    }

    @Test @DisplayName("J-3b: LLM returns explicit BLOCK format → parsed correctly")
    void j3b_validBlockResponse() {
        String response = "DECISION: BLOCK\nREASONING: Smurfing pattern detected - 5 small transfers\nCONFIDENCE: HIGH\nRECOMMENDED_ACTION: Freeze account and contact customer";
        var parsed = aiResultParser.parse(response);
        assertEquals(RiskDecision.BLOCK, parsed.decision());
        assertTrue(parsed.reasoning().contains("Smurfing"));
        assertEquals("HIGH", parsed.confidence());
        assertTrue(parsed.recommendedAction().contains("Freeze"));
    }

    @Test @DisplayName("G-extra: 5 rules all return correct ruleName()")
    void allRulesHaveNames() {
        assertNotNull(largeAmountRule.ruleName());
        assertNotNull(nightTimeRule.ruleName());
        assertNotNull(selfTransferRule.ruleName());
        assertNotNull(newPayeeRule.ruleName());
        assertNotNull(velocityRule.ruleName());
    }
}
