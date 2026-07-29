# 支付系统 JUnit 并发测试与风控测试场景设计文档

> 项目：Payment Processing System (Spring Boot 3 + MyBatis-Plus + MySQL/H2)
> 测试框架：JUnit 5 + Spring Boot Test + H2 内存数据库
> 目标：覆盖并发安全 + 三层风控引擎全路径
> 日期：2026-07-29

---

## 一、测试环境与技术栈

### 1.1 现有测试基础设施

| 组件 | 配置 | 说明 |
|------|------|------|
| 测试 profile | `application-test.yml` | H2 内存数据库 `jdbc:h2:mem:testdb;MODE=MySQL` |
| Schema 初始化 | `schema-h2.sql` (mode=always) | 含 payments/status_history/idempotency_keys/accounts/risk_assessments/account_stats |
| 现有测试 | 5 个文件 65 个用例 | PaymentApiIntegrationTest(20) + AccountApiIntegrationTest(3) + PaymentServiceTest(13) + StateMachineServiceTest(15) + ValidationServiceTest(14) |
| 缺口 | **0 个并发测试 + 0 个风控测试** | 本文档要补齐 |

### 1.2 并发测试所需工具

```xml
<!-- pom.xml 已有，确认存在 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

并发测试不需要额外依赖，用 JDK 自带的 `CountDownLatch` + `ExecutorService` + `CompletableFuture` 即可。

### 1.3 测试基类模板

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseConcurrentTest {

    @Autowired protected PaymentService paymentService;
    @Autowired protected PaymentMapper paymentMapper;
    @Autowired protected AccountMapper accountMapper;
    @Autowired protected IdempotencyService idempotencyService;
    @Autowired protected RiskAssessmentService riskAssessmentService;

    /** 构造有效的创建支付请求（含密码和收款人姓氏） */
    protected CreatePaymentRequest buildValidRequest(String source, String dest, BigDecimal amount) {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setSourceAccount(source);
        req.setDestinationAccount(dest);
        req.setAmount(amount);
        req.setCurrency("USD");
        req.setDescription("test payment");
        req.setSourceAccountPassword("Payment@123");  // 种子账户密码
        req.setRecipientLastName(dest.endsWith("01") ? "Operations"
                : dest.endsWith("02") ? "Desk"
                : dest.endsWith("07") ? "Retail" : "Test");
        return req;
    }

    /** 等待所有线程就绪后同时开始，收集结果 */
    protected <T> List<CompletableFuture<T>> runConcurrent(int threadCount, Supplier<T> task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch readyGate = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<T>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                readyGate.countDown();
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return task.get();
            }, executor));
        }

        readyGate.await(5, TimeUnit.SECONDS);
        startGate.countDown();  // 同时放行
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        return futures;
    }

    /** 统计成功/失败/异常数量 */
    protected <T> ConcurrentResult summarize(List<CompletableFuture<T>> futures) {
        int success = 0, businessError = 0, otherError = 0;
        for (CompletableFuture<T> f : futures) {
            try { f.get(5, TimeUnit.SECONDS); success++; }
            catch (ExecutionException e) {
                if (e.getCause() instanceof BusinessException) businessError++;
                else otherError++;
            } catch (Exception e) { otherError++; }
        }
        return new ConcurrentResult(success, businessError, otherError, futures.size());
    }

    protected record ConcurrentResult(int success, int businessError, int otherError, int total) {}
}
```

---

## 二、并发测试场景（7 大类 18 个用例）

### 场景类 A：幂等性并发（3 个用例）

#### A-1 相同 Idempotency-Key 并发创建 — 只有一笔成功

```
前置条件：DB 无 Idempotency-Key=K1
并发动作：2 个线程同时用 K1 创建相同支付
预期结果：
  - success = 1（第一个拿到锁的）
  - businessError = 1（第二个收到 DUPLICATE_PAYMENT 409）
  - DB payments 表只有 1 条记录
  - DB idempotency_keys 表只有 1 条记录（K1 → 唯一 paymentId）
验证点：
  ① paymentMapper.selectCount() == 1
  ② idempotencyService.findPaymentIdByKey("K1") 返回唯一的 paymentId
  ③ 成功的 payment 的 idempotencyKey == "K1"
```

