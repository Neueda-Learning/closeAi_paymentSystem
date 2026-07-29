# 支付系统合规检查报告 V4（最新 main 分支 — 用户更改后）

> 检查对象：https://github.com/Neueda-Learning/closeAi_paymentSystem.git (main, commit 3b45df6)
> 对照规范：`01-支付处理系统_Payment_Processing_zh.md`
> 检查日期：2026-07-28
> 本次新增：账户密码安全 + 收款人姓氏校验 + 汇率锁定（跨币种修复）+ Layer 3 AI Agent + 审计 triggered_by + 前端风控展示 + 数据库迁移脚本

---

## 一、总体结论

| 维度 | V3 评分 | V4 评分 | 变化 |
|------|---------|---------|------|
| 核心要求（14 项） | ✅ 100% | ✅ 100% | 保持 |
| 高级功能（8 项） | ⚠️ 5/8 | ✅ 6/8 | 多货币从⚠️→✅，审计从⚠️→✅ |
| 安全增强 | — | ✅ 新增 | 密码校验 + 收款人校验 + 账户管理 API |
| 并发安全 | ⚠️ 3 个问题 | 🔴 仍存在 | 余额并发漏洞升级为 P0 |
| 代码质量 | ⚠️ 3 P0 | ⚠️ 2 P0 + 1 P0 新 | 风控异常保护未修、状态机未修、余额并发新发现 |

**整体合规度：约 95%**（核心 100%，高级功能 75%，安全增强突出，但并发余额漏洞是 P0）

---

## 二、本轮修复确认（用户更改成效）

| # | 上轮问题 | 状态 | 修复方式 |
|---|----------|------|----------|
| P0-3 | 跨币种无转换 | ✅ 已修复 | createPayment 调 ExchangeRateService.quote() 锁定汇率→存 exchangeRate/settlementAmount/settlementCurrency；processComplete 用 settlementAmount 入账目标账户 |
| P0-3 子 | 货币一致性未校验 | ✅ 已修复 | validateOnCreate 第 53 行：`sourceAccount.currency == payment.currency` 校验 |
| P2-5 | 审计缺触发者 | ✅ 已修复 | StatusHistory 加 triggeredBy 字段，所有调用传入 PAYMENT_API/USER/VALIDATION_SERVICE/RISK_ASSESSMENT_SERVICE/PAYMENT_GATEWAY/SETTLEMENT_SERVICE/RETRY_PROCESS/SYSTEM |
| P1-3 | 前端风控死代码 | ✅ 部分修复 | PaymentDetailView 展示 riskScore/riskLevel/riskDecision（`v-if="payment.riskScore"` ） |
| — | Layer 3 LLM | ✅ 已实现 | PaymentRiskAgent（零 LangChain4j，RestTemplate 调 OpenAI 兼容 API）+ AiAgentResultParser + try-catch + 只升级不降级 |
| — | 安全增强（新） | ✅ 新增 | 源账户密码校验（INVALID_CREDENTIALS）+ 收款人姓氏校验（BENEFICIARY_MISMATCH）+ AccountService 账户 CRUD + AccountsView 前端 |

---

## 三、核心要求逐项核对（14/14 全满足）

| # | 规范要求 | 状态 | 实现位置 |
|---|----------|------|----------|
| 1 | 生命周期 CREATED→VALIDATED→SENT→COMPLETED，FAILED 任意阶段 | ✅ | StateMachineService + processValidate/Send/Complete/Fail |
| 2 | 幂等性键防重复 | ✅ | Idempotency-Key + DB UNIQUE + DuplicateKeyException + 200/201 区分 |
| 3 | 重试处理 + 次数上限 | ✅ | MAX_RETRIES=3 + RETRY_EXHAUSTED + retry_count |
| 4 | 状态机防无效转换 | ✅ | StateMachineService VALID_TRANSITIONS Map |
| 5 | 错误代码 + HTTP 映射 | ✅ | ErrorCode 16 个枚举（新增 INVALID_CREDENTIALS/BENEFICIARY_MISMATCH/EXCHANGE_RATE_NOT_FOUND/DUPLICATE_ACCOUNT）+ GlobalExceptionHandler switch 完整覆盖 |
| 6 | 审计跟踪（时间戳 + 触发者） | ✅ | status_history + triggered_by 字段（本轮修复） |
| 7 | 金额验证 | ✅ | @DecimalMin/@DecimalMax/@Digits + validateOnTransition 复核 |
| 8 | 账户验证（源≠目标、格式、存在性、密码、收款人） | ✅ | ACCOUNT_PATTERN + accountMapper.selectById + passwordService.matches + holderLastName 校验 |
| 9 | 货币验证 | ✅ | SUPPORTED_CURRENCIES 白名单 + sourceAccount.currency == payment.currency 一致性校验 |
| 10 | 并发处理（乐观锁） | ⚠️ | Payment 有 @Version + 409，但 **Account 无 @Version**（见 P0-1） |
| 11 | Swagger/OpenAPI | ✅ | springdoc + @Tag/@Operation |
| 12 | 无需真实网关 | ✅ | processSend 20% 随机 NETWORK_ERROR |
| 13 | 前端 5 项操作 | ✅ | CreatePaymentView / PaymentDetailView（含风控展示）/ PaymentListView / StatusTimeline / ErrorPanel + AccountsView + ReportsView |
| 14 | 从最小字段集逐步增强 | ✅ | payments 17 字段 + 关联表 + 迁移脚本 |

