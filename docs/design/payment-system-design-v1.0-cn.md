# 支付处理系统 — 系统设计文档 v1.0

**日期:** 2026-07-26
**版本:** 1.0
**状态:** 已批准

---

## 目录

1. [系统概述](#1-系统概述)
2. [架构设计](#2-架构设计)
3. [支付状态机](#3-支付状态机)
4. [数据模型](#4-数据模型)
5. [REST API 设计](#5-rest-api-设计)
6. [边界条件与约束](#6-边界条件与约束)
7. [失败模式与备选方案](#7-失败模式与备选方案)
8. [幂等性设计](#8-幂等性设计)
9. [错误处理框架](#9-错误处理框架)
10. [AI 风险评估（第四阶段）](#10-ai-风险评估第四阶段)
11. [可观测性与监控](#11-可观测性与监控)
12. [安全考虑](#12-安全考虑)
13. [开发阶段](#13-开发阶段)
14. [参考资料](#14-参考资料)

---

## 1. 系统概述

### 1.1 我们在构建什么

一个全栈支付处理系统，管理金融支付的完整生命周期——从创建、验证、发送到最终结算（或失败）。系统强制执行严格的状态机规则，通过幂等性提供「至少一次」处理保证，维护完整的只追加审计追踪，并集成了可选的 AI 异常检测引擎。

### 1.2 核心设计原则

| 原则 | 实现方式 |
|------|---------|
| **正确性优先于吞吐量** | 每次状态转换都经过校验；无效状态无法写入数据库 |
| **一次性语义** | 客户端提供的幂等键防止重复创建支付 |
| **只追加审计** | `status_history` 表只写一次，永不更新或删除 |
| **快速失败校验** | 无效请求在控制器层即被拒绝，不进入业务逻辑 |
| **事务完整性** | 状态更新 + 历史记录在同一事务中原子性提交 |
| **纵深防御** | 三层校验：DTO 注解 → 业务规则 → 数据库约束 |

### 1.3 技术栈

| 层级 | 技术 | 选型理由 |
|------|------|---------|
| 后端框架 | Spring Boot 3.2 | 成熟生态，声明式事务，校验支持 |
| 开发语言 | Java 17 | LTS 长期支持版本 |
| 数据库 | MySQL 8.0 | ACID 保证，行级锁 |
| ORM | MyBatis-Plus 3.5 | 分页插件，Lambda 查询包装器 |
| 前端 | Vue 3 + Vite + Element Plus | 组合式 API，快速开发 |
| 构建工具（后端） | Maven 3.8+ | 依赖管理，插件生态 |
| API 文档 | SpringDoc OpenAPI 3 | 自动生成 Swagger UI |
| 测试 | JUnit 5 + Mockito + H2 | 单元测试 + 集成测试 |

---

## 2. 架构设计

### 2.1 逻辑架构（分层设计）

```
┌──────────────────────────────────────────────────────────────┐
│                      表示层                                  │
│  ┌─────────────────────┐  ┌──────────────────────────────┐  │
│  │   Vue 3 SPA         │  │   Swagger UI                 │  │
│  │   (浏览器)           │  │   (http://host:8080/swagger) │  │
│  └──────────┬───────────┘  └──────────────┬───────────────┘  │
│             │                             │                   │
│             └──────────┬──────────────────┘                   │
│                        │ HTTP/REST                            │
├────────────────────────┼──────────────────────────────────────┤
│                控制器层                                       │
│  ┌─────────────────────┐  ┌──────────────────────────────┐   │
│  │ PaymentController   │  │ PaymentProcessController     │   │
│  │ (增删改查 + 查询)    │  │ (状态流转操作)               │   │
│  └──────────┬───────────┘  └──────────────┬───────────────┘   │
│             │                             │                    │
│             └──────────┬──────────────────┘                    │
│                        │                                      │
├────────────────────────┼──────────────────────────────────────┤
│                服务层                                          │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              PaymentServiceImpl                      │    │
│  │  (编排：校验 → 幂等 → 状态转换 → 审计记录 → 响应)     │    │
│  └───┬──────────┬────────────┬─────────────┬───────────┘    │
│      │          │            │             │                  │
│  ┌───▼──┐ ┌────▼────┐ ┌────▼──────┐ ┌───▼──────────┐        │
│  │状态机 │ │幂等服务 │ │校验服务   │ │AI 风控引擎  │        │
│  │服务   │ │         │ │           │ │(第四阶段)    │        │
│  └──────┘ └─────────┘ └───────────┘ └──────────────┘        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                数据访问层                                     │
│  ┌──────────────┐ ┌───────────────────┐ ┌────────────────┐   │
│  │PaymentMapper │ │StatusHistoryMapper│ │IdempotencyMapper│  │
│  └──────┬───────┘ └────────┬──────────┘ └───────┬────────┘   │
│         │                  │                    │             │
├─────────┼──────────────────┼────────────────────┼─────────────┤
│         └──────────────────┼────────────────────┘             │
│                    数据库层                                   │
│  ┌───────────────────────────────────────────────────────┐   │
│  │                   MySQL 8.0                           │   │
│  │  ┌──────────┐  ┌───────────────┐  ┌────────────────┐  │   │
│  │  │ payments │  │status_history │  │idempotency_keys│  │   │
│  │  └──────────┘  └───────────────┘  └────────────────┘  │   │
│  └───────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 请求流程：创建支付

```
客户端              控制器                 服务层                 数据库
  │                    │                      │                    │
  │ POST /api/payments │                      │                    │
  │ Idempotency-Key:X  │                      │                    │
  │───────────────────→│                      │                    │
  │                    │ createPayment(req,X) │                    │
  │                    │─────────────────────→│                    │
  │                    │                      │ 1. 查找幂等键       │
  │                    │                      │───────────────────→│
  │                    │                      │←───────────────────│
  │                    │                      │   (null = 新键)    │
  │                    │                      │                    │
  │                    │                      │ 2. 业务校验         │
  │                    │                      │   (账户、币种)     │
  │                    │                      │                    │
  │                    │                      │ 3. INSERT payment  │
  │                    │                      │───────────────────→│
  │                    │                      │←───────────────────│
  │                    │                      │                    │
  │                    │                      │ 4. INSERT 幂等记录  │
  │                    │                      │───────────────────→│
  │                    │                      │←── (或唯一键冲突)  │
  │                    │                      │                    │
  │                    │                      │ 5. INSERT 状态历史  │
  │                    │                      │───────────────────→│
  │                    │                      │←───────────────────│
  │                    │                      │                    │
  │                    │                      │  {全部在一个事务中} │
  │                    │←─────────────────────│                    │
  │←── 201 Created ───│                      │                    │
```

### 2.3 事务边界

每个状态变更操作都在 Spring `@Transactional` 边界内运行：

```java
@Transactional  // <-- 整个方法是一个 ACID 单元
public PaymentResponse processValidate(String paymentId) {
    Payment payment = findPaymentById(paymentId);   // SELECT（事务内）
    validateTransition(from, to);                    // 内存校验
    updatePaymentStatus(payment, newStatus, null);  // UPDATE payments
    recordStatusHistory(...);                        // INSERT status_history
    // 如果任何一步写入失败 → 全部回滚
}
```

**为什么这很重要：** 状态转换必须是原子的。如果 `payments` 表更新成功但 `status_history` 插入失败，就会出现不一致——数据库声称支付已 VALIDATED，但没有何时、为何的记录。事务边界防止了这种情况。

---

## 3. 支付状态机

### 3.1 状态定义

| 状态 | 含义 | 允许停留时长 | 是否为终态 |
|------|------|-------------|-----------|
| `CREATED` | 支付已提交，等待验证 | 不限 | 否 |
| `VALIDATED` | 所有业务规则通过，等待发送 | 不限 | 否 |
| `SENT` | 已发送至目标系统 | 不限 | 否 |
| `COMPLETED` | 已成功处理并确认 | 永久 | **是** |
| `FAILED` | 在某个阶段失败，带有错误码 | 不限（可重试） | 否 |

### 3.2 状态流转图

```
┌─────────┐   validate    ┌───────────┐    send     ┌─────────┐  complete  ┌───────────┐
│ CREATED │──────────────→│ VALIDATED │────────────→│  SENT   │───────────→│ COMPLETED │
└────┬─────┘               └─────┬─────┘             └───┬─────┘            └───────────┘
     │ fail                     │ fail                  │ fail                   ▲
     │                          │                       │                        │
     ▼                          ▼                       ▼                     终态
┌─────────┐              ┌──────────────────────────────────┐
│ FAILED  │───retry─────→│ 回到 VALIDATED（需要新幂等键）   │
└─────────┘              └──────────────────────────────────┘
```

### 3.3 合法状态转换矩阵

| 从 \ 到 | CREATED | VALIDATED | SENT | COMPLETED | FAILED |
|---------|---------|-----------|------|-----------|--------|
| **CREATED** | — | ✅ | ❌ | ❌ | ✅ |
| **VALIDATED** | ❌ | — | ✅ | ❌ | ✅ |
| **SENT** | ❌ | ❌ | — | ✅ | ✅ |
| **COMPLETED** | ❌ | ❌ | ❌ | — | ❌ |
| **FAILED** | ❌ | ✅ (重试) | ❌ | ❌ | — |

### 3.4 关键设计决策

1. **COMPLETED 是不可变的。** 一旦支付达到 COMPLETED，不允许任何后续状态变更。由 `StateMachineService` 层面强制执行——`COMPLETED` 映射到空的 `Set<PaymentStatus>`。

2. **FAILED 可重试，但只能回到 VALIDATED。** 重试需要创建新的幂等键并重新进入校验流程。这确保了重试的支付与新建支付一样经过同样严格的校验（以及第四阶段的风险评估）。

3. **禁止跨步骤跳转。** CREATED → SENT 是非法的。支付必须逐步通过每个中间状态。这防止了未经校验的支付被意外处理。

4. **任何非终态都可以失败。** CREATED、VALIDATED 和 SENT 都可以转换到 FAILED。这反映了现实——失败可以发生在任何阶段。

### 3.5 状态机实现

```java
// StateMachineService.java
private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
    PaymentStatus.CREATED,   Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
    PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
    PaymentStatus.SENT,      Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
    PaymentStatus.COMPLETED, Set.of(),  // 终态 — 无出口
    PaymentStatus.FAILED,    Set.of(PaymentStatus.VALIDATED)  // 仅限重试
);
```

状态机是**不可变**的——定义为 static final Map，运行时永远不会被修改。这从根本上消除了由运行时配置变更引入的整类 bug。

---

## 4. 数据模型

### 4.1 ER 图

```
┌──────────────────────┐       ┌──────────────────────────┐
│      payments        │       │     status_history        │
├──────────────────────┤       ├──────────────────────────┤
│ id (PK, VARCHAR 36)  │──┐    │ id (PK, BIGINT 自增)     │
│ idempotency_key (UQ) │  │    │ payment_id (FK)          │──┘
│ source_account       │  ├───→│ from_status (可为空)     │
│ destination_account  │  │    │ to_status                │
│ amount (DECIMAL)     │  │    │ changed_at (TIMESTAMP)    │
│ currency (VARCHAR 3) │  │    │ reason (TEXT)             │
│ description (TEXT)   │  │    │ error_code                │
│ status (VARCHAR 20)  │  │    └──────────────────────────┘
│ error_code           │  │
│ created_at           │  │    ┌──────────────────────────┐
│ updated_at           │  │    │    idempotency_keys      │
└──────────────────────┘  │    ├──────────────────────────┤
                           │    │ key_record (PK, VARCHAR) │
                           └───→│ payment_id (FK)          │
                                │ created_at               │
                                └──────────────────────────┘
```

### 4.2 建表语句

#### payments

```sql
CREATE TABLE payments (
    id                  VARCHAR(36)   PRIMARY KEY,
    idempotency_key     VARCHAR(64)   NOT NULL,
    source_account      VARCHAR(50)   NOT NULL,
    destination_account VARCHAR(50)   NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    description         TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_currency (currency),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### status_history

```sql
CREATE TABLE status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  VARCHAR(36)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      TEXT,
    error_code  VARCHAR(50),
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### idempotency_keys

```sql
CREATE TABLE idempotency_keys (
    key_record  VARCHAR(64)  PRIMARY KEY,
    payment_id  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.3 数据模型设计决策

1. **VARCHAR(36) 作为支付 ID** — 使用 `UUID.randomUUID().toString()`，服务端生成。避免顺序 ID 泄露，也适合分布式环境。

2. **DECIMAL(15,2) 存储金额** — 绝不用浮点数存钱。DECIMAL(15,2) 支持高达 9,999,999,999,999.99 的金额，且精度准确。

3. **VARCHAR(3) 存储币种** — ISO 4217 币种代码始终是 3 个大写字母。

4. **status_history.from_status 可为空** — 当支付首次创建时，没有「从」状态。这在语义上是正确的：首次 CREATED 记录代表从无到有的创建。

5. **不使用软删除** — 支付从不删除。系统在数据层面是只追加的。

---

## 5. REST API 设计

### 5.1 端点总览

| 方法 | 路径 | 用途 | 是否需要幂等键 |
|------|------|------|---------------|
| `POST` | `/api/payments` | 创建支付 | **需要** (`Idempotency-Key` 请求头) |
| `GET` | `/api/payments` | 列表/搜索支付 | 否（只读） |
| `GET` | `/api/payments/{id}` | 获取支付详情 + 历史 | 否（只读） |
| `GET` | `/api/payments/{id}/history` | 仅获取状态历史 | 否（只读） |
| `POST` | `/api/payments/{id}/validate` | CREATED → VALIDATED | 否（按状态保证幂等） |
| `POST` | `/api/payments/{id}/send` | VALIDATED → SENT | 否（按状态保证幂等） |
| `POST` | `/api/payments/{id}/complete` | SENT → COMPLETED | 否（按状态保证幂等） |
| `POST` | `/api/payments/{id}/fail` | 标记为失败 | 否（按状态保证幂等） |
| `POST` | `/api/payments/{id}/retry` | FAILED → VALIDATED | **需要**（新幂等键） |

### 5.2 统一响应格式

**成功响应：**
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "total": 0
}
```

**错误响应：**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "金额必须大于 0",
    "details": { "field": "amount", "value": -100 }
  },
  "total": 0
}
```

### 5.3 HTTP 状态码映射

| 状态码 | 使用场景 |
|--------|---------|
| `201 Created` | 支付成功创建 |
| `200 OK` | 查询成功、状态转换成功、或幂等重复返回 |
| `400 Bad Request` | 校验失败、非法的状态转换 |
| `403 Forbidden` | 风险评估拦截（第四阶段） |
| `404 Not Found` | 支付 ID 不存在 |
| `409 Conflict` | 重复幂等键且请求体不同 |
| `500 Internal Server Error` | 意外处理错误 |
| `503 Service Unavailable` | 网络/下游故障 |

---

## 6. 边界条件与约束

### 6.1 输入边界

| 字段 | 约束 | 执行层 |
|------|------|--------|
| `Idempotency-Key` | 必填，最长 255 字符 | 控制器层（手动校验） |
| `sourceAccount` | 必填，最长 50 字符 | DTO（`@NotBlank`, `@Size`） |
| `destinationAccount` | 必填，最长 50 字符，且 ≠ 源账户 | DTO + ValidationService |
| `amount` | > 0，≤ 1,000,000，最多 2 位小数 | DTO（`@DecimalMin`, `@DecimalMax`, `@Digits`） |
| `currency` | 恰好 3 字符，必须是 USD/EUR/GBP/CNY | DTO + ValidationService |
| `description` | 可选，最长 500 字符 | DTO（`@Size`） |

### 6.2 系统边界

| 边界 | 取值 | 理由 |
|------|------|------|
| 单笔最大金额 | 1,000,000 | 防止误操作；可根据业务需要调整 |
| 支持币种 | USD, EUR, GBP, CNY | 培训范围；易于扩展 |
| 最大分页大小 | 100 | 默认 20；防止内存耗尽 |
| 幂等键保留时间 | 30 天 | 平衡存储成本与重试窗口；参考 Stripe v2 |
| 备注最大长度 | 500 字符 | 支付附言的实际限制 |
| 状态历史保留时间 | 永久 | 审计追踪必须持久 |

### 6.3 并发边界

| 场景 | 处理方式 |
|------|---------|
| 相同幂等键并发 POST | MySQL `key_record` 上的唯一约束 → 先写入者胜出；后者触发 `DuplicateKeyException` → 回滚 |
| 同一支付并发状态转换 | `@Transactional` + `updateById` 的行级锁 |
| 写入期间读取 | MySQL MVCC 提供一致性读快照 |

### 6.4 明确不支持的功能

- ❌ **认证/授权** — 按项目规范：单用户，无鉴权
- ❌ **真实支付网关集成** — 仅内部模拟
- ❌ **多租户** — 单逻辑部署
- ❌ **已完成支付的撤销/退款** — 终态设计
- ❌ **批量支付** — 每次请求一笔支付
- ❌ **预约/定期支付** — 仅支持即时处理

---

## 7. 失败模式与备选方案

本章节记录了支付生命周期中每个可能的故障点及其对应的恢复策略。这是设计文档中最关键的部分。

### 7.1 失败模式矩阵

| # | 阶段 | 故障 | 影响 | 备选方案 |
|---|------|------|------|---------|
| 1 | 创建 | 幂等键已存在 | 无 — 返回已有支付 | 返回已有支付（HTTP 200）。客户端透明获取相同结果 |
| 2 | 创建 | 校验失败（金额、币种等） | 支付未创建 | 返回 400 + 错误码 + 字段级详情。客户端修正后用新键重新提交 |
| 3 | 创建 | INSERT 时数据库不可用 | 支付未落库 | 事务回滚。客户端用相同幂等键重试（指数退避：1秒→2秒→4秒→8秒，最多 3 次） |
| 4 | 创建 | 幂等记录 INSERT 失败（并发重复） | 支付 INSERT 成功，幂等记录失败 | `@Transactional` 确保两者同时回滚。客户端用相同键重试 → 幂等检查未找到记录 → 创建新支付。**潜在风险：重试不巧可能重复** → 由唯一约束兜底 |
| 5 | 验证 | 非法转换（如从 COMPLETED 出发） | 状态未变更 | 返回 400 + `INVALID_STATUS_TRANSITION`。客户端检查当前状态后调整 |
| 6 | 验证 | 风控拦截（第四阶段） | 支付 → FAILED，错误码 `RISK_BLOCKED` | 返回 403。支付进入 FAILED 状态并记录错误码。人工审核后才能重试 |
| 7 | 发送 | 模拟发送失败 | 支付卡在 VALIDATED | 返回 500 + `PROCESSING_ERROR`。支付保持 VALIDATED，客户端可重试 `/send`。**替代方案：** 3 次发送失败后自动转为 FAILED（可配置） |
| 8 | 完成 | 模拟完成失败 | 支付卡在 SENT | 返回 500 + `PROCESSING_ERROR`。支付保持 SENT，客户端可重试 `/complete` |
| 9 | 失败 | 缺少错误码 | Fail 请求被拒绝 | 返回 400。Fail 操作必须提供错误码以保证审计完整性 |
| 10 | 重试 | 幂等键被复用 | 重试被拒绝 | 返回 409 `DUPLICATE_PAYMENT`。客户端每次重试必须生成新键 |
| 11 | 任意 | 事务中途数据库断连 | 不可能产生部分更新 | MySQL InnoDB 保证事务回滚。状态不变。客户端重试 |
| 12 | 任意 | 网络超时（客户端↔服务端） | 客户端不确定结果 | 客户端用相同幂等键重试。服务端幂等层处理去重 |

### 7.2 各阶段补偿动作

虽然我们的系统是单体架构（不是分布式微服务），但我们仍然为每个阶段建模了明确的补偿动作。这为将来系统拆分做好准备。

| 阶段 | 正向操作 | 补偿（当下游失败时） |
|------|---------|---------------------|
| CREATED → VALIDATED | 验证支付字段，执行风险评估 | 标记 FAILED 并记录错误码。无外部副作用需要撤销 |
| VALIDATED → SENT | 模拟发送 | 标记 FAILED，错误码 `PROCESSING_ERROR`。支付回到可重试状态 |
| SENT → COMPLETED | 确认结算 | 标记 FAILED，错误码 `NETWORK_ERROR`。SENT 是可恢复状态——客户端可重试 |
| FAILED → VALIDATED（重试） | 创建新幂等键，重新验证 | 重新验证失败则再次标记 FAILED 并记录新错误详情 |

### 7.3 重试策略

```
客户端重试策略
├── 最大尝试次数: 3
├── 退避算法: 指数退避 + 完全抖动
│   ├── 第 1 次: 立即
│   ├── 第 2 次: rand(0, 2000ms)
│   └── 第 3 次: rand(0, 4000ms)
├── 幂等键: 同一逻辑操作的所有重试复用相同键
├── 收到 4xx: 不重试（客户端错误，相同请求必然再次失败）
├── 收到 5xx: 重试（服务端错误，重试可能成功）
└── 超时: 重试（请求可能已处理，也可能未处理）
```

**关键规则：** 客户端重试时必须复用相同的幂等键。生成新键会使幂等性保障失效，可能导致重复支付。

### 7.4 服务端恢复：后台完成器（未来增强）

在生产系统中，应有一个后台进程扫描长时间停留在非终态的支付：

```
完成器进程（每 5 分钟执行一次）
├── 查找满足以下条件的支付：
│   ├── status IN ('VALIDATED', 'SENT')
│   └── updated_at < NOW() - 5 分钟
├── 对每个卡住的支付：
│   ├── 检查下游操作是否实际完成
│   ├── 若已完成 → 推进到下一状态
│   └── 若未完成 → 标记 FAILED，错误码 PROCESSING_TIMEOUT
```

此处记录为设计模式，供未来生产化时参考。培训范围内不实现。

---

## 8. 幂等性设计

### 8.1 问题本质

```
网络故障从客户端视角有三种不可区分的结果：

  客户端 ──POST──→ [网络云] ──→ 服务端
  
  结果 A: 请求从未到达服务端        → 重试安全
  结果 B: 服务端已处理，响应丢失     → 重试产生重复数据
  结果 C: 服务端处理中途崩溃         → 状态未知
```

没有幂等性，客户端无法区分 A 和 B。重试会产生真实的业务重复。

### 8.2 解决方案：幂等键模式

```
客户端每次逻辑操作选择一个唯一键。
服务端保证: 相同键 → 相同的可观测结果。

  键: "abc-123" + 请求体 → 服务端处理 → 存储: {键: "abc-123", 结果: {...}}
  键: "abc-123" + 请求体 → 服务端查询   → 返回已存储结果（不重新处理）
```

### 8.3 实现方式

**客户端侧：**
```javascript
const idempotencyKey = crypto.randomUUID();
const response = await axios.post('/api/payments', body, {
  headers: { 'Idempotency-Key': idempotencyKey }
});
```

**服务端侧（两阶段方案）：**

```
阶段 1 — 快速查询（读）:
  SELECT payment_id FROM idempotency_keys WHERE key_record = ?
  → 找到？返回已有支付（HTTP 200）。结束。
  → 未找到？进入阶段 2。

阶段 2 — 原子创建（写，在 @Transactional 内）:
  INSERT INTO payments (...)
  INSERT INTO idempotency_keys (key_record, payment_id)
  INSERT INTO status_history (...)
  → 如果 idempotency_keys 触发 DuplicateKeyException: 全部回滚
    → 这处理了两个相同键的请求同时通过阶段 1 的竞态条件。
```

### 8.4 幂等键生命周期

| 阶段 | 时长 | 行为 |
|------|------|------|
| 活跃 | 0–30 天 | 键返回已有支付 |
| 过期 | 30 天以上 | 键被删除（清理任务）；相同键的新请求将创建新支付 |

### 8.5 幂等性不覆盖的情况

- **不同键，相同逻辑支付。** 系统无法检测到 `key-A` 和 `key-B` 代表同一业务意图。客户端负责键管理。
- **相同键，不同请求体。** 当前未校验。未来增强：对请求体做哈希比对；不匹配则拒绝。
- **跨操作类型的键。** `create` 使用的键不能用于 `retry`。每种操作类型应使用独立的键空间。

---

## 9. 错误处理框架

### 9.1 错误分类

```
错误层级
├── 4xx — 客户端错误（需修正请求）
│   ├── 400 VALIDATION_FAILED         — 字段级校验失败
│   ├── 400 INVALID_AMOUNT            — 金额超出范围
│   ├── 400 INVALID_ACCOUNT           — 账户格式或相同账户校验失败
│   ├── 400 INVALID_CURRENCY          — 不支持的币种代码
│   ├── 400 INVALID_STATUS_TRANSITION — 状态机拒绝该转换
│   ├── 403 RISK_BLOCKED              — AI 风险评估阻止支付
│   ├── 404 PAYMENT_NOT_FOUND         — 支付 ID 不存在
│   └── 409 DUPLICATE_PAYMENT         — 幂等键已被使用
│
└── 5xx — 服务端错误（用相同键重试）
    ├── 500 PROCESSING_ERROR          — 意外内部错误
    └── 503 NETWORK_ERROR             — 模拟下游故障
```

### 9.2 异常处理流程

```
Controller
  │
  ├── DTO @Valid 校验失败
  │   └── MethodArgumentNotValidException
  │       └── GlobalExceptionHandler.handleValidationException()
  │           └── 400 + 字段级错误详情
  │
  ├── 业务逻辑抛出 BusinessException
  │   └── GlobalExceptionHandler.handleBusinessException()
  │       └── ErrorCode → HTTP 状态码映射 + 统一错误响应
  │
  └── 未预期的异常
      └── GlobalExceptionHandler.handleGenericException()
          └── 500 + PROCESSING_ERROR（生产环境脱敏处理）
```

---

## 10. AI 风险评估（第四阶段）

### 10.1 集成点

AI 风控引擎在 `CREATED → VALIDATED` 转换期间触发，位于基础字段校验通过之后、状态变更提交之前。

```
POST /api/payments/{id}/validate
  │
  ├── 1. 查找支付
  ├── 2. 校验状态转换 (CREATED → VALIDATED)
  ├── 3. AI 风险评估 ←── 集成点
  │     ├── 获取账户历史（近 90 天）
  │     ├── 第一层：规则引擎（5 条规则）
  │     ├── 第二层：统计异常检测
  │     ├── 第三层：LLM 推理（可选，仅高风险场景）
  │     └── 输出：riskScore (0-100) + 建议
  │
  ├── 4. 决策：
  │     ├── BLOCK  → 转到 FAILED (RISK_BLOCKED)
  │     ├── REVIEW → 转到 VALIDATED（标记人工审核）
  │     └── APPROVE → 转到 VALIDATED（正常流程）
  │
  └── 5. 记录 risk_assessment + status_history（原子操作）
```

### 10.2 规则引擎（第一层）

| 规则 | 触发条件 | 分值 |
|------|---------|------|
| AMOUNT_ANOMALY | 金额超过账户历史均值 5 倍 | 35 |
| UNUSUAL_TIME | 交易发生在 00:00–06:00 | 25 |
| NEW_PAYEE | 首次向该目标账户转账 | 30 |
| VELOCITY_SPIKE | 10 分钟内 ≥ 5 笔交易 | 40 |
| HIGH_AMOUNT | 单笔交易 > $50,000 | 25 |

### 10.3 统计检测（第二层）

- **Z 分数：** |z| > 2（显著）或 |z| > 3（极端）时触发
- **IQR：** amount > Q3 + 1.5×IQR 时触发
- 至少需要 10 笔历史交易才具备统计有效性

### 10.4 风险决策矩阵

| 风险分 | 等级 | 建议 | 动作 |
|--------|------|------|------|
| 0–39 | LOW | APPROVE | 正常转到 VALIDATED |
| 40–69 | MEDIUM | REVIEW | 转到 VALIDATED；标记人工审核 |
| 70–100 | HIGH | BLOCK | 转到 FAILED，错误码 `RISK_BLOCKED` |

---

## 11. 可观测性与监控

### 11.1 监控指标（生产就绪）

| 指标 | 信号 | 告警阈值 |
|------|------|---------|
| 支付创建速率 | 每分钟创建的支付数 | 偏离基线 > 50% |
| 失败率 | FAILED 支付 / 总支付数 | 5 分钟窗口内 > 10% |
| 平均处理时间 | 创建 → COMPLETED 耗时 | > 30 秒 (p50) |
| 幂等命中率 | 重复键 / 总键数 | > 5%（可能意味着客户端重试风暴） |
| 风控拦截率 | RISK_BLOCKED / 总 VALIDATED | > 20%（可能意味着规则配置不当） |
| 数据库连接池 | 活跃连接 / 空闲连接 | > 80% 利用率 |

### 11.2 日志标准

```
级别   使用场景
ERROR  意外异常、事务回滚、数据库故障
WARN   业务异常（校验失败、非法转换）、风控拦截
INFO   每次状态转换: "Payment {id} CREATED → VALIDATED"
DEBUG  完整请求/响应体（生产环境关闭）
```

---

## 12. 安全考虑

### 12.1 当前状态（培训项目）

- 无身份认证（符合项目规范）
- 无用户会话管理
- 无基于角色的访问控制

### 12.2 生产环境加固清单

部署到生产环境前需要完成以下事项：

- [ ] **认证：** 所有端点接入 OAuth 2.0 / JWT 认证
- [ ] **授权：** 支付归属权——用户只能访问自己的支付
- [ ] **输入净化：** 防止 SQL 注入（MyBatis-Plus 参数化查询已处理此问题）
- [ ] **限流：** 对 POST 端点实施按用户限流（防滥用）
- [ ] **HTTPS：** 所有通信启用 TLS 1.3
- [ ] **密钥管理：** 数据库凭据通过环境变量/密钥保险库管理，不写入 `application.yml`
- [ ] **审计日志：** 记录对所有支付数据的访问，满足合规要求
- [ ] **PCI DSS：** 对账户号做令牌化处理；绝不存储原始 PAN 号

### 12.3 数据敏感度分级

| 数据 | 分级 | 处理方式 |
|------|------|---------|
| 支付 ID | 内部 | 可以记录日志 |
| 账户号 | **敏感** | 日志中脱敏: `ACC-***-001` |
| 金额 + 币种 | 内部 | 可以记录日志 |
| 备注描述 | **潜在 PII** | 生产中不记录日志 |
| 错误码 | 内部 | 可以记录日志 |

---

## 13. 开发阶段

| 阶段 | 重点 | 主要交付物 | 状态 |
|------|------|-----------|------|
| **第一阶段** | 后端核心 | 项目骨架、数据库表结构、实体类、Mapper、状态机、幂等性、CRUD、校验、异常处理、Swagger | ✅ 已完成 |
| **第二阶段** | 验证测试 | 单元测试（状态机、服务层）、集成测试（完整生命周期、失败+重试）、手动冒烟测试 | 进行中 |
| **第三阶段** | 前端 | Vue 3 项目，创建/列表/详情页面，API 对接，状态时间线，操作按钮 | 待开始 |
| **第四阶段** | AI 异常检测 | 规则引擎（5 条规则）、统计检测（Z 分数 + IQR）、risk_assessments 表、LLM 推理（可选） | 待开始 |

---

## 14. 参考资料

### 14.1 业界最佳实践

1. **Stripe API — 幂等请求。** *"幂等键是客户端生成的唯一值，服务端用它来识别同一请求的后续重试。"* [docs.stripe.com/api/idempotent_requests](https://docs.stripe.com/api/idempotent_requests)

2. **Stripe — 幂等性与可靠性模式。** *"在发起任何外部状态变更之前，先提交本地状态。"* 核心概念：原子阶段、以 DAG 形式组织的恢复点、事务性分阶段作业投递。

3. **支付系统设计（系统设计手册）。** *"直接 UPDATE 余额而不是记录分录是致命错误。"* 核心概念：复式记账账本、Saga 编排、补偿事务。

4. **Saga 模式。** *"补偿是显式代码，而非隐式回滚。"* 当单体架构向微服务拆分时，用于跨支付阶段的分布式事务。

### 14.2 项目文档

- `payment_processing.md` — 原始培训需求
- `Dev_Guide_Payment_Processing_AI_Anomaly_Detection.md` — 开发指南（含代码示例）
- `docs/superpowers/specs/2026-07-24-payment-processing-system-design.md` — 原始设计规格
- `docs/superpowers/plans/2026-07-24-payment-processing-system-plan.md` — 实施计划

---

## 附录 A：术语表

| 术语 | 定义 |
|------|------|
| **幂等性** | 多个相同请求产生与单个请求相同结果的属性 |
| **幂等键** | 客户端生成的唯一值，通过 HTTP 请求头发送，用于支持安全重试 |
| **状态机** | 定义支付生命周期中合法状态和转换的形式化模型 |
| **Saga** | 使用本地事务序列 + 补偿动作处理分布式事务的模式 |
| **补偿** | Saga 中撤销之前已提交步骤的逆向操作 |
| **审计追踪** | 所有状态变更的只追加、按时间排序的记录 |
| **终态** | 不允许任何后续转换的状态（COMPLETED） |
| **指数退避** | 每次失败后将等待时间翻倍的重试策略 |
| **抖动** | 添加到重试延迟中的随机变化量，防止惊群效应 |

---

## 附录 B：速查表 — 所有错误码

| 错误码 | HTTP 状态码 | 含义 |
|--------|-----------|------|
| `VALIDATION_FAILED` | 400 | 字段校验失败 |
| `INSUFFICIENT_FUNDS` | 400 | 源账户余额不足 |
| `INVALID_ACCOUNT` | 400 | 账户号无效或源/目标账户相同 |
| `INVALID_CURRENCY` | 400 | 币种代码不支持 |
| `INVALID_AMOUNT` | 400 | 金额为零、负数或超出限制 |
| `DUPLICATE_PAYMENT` | 409 | 幂等键已被使用 |
| `INVALID_STATUS_TRANSITION` | 400 | 状态机拒绝该转换 |
| `PAYMENT_NOT_FOUND` | 404 | 支付 ID 不存在 |
| `PROCESSING_ERROR` | 500 | 意外内部错误 |
| `NETWORK_ERROR` | 503 | 下游通信失败 |
| `RISK_BLOCKED` | 403 | AI 风险评估拦截（第四阶段） |