```java
@Test
@DisplayName("A-1: 相同 Idempotency-Key 并发创建 — 只有一笔成功")
void concurrentSameIdempotencyKey() throws Exception {
    String key = "CONCURRENT-KEY-A1";
    CreatePaymentRequest req = buildValidRequest("ACC-00001", "ACC-00002", new BigDecimal("100"));

    List<CompletableFuture<PaymentResponse>> futures = runConcurrent(2,
        () -> paymentService.createPayment(req, key));
    ConcurrentResult result = summarize(futures);

    assertEquals(1, result.success(), "应只有 1 个成功");
    assertEquals(1, result.businessError(), "应有 1 个 DUPLICATE_PAYMENT");
    assertEquals(1, paymentMapper.selectCount(null), "DB 只应有 1 条 payment");
    assertNotNull(idempotencyService.findPaymentIdByKey(key));
}
```

#### A-2 不同 Idempotency-Key 并发创建 — 都成功

```
前置条件：DB 无 K2、K3
并发动作：2 个线程用不同 key 创建支付（不同金额或不同账户）
预期结果：success = 2，DB 有 2 条 payment
验证点：两条 payment 的 id 不同、idempotencyKey 不同
```

#### A-3 相同 Idempotency-Key 非并发重复创建 — 幂等返回

```
前置条件：无
动作：线程 1 先创建成功（K4），线程 2 后用 K4 创建
预期结果：
  - 线程 1 返回 201 CREATED + paymentId=UUID-A
  - 线程 2 返回 200 OK + 同一 paymentId=UUID-A（非异常）
验证点：两次调用返回的 payment.id 相同
```

---

### 场景类 B：Payment 乐观锁并发（3 个用例）

#### B-1 并发更新同一 Payment — 一个成功一个 409

```
前置条件：创建 payment P1（CREATED 状态，version=0）
并发动作：2 个线程同时 updatePayment(P1, 不同金额)
预期结果：
  - success = 1
  - businessError = 1（OptimisticLockingFailureException → 409 CONFLICT）
验证点：
  ① DB 中 P1 的 version = 1（只 +1 了一次）
  ② 成功的那笔的金额覆盖了 DB
  ③ 失败的那笔的金额没有写入
```

#### B-2 并发状态转换（validate）同一 Payment — 一个成功一个 409

```
前置条件：创建 P2（CREATED）
并发动作：2 个线程同时 processValidate(P2)
预期结果：success = 1，businessError = 1
验证点：
  ① P2 状态 = VALIDATED（不是 FAILED，因为风控应该 APPROVE 小额）
  ② status_history 只有 1 条 CREATED→VALIDATED 记录
  ③ P2 的 version = 1
```

#### B-3 并发 update + validate 同一 Payment — 互斥

```
前置条件：创建 P3（CREATED）
并发动作：线程 1 updatePayment(P3, 新金额)，线程 2 processValidate(P3)
预期结果：只有一个成功，另一个 409
验证点：
  - 若 update 先到：P3 金额已改，validate 基于新金额校验
  - 若 validate 先到：P3 状态=VALIDATED，update 抛 INVALID_STATUS_TRANSITION
  - 不允许出现"金额改了但状态没变"或"状态变了但金额没改"的不一致
```

---

### 场景类 C：账户余额并发（4 个用例）⚠️ 重点

#### C-1 同一源账户并发 COMPLETE — 余额不应被覆盖

