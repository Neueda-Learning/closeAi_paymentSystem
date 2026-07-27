# AI Agent 异常检测 — LangChain4j 开发文档（完整版）

> 项目：Payment Processing System (Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Vue 3)
> AI Agent 框架：**LangChain4j 1.0.0-beta3**（声明式 @AiService + @Tool）
> 风控挂载点：`PaymentServiceImpl.processValidate()` 第 209 行
> 版本：V2.0 (Definitive) | 日期：2026-07-27

---

## 目录

1. [系统架构总览](#一系统架构总览)
2. [LangChain4j 集成详解](#二langchain4j-集成详解)
3. [Step 1 — 数据库新增表](#三step-1--数据库新增表)
4. [Step 2 — 枚举 + 配置类](#四step-2--枚举--配置类)
5. [Step 3 — Layer 1 规则引擎](#五step-3--layer-1-规则引擎)
6. [Step 4 — Layer 2 统计检测](#六step-4--layer-2-统计检测)
7. [Step 5 — Layer 3 AI Agent (LangChain4j 核心)](#七step-5--layer-3-ai-agent-langchain4j-核心)
8. [Step 6 — Entity + Mapper](#八step-6--entity--mapper)
9. [Step 7 — 核心编排服务](#九step-7--核心编排服务)
10. [Step 8 — 集成到 processValidate](#十step-8--集成到-processvalidate)
11. [Step 9 — PaymentResponse 扩展 + API 端点](#十一step-9--paymentresponse-扩展--api-端点)
12. [Step 10 — 前端实现](#十二step-10--前端实现)
13. [开发排期](#十三开发排期)
14. [测试场景清单](#十四测试场景清单)
15. [LLM Provider 切换指南](#十五llm-provider-切换指南)
16. [完整新增/修改文件清单](#十六完整新增修改文件清单)
17. [启动与调试指南](#十七启动与调试指南)

---

## 一、系统架构总览

### 1.1 三层递进式风险评估架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Payment Request                          │
│                      POST /api/payments                         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PaymentServiceImpl                            │
│  processValidate(paymentId)                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. validateOnTransition(payment) → 合规校验              │  │
│  │  2. ★ riskAssessmentService.assess(payment) → 风控决策    │  │
│  │     ┌────────────────────────────────────────────────┐   │  │
│  │     │  Layer 1: Rule Engine (5 rules, <5ms)          │   │  │
│  │     │  → score < 30: APPROVE (80% 正常支付到此结束)   │   │  │
│  │     │  → score ≥ 60: BLOCK (直接 FAILED)              │   │  │
│  │     │  → score 30~59: 进入 Layer 2                   │   │  │
│  │     ├────────────────────────────────────────────────┤   │  │
│  │     │  Layer 2: Statistical Detection (<50ms)        │   │  │
│  │     │  → z-score / IQR / velocity 异常加分           │   │  │
│  │     │  → score ≥ 60: BLOCK                           │   │  │
│  │     │  → score 30~59: REVIEW → 进入 Layer 3          │   │  │
│  │     ├────────────────────────────────────────────────┤   │  │
│  │     │  Layer 3: AI Agent (LangChain4j, 2~5s)        │   │  │
│  │     │  → @AiService + @Tool 查询真实账户/交易数据     │   │  │
│  │     │  → 推理文本写入 risk_assessments.reasoning      │   │  │
│  │     │  → 维持 REVIEW 或升级为 BLOCK                  │   │  │
│  │     └────────────────────────────────────────────────┘   │  │
│  │  3. 根据 decision 更新支付状态:                           │  │
│  │     APPROVE → VALIDATED (正常)                           │  │
│  │     BLOCK   → FAILED (RISK_BLOCKED)                     │  │
│  │     REVIEW  → VALIDATED + riskLevel=REVIEW 标记         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| Layer 1 规则引擎 | Spring `List<RiskRule>` Strategy 链 + YAML 配置 | 5 条规则类自动注入，确定性硬规则，<5ms |
| Layer 2 统计检测 | Java 数学库 + DB 聚合查询 | z-score / IQR / velocity，<50ms |
| Layer 3 AI Agent | **LangChain4j 1.0.0-beta3** `@AiService` + `@Tool` | 声明式 AI Service，LLM 可调用后端 Java 方法获取真实数据 |
| LLM Provider | OpenAI / 阿里百炼 DashScope / Ollama 本地 | 通过 `langchain4j-open-ai-spring-boot-starter` 统一配置 |
| 数据存储 | MySQL `risk_assessments` + `account_stats` 两张新表 | 风险评估持久化 + 账户统计基线 |
| 前端 | Vue 3 + ECharts | RiskDashboardView + 详情页风险面板 |

### 1.3 关键设计决策

| 决策 | 说明 |
|------|------|
| **80/20 筛选** | Layer 1 毫秒级处理全部支付，80% 正常交易直接放行；只有 ~20% 可疑的才进 Layer 2/3 |
| **LangChain4j @Tool** | AI Agent 不是纯 LLM 推理——LLM 可通过 `@Tool` 注解的 Java 方法获取账户历史、交易频率等**真实数据**，基于真实数据做推理 |
| **三层可独立关闭** | `application.yml` 中 `risk.layer1/layer2/layer3.enabled` 可独立开关，Layer 3 默认关闭 |
| **零破坏性集成** | 不改变现有状态机语义、不新增 PaymentStatus、不修改已有 API 契约，只扩展 |
| **LLM 只升级不降级** | AI Agent 可将 REVIEW 升级为 BLOCK，但不能将 BLOCK 降级为 APPROVE |

### 1.4 为什么是三层而非一层

| 层 | 速度 | 准确率 | 可解释性 | 适用场景 |
|----|------|--------|---------|---------|
| Layer 1 规则引擎 | <5ms | 100% | 100% | 硬阈值拦截（金额 >1M、凌晨交易、自转账），确定性 |
| Layer 2 统计检测 | <50ms | ~85% | ~70% | 基于历史模式偏离（z-score >3σ、频率异常等） |
| Layer 3 AI Agent | 2~5s | ~90% | ~95% | 跨维度综合推理（"收款方最近 3 天被 5 个不同来源支付"），可解释 |

---

## 二、LangChain4j 集成详解

### 2.1 为什么选 LangChain4j

| 维度 | LangChain4j | 直接 HTTP Client 调 LLM |
|------|-------------|------------------------|
| **声明式 AI Service** | `@AiService` 接口自动实现，无需写实现类 | 手写 HTTP 请求 + JSON 解析 |
| **Tool Calling** | `@Tool` 注解自动注册，LLM 可调用 Java 方法 | 手写 function calling schema |
| **Chat Memory** | 内置 `ChatMemory` 管理 | 手动管理对话上下文 |
| **Prompt Template** | `@SystemMessage` / `@UserMessage` + `@V` 变量模板 | 手写 prompt 拼接 |
| **Spring Boot 集成** | 官方 starter 自动装配 | 手写 `@Configuration` |
| **多模型切换** | 改一行配置即可切换 OpenAI/DashScope/Ollama | 每换一个模型改一套代码 |
| **类型安全** | 返回强类型 Java 对象 | 返回 String 需手动解析 |

### 2.2 Maven 依赖（pom.xml 新增）

在现有 `<dependencies>` 中新增：

```xml
<!-- ===== LangChain4j Dependencies ===== -->

<!-- LangChain4j BOM (统一版本管理) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.0.0-beta3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- 现有依赖保持不变 ... -->

    <!-- LangChain4j OpenAI Spring Boot Starter (支持任何 OpenAI-compatible API) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    </dependency>

    <!-- LangChain4j Spring Boot Starter (@AiService + @Tool 自动发现) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

> **注意**：LangChain4j BOM 统一管理版本号，子依赖无需指定 `<version>`。如果需要阿里百炼 DashScope，可额外加 `langchain4j-community-dashscope-spring-boot-starter`。

### 2.3 LangChain4j Spring Boot 自动装配原理

```
┌─────────────────────────────────────────────────────┐
│  langchain4j-spring-boot-starter 启动流程            │
│                                                       │
│  1. 扫描所有 @AiService 接口                          │
│     → PaymentRiskAgent 接口                           │
│     → 自动创建动态代理实现类                           │
│     → 注册为 Spring Bean (按接口名首字母小写)         │
│                                                       │
│  2. 扫描所有 @Tool 注解方法                           │
│     → PaymentRiskTools 中的 6 个 @Tool 方法           │
│     → 自动注册为 LLM 可调用的 Function                │
│                                                       │
│  3. 注入 ChatLanguageModel                           │
│     → 由 langchain4j-open-ai-spring-boot-starter     │
│     → 提供，配置在 application.yml 中                 │
│                                                       │
│  结果: @Autowired PaymentRiskAgent 即可使用           │
│  无需写任何实现类！                                    │
└─────────────────────────────────────────────────────┘
```

### 2.4 application.yml 配置

```yaml
# ===== LangChain4j Configuration =====
langchain4j:
  open-ai:
    chat-model:
      # 默认 OpenAI; 切换阿里百炼只需改 base-url 和 api-key
      base-url: ${LLM_BASE_URL:https://api.openai.com/v1}
      api-key: ${LLM_API_KEY:sk-demo}
      model-name: ${LLM_MODEL_NAME:gpt-4o-mini}
      temperature: 0.1          # 低温度保证推理稳定性
      max-tokens: 1024          # 控制推理输出长度
      log-requests: true        # 开发阶段记录请求
      log-responses: true       # 开发阶段记录响应
      timeout: 60s              # 超时 60 秒 (Layer 3 只对少数可疑支付调用)

# ===== Risk Assessment Configuration =====
risk:
  layer1:
    enabled: true
    large-amount-block: 1000000       # 大额直接拦截阈值
    large-amount-warning: 100000      # 大额加分阈值
    night-time-start: 0               # 凌晨时段起始(小时)
    night-time-end: 5                 # 凌晨时段结束(小时)
    self-transfer-score: 50           # 自转账加分
    new-payee-score: 30               # 新收款人加分
    velocity-score: 35                # 高频加分
  layer2:
    enabled: true
    zscore-threshold: 3.0             # z-score 偏离阈值
    iqr-multiplier: 1.5              # IQR 乘数
    velocity-deviation: 5             # 速度偏离倍数
  layer3:
    enabled: false                    # 默认关闭，配置 API Key 后手动开启
    timeout-seconds: 30               # LLM 调用超时
    max-retries: 2                    # LLM 调用失败重试次数
  thresholds:
    block: 60                         # BLOCK 分数阈值
    review: 30                        # REVIEW 分数阈值
```

---

## 三、Step 1 — 数据库新增表

### 3.1 DDL — risk_assessments

```sql
CREATE TABLE IF NOT EXISTS risk_assessments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        VARCHAR(36)   NOT NULL,
    risk_score        INT           NOT NULL,       -- 0~100
    risk_level        VARCHAR(10)   NOT NULL,       -- LOW / MEDIUM / HIGH / CRITICAL
    risk_decision     VARCHAR(10)   NOT NULL,       -- APPROVE / REVIEW / BLOCK
    triggered_rules   TEXT,                          -- JSON: 规则引擎命中的规则列表 + 分数
    statistical_flags TEXT,                          -- JSON: 统计异常指标详情
    reasoning         TEXT,                          -- AI Agent 推理文本 (Layer 3 产出)
    llm_model_used    VARCHAR(50),                  -- 使用的 LLM 模型名称
    assessed_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_risk_payment_id ON risk_assessments (payment_id);
CREATE INDEX idx_risk_level ON risk_assessments (risk_level);
CREATE INDEX idx_risk_decision ON risk_assessments (risk_decision);
CREATE INDEX idx_risk_assessed_at ON risk_assessments (assessed_at);
```

### 3.2 DDL — account_stats (统计基线数据)

```sql
CREATE TABLE IF NOT EXISTS account_stats (
    account_number   VARCHAR(50)   PRIMARY KEY,
    avg_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 历史平均金额
    std_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 历史标准差
    median_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 中位数
    q1_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 25 分位
    q3_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,       -- 75 分位
    total_count      INT           NOT NULL DEFAULT 0,       -- 总交易笔数
    known_payees     TEXT,                                    -- JSON: 常见收款人列表
    last_updated     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stats_account FOREIGN KEY (account_number) REFERENCES accounts(account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 种子数据 — account_stats

为测试环境提供基线数据：

```sql
INSERT INTO account_stats (account_number, avg_amount, std_amount, median_amount,
    q1_amount, q3_amount, total_count, known_payees) VALUES
    ('ACC-00001', 50000.00, 30000.00, 45000.00, 20000.00, 75000.00, 120,
     '["ACC-00002","ACC-00003","ACC-00004"]'),
    ('ACC-00002', 20000.00, 15000.00, 18000.00, 8000.00, 30000.00, 85,
     '["ACC-00001","ACC-00005"]'),
    ('ACC-00003', 10000.00, 8000.00, 9000.00, 5000.00, 14000.00, 60,
     '["ACC-00001","ACC-00002"]'),
    ('ACC-00007', 500.00, 300.00, 450.00, 200.00, 700.00, 200,
     '["ACC-00001","ACC-00008"]'),
    ('ACC-00009', 50.00, 30.00, 45.00, 20.00, 70.00, 15,
     '["ACC-00001"]')
ON DUPLICATE KEY UPDATE avg_amount = VALUES(avg_amount);
```

### 3.4 schema-h2.sql 对应更新

在测试用 `schema-h2.sql` 中添加相同的两张表定义，去掉 MySQL 特有的 `ON UPDATE CURRENT_TIMESTAMP` 和 `ENGINE=InnoDB`：

```sql
-- H2 test schema: risk_assessments + account_stats
CREATE TABLE IF NOT EXISTS risk_assessments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        VARCHAR(36)   NOT NULL,
    risk_score        INT           NOT NULL,
    risk_level        VARCHAR(10)   NOT NULL,
    risk_decision     VARCHAR(10)   NOT NULL,
    triggered_rules   CLOB,
    statistical_flags CLOB,
    reasoning         CLOB,
    llm_model_used    VARCHAR(50),
    assessed_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_stats (
    account_number   VARCHAR(50)   PRIMARY KEY,
    avg_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    std_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    median_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    q1_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    q3_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_count      INT           NOT NULL DEFAULT 0,
    known_payees     CLOB,
    last_updated     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 四、Step 2 — 枚举 + 配置类

### 4.1 文件：`enums/RiskDecision.java`

```java
package com.hsbc.payment.enums;

public enum RiskDecision {
    APPROVE,   // 放行 (score < 30)
    REVIEW,    // 可疑但放行，标记需人工审核 (score 30~59)
    BLOCK      // 拦截，支付直接转 FAILED (score >= 60)

    ;

    /**
     * 从字符串解析，兼容大小写。与 PaymentStatus.fromString 保持一致的风格。
     */
    public static RiskDecision fromString(String value) {
        if (value == null) return null;
        try {
            return RiskDecision.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REVIEW;  // 解析失败时保守处理：默认 REVIEW
        }
    }
}
```

### 4.2 文件：`enums/RiskLevel.java`

```java
package com.hsbc.payment.enums;

public enum RiskLevel {
    LOW,       // score < 30
    MEDIUM,    // score 30~59
    HIGH,      // score 60~79
    CRITICAL   // score >= 80

    ;

    public static RiskLevel fromString(String value) {
        if (value == null) return null;
        try {
            return RiskLevel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}
```

### 4.3 文件：`config/RiskConfig.java`

```java
package com.hsbc.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "risk")
public class RiskConfig {
    private Layer1 layer1 = new Layer1();
    private Layer2 layer2 = new Layer2();
    private Layer3 layer3 = new Layer3();
    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Layer1 {
        private boolean enabled = true;
        private BigDecimal largeAmountBlock = new BigDecimal("1000000");
        private BigDecimal largeAmountWarning = new BigDecimal("100000");
        private int nightTimeStart = 0;
        private int nightTimeEnd = 5;
        private int selfTransferScore = 50;
        private int newPayeeScore = 30;
        private int velocityScore = 35;
    }

    @Data
    public static class Layer2 {
        private boolean enabled = true;
        private double zscoreThreshold = 3.0;
        private double iqrMultiplier = 1.5;
        private int velocityDeviation = 5;
    }

    @Data
    public static class Layer3 {
        private boolean enabled = false;
        private int timeoutSeconds = 30;
        private int maxRetries = 2;
    }

    @Data
    public static class Thresholds {
        private int block = 60;
        private int review = 30;
    }
}
```

---

## 五、Step 3 — Layer 1 规则引擎

### 5.1 文件：`service/risk/RiskRule.java`（接口）

```java
package com.hsbc.payment.service.risk;

import com.hsbc.payment.entity.Payment;

/**
 * 规则引擎规则接口。所有实现类标注 @Service 后，
 * Spring 自动收集为 List<RiskRule>，注入到 RiskAssessmentService。
 */
public interface RiskRule {
    /** 规则名称 */
    String ruleName();
    /** 评估风险分数增量，0 表示未触发 */
    int evaluate(Payment payment, RiskContext context);
    /** 触发原因描述，null 表示未触发 */
    String reason(Payment payment, RiskContext context);
}
```

### 5.2 文件：`service/risk/RiskContext.java`（上下文）

```java
package com.hsbc.payment.service.risk;

import com.hsbc.payment.entity.AccountStats;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 风险评估上下文——在三层间传递的共享数据。
 * 由 RiskAssessmentService.buildContext() 一次性构建，避免重复查询 DB。
 */
@Data
@Builder
public class RiskContext {
    private int transactionHour;              // 当前交易时间(小时)
    private BigDecimal accountAvgAmount;      // 账户历史平均金额
    private BigDecimal accountStdAmount;      // 账户历史标准差
    private BigDecimal accountMedian;         // 中位数
    private BigDecimal accountQ3;             // 75 分位
    private Set<String> knownPayees;          // 常见收款人集合
    private int recentTransactionCount;       // 最近 10 分钟交易数
    private AccountStats accountStats;        // 完整统计对象(可选)
}
```

### 5.3 五条规则实现

**文件：`service/risk/impl/LargeAmountRule.java`**

```java
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

    @Override
    public String ruleName() { return "LargeAmountRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountBlock()) >= 0) {
            return 100;  // 直接满分 → BLOCK
        }
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountWarning()) >= 0) {
            return 30;   // 加分 → 可能 REVIEW
        }
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountBlock()) >= 0) {
            return "Amount " + payment.getAmount() + " exceeds block threshold "
                   + riskConfig.getLayer1().getLargeAmountBlock();
        }
        if (payment.getAmount().compareTo(riskConfig.getLayer1().getLargeAmountWarning()) >= 0) {
            return "Amount " + payment.getAmount() + " exceeds warning threshold "
                   + riskConfig.getLayer1().getLargeAmountWarning();
        }
        return null;
    }
}
```

**文件：`service/risk/impl/NightTimeRule.java`**

```java
package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NightTimeRule implements RiskRule {

    private final RiskConfig riskConfig;

    @Override
    public String ruleName() { return "NightTimeRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        int start = riskConfig.getLayer1().getNightTimeStart();
        int end = riskConfig.getLayer1().getNightTimeEnd();
        if (context.getTransactionHour() >= start && context.getTransactionHour() < end) {
            return 25;
        }
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        int start = riskConfig.getLayer1().getNightTimeStart();
        int end = riskConfig.getLayer1().getNightTimeEnd();
        if (context.getTransactionHour() >= start && context.getTransactionHour() < end) {
            return "Transaction at " + context.getTransactionHour() + ":00 "
                   + "(night time window " + start + "-" + end + ")";
        }
        return null;
    }
}
```

**文件：`service/risk/impl/SelfTransferRule.java`**

```java
package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfTransferRule implements RiskRule {

    private final RiskConfig riskConfig;

    @Override
    public String ruleName() { return "SelfTransferRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (payment.getSourceAccount().equalsIgnoreCase(payment.getDestinationAccount())) {
            return riskConfig.getLayer1().getSelfTransferScore();
        }
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (payment.getSourceAccount().equalsIgnoreCase(payment.getDestinationAccount())) {
            return "Source and destination are the same account: " + payment.getSourceAccount();
        }
        return null;
    }
}
```

**文件：`service/risk/impl/NewPayeeRule.java`**

```java
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

    @Override
    public String ruleName() { return "NewPayeeRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        Set<String> knownPayees = context.getKnownPayees();
        if (knownPayees == null || knownPayees.isEmpty()) return 0;
        if (!knownPayees.contains(payment.getDestinationAccount())) {
            return riskConfig.getLayer1().getNewPayeeScore();
        }
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        Set<String> knownPayees = context.getKnownPayees();
        if (knownPayees != null && !knownPayees.isEmpty()
                && !knownPayees.contains(payment.getDestinationAccount())) {
            return "Destination " + payment.getDestinationAccount()
                   + " is not in known payees list";
        }
        return null;
    }
}
```

**文件：`service/risk/impl/VelocityRule.java`**

```java
package com.hsbc.payment.service.risk.impl;

import com.hsbc.payment.config.RiskConfig;
import com.hsbc.payment.entity.Payment;
import com.hsbc.payment.service.risk.RiskContext;
import com.hsbc.payment.service.risk.RiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VelocityRule implements RiskRule {

    private final RiskConfig riskConfig;

    @Override
    public String ruleName() { return "VelocityRule"; }

    @Override
    public int evaluate(Payment payment, RiskContext context) {
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation()) {
            return riskConfig.getLayer1().getVelocityScore();
        }
        return 0;
    }

    @Override
    public String reason(Payment payment, RiskContext context) {
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation()) {
            return "High velocity: " + context.getRecentTransactionCount()
                   + " transactions in last 10 minutes";
        }
        return null;
    }
}
```

### 5.4 规则列表总览

| # | 规则名 | 类名 | 触发条件 | 分数 | 对应决策 |
|---|--------|------|---------|------|---------|
| R1 | 大额交易 | `LargeAmountRule` | amount >= 1,000,000 | +100 | 直接 BLOCK |
|   | 大额警告 | | amount >= 100,000 | +30 | 可能 REVIEW |
| R2 | 异常时段 | `NightTimeRule` | hour ∈ [0, 5) | +25 | 可能 REVIEW |
| R3 | 自转账 | `SelfTransferRule` | source == destination | +50 | 高概率 BLOCK |
| R4 | 新收款人 | `NewPayeeRule` | dest 不在 knownPayees | +30 | 可能 REVIEW |
| R5 | 高频交易 | `VelocityRule` | 10min 内 >= 5 笔 | +35 | 可能 REVIEW |

---

## 六、Step 4 — Layer 2 统计检测

### 6.1 文件：`service/risk/StatisticalDetector.java`

```java
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

    /**
     * 对已通过 Layer 1 的支付做统计异常检测。
     * 返回额外加分和检测到的异常指标列表。
     */
    public StatisticalResult detect(Payment payment, RiskContext context) {
        if (!riskConfig.getLayer2().isEnabled()) {
            return StatisticalResult.noAnomaly();
        }

        // 基线数据不足时跳过（至少 5 笔历史才有统计意义）
        if (context.getAccountStats() == null
                || context.getAccountStats().getTotalCount() < 5) {
            log.info("Insufficient baseline data for account {}, skipping Layer 2",
                     payment.getSourceAccount());
            return StatisticalResult.insufficientData();
        }

        List<StatisticalFlag> flags = new ArrayList<>();
        int totalScore = 0;

        // ===== 1. Z-score 检测: 金额偏离均值 =====
        if (context.getAccountAvgAmount() != null && context.getAccountStdAmount() != null
                && context.getAccountStdAmount().compareTo(BigDecimal.ZERO) > 0) {
            double zscore = (payment.getAmount().doubleValue()
                             - context.getAccountAvgAmount().doubleValue())
                           / context.getAccountStdAmount().doubleValue();
            if (Math.abs(zscore) >= riskConfig.getLayer2().getZscoreThreshold()) {
                int score = (int) Math.min(Math.abs(zscore) * 8, 25);  // z=3→24, z=5→25(上限)
                totalScore += score;
                flags.add(new StatisticalFlag("ZSCORE_ANOMALY", zscore, score,
                        "Amount z-score " + String.format("%.2f", zscore)
                        + " exceeds threshold " + riskConfig.getLayer2().getZscoreThreshold()));
            }
        }

        // ===== 2. IQR 检测: 金额突破上界 =====
        if (context.getAccountQ3() != null && context.getAccountMedian() != null) {
            BigDecimal iqr = context.getAccountQ3().subtract(context.getAccountMedian());
            BigDecimal upperBound = context.getAccountQ3().add(
                    iqr.multiply(BigDecimal.valueOf(riskConfig.getLayer2().getIqrMultiplier())));
            if (payment.getAmount().compareTo(upperBound) > 0) {
                totalScore += 20;
                flags.add(new StatisticalFlag("IQR_ANOMALY", upperBound.doubleValue(), 20,
                        "Amount " + payment.getAmount() + " exceeds IQR upper bound "
                        + String.format("%.2f", upperBound.doubleValue())));
            }
        }

        // ===== 3. Velocity 检测: 短时间交易频率偏离 =====
        if (context.getRecentTransactionCount() >= riskConfig.getLayer2().getVelocityDeviation()) {
            totalScore += 25;
            flags.add(new StatisticalFlag("VELOCITY_ANOMALY",
                    context.getRecentTransactionCount(), 25,
                    "Velocity " + context.getRecentTransactionCount()
                    + "x in 10min exceeds "
                    + riskConfig.getLayer2().getVelocityDeviation() + "x baseline"));
        }

        return new StatisticalResult(totalScore, flags);
    }

    // --- Inner records (不可变数据类) ---

    public record StatisticalResult(int additionalScore, List<StatisticalFlag> flags) {
        public static StatisticalResult noAnomaly() { return new StatisticalResult(0, List.of()); }
        public static StatisticalResult insufficientData() { return new StatisticalResult(0, List.of()); }
    }

    public record StatisticalFlag(String type, double value, int score, String description) {}
}
```

### 6.2 三种统计方法对照

| 方法 | 检测什么 | 计算方式 | 异常阈值 | 分数上限 |
|------|---------|---------|---------|---------|
| z-score | 金额偏离均值 | `(amount - avg) / stdDev` | > 3.0 | 25 |
| IQR | 金额偏离中位数 | `amount > Q3 + 1.5 × IQR` | 超出上界 | 20 |
| velocity | 短时间密集交易 | 10min 交易数 >= 5x | 5x 基线 | 25 |

---

## 七、Step 5 — Layer 3 AI Agent (LangChain4j 核心)

### 7.1 文件：`service/risk/PaymentRiskTools.java`（@Tool 定义）

这是 LangChain4j @Tool 类——让 LLM 能**调用后端 Java 方法**获取真实数据。

```java
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
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j @Tool 类 — AI Agent 推理时可调用的后端数据查询方法。
 *
 * LLM 在推理过程中自动选择需要的 Tool 调用：
 * - 首先调用 getPaymentDetails 获取支付基本信息
 * - 然后调用 getAccountProfile / getAccountStatistics 获取账户背景
 * - 再调用 getRecentPayments / countRecentTransactions 获取交易频率
 * - 最后综合所有数据做出推理判断
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRiskTools {

    private final PaymentMapper paymentMapper;
    private final AccountMapper accountMapper;
    private final AccountStatsMapper accountStatsMapper;
    private final StatusHistoryMapper statusHistoryMapper;

    @Tool("Get the full details of a payment by its ID, including amount, currency, accounts, and status")
    public String getPaymentDetails(
            @P("The payment ID to look up") String paymentId
    ) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) return "Payment not found: " + paymentId;
        return String.format(
                "Payment[id=%s, amount=%s %s, from=%s, to=%s, status=%s, description=%s, createdAt=%s]",
                payment.getId(), payment.getAmount(), payment.getCurrency(),
                payment.getSourceAccount(), payment.getDestinationAccount(),
                payment.getStatus(), payment.getDescription(), payment.getCreatedAt());
    }

    @Tool("Get the account balance and profile information for a given account number")
    public String getAccountProfile(
            @P("The account number to look up, format: ACC-XXXXX") String accountNumber
    ) {
        Account account = accountMapper.selectById(accountNumber);
        if (account == null) return "Account not found: " + accountNumber;
        return String.format(
                "Account[number=%s, name=%s, balance=%s %s]",
                account.getAccountNumber(), account.getAccountName(),
                account.getBalance(), account.getCurrency());
    }

    @Tool("Get statistical baseline data for an account: average amount, standard deviation, median, known payees")
    public String getAccountStatistics(
            @P("The account number to get statistics for") String accountNumber
    ) {
        AccountStats stats = accountStatsMapper.selectById(accountNumber);
        if (stats == null) return "No statistics available for account: " + accountNumber;
        return String.format(
                "AccountStats[account=%s, avgAmount=%s, stdAmount=%s, median=%s, "
                + "Q3=%s, totalCount=%d, knownPayees=%s]",
                stats.getAccountNumber(), stats.getAvgAmount(), stats.getStdAmount(),
                stats.getMedianAmount(), stats.getQ3Amount(), stats.getTotalCount(),
                stats.getKnownPayees());
    }

    @Tool("Get the recent payment history (last N transactions) for a specific account as source")
    public String getRecentPayments(
            @P("The source account number") String sourceAccount,
            @P("Maximum number of transactions to return, default 10") int limit
    ) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getSourceAccount, sourceAccount)
               .orderByDesc(Payment::getCreatedAt)
               .last("LIMIT " + Math.min(limit, 20));
        List<Payment> payments = paymentMapper.selectList(wrapper);
        if (payments.isEmpty()) return "No recent payments for account: " + sourceAccount;
        return payments.stream()
                .map(p -> String.format("[%s] %s %s -> %s (%s)",
                        p.getCreatedAt(), p.getAmount(), p.getCurrency(),
                        p.getDestinationAccount(), p.getStatus()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Get the status change history for a payment, showing all transitions with timestamps")
    public String getPaymentStatusHistory(
            @P("The payment ID to get history for") String paymentId
    ) {
        List<StatusHistory> history = statusHistoryMapper.findByPaymentId(paymentId);
        if (history.isEmpty()) return "No status history for payment: " + paymentId;
        return history.stream()
                .map(h -> String.format("[%s] %s -> %s (reason: %s)",
                        h.getChangedAt(), h.getFromStatus(), h.getToStatus(),
                        h.getReason() != null ? h.getReason() : "N/A"))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Count how many payments a source account has made in the last N minutes")
    public int countRecentTransactions(
            @P("The source account number") String sourceAccount,
            @P("The time window in minutes, e.g. 10") int minutes
    ) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getSourceAccount, sourceAccount)
               .ge(Payment::getCreatedAt, cutoff);
        return Math.max(0, paymentMapper.selectCount(wrapper).intValue());
    }
}
```

> **关键原理**：`@Tool` 注解的方法会被 LangChain4j Spring Boot Starter 自动发现并注册为 LLM 可调用的 Function。LLM 在推理时看到 Tool 的描述，会自动决定是否调用、调用哪个。LLM 先调用 Tool 获取真实数据，再基于数据做推理——这是 LangChain4j Agent 的核心机制。

### 7.2 文件：`service/risk/PaymentRiskAgent.java`（@AiService 声明式接口）

```java
package com.hsbc.payment.service.risk;

import dev.langchain4j.service.AiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j 声明式 AI Service — 支付风险评估推理 Agent。
 *
 * ★ 无需写实现类！
 * langchain4j-spring-boot-starter 启动时会自动扫描 @AiService 接口，
 * 创建动态代理实现类，注入 ChatLanguageModel + @Tool，
 * 并注册为 Spring Bean。直接 @Autowired 注入即可调用。
 */
@AiService
@SystemMessage("""
You are a senior payment risk analyst at a major bank. Your role is to assess whether
a payment transaction is suspicious or potentially fraudulent.

You have access to tools that can retrieve real payment data, account profiles,
transaction history, and statistical baselines. USE THESE TOOLS to gather evidence
before making your assessment — never rely solely on your general knowledge.

Assessment process:
1. First, get the payment details using getPaymentDetails
2. Then, get the source account profile and statistics using getAccountProfile and getAccountStatistics
3. Check recent transaction history using getRecentPayments
4. Count recent transaction velocity using countRecentTransactions
5. Review the payment status history if needed using getPaymentStatusHistory

After gathering data, provide your assessment in this EXACT format:

DECISION: [REVIEW or BLOCK]
REASONING: [Your detailed analysis explaining why, referencing specific data points]
CONFIDENCE: [HIGH, MEDIUM, or LOW]
RECOMMENDED_ACTION: [What the human reviewer should do next]

Important rules:
- Only upgrade to BLOCK if you find strong evidence of fraud or money laundering
- If evidence is ambiguous, maintain REVIEW status
- Always explain your reasoning with specific data references
- Consider patterns like: smurfing (splitting large amounts), unusual timing,
  new payees with large amounts, velocity spikes, round-trip transfers
""")
public interface PaymentRiskAgent {

    @UserMessage("""
    Assess this payment for risk:

    Payment ID: {{paymentId}}
    Amount: {{amount}} {{currency}}
    Source Account: {{sourceAccount}}
    Destination Account: {{destinationAccount}}
    Transaction Time: {{transactionHour}}:00
    Description: {{description}}

    Layer 1 Rule Engine score: {{ruleScore}}
    Triggered rules: {{triggeredRules}}
    Layer 2 Statistical score: {{statScore}}
    Statistical anomalies: {{statFlags}}

    Please use the available tools to verify and enrich your analysis,
    then provide your final assessment.
    """)
    String assessPayment(
            @V("paymentId") String paymentId,
            @V("amount") String amount,
            @V("currency") String currency,
            @V("sourceAccount") String sourceAccount,
            @V("destinationAccount") String destinationAccount,
            @V("transactionHour") String transactionHour,
            @V("description") String description,
            @V("ruleScore") String ruleScore,
            @V("triggeredRules") String triggeredRules,
            @V("statScore") String statScore,
            @V("statFlags") String statFlags
    );
}
```

### 7.3 文件：`service/risk/AiAgentResultParser.java`（结果解析器）

LLM 返回纯文本，需解析为结构化决策：

```java
package com.hsbc.payment.service.risk;

import com.hsbc.payment.enums.RiskDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiAgentResultParser {

    private static final Pattern DECISION_PATTERN =
            Pattern.compile("DECISION:\\s*(REVIEW|BLOCK)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REASONING_PATTERN =
            Pattern.compile("REASONING:\\s*(.+?)(?=\\nCONFIDENCE:|\\nRECOMMENDED_ACTION:|$)",
                            Pattern.DOTALL);
    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("CONFIDENCE:\\s*(HIGH|MEDIUM|LOW)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("RECOMMENDED_ACTION:\\s*(.+?)$", Pattern.DOTALL);

    public AiAgentResult parse(String llmResponse) {
        RiskDecision decision = RiskDecision.REVIEW;  // 默认保守: REVIEW
        String reasoning = "";
        String confidence = "MEDIUM";
        String recommendedAction = "";

        Matcher dm = DECISION_PATTERN.matcher(llmResponse);
        if (dm.find()) {
            decision = RiskDecision.fromString(dm.group(1).toUpperCase());
        }

        Matcher rm = REASONING_PATTERN.matcher(llmResponse);
        if (rm.find()) {
            reasoning = rm.group(1).trim();
        }

        Matcher cm = CONFIDENCE_PATTERN.matcher(llmResponse);
        if (cm.find()) {
            confidence = cm.group(1).toUpperCase();
        }

        Matcher am = ACTION_PATTERN.matcher(llmResponse);
        if (am.find()) {
            recommendedAction = am.group(1).trim();
        }

        log.info("AI Agent parsed: decision={}, confidence={}, reasoningLength={}",
                 decision, confidence, reasoning.length());

        return new AiAgentResult(decision, reasoning, confidence, recommendedAction, llmResponse);
    }

    public record AiAgentResult(
            RiskDecision decision,
            String reasoning,
            String confidence,
            String recommendedAction,
            String rawResponse
    ) {}
}
```

---

## 八、Step 6 — Entity + Mapper

### 8.1 文件：`entity/RiskAssessment.java`

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_assessments")
public class RiskAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentId;
    private Integer riskScore;
    private String riskLevel;       // LOW / MEDIUM / HIGH / CRITICAL
    private String riskDecision;    // APPROVE / REVIEW / BLOCK
    private String triggeredRules;  // JSON: 规则引擎命中的规则
    private String statisticalFlags; // JSON: 统计异常指标
    private String reasoning;       // AI Agent 推理文本
    private String llmModelUsed;    // LLM 模型名称
    private LocalDateTime assessedAt;
}
```

### 8.2 文件：`entity/AccountStats.java`

```java
package com.hsbc.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("account_stats")
public class AccountStats {
    @TableId
    private String accountNumber;
    private BigDecimal avgAmount;
    private BigDecimal stdAmount;
    private BigDecimal medianAmount;
    private BigDecimal q1Amount;
    private BigDecimal q3Amount;
    private Integer totalCount;
    private String knownPayees;     // JSON 数格式: ["ACC-00002","ACC-00003"]
    private LocalDateTime lastUpdated;
}
```

### 8.3 文件：`mapper/RiskAssessmentMapper.java`

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.RiskAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskAssessmentMapper extends BaseMapper<RiskAssessment> {

    @Select("SELECT * FROM risk_assessments WHERE payment_id = #{paymentId} "
          + "ORDER BY assessed_at DESC LIMIT 1")
    RiskAssessment findLatestByPaymentId(String paymentId);

    @Select("SELECT * FROM risk_assessments WHERE payment_id = #{paymentId} "
          + "ORDER BY assessed_at DESC")
    List<RiskAssessment> findByPaymentId(String paymentId);

    @Select("SELECT * FROM risk_assessments WHERE risk_decision = 'BLOCK' "
          + "ORDER BY assessed_at DESC LIMIT #{limit}")
    List<RiskAssessment> findBlockedPayments(int limit);

    @Select("SELECT * FROM risk_assessments WHERE risk_decision = 'REVIEW' "
          + "ORDER BY assessed_at DESC LIMIT #{limit}")
    List<RiskAssessment> findReviewPayments(int limit);

    @Select("SELECT COUNT(*) FROM risk_assessments WHERE risk_decision = #{decision}")
    long countByDecision(String decision);

    @Select("SELECT COUNT(*) FROM risk_assessments "
          + "WHERE risk_decision = #{decision} AND assessed_at >= #{since}")
    long countByDecisionSince(String decision, LocalDateTime since);
}
```

> **注意**：`countByDecisionSince` 的 `since` 参数类型是 `java.time.LocalDateTime`，MyBatis 会自动转换为 SQL TIMESTAMP。

### 8.4 文件：`mapper/AccountStatsMapper.java`

```java
package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.AccountStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountStatsMapper extends BaseMapper<AccountStats> {
}
```

---

## 九、Step 7 — 核心编排服务

### 9.1 文件：`service/risk/RiskAssessmentService.java`

这是整个三层递进式风险评估的**核心编排类**，负责：
- 构建 `RiskContext`（一次性查询 DB）
- 依次执行 Layer 1 → Layer 2 → Layer 3
- 根据累计分数做决策
- 持久化 `risk_assessments` 记录

```java
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
import com.hsbc.payment.service.risk.StatisticalDetector.StatisticalResult;
import com.hsbc.payment.service.risk.impl.*;
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

    private final List<RiskRule> rules;              // Spring 自动注入所有 RiskRule 实现
    private final StatisticalDetector statisticalDetector;
    private final RiskConfig riskConfig;
    private final AccountStatsMapper accountStatsMapper;
    private final PaymentMapper paymentMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;

    // Layer 3 (可选，仅在配置启用时才可用)
    private final PaymentRiskAgent paymentRiskAgent;     // LangChain4j @AiService
    private final AiAgentResultParser aiResultParser;

    /**
     * 三层递进式风险评估核心方法。
     * 在 PaymentServiceImpl.processValidate() 第 209 行被调用。
     *
     * 执行顺序：
     *   Layer 1 → (score >= 60 则 BLOCK, 直接返回)
     *   Layer 2 → (累计 score >= 60 则 BLOCK)
     *   Layer 3 → (仅 REVIEW 状态触发，AI Agent 可升级为 BLOCK)
     */
    public RiskAssessmentResult assess(Payment payment) {
        RiskContext context = buildContext(payment);

        // ===== Layer 1: 规则引擎 =====
        int totalScore = 0;
        List<TriggeredRule> triggeredRules = new ArrayList<>();

        if (riskConfig.getLayer1().isEnabled()) {
            for (RiskRule rule : rules) {
                int score = rule.evaluate(payment, context);
                if (score > 0) {
                    totalScore += score;
                    triggeredRules.add(new TriggeredRule(
                            rule.ruleName(), score, rule.reason(payment, context)));
                }
            }
            log.info("Layer 1 result: totalScore={}, triggeredRules={}", totalScore, triggeredRules.size());
        }

        // Layer 1 快速路径：直接 BLOCK
        RiskDecision decision = determineDecision(totalScore);
        if (decision == RiskDecision.BLOCK) {
            return persistAndReturn(payment, totalScore, decision, triggeredRules,
                    StatisticalResult.noAnomaly(), null);
        }

        // ===== Layer 2: 统计检测 =====
        StatisticalResult statResult = StatisticalResult.noAnomaly();
        if (riskConfig.getLayer2().isEnabled()
                && (decision == RiskDecision.REVIEW
                    || totalScore >= riskConfig.getThresholds().getReview())) {
            statResult = statisticalDetector.detect(payment, context);
            totalScore += statResult.additionalScore();
            decision = determineDecision(totalScore);
            log.info("Layer 2 result: additionalScore={}, totalScore={}, decision={}",
                     statResult.additionalScore(), totalScore, decision);
        }

        // ===== Layer 3: AI Agent (仅 REVIEW 状态触发) =====
        AiAgentResultParser.AiAgentResult aiResult = null;
        if (decision == RiskDecision.REVIEW && riskConfig.getLayer3().isEnabled()) {
            try {
                String llmResponse = paymentRiskAgent.assessPayment(
                        payment.getId(),
                        payment.getAmount().toPlainString(),
                        payment.getCurrency(),
                        payment.getSourceAccount(),
                        payment.getDestinationAccount(),
                        String.valueOf(context.getTransactionHour()),
                        payment.getDescription() != null ? payment.getDescription() : "N/A",
                        String.valueOf(totalScore),
                        triggeredRules.stream()
                            .map(t -> t.ruleName + "(" + t.score + ")")
                            .collect(Collectors.joining(", ")),
                        String.valueOf(statResult.additionalScore()),
                        statResult.flags().stream()
                            .map(f -> f.type() + ": " + f.description())
                            .collect(Collectors.joining("; "))
                );
                aiResult = aiResultParser.parse(llmResponse);

                // AI Agent 只能升级 REVIEW→BLOCK，不能降级
                if (aiResult.decision() == RiskDecision.BLOCK) {
                    decision = RiskDecision.BLOCK;
                    totalScore = Math.max(totalScore, 80);  // BLOCK 至少 80 分
                }
                log.info("Layer 3 result: decision={}, confidence={}",
                         aiResult.decision(), aiResult.confidence());
            } catch (Exception e) {
                log.error("Layer 3 AI Agent failed, maintaining REVIEW: {}", e.getMessage());
                // 保守处理：LLM 调用失败时维持 REVIEW，不升级也不降级
            }
        }

        return persistAndReturn(payment, totalScore, decision, triggeredRules, statResult, aiResult);
    }

    // --- Private helpers ---

    private RiskDecision determineDecision(int score) {
        if (score >= riskConfig.getThresholds().getBlock()) return RiskDecision.BLOCK;
        if (score >= riskConfig.getThresholds().getReview()) return RiskDecision.REVIEW;
        return RiskDecision.APPROVE;
    }

    private RiskContext buildContext(Payment payment) {
        AccountStats stats = accountStatsMapper.selectById(payment.getSourceAccount());
        int hour = payment.getCreatedAt() != null
                   ? payment.getCreatedAt().getHour()
                   : LocalDateTime.now().getHour();

        // 统计最近 10 分钟该账户的交易数
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getSourceAccount, payment.getSourceAccount())
               .ge(Payment::getCreatedAt, cutoff);
        int recentCount = paymentMapper.selectCount(wrapper).intValue();

        Set<String> knownPayees = Set.of();
        BigDecimal avgAmount = BigDecimal.ZERO;
        BigDecimal stdAmount = BigDecimal.ZERO;
        BigDecimal median = BigDecimal.ZERO;
        BigDecimal q3 = BigDecimal.ZERO;

        if (stats != null) {
            avgAmount = stats.getAvgAmount();
            stdAmount = stats.getStdAmount();
            median = stats.getMedianAmount();
            q3 = stats.getQ3Amount();
            if (stats.getKnownPayees() != null && !stats.getKnownPayees().isEmpty()) {
                knownPayees = parseKnownPayees(stats.getKnownPayees());
            }
        }

        return RiskContext.builder()
                .transactionHour(hour)
                .accountAvgAmount(avgAmount)
                .accountStdAmount(stdAmount)
                .accountMedian(median)
                .accountQ3(q3)
                .knownPayees(knownPayees)
                .recentTransactionCount(recentCount)
                .accountStats(stats)
                .build();
    }

    private Set<String> parseKnownPayees(String json) {
        if (json == null || json.isBlank()) return Set.of();
        json = json.trim().replace("[", "").replace("]", "").replace("\"", "");
        if (json.isEmpty()) return Set.of();
        return Arrays.stream(json.split(","))
                      .map(String::trim)
                      .collect(Collectors.toSet());
    }

    private RiskAssessmentResult persistAndReturn(Payment payment, int score,
            RiskDecision decision, List<TriggeredRule> triggeredRules,
            StatisticalResult statResult, AiAgentResultParser.AiAgentResult aiResult) {

        RiskLevel level = mapRiskLevel(score);

        // 持久化到 risk_assessments 表
        RiskAssessment entity = new RiskAssessment();
        entity.setPaymentId(payment.getId());
        entity.setRiskScore(score);
        entity.setRiskLevel(level.name());
        entity.setRiskDecision(decision.name());
        entity.setTriggeredRules(toJson(triggeredRules));
        entity.setStatisticalFlags(toJson(statResult.flags()));
        entity.setReasoning(aiResult != null ? aiResult.reasoning() : null);
        entity.setLlmModelUsed(aiResult != null ? "LangChain4j-Agent" : null);
        riskAssessmentMapper.insert(entity);

        log.info("Risk assessment persisted: paymentId={}, score={}, level={}, decision={}",
                 payment.getId(), score, level, decision);

        return new RiskAssessmentResult(score, level, decision, triggeredRules, statResult, aiResult);
    }

    private RiskLevel mapRiskLevel(int score) {
        if (score >= 80) return RiskLevel.CRITICAL;
        if (score >= 60) return RiskLevel.HIGH;
        if (score >= 30) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private String toJson(List<?> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(items.get(i).toString());
        }
        sb.append("]");
        return sb.toString();
    }

    // --- Inner records (不可变结果类) ---

    public record TriggeredRule(String ruleName, int score, String reason) {}

    public record RiskAssessmentResult(
            int totalScore,
            RiskLevel riskLevel,
            RiskDecision riskDecision,
            List<TriggeredRule> triggeredRules,
            StatisticalResult statisticalResult,
            AiAgentResultParser.AiAgentResult aiAgentResult
    ) {}
}
```

---

## 十、Step 8 — 集成到 processValidate

### 10.1 修改 `PaymentServiceImpl.java` 第 209 行

**原代码（第 209~213 行）：**

```java
// ★ AI risk assessment hook (Phase 4: insert riskAssessmentService.assess(payment) here)

updatePaymentStatus(payment, toStatus.name(), null);
recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), null, null);
return getPayment(paymentId);
```

**替换为：**

```java
// ===== Risk Assessment (three-layer progressive) =====
RiskAssessmentService.RiskAssessmentResult riskResult = riskAssessmentService.assess(payment);

if (riskResult.riskDecision() == RiskDecision.BLOCK) {
    // BLOCK → 直接转 FAILED, error_code = RISK_BLOCKED
    updatePaymentStatus(payment, PaymentStatus.FAILED.name(), ErrorCode.RISK_BLOCKED.name());
    recordStatusHistory(paymentId, fromStatus.name(), PaymentStatus.FAILED.name(),
            "Risk BLOCKED: score=" + riskResult.totalScore()
            + ", level=" + riskResult.riskLevel()
            + ", rules=" + riskResult.triggeredRules().stream()
                .map(t -> t.ruleName() + "(" + t.score() + ")")
                .collect(Collectors.joining(", ")),
            ErrorCode.RISK_BLOCKED.name());
    return getPayment(paymentId);
}

// APPROVE 或 REVIEW → 转 VALIDATED
updatePaymentStatus(payment, toStatus.name(), null);

String riskNote = null;
if (riskResult.riskDecision() == RiskDecision.REVIEW) {
    riskNote = "Risk REVIEW: score=" + riskResult.totalScore()
             + ", level=" + riskResult.riskLevel()
             + ", awaiting manual review";
}
recordStatusHistory(paymentId, fromStatus.name(), toStatus.name(), riskNote, null);
```

### 10.2 在 `PaymentServiceImpl` 中新增依赖注入

```java
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    // 新增注入
    private final RiskAssessmentService riskAssessmentService;
    // ... 现有注入保持不变 (paymentMapper, statusHistoryMapper 等)
}
```

---

## 十一、Step 9 — PaymentResponse 扩展 + API 端点

### 11.1 修改 `PaymentResponse.java`

在现有字段之后新增 3 个风险相关字段：

```java
@Data
@Builder
public class PaymentResponse {
    // ... 现有字段保持不变 ...
    private String id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String errorCode;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StatusHistoryResponse> statusHistory;

    // ★ AI Risk Assessment 新增字段
    private Integer riskScore;      // 0~100
    private String riskLevel;       // LOW / MEDIUM / HIGH / CRITICAL
    private String riskDecision;    // APPROVE / REVIEW / BLOCK
}
```

### 11.2 修改 `PaymentServiceImpl.toPaymentResponse()`

```java
private PaymentResponse toPaymentResponse(Payment payment) {
    // 查询最新的风险评估记录
    RiskAssessment latestRisk = riskAssessmentMapper.findLatestByPaymentId(payment.getId());

    return PaymentResponse.builder()
            .id(payment.getId())
            .idempotencyKey(payment.getIdempotencyKey())
            .sourceAccount(payment.getSourceAccount())
            .destinationAccount(payment.getDestinationAccount())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .description(payment.getDescription())
            .status(payment.getStatus())
            .errorCode(payment.getErrorCode())
            .retryCount(payment.getRetryCount())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            // ★ 风险评估字段
            .riskScore(latestRisk != null ? latestRisk.getRiskScore() : null)
            .riskLevel(latestRisk != null ? latestRisk.getRiskLevel() : null)
            .riskDecision(latestRisk != null ? latestRisk.getRiskDecision() : null)
            .build();
}
```

> **注意**：需要在 `PaymentServiceImpl` 中注入 `RiskAssessmentMapper`。

### 11.3 新增文件：`controller/RiskController.java`

```java
package com.hsbc.payment.controller;

import com.hsbc.payment.dto.response.ApiResponse;
import com.hsbc.payment.entity.RiskAssessment;
import com.hsbc.payment.mapper.RiskAssessmentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Assessment", description = "AI-driven risk assessment and monitoring")
public class RiskController {

    private final RiskAssessmentMapper riskAssessmentMapper;

    @GetMapping("/assessments/{paymentId}")
    @Operation(summary = "Get risk assessment details for a payment")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getPaymentRiskAssessment(
            @PathVariable String paymentId) {
        List<RiskAssessment> assessments = riskAssessmentMapper.findByPaymentId(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(assessments));
    }

    @GetMapping("/blocked")
    @Operation(summary = "List all BLOCKED payments (highest risk)")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getBlockedPayments(
            @RequestParam(defaultValue = "20") int limit) {
        List<RiskAssessment> blocked = riskAssessmentMapper.findBlockedPayments(limit);
        return ResponseEntity.ok(ApiResponse.ok(blocked));
    }

    @GetMapping("/review")
    @Operation(summary = "List all REVIEW payments (needs human review)")
    public ResponseEntity<ApiResponse<List<RiskAssessment>>> getReviewPayments(
            @RequestParam(defaultValue = "20") int limit) {
        List<RiskAssessment> review = riskAssessmentMapper.findReviewPayments(limit);
        return ResponseEntity.ok(ApiResponse.ok(review));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get risk assessment statistics summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRiskStats() {
        long totalBlocked = riskAssessmentMapper.countByDecision("BLOCK");
        long totalReview = riskAssessmentMapper.countByDecision("REVIEW");
        long totalApprove = riskAssessmentMapper.countByDecision("APPROVE");

        // 今日新增拦截数
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayBlocked = riskAssessmentMapper.countByDecisionSince("BLOCK", todayStart);

        long total = totalBlocked + totalReview + totalApprove;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssessments", total);
        stats.put("blockedCount", totalBlocked);
        stats.put("reviewCount", totalReview);
        stats.put("approveCount", totalApprove);
        stats.put("todayBlocked", todayBlocked);
        stats.put("blockRate", total > 0 ? String.format("%.1f%%", 100.0 * totalBlocked / total) : "N/A");
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
```

---

## 十二、Step 10 — 前端实现

### 12.1 新增文件：`frontend/src/api/risk.js`

```javascript
import api from './index'

export function getPaymentRiskAssessment(paymentId) {
  return api.get(`/risk/assessments/${paymentId}`)
}

export function getBlockedPayments(limit = 20) {
  return api.get('/risk/blocked', { params: { limit } })
}

export function getReviewPayments(limit = 20) {
  return api.get('/risk/review', { params: { limit } })
}

export function getRiskStats() {
  return api.get('/risk/stats')
}
```

### 12.2 新增文件：`frontend/src/views/RiskDashboardView.vue`

```vue
<template>
  <div class="risk-dashboard">
    <h2>Risk Assessment Dashboard</h2>

    <!-- KPI Cards -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span class="kpi-label">Total Assessments</span>
        <span class="kpi-value">{{ stats.totalAssessments || 0 }}</span>
      </div>
      <div class="kpi-card kpi-danger">
        <span class="kpi-label">Blocked</span>
        <span class="kpi-value">{{ stats.blockedCount || 0 }}</span>
      </div>
      <div class="kpi-card kpi-warning">
        <span class="kpi-label">Needs Review</span>
        <span class="kpi-value">{{ stats.reviewCount || 0 }}</span>
      </div>
      <div class="kpi-card kpi-success">
        <span class="kpi-label">Approved</span>
        <span class="kpi-value">{{ stats.approveCount || 0 }}</span>
      </div>
      <div class="kpi-card kpi-danger-light">
        <span class="kpi-label">Today Blocked</span>
        <span class="kpi-value">{{ stats.todayBlocked || 0 }}</span>
      </div>
    </div>

    <!-- Blocked Payments Table -->
    <h3>Blocked Payments (Critical Risk)</h3>
    <table class="risk-table">
      <thead>
        <tr>
          <th>Payment ID</th><th>Score</th><th>Level</th>
          <th>Rules</th><th>AI Reasoning</th><th>Time</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in blockedPayments" :key="item.id" class="row-danger">
          <td>
            <router-link :to="'/payments/' + item.paymentId">{{ item.paymentId }}</router-link>
          </td>
          <td>{{ item.riskScore }}</td>
          <td>{{ item.riskLevel }}</td>
          <td>{{ truncate(item.triggeredRules, 50) }}</td>
          <td>{{ truncate(item.reasoning, 80) }}</td>
          <td>{{ formatTime(item.assessedAt) }}</td>
        </tr>
      </tbody>
    </table>

    <!-- Review Payments Table -->
    <h3>Payments Pending Review</h3>
    <table class="risk-table">
      <thead>
        <tr>
          <th>Payment ID</th><th>Score</th><th>Level</th>
          <th>Rules</th><th>Statistical Flags</th><th>Time</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in reviewPayments" :key="item.id" class="row-warning">
          <td>
            <router-link :to="'/payments/' + item.paymentId">{{ item.paymentId }}</router-link>
          </td>
          <td>{{ item.riskScore }}</td>
          <td>{{ item.riskLevel }}</td>
          <td>{{ truncate(item.triggeredRules, 50) }}</td>
          <td>{{ truncate(item.statisticalFlags, 50) }}</td>
          <td>{{ formatTime(item.assessedAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRiskStats, getBlockedPayments, getReviewPayments } from '../api/risk'

const stats = ref({})
const blockedPayments = ref([])
const reviewPayments = ref([])

onMounted(async () => {
  try {
    const statsResp = await getRiskStats()
    stats.value = statsResp.data?.data || statsResp.data || {}
    const blockedResp = await getBlockedPayments()
    blockedPayments.value = blockedResp.data?.data || blockedResp.data || []
    const reviewResp = await getReviewPayments()
    reviewPayments.value = reviewResp.data?.data || reviewResp.data || []
  } catch (e) {
    console.error('Failed to load risk dashboard data:', e)
  }
})

function truncate(str, len) {
  if (!str) return 'N/A'
  return str.length > len ? str.substring(0, len) + '...' : str
}

function formatTime(ts) {
  if (!ts) return 'N/A'
  return new Date(ts).toLocaleString()
}
</script>

<style scoped>
.risk-dashboard {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}
.kpi-card {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  text-align: center;
}
.kpi-card.kpi-danger { background: #ffebee; }
.kpi-card.kpi-warning { background: #fff8e1; }
.kpi-card.kpi-success { background: #e8f5e9; }
.kpi-card.kpi-danger-light { background: #fce4ec; }
.kpi-label { display: block; font-size: 12px; color: #666; margin-bottom: 8px; }
.kpi-value { display: block; font-size: 28px; font-weight: bold; }
.risk-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 24px;
}
.risk-table th, .risk-table td {
  padding: 8px 12px;
  border: 1px solid #ddd;
  text-align: left;
}
.risk-table th { background: #f0f0f0; font-weight: 600; }
.row-danger td { background: #fff3f3; }
.row-warning td { background: #fffbeb; }
</style>
```

### 12.3 修改 `PaymentDetailView.vue` — 添加风险面板

在现有 `ActionButtons` 之后添加风险信息区：

```vue
<!-- Risk Assessment Panel (仅在存在风险评估时显示) -->
<div v-if="payment.riskLevel" class="card risk-card">
  <div class="card-header">
    <span class="card-label">Risk Assessment</span>
    <span :class="'risk-badge risk-' + payment.riskLevel.toLowerCase()">
      {{ payment.riskLevel }}
    </span>
  </div>
  <div class="grid">
    <div class="kv">
      <span class="k">Risk Score</span>
      <span class="v">{{ payment.riskScore }}/100</span>
    </div>
    <div class="kv">
      <span class="k">Decision</span>
      <span :class="'decision-' + payment.riskDecision.toLowerCase()">
        {{ payment.riskDecision }}
      </span>
    </div>
  </div>

  <!-- AI Agent Reasoning (如果存在) -->
  <div v-if="riskReasoning" class="reasoning-box">
    <h4>AI Agent Reasoning</h4>
    <p>{{ riskReasoning }}</p>
  </div>
</div>
```

在 `<script setup>` 中添加：

```javascript
import { getPaymentRiskAssessment } from '../api/risk'

const riskReasoning = ref('')

// 在 loadPayment 方法后追加:
async function loadRiskAssessment() {
  if (!payment.value?.id) return
  try {
    const resp = await getPaymentRiskAssessment(payment.value.id)
    const assessments = resp.data?.data || resp.data || []
    if (assessments.length > 0 && assessments[0].reasoning) {
      riskReasoning.value = assessments[0].reasoning
    }
  } catch (e) {
    // 风险数据不存在时忽略
  }
}

onMounted(() => { loadPayment(); loadRiskAssessment() })
```

### 12.4 修改 `frontend/src/router/index.js`

```javascript
// 新增路由
{
  path: '/risk-dashboard',
  name: 'RiskDashboard',
  component: () => import('../views/RiskDashboardView.vue')
}
```

---

## 十三、开发排期

| Day | 任务 | 产出文件 | 验证方法 |
|-----|------|---------|---------|
| **Day 1** | pom.xml + application.yml + DDL + Entity + Mapper | pom.xml, yml, 2 DDL, RiskAssessment.java, AccountStats.java, 2 Mapper | `mvn compile` 成功; MySQL 表创建成功; H2 测试 schema 加载成功 |
| **Day 2** | Layer 1 规则引擎 (5 规则类 + RiskConfig + RiskContext + RiskRule 接口) + 2 枚举 | 5 Rule 类 + 2 枚举 + 1 Config + 1 Context + 1 接口 | 单元测试 8 场景 |
| **Day 3** | Layer 2 统计检测 + AccountStatsMapper | StatisticalDetector.java | 单元测试 6 场景 |
| **Day 4** | LangChain4j 集成: @Tool + @AiService + ResultParser | PaymentRiskTools.java + PaymentRiskAgent.java + AiAgentResultParser.java | 用 gpt-4o-mini 或 Ollama 测试 Tool Calling |
| **Day 5** | 核心编排 RiskAssessmentService + processValidate 集成 | RiskAssessmentService.java + PaymentServiceImpl 修改 | API 测试: 创建支付 → validate → 检查 risk 字段 |
| **Day 6** | PaymentResponse 扩展 + RiskController + 前端 API | PaymentResponse 修改 + RiskController.java + risk.js | Swagger 测试所有 /api/risk 端点 |
| **Day 7** | 前端: RiskDashboard + 详情页风险面板 + 路由 | RiskDashboardView.vue + PaymentDetailView 修改 + router 修改 | 浏览器查看仪表盘 + 详情页风险标签 |
| **Day 8** | 测试: 22 个场景 + 配置调优 + README 更新 | 测试文件 + application.yml 调优 | `mvn test` 全绿 |

---

## 十四、测试场景清单

### Layer 1 规则引擎 (8 个)

| # | 场景 | 预期 |
|---|------|------|
| T1 | 正常金额 $500, 白天, 已知收款人 | score=0, APPROVE |
| T2 | 大额警告 $100,000 | LargeAmountRule +30, 可能 REVIEW |
| T3 | 超大额 $1,000,000 | LargeAmountRule +100, BLOCK |
| T4 | 凌晨 3:00 交易 | NightTimeRule +25 |
| T5 | 白天 14:00 交易 | NightTimeRule +0 |
| T6 | 自转账 (source == dest) | SelfTransferRule +50 |
| T7 | 新收款人 (不在 knownPayees) | NewPayeeRule +30 |
| T8 | 高频 (10min 内 5 笔+) | VelocityRule +35 |

### Layer 2 统计检测 (6 个)

| # | 场景 | 预期 |
|---|------|------|
| T9 | 账户历史 <5 笔 | insufficientData, 跳过 |
| T10 | 金额 z-score = 3.2 | ZSCORE_ANOMALY +24 |
| T11 | 金额突破 IQR 上界 | IQR_ANOMALY +20 |
| T12 | 速度偏离 5x 基线 | VELOCITY_ANOMALY +25 |
| T13 | 组合: z-score + IQR + velocity | 24+20+25=69 → BLOCK |
| T14 | Layer 2 关闭 (`enabled=false`) | 无异常加分 |

### Layer 3 AI Agent (8 个)

| # | 场景 | 预期 |
|---|------|------|
| T15 | LLM 推理 → 维持 REVIEW | decision=REVIEW |
| T16 | LLM 推理 → 升级 BLOCK | decision=BLOCK |
| T17 | LLM 调用失败/超时 | 保守维持 REVIEW |
| T18 | LLM 返回格式异常 | 默认 REVIEW |
| T19 | Layer 3 关闭 (`enabled=false`) | 不调用 LLM |
| T20 | AI Agent 使用 @Tool 查询真实数据 | Tool 调用日志可见 |
| T21 | AI Agent 推理文本持久化到 risk_assessments.reasoning | DB 有记录 |
| T22 | LLM 只升级不降级 (BLOCK→REVIEW 被拒绝) | 维持 BLOCK |

### 集成测试 (补充)

| # | 场景 | 预期 |
|---|------|------|
| T23 | 正常支付 validate → APPROVE → VALIDATED | 正常状态 |
| T24 | 可疑支付 validate → BLOCK → FAILED (RISK_BLOCKED) | 403 响应 |
| T25 | 可疑支付 validate → REVIEW → VALIDATED + riskLevel=HIGH | 200 + 风险标记 |
| T26 | GET /api/risk/stats 返回 KPI 数据 | 统计数据正确 |
| T27 | GET /api/risk/blocked 返回拦截列表 | 表格数据正确 |

---

## 十五、LLM Provider 切换指南

项目支持三种 LLM Provider，只需修改 `application.yml` 即可切换，**无需改任何代码**：

### OpenAI (默认)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini
      temperature: 0.1
```

### 阿里百炼 DashScope (推荐中国用户)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
      model-name: qwen-plus
      temperature: 0.1
```

> 如需使用 DashScope 原生 SDK（而非 OpenAI-compatible 模式），额外添加依赖：
> ```xml
> <dependency>
>     <groupId>dev.langchain4j</groupId>
>     <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
>     <version>1.0.0-beta3</version>
> </dependency>
> ```

### Ollama 本地部署 (零成本开发/测试)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: http://localhost:11434/v1
      api-key: ollama          # Ollama 不需要真实 API Key
      model-name: llama3       # 或 qwen2.5、deepseek-r1 等
      temperature: 0.1
```

> **切换原理**：`langchain4j-open-ai-spring-boot-starter` 支持任何 OpenAI-compatible API。阿里百炼 DashScope 和 Ollama 都兼容 OpenAI API 格式，所以只需改 `base-url` 和 `model-name`。

---

## 十六、完整新增/修改文件清单

### 新增文件 (17 Java + 2 Vue + 1 JS)

```
backend/src/main/java/com/hsbc/payment/
├── config/RiskConfig.java                              (新增)
├── enums/RiskDecision.java                             (新增)
├── enums/RiskLevel.java                                (新增)
├── entity/RiskAssessment.java                          (新增)
├── entity/AccountStats.java                            (新增)
├── mapper/RiskAssessmentMapper.java                    (新增)
├── mapper/AccountStatsMapper.java                      (新增)
├── controller/RiskController.java                      (新增)
├── service/risk/
│   ├── RiskRule.java                                   (新增, 接口)
│   ├── RiskContext.java                                (新增)
│   ├── RiskAssessmentService.java                      (新增, 核心编排)
│   ├── StatisticalDetector.java                        (新增)
│   ├── PaymentRiskAgent.java                           (新增, @AiService)
│   ├── PaymentRiskTools.java                           (新增, @Tool)
│   ├── AiAgentResultParser.java                        (新增)
│   └── impl/
│       ├── LargeAmountRule.java                        (新增)
│       ├── NightTimeRule.java                          (新增)
│       ├── SelfTransferRule.java                       (新增)
│       ├── NewPayeeRule.java                           (新增)
│       └── VelocityRule.java                           (新增)

frontend/src/
├── api/risk.js                                         (新增)
├── views/RiskDashboardView.vue                         (新增)
```

### 修改文件 (7 个)

```
backend/pom.xml                                         (修改: 加 LangChain4j 依赖)
backend/src/main/resources/application.yml              (修改: 加 risk + langchain4j 配置)
backend/src/main/resources/db/schema.sql                (修改: 加 2 表 DDL)
backend/src/main/resources/db/schema-h2.sql             (修改: 加 2 表 DDL + 种子数据)
backend/src/main/java/.../PaymentServiceImpl.java       (修改: 第209行替换 + 注入 riskAssessmentService + riskAssessmentMapper)
backend/src/main/java/.../dto/response/PaymentResponse.java  (修改: 加 3 字段)
frontend/src/router/index.js                            (修改: 加路由)
frontend/src/views/PaymentDetailView.vue                (修改: 加风险面板)
```

### 新增 DDL: 2 张表

```
risk_assessments — 风险评估记录表
account_stats    — 账户统计基线表
```

---

## 十七、启动与调试指南

### 17.1 基础启动（Layer 1+2, Layer 3 关闭）

1. 执行 DDL 创建 `risk_assessments` 和 `account_stats` 表
2. 执行种子数据 SQL 填充 `account_stats`
3. 在 `application.yml` 中设置 `risk.layer3.enabled: false`
4. 启动 Spring Boot：`mvn spring-boot:run`
5. 测试：创建支付 → POST `/api/payments/{id}/validate` → 检查返回的 `riskScore/riskLevel/riskDecision`

### 17.2 Layer 3 AI Agent 启动

1. 选择 LLM Provider 并配置 `application.yml`
2. 设置 `risk.layer3.enabled: true`
3. 设置 `langchain4j.open-ai.chat-model.api-key`（OpenAI/DashScope）或启动 Ollama
4. 启动 Spring Boot
5. 测试：对 REVIEW 状态的支付验证 AI Agent 是否被触发
6. 查看 `risk_assessments.reasoning` 字段是否有 AI 推理文本

### 17.3 Ollama 本地测试 (零成本)

```bash
# 1. 安装并启动 Ollama
ollama serve

# 2. 拉取模型
ollama pull llama3    # 或 qwen2.5、deepseek-r1

# 3. 配置 application.yml
langchain4j:
  open-ai:
    chat-model:
      base-url: http://localhost:11434/v1
      api-key: ollama
      model-name: llama3

# 4. 启动 Spring Boot
mvn spring-boot:run
```

### 17.4 调试技巧

| 方法 | 说明 |
|------|------|
| `langchain4j.open-ai.chat-model.log-requests: true` | 记录 LLM 请求日志 |
| `langchain4j.open-ai.chat-model.log-responses: true` | 记录 LLM 响应日志 |
| 查看 `risk_assessments` 表 | 检查 `triggered_rules` / `statistical_flags` / `reasoning` 字段 |
| Swagger UI: `/swagger-ui.html` | 测试所有 /api/risk 端点 |
| 日志关键字: `Layer 1 result` / `Layer 2 result` / `Layer 3 result` | 搜索应用日志跟踪三层执行 |

### 17.5 渐进式启用策略

建议按以下顺序逐步启用：

```
Phase 1: Layer 1 only (risk.layer2.enabled=false, risk.layer3.enabled=false)
  → 验证 5 条规则引擎正常工作

Phase 2: Layer 1 + 2 (risk.layer2.enabled=true, risk.layer3.enabled=false)
  → 验证统计检测正常工作

Phase 3: Layer 1 + 2 + 3 (risk.layer3.enabled=true)
  → 配置 LLM API Key，验证 AI Agent 推理
  → 先用 Ollama 本地测试，再切换正式 LLM Provider
```

---

## 附录：与现有系统的兼容性确认

| 维度 | 影响 | 说明 |
|------|------|------|
| 状态机 | **不变** | BLOCK → FAILED (RISK_BLOCKED) 是现有状态机允许的（任何状态 → FAILED） |
| ErrorCode | **已有** | `RISK_BLOCKED` 已在 ErrorCode 枚举中，GlobalExceptionHandler 已映射 → 403 |
| processValidate | **扩展** | 在验证通过后插入风险评估，原逻辑不变 |
| PaymentResponse | **扩展** | 新增 3 个 nullable 字段，不影响现有字段 |
| 前端路由 | **扩展** | 新增 /risk-dashboard，不影响现有页面 |
| 数据库 | **新增** | 2 张新表 + 索引，不影响现有 4 张表 |
| 幂等性 | **不变** | 风险评估不涉及创建/重试的幂等键 |
| 乐观锁 | **不变** | 风险评估在 validate 流程内，与 Payment.version 兼容 |
| 重试机制 | **不变** | 风险 BLOCK 后支付变为 FAILED，可被现有 retry 流程处理 |

**最大优势：零破坏性改动**。所有变更都是扩展性的。风险评估可以通过 `risk.layer2.enabled=false` 一键关闭退化为纯规则引擎；甚至可以通过不注入 `RiskAssessmentService` 完全关闭，回到原始 validate 流程。