---

## 四、高级功能逐项核对（6/8）

| # | 功能 | V3 | V4 | 说明 |
|---|------|-----|-----|------|
| 1 | 批量支付 | ✅ | ✅ | POST /api/payments/batch |
| 2 | 支付调度 | ❌ | ❌ | 未实现 |
| 3 | 通知 | ⚠️ | ⚠️ | notification_log 表仍空壳，无代码写入 |
| 4 | 报告/分析 | ✅ | ✅ | ReportsController 4 端点 + ReportsView ECharts |
| 5 | 并发处理 | ✅ | 🔴 | Payment 乐观锁✅，**Account 余额更新无锁**（见 P0-1） |
| 6 | 支付撤销/取消 | ✅ | ✅ | cancelPayment + reversePayment（含汇率反向） |
| 7 | 多货币支持 | ⚠️ | ✅ | ExchangeRateService.quote() + exchange_rates 表 + 锁定汇率 + settlementAmount 入账 |
| 8 | 审计日志 | ⚠️ | ✅ | status_history + triggered_by 字段（本轮修复） |

---

## 五、并发分析（重点）

### ✅ 已正确处理的并发场景

**场景 1：两个用户同时更新同一 Payment（乐观锁）**
- Payment 有 `@Version` + OptimisticLockerInnerInterceptor
- User A updateById → `WHERE id=? AND version=0` → affected=1 → version=1
- User B updateById → `WHERE id=? AND version=0` → affected=0 → OptimisticLockingFailureException → **HTTP 409 CONFLICT**
- **结果正确**：只有一个成功，另一个收到 409 + "please retry"

**场景 2：两个用户用相同 Idempotency-Key 创建支付**
- 都 SELECT 幂等键 → 都看到 null → 都 insert payment → 都 checkAndSave
- 第一个 insert 幂等键成功，第二个 DuplicateKeyException → 抛 DUPLICATE_PAYMENT → @Transactional 回滚 payment insert
- **结果正确**：只有一个 payment 存在，第二个收到 409

**场景 3：两个用户同时 validate 同一 Payment**
- 都 selectById（version=N）→ 都通过状态机 → 都 updatePaymentStatus
- updateById → 第一个成功 version+1，第二个乐观锁失败 → 409
- **结果正确**

### 🔴 存在问题的并发场景

**场景 4（P0）：两个并发 COMPLETE 操作不同 Payment 但同一源账户**

这是最严重的并发漏洞。例如：Payment1 和 Payment2 都从 ACC-00007（余额 200）扣款：

```
时间线：
  T1: Complete(Payment1, amount=150)
      → accountService.updateBalance("ACC-00007", -150)
      → SELECT balance=200  (事务 A 快照)
      → 计算 newBalance = 200-150 = 50
      → UPDATE accounts SET balance=50 WHERE account_number='ACC-00007'
      → (行锁持有中)

  T2: Complete(Payment2, amount=180)  (并发)
      → accountService.updateBalance("ACC-00007", -180)
      → SELECT balance=200  (事务 B 快照 — REPEATABLE READ 看到旧值)
      → 计算 newBalance = 200-180 = 20
      → UPDATE accounts SET balance=20 WHERE account_number='ACC-00007'
      → (被事务 A 的行锁阻塞)

  T3: 事务 A 提交 → 事务 B 的 UPDATE 执行
      → balance 被设为 20（覆盖了 A 的 50）

  最终结果：balance=20，但实际应该扣 150+180=330（超支 130）
  → 资金错误！余额 20 而非 -130（应拒绝第二笔）
```

**根因**：
1. Account 实体**无 @Version**，accounts 表**无 version 列**
2. `AccountService.updateBalance()` 是 **SELECT-then-SET**（绝对值覆盖），非原子增量更新
3. MyBatis-Plus `updateById` 生成 `SET balance=?`（用内存计算的值），不是 `SET balance=balance-?`