```
前置条件：
  - 创建 P4、P5（都从 ACC-00007 扣款，ACC-00007 余额=200）
  - P4 amount=150，P5 amount=180
  - 两笔都先 validate + send 到 SENT 状态
并发动作：2 个线程同时 processComplete
预期结果（当前代码有 BUG）：
  - 两个都可能"成功"（Account 无 @Version，不会抛 409）
  - 但 DB 中 ACC-00007 余额被覆盖（不是正确的 -130，而是 50 或 20）
  - 这是已知的 P0 漏洞
测试策略：
  - 先写测试暴露 BUG（断言失败）
  - 修复后（原子 SQL）改为断言：success=1，businessError=1（INSUFFICIENT_FUNDS）
验证点：
  ① 修复前：断言余额 != 正确值（暴露 bug）
  ② 修复后：断言余额 = 200 - 150 = 50（或 200-180=20），另一个收到 INSUFFICIENT_FUNDS
```

```java
@Test
@DisplayName("C-1: 同一源账户并发 COMPLETE — 余额原子性")
void concurrentCompleteSameSourceAccount() throws Exception {
    // 准备两笔 SENT 状态的支付，都从 ACC-00007(余额200) 扣款
    String p4 = createAndProgressToSent("ACC-00007", "ACC-00001", new BigDecimal("150"));
    String p5 = createAndProgressToSent("ACC-00007", "ACC-00001", new BigDecimal("180"));

    List<CompletableFuture<PaymentResponse>> futures = runConcurrent(2, () -> {
        // 交替 complete P4 和 P5
        // 简化：用线程安全的方式分配
        ...
    });
    ConcurrentResult result = summarize(futures);

    Account acc = accountMapper.selectById("ACC-00007");
    // 当前 BUG：余额被覆盖，不等于正确的 200-150-180=-130
    // 修复后：应只有一个成功，余额=50 或 20，另一个 INSUFFICIENT_FUNDS
    assertTrue(acc.getBalance().compareTo(BigDecimal.ZERO) >= 0
        || result.businessError() >= 1, "余额不应为负");
}
```

#### C-2 并发扣款 + 充值同一账户 — 余额一致

```
前置条件：ACC-00008 余额=100
并发动作：
  - 线程 1：processComplete(P6) 扣 30（ACC-00008 → ACC-00001）
  - 线程 2：processComplete(P7) 充 50（ACC-00001 → ACC-00008）
预期结果：
  - 两个操作都成功（或一个 409 如果最终余额校验失败）
  - 最终余额应该反映两笔交易（100-30+50=120 或 100+50-30=120）
验证点：ACC-00008 最终余额 = 120（非 70 或 150 等被覆盖的值）
```

#### C-3 并发 retry 同一 Payment + 另一笔 update — 互斥

```
前置条件：P8 在 FAILED 状态
并发动作：线程 1 processRetry(P8)，线程 2 updatePayment(P8)
预期结果：只有一个成功
验证点：version 只 +1，状态一致
```

#### C-4 高并发压测 — 10 线程同时 COMPLETE 不同 Payment 同一账户

```
前置条件：ACC-00009 余额=5000，创建 10 笔 600 的支付并都推进到 SENT
并发动作：10 线程同时 COMPLETE
预期结果：
  - 最多 8 笔成功（5000/600=8.33），至少 2 笔 INSUFFICIENT_FUNDS
  - 最终余额 >= 0 且 <= 200（5000 - 8*600 = 200）
  - 余额 = 5000 - (成功笔数 × 600)
验证点：
  ① 成功笔数 ≤ 8
  ② 余额 >= 0
  ③ 余额 == 5000 - successCount * 600（原子性验证）
```

---

### 场景类 D：幂等 + 状态转换交叉（2 个用例）

#### D-1 并发 retry 用相同 Idempotency-Key — 幂等返回

```
前置条件：P9 在 FAILED 状态
并发动作：2 线程用同一 retry Idempotency-Key 调 processRetry(P9)
预期结果：
  - success = 1，businessError = 1（DUPLICATE_PAYMENT）
  - retryCount 只 +1（不是 +2）
验证点：P9.retryCount == 旧值 + 1
```

#### D-2 并发 cancel + complete 同一 Payment — 互斥

```
前置条件：P10 在 SENT 状态
并发动作：线程 1 cancelPayment(P10)，线程 2 processComplete(P10)
预期结果：只有一个成功
验证点：
  - 若 cancel 先到：P10=CANCELLED，complete 收到 409
  - 若 complete 先到：P10=COMPLETED，cancel 收到 INVALID_STATUS_TRANSITION
  - 不允许出现"既 CANCELLED 又扣了款"的不一致
```

---

### 场景类 E：风控并发（2 个用例）

#### E-1 并发 validate 同一账户的两笔支付 — 风控 velocity 规则触发

```
前置条件：ACC-00001 近 10 分钟无交易
动作：快速连续创建并 validate 6 笔支付（每笔间隔 < 1s）
预期结果：
  - 第 1-4 笔 APPROVE（recentCount < 5）
  - 第 5-6 笔可能触发 VelocityRule（recentCount >= 5）→ 加分 → REVIEW 或 BLOCK
验证点：
  ① 后几笔的 risk_assessments 中 triggeredRules 包含 VelocityRule
  ② 风控评分递增
注意：由于并发时序不确定，断言用"至少有一笔触发 velocity"而非精确断言
```

#### E-2 并发 validate 同一 Payment — 风控只执行一次

```
前置条件：P11 CREATED
并发动作：2 线程同时 processValidate(P11)
预期结果：
  - success = 1，businessError = 1（409）
  - risk_assessments 表只有 1 条 P11 的记录（不是 2 条）
验证点：riskAssessmentMapper.findByPaymentId("P11").size() == 1
```

---

### 场景类 F：数据库故障模拟（2 个用例）

#### F-1 processValidate 中风控 DB 异常 — 应不破坏主流程

```
前置条件：mock RiskAssessmentService.assess() 抛 RuntimeException
动作：调用 processValidate
预期结果（当前 BUG）：
  - 抛 500 PROCESSING_ERROR（事务回滚，支付卡在 CREATED）
  - 这是已知的 P0 问题
测试策略：
  - 先写测试暴露 BUG
  - 修复后（assess 加 try-catch）断言：validate 成功，payment=VALIDATED
```

#### F-2 createPayment 中汇率查询失败 — 事务回滚无脏数据

```
前置条件：mock ExchangeRateService.quote() 抛 EXCHANGE_RATE_NOT_FOUND
动作：调用 createPayment
预期结果：
  - 抛 BusinessException(EXCHANGE_RATE_NOT_FOUND)
  - DB 无 payment 记录（事务回滚）
  - DB 无 idempotency_keys 记录
验证点：paymentMapper.selectCount(null) == 0
```

---

## 三、风控测试场景（4 大类 22 个用例）

### 场景类 G：Layer 1 规则引擎单元测试（5 条规则 × 2 场景 = 10 个用例）

#### G-1 LargeAmountRule — 超过 block 阈值

```
前置条件：payment.amount = 1,000,000（等于 block 阈值）
预期结果：score = 100 → 直接 BLOCK（无需走 Layer 2/3）
验证点：
  ① riskAssessment.riskDecision == "BLOCK"
  ② riskAssessment.riskScore >= 60（thresholds.block）
  ③ triggeredRules 包含 "LargeAmountRule(100)"
  ④ payment 状态变为 FAILED + RISK_BLOCKED
```

#### G-2 LargeAmountRule — 超过 warning 阈值但低于 block

```
前置条件：payment.amount = 100,000（等于 warning 阈值）
预期结果：score = 30 → REVIEW（30 >= thresholds.review=30）
验证点：riskDecision == "REVIEW"，payment 仍 VALIDATED（不 BLOCK）
```

#### G-3 LargeAmountRule — 正常金额

```
前置条件：payment.amount = 1,000
预期结果：score = 0 → APPROVE
```

#### G-4 NightTimeRule — 凌晨 2 点交易