**修复方案（三选一，推荐方案 B）**：
- **方案 A（乐观锁）**：Account 加 @Version + accounts 加 version 列 + OptimisticLockerInnerInterceptor 保护。但需要前端/重试处理 409。
- **方案 B（原子 SQL，推荐）**：AccountMapper 加自定义方法：
  ```sql
  UPDATE accounts SET balance = balance + #{delta}, updated_at = NOW()
  WHERE account_number = #{accountNumber} AND balance + #{delta} >= 0
  ```
  返回 affected rows = 0 时抛 INSUFFICIENT_FUNDS。完全避免读-改-写竞态。
- **方案 C（悲观锁）**：updateBalance 用 `SELECT ... FOR UPDATE` 锁行。简单但降低并发吞吐。

**场景 5（P0 残留）：RiskAssessmentService.assess() 无 try-catch**

`processValidate` 第 239 行：
```java
RiskAssessmentService.RiskAssessmentResult riskResult = riskAssessmentService.assess(payment);
```

assess() 内部 Layer 1/2 有 3 次 DB 操作（accountStatsMapper.selectById、paymentMapper.selectCount、riskAssessmentMapper.insert）。虽然 Layer 3 的 PaymentRiskAgent 有 try-catch（第 108-134 行），但 Layer 1/2 的 DB 操作**没有保护**。如果 DB 抖动：
- assess() 抛异常 → processValidate 整个事务回滚 → 支付卡在 CREATED → 用户收到 500 PROCESSING_ERROR
- 风控本应是增强，现在成了单点故障

**场景 6（P0 残留）：cancelPayment 绕过状态机**

```java
// PaymentServiceImpl.cancelPayment() — 仍直接 set 状态，未走 canTransition()
updatePaymentStatus(payment, "CANCELLED", null);
```

StateMachineService 的 VALID_TRANSITIONS 中**没有定义任何 → CANCELLED 的转换**（CREATED/VALIDATED/SENT/FAILED → CANCELLED 均不在 Map 中）。导致：
- FAILED 状态的支付也能被 cancel（业务上 FAILED 应走 retry 或放弃）
- 状态机定义与实际控制不一致

---

## 六、问题优先级汇总

| 级别 | 问题 | 影响 | 修复建议 |
|------|------|------|----------|
| **P0-1** | Account 余额更新无锁（并发覆盖） | 高并发下资金错误、超支 | AccountMapper 加原子 SQL `SET balance=balance+? WHERE balance+?>=0` |
| **P0-2** | RiskAssessmentService.assess() 无 try-catch | 风控 DB 故障→主流程 500 | processValidate 用 try-catch 包裹 assess()，失败时保守放行或维持 CREATED |
| **P0-3** | cancelPayment/reversePayment 绕过状态机 | 状态机形同虚设，FAILED 可被 cancel | 状态机加 CREATED/VALIDATED/SENT → CANCELLED 转换 + cancelPayment 调 canTransition() |
| **P1-1** | N+1 风控查询 | 列表页每页多 20 次查询 | listPayments 批量查询 risk_assessments（WHERE payment_id IN (...)）|
| **P1-2** | 测试缺并发和风控测试 | 规范测试 #5 未覆盖 | 加 CountDownLatch 并发测试 + 5 条规则单元测试 |
| **P2-1** | 通知功能空壳 | notification_log 表无用 | 状态变更时写通知记录，或删除表避免误导 |
| **P2-2** | 支付调度未实现 | 高级功能 #2 缺失 | 如需要，加 payment_schedules 表 + @Scheduled 定时任务 |

---

## 七、结论

本轮更改成效显著：**跨币种问题彻底修复**（汇率锁定 + settlementAmount 入账）、**审计 triggered_by 字段补全**、**Layer 3 AI Agent 落地**（零依赖 RestTemplate + 异常保护 + 只升级不降级）、**新增账户密码安全和收款人姓氏校验**（防范未授权转账和转错账）。前端风控展示也已在详情页实现。

但 **3 个 P0 问题仍存在**，其中**账户余额并发覆盖是本轮最严重的新发现**：AccountService.updateBalance() 的 SELECT-then-SET 模式在高并发下会导致余额被覆盖、资金超支。这是支付系统的核心安全问题，建议优先用**原子 SQL**（`SET balance=balance+? WHERE balance+?>=0`）修复。

**并发安全总结**：
- ✅ Payment 更新有乐观锁保护（@Version + 409）
- ✅ 幂等创建有 DB UNIQUE 约束保护
- 🔴 **Account 余额更新无任何锁保护** — 同一账户并发扣款会导致余额错误
- ⚠️ 风控调用无异常保护 — 风控故障会拖垮主流程