```
前置条件：payment.createdAt 的 hour = 2（nightTimeStart=0, nightTimeEnd=5）
预期结果：score = 25
注意：由于 createdAt 由 DB 生成，测试时需 mock RiskContext 或用 payment.createdAt 手动设置
```

#### G-5 NightTimeRule — 下午 2 点交易

```
前置条件：hour = 14
预期结果：score = 0
```

#### G-6 SelfTransferRule — 源=目标账户

```
前置条件：sourceAccount == destinationAccount
预期结果：score = 50（selfTransferScore）
注意：validateOnCreate 会先拦截（source != dest），所以此规则实际在 validate 阶段不会触发
测试方式：直接调用 rule.evaluate()，不走完整流程
```

#### G-7 SelfTransferRule — 源≠目标账户

```
预期结果：score = 0
```

#### G-8 NewPayeeRule — 目标账户不在 knownPayees

```
前置条件：account_stats 中 ACC-00001 的 knownPayees = ["ACC-00002","ACC-00003"]
         payment.destinationAccount = "ACC-00009"（不在列表中）
预期结果：score = 30（newPayeeScore）
```

#### G-9 NewPayeeRule — 目标账户在 knownPayees

```
前置条件：payment.destinationAccount = "ACC-00002"（在列表中）
预期结果：score = 0
```

#### G-10 VelocityRule — 10 分钟内 >= 5 笔交易

```
前置条件：recentTransactionCount = 6（velocityDeviation=5）
预期结果：score = 35（velocityScore）
```

### 规则单元测试模板

```java
@SpringBootTest
@ActiveProfiles("test")
class RiskRuleTest {

    @Autowired private LargeAmountRule largeAmountRule;
    @Autowired private NightTimeRule nightTimeRule;
    @Autowired private SelfTransferRule selfTransferRule;
    @Autowired private NewPayeeRule newPayeeRule;
    @Autowired private VelocityRule velocityRule;
    @Autowired private RiskConfig riskConfig;

    private RiskContext context(int hour, int recentCount, Set<String> knownPayees) {
        return RiskContext.builder()
            .transactionHour(hour)
            .recentTransactionCount(recentCount)
            .knownPayees(knownPayees)
            .accountAvgAmount(BigDecimal.ZERO)
            .accountStdAmount(BigDecimal.ZERO)
            .accountMedian(BigDecimal.ZERO)
            .accountQ3(BigDecimal.ZERO)
            .build();
    }

    @Test @DisplayName("G-1: LargeAmountRule — amount >= block threshold → 100 分")
    void largeAmountBlock() {
        Payment p = new Payment();
        p.setAmount(riskConfig.getLayer1().getLargeAmountBlock()); // 1,000,000
        assertEquals(100, largeAmountRule.evaluate(p, context(14, 0, Set.of())));
    }

    @Test @DisplayName("G-2: LargeAmountRule — amount >= warning threshold → 30 分")
    void largeAmountWarning() {
        Payment p = new Payment();
        p.setAmount(riskConfig.getLayer1().getLargeAmountWarning()); // 100,000
        assertEquals(30, largeAmountRule.evaluate(p, context(14, 0, Set.of())));
    }

    @Test @DisplayName("G-8: NewPayeeRule — 目标不在 knownPayees → 30 分")
    void newPayeeTriggered() {
        Payment p = new Payment();
        p.setDestinationAccount("ACC-00009");
        RiskContext ctx = context(14, 0, Set.of("ACC-00002", "ACC-00003"));
        assertEquals(30, newPayeeRule.evaluate(p, ctx));
    }
}
```

---

### 场景类 H：Layer 2 统计检测测试（4 个用例）

#### H-1 z-score 异常 — 金额偏离均值 > 3 个标准差

```
前置条件：account_stats ACC-00001: avg=50000, std=30000, totalCount=120
         payment.amount = 200000（z-score = |200000-50000|/30000 = 5.0 >= 3.0）
预期结果：
  ① StatisticalResult.additionalScore > 0
  ② flags 包含 ZSCORE_ANOMALY
  ③ z-score 值 >= 3.0
```

#### H-2 IQR 异常 — 金额超过 Q3 + 1.5×IQR

```
前置条件：account_stats ACC-00001: median=45000, Q3=75000, IQR=30000, upperBound=75000+45000=120000
         payment.amount = 150000（> 120000）
预期结果：
  ① flags 包含 IQR_ANOMALY
  ② additionalScore 包含 +20
```

#### H-3 velocity 异常 — 10 分钟内 >= 5 笔

```
前置条件：recentTransactionCount = 7（velocityDeviation=5）
预期结果：
  ① flags 包含 VELOCITY_ANOMALY
  ② additionalScore 包含 +25
```

#### H-4 数据不足 — totalCount < 5 跳过

```
前置条件：account_stats ACC-00009: totalCount=3（< 5）
预期结果：StatisticalResult = insufficientData（additionalScore=0, flags=空）
```

---

### 场景类 I：三层递进编排测试（5 个用例）

#### I-1 Layer 1 直接 BLOCK — 不走 Layer 2/3

```
前置条件：amount=1,000,000（LargeAmountRule 给 100 分 >= 60）
动作：riskAssessmentService.assess(payment)
预期结果：
  ① riskDecision = BLOCK
  ② 只执行了 Layer 1（triggeredRules 非空，statisticalFlags 为空）
  ③ Layer 3 未执行（aiAgentResult 为 null）
验证点：risk_assessments 表 reasoning 字段为 null（Layer 3 未运行）
```

#### I-2 Layer 1 + Layer 2 累积 BLOCK

```
前置条件：
  - amount=100,000（LargeAmountRule 给 30 分 → REVIEW）
  - account_stats 让 z-score=4.0（+32 分 → total=62 >= 60 → BLOCK）
预期结果：
  ① riskDecision = BLOCK
  ② triggeredRules 包含 LargeAmountRule
  ③ statisticalFlags 包含 ZSCORE_ANOMALY
  ④ Layer 3 未执行（因为 Layer 2 已经 BLOCK）
```

#### I-3 Layer 1 REVIEW + Layer 3 未启用 — 维持 REVIEW

```
前置条件：
  - risk.layer3.enabled = false
  - amount=100,000（30 分 → REVIEW）
  - Layer 2 无异常（+0）
预期结果：
  ① riskDecision = REVIEW
  ② payment 仍 VALIDATED（REVIEW 不 BLOCK）
  ③ status_history reason 包含 "Risk REVIEW"
```

#### I-4 Layer 1 REVIEW + Layer 3 启用 + LLM 返回 BLOCK — 升级为 BLOCK

```
前置条件：
  - risk.layer3.enabled = true
  - mock PaymentRiskAgent.assessPayment() 返回 "DECISION: BLOCK\nREASONING: ..."
  - mock AiAgentResultParser.parse() 返回 decision=BLOCK
  - amount=100,000（30 分 → REVIEW）
预期结果：
  ① riskDecision = BLOCK（升级）
  ② riskScore >= 80（Math.max(totalScore, 80)）
  ③ payment 状态 = FAILED + RISK_BLOCKED
```

#### I-5 Layer 1 REVIEW + Layer 3 启用 + LLM 返回 REVIEW — 维持 REVIEW（只升级不降级）

```
前置条件：同 I-4 但 LLM 返回 REVIEW
预期结果：
  ① riskDecision = REVIEW（未升级为 BLOCK）
  ② riskScore = 原始 totalScore（未变）
  ③ payment 仍 VALIDATED
```

---

### 场景类 J：Layer 3 异常保护测试（3 个用例）

#### J-1 LLM 调用超时 — 返回默认 REVIEW

```
前置条件：
  - risk.layer3.enabled = true
  - mock PaymentRiskAgent.assessPayment() 抛 SocketTimeoutException
预期结果（PaymentRiskAgent 内部处理）：
  ① 返回 "DECISION: REVIEW\nREASONING: LLM call failed..."
  ② AiAgentResultParser 解析为 decision=REVIEW
  ③ riskDecision = REVIEW（维持，不升级）
  ④ payment 仍 VALIDATED
```

#### J-2 LLM_API_KEY 未配置 — 返回默认 REVIEW

```
前置条件：LLM_API_KEY 环境变量为空或 "sk-demo"
预期结果（PaymentRiskAgent 第 103-106 行）：
  ① 返回 "DECISION: REVIEW\nREASONING: AI Agent not configured..."
  ② riskDecision = REVIEW
```

#### J-3 LLM 返回格式异常 — 解析器默认 REVIEW

```
前置条件：mock LLM 返回 "I think this is risky"（无 DECISION: 行）
预期结果（AiAgentResultParser.fromString）：
  ① decision = REVIEW（默认值）
  ② confidence = MEDIUM（默认值）
  ③ reasoning = 原始文本
```

---

## 四、测试矩阵汇总

| 类别 | 用例数 | 覆盖的规范要求 | 优先级 |
|------|--------|---------------|--------|
| A. 幂等性并发 | 3 | 幂等性 + 重复检测 | P0 |
| B. Payment 乐观锁 | 3 | 并发处理 + 状态转换 | P0 |
| C. 账户余额并发 | 4 | 并发处理 + 数据一致性 | P0 |
| D. 幂等+状态交叉 | 2 | 状态机 + 幂等性 | P1 |
| E. 风控并发 | 2 | 风控 + 并发 | P1 |
| F. DB 故障模拟 | 2 | 容错 + 事务回滚 | P1 |
| G. Layer 1 规则 | 10 | 风控规则引擎 | P1 |
| H. Layer 2 统计 | 4 | 风控统计检测 | P1 |
| I. 三层编排 | 5 | 风控递进决策 | P1 |
| J. Layer 3 异常 | 3 | LLM 容错 | P2 |
| **合计** | **38** | | |

---

## 五、Mock 策略

### 5.1 风控测试中的 Mock 对象

| 组件 | Mock 方式 | 说明 |
|------|-----------|------|
| `PaymentRiskAgent` | `@MockBean` | Layer 3 测试时 mock LLM 响应 |
| `AiAgentResultParser` | `@MockBean` | mock 解析结果 |
| `RestTemplate`（PaymentRiskAgent 内部） | `@MockBean` | mock HTTP 调用 |
| `AccountStatsMapper` | 真实 H2 | 用种子数据 |
| `StatisticalDetector` | 真实 | 测真实逻辑 |

### 5.2 Layer 3 测试 Mock 模板

```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "risk.layer3.enabled=true")
class Layer3RiskTest {

    @MockBean private PaymentRiskAgent paymentRiskAgent;
    @MockBean private AiAgentResultParser aiResultParser;

    @Test @DisplayName("I-4: Layer 1 REVIEW + Layer 3 BLOCK → 升级")
    void layer3UpgradesToBlock() {
        // 准备一笔会触发 REVIEW 的支付
        Payment payment = createPaymentWithAmount(new BigDecimal("100000")); // 30 分

        // mock Layer 3 返回 BLOCK
        when(paymentRiskAgent.assessPayment(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
            .thenReturn("DECISION: BLOCK\nREASONING: smurfing pattern\nCONFIDENCE: HIGH");
        when(aiResultParser.parse(anyString()))
            .thenReturn(new AiAgentResultParser.AiAgentResult(
                RiskDecision.BLOCK, "smurfing", "HIGH", "freeze account", "raw"));

        paymentService.processValidate(payment.getId());

        Payment updated = paymentMapper.selectById(payment.getId());
        assertEquals("FAILED", updated.getStatus());
        assertEquals("RISK_BLOCKED", updated.getErrorCode());
    }
}
```

---

## 六、注意事项

### 6.1 H2 vs MySQL 差异

| 差异点 | H2 行为 | MySQL 行为 | 测试影响 |
|--------|---------|------------|----------|
| 乐观锁行锁 | H2 MVCC 模式下行为可能不同 | InnoDB 行锁 +间隙锁 | 并发测试在 H2 上可能无法完全复现 MySQL 的锁竞争 |
| `ON DUPLICATE KEY UPDATE` | H2 不支持 | MySQL 支持 | schema-h2.sql 用 `MERGE INTO` 替代 |
| `TEXT` 类型 | H2 用 `CLOB` | MySQL 用 `TEXT` | 无影响，MyBatis 自动适配 |
| 事务隔离 | H2 默认 READ_UNCOMMITTED | MySQL 默认 REPEATABLE_READ | **关键**：并发测试需在 H2 设置 `MODE=MySQL` 或改用 Testcontainers+MySQL |

**建议**：对于 C 类（账户余额并发）测试，建议用 **Testcontainers + 真实 MySQL**，因为 H2 的锁行为与 MySQL 不完全一致，可能无法复现并发覆盖漏洞。

### 6.2 风控测试的种子数据依赖

风控测试严重依赖 `account_stats` 表的种子数据。当前种子数据只有 5 个账户（ACC-00001/02/03/07/09），测试时注意：
- `NewPayeeRule` 需要 `knownPayees` 非空 → 只对这 5 个账户生效
- `StatisticalDetector` 需要 `totalCount >= 5` → 只对这 5 个账户生效
- 其他账户（ACC-00004/05/06/08/10）没有 stats → Layer 2 会返回 `insufficientData`

### 6.3 时间相关的测试

`NightTimeRule` 和 `VelocityRule` 依赖时间：
- `NightTimeRule` 用 `payment.createdAt.getHour()` → 测试时需手动设置 createdAt 或 mock
- `VelocityRule` 统计最近 10 分钟的交易 → 测试前需清理 recent payments 或用新账户

```java
// 清理近 10 分钟交易，确保 velocity 基线干净
@BeforeEach
void cleanRecentPayments() {
    LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(Payment::getCreatedAt, LocalDateTime.now().minusMinutes(10));
    paymentMapper.delete(wrapper);
}
```

### 6.4 并发测试的确定性

并发测试本质上有时序不确定性。策略：
1. **用 CountDownLatch 确保同时开始**（减少时序偏差）
2. **多次运行取平均**（`@RepeatedTest(5)`）
3. **断言不变式而非精确值**（如"success >= 1 && success <= total"而非"success == 1"）
4. **对于必须确定性的测试**（如乐观锁），用 `@Transactional(isolation = SERIALIZABLE)` 或手动加锁控制时序

---

## 七、实施排期建议

| 阶段 | 内容 | 用例数 | 建议工时 |
|------|------|--------|----------|
| Phase 1 | A + B 类（幂等 + 乐观锁） | 6 | 0.5 天 |
| Phase 2 | C 类（账户余额并发 — 暴露+修复 P0） | 4 | 1 天 |
| Phase 3 | G 类（Layer 1 规则单元测试） | 10 | 0.5 天 |
| Phase 4 | H + I 类（Layer 2 + 三层编排） | 9 | 1 天 |
| Phase 5 | D + E + F + J 类（交叉 + 风控并发 + 容错） | 9 | 1 天 |
| **合计** | | **38** | **4 天** |

---

## 八、测试通过标准

| 维度 | 标准 |
|------|------|
| 覆盖率 | 行覆盖 >= 80%，风控 service/risk 包 >= 90% |
| 并发安全 | C 类测试在 MySQL 下通过（无余额覆盖） |
| 幂等性 | A 类测试通过，相同 key 永远只创建一笔 |
| 风控规则 | G 类 10 个规则边界用例全通过 |
| Layer 3 容错 | J 类 3 个异常场景全通过，主流程不崩溃 |
| 回归 | 现有 65 个测试不回归 |
