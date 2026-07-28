# Payment Processing System + AI Anomaly Detection

基于 Spring Boot + Vue 3 的支付处理系统，管理金融支付的完整生命周期（创建 → 验证 → 发送 → 完成/失败），支持全状态变更审计追踪，并可扩展 AI 异常检测功能。

---

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **后端框架** | Spring Boot 3 | Java REST API |
| **数据库** | MySQL 8 | 关系型数据库 |
| **ORM** | MyBatis-Plus | 增强版 MyBatis，简化 CRUD |
| **前端框架** | Vue 3 + Vite | 现代化前端开发 |
| **UI 组件库** | Element Plus / Naive UI | 开箱即用的 UI 组件 |
| **HTTP 客户端** | Axios | 前端 API 请求 |
| **状态管理** | Pinia | Vue 3 官方状态管理 |
| **路由** | Vue Router 4 | 前端路由 |
| **API 文档** | Swagger / OpenAPI | 自动生成 API 文档 |
| **测试** | JUnit 5 + Mockito | 后端单元测试与集成测试 |
| **AI 异常检测** | 规则引擎 + 统计分析 + LLM（可扩展） | 三阶段渐进式风险评估 |

---

## 项目结构

```
closeAi_paymentSystem/
├── backend/                          # Spring Boot 后端项目
│   ├── src/main/java/com/hsbc/payment/
│   │   ├── controller/               # REST API 控制器层
│   │   │   ├── PaymentController     # 支付 CRUD + 查询
│   │   │   └── PaymentProcessController # 状态流转操作
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── PaymentService        # 支付核心业务
│   │   │   ├── StateMachineService   # 状态机（状态转换校验）
│   │   │   ├── IdempotencyService    # 幂等性检查
│   │   │   └── ValidationService     # 字段验证
│   │   ├── mapper/                   # MyBatis-Plus Mapper 接口
│   │   │   ├── PaymentMapper
│   │   │   ├── StatusHistoryMapper
│   │   │   └── IdempotencyMapper
│   │   ├── entity/                   # 数据库实体类
│   │   │   ├── Payment
│   │   │   ├── StatusHistory
│   │   │   └── IdempotencyRecord
│   │   ├── dto/                      # 请求/响应传输对象
│   │   │   ├── request/              # 请求 DTO
│   │   │   │   ├── CreatePaymentRequest
│   │   │   │   └── PaymentQueryRequest
│   │   │   └── response/             # 响应 DTO
│   │   │       ├── PaymentResponse
│   │   │       ├── StatusHistoryResponse
│   │   │       └── ErrorResponse
│   │   ├── enums/                    # 枚举类
│   │   │   ├── PaymentStatus         # 支付状态枚举
│   │   │   └── ErrorCode             # 错误码枚举
│   │   ├── exception/                # 全局异常处理
│   │   │   ├── GlobalExceptionHandler
│   │   │   ├── BusinessException
│   │   │   └── PaymentNotFoundException
│   │   └── config/                   # 配置类
│   │       ├── MyBatisPlusConfig
│   │       ├── SwaggerConfig
│   │       └── WebMvcConfig
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置文件
│   │   └── db/
│   │       └── migration/            # SQL 建表脚本
│   │           ├── V1__init_payments.sql
│   │           ├── V2__init_status_history.sql
│   │           └── V3__init_idempotency.sql
│   └── pom.xml
│
├── frontend/                         # Vue 3 前端项目
│   ├── src/
│   │   ├── views/                    # 页面视图
│   │   │   ├── CreatePaymentView     # 创建支付
│   │   │   ├── PaymentListView       # 支付列表（搜索/筛选）
│   │   │   └── PaymentDetailView     # 支付详情 + 状态时间线
│   │   ├── components/               # 可复用组件
│   │   │   ├── PaymentForm           # 支付表单
│   │   │   ├── PaymentTable          # 支付列表表格
│   │   │   ├── StatusTimeline        # 状态历史时间线
│   │   │   ├── StatusBadge           # 状态标签（带颜色）
│   │   │   ├── ErrorPanel            # 失败信息面板
│   │   │   ├── RiskScoreGauge        # 风险评分仪表盘（后期扩展）
│   │   │   └── ActionButtons         # 动态操作按钮
│   │   ├── api/                      # Axios API 封装
│   │   │   ├── index                 # Axios 实例配置
│   │   │   └── payment               # 支付相关 API
│   │   ├── router/                   # Vue Router 路由配置
│   │   │   └── index
│   │   ├── stores/                   # Pinia 状态管理
│   │   │   └── payment
│   │   ├── utils/                    # 工具函数
│   │   │   ├── constants             # 状态映射、货币列表等常量
│   │   │   └── validators            # 前端校验规则
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                             # 项目文档
│   └── api/                          # OpenAPI/Swagger 规范
│
├── README.md                         # 项目说明（本文件）
├── payment_processing.md             # 原始需求文档
└── Dev_Guide_Payment_Processing_AI_Anomaly_Detection.md  # 开发指南
```

---

## 支付生命周期状态机

```
CREATED → VALIDATED → SENT → COMPLETED
    ↓         ↓          ↓
  FAILED    FAILED     FAILED
```

### 合法状态转换矩阵

| 当前 \ 目标 | CREATED | VALIDATED | SENT | COMPLETED | FAILED |
|-------------|---------|-----------|------|-----------|--------|
| **CREATED** | - | ✅ | ❌ | ❌ | ✅ |
| **VALIDATED** | ❌ | - | ✅ | ❌ | ✅ |
| **SENT** | ❌ | ❌ | - | ✅ | ✅ |
| **COMPLETED** | ❌ | ❌ | ❌ | - | ❌ |
| **FAILED** | ❌ | ✅ (重试) | ❌ | ❌ | - |

---

## REST API 设计

### 端点一览

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/payments` | 创建支付（需 `Idempotency-Key` 请求头） |
| `GET` | `/api/payments` | 查询支付列表（支持 status/currency 筛选 + 分页） |
| `GET` | `/api/payments/{id}` | 查询单个支付详情 |
| `GET` | `/api/payments/{id}/history` | 查询支付状态变更历史 |
| `POST` | `/api/payments/{id}/validate` | 验证支付（CREATED → VALIDATED） |
| `POST` | `/api/payments/{id}/send` | 发送支付（VALIDATED → SENT） |
| `POST` | `/api/payments/{id}/complete` | 完成支付（SENT → COMPLETED） |
| `POST` | `/api/payments/{id}/fail` | 标记失败（任意状态 → FAILED） |
| `POST` | `/api/payments/{id}/retry` | 重试失败支付（FAILED → VALIDATED） |

### 创建支付

```
POST /api/payments
Headers:
  Idempotency-Key: <client-generated-uuid>
Body:
{
  "sourceAccount":      "ACC-001",
  "destinationAccount": "ACC-002",
  "amount":             5000.00,
  "currency":           "USD",
  "description":        "August rent"
}
```

### 统一错误响应格式

```json
{
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "金额必须大于 0",
    "details": {
      "field": "amount",
      "value": -100
    }
  }
}
```

### 错误码一览

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|-------------|
| `VALIDATION_FAILED` | 支付验证失败 | 400 |
| `INSUFFICIENT_FUNDS` | 源账户余额不足 | 400 |
| `INVALID_ACCOUNT` | 账户号无效或不存在 | 400 |
| `INVALID_CURRENCY` | 货币代码不支持 | 400 |
| `INVALID_AMOUNT` | 金额为零、负数或无效 | 400 |
| `DUPLICATE_PAYMENT` | 相同的幂等键已存在 | 409 |
| `INVALID_STATUS_TRANSITION` | 状态转换非法 | 400 |
| `PAYMENT_NOT_FOUND` | 支付 ID 不存在 | 404 |
| `PROCESSING_ERROR` | 内部处理错误 | 500 |
| `NETWORK_ERROR` | 网络通信失败 | 503 |
| `RISK_BLOCKED` | AI 风险评估拦截（后期扩展） | 403 |

---

## 数据模型

### payments 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID 主键 |
| idempotency_key | VARCHAR(64) | UNIQUE, NOT NULL | 客户端提供的幂等键 |
| source_account | VARCHAR(50) | NOT NULL | 源账户 |
| destination_account | VARCHAR(50) | NOT NULL | 目标账户 |
| amount | DECIMAL(15,2) | NOT NULL | 金额 |
| currency | VARCHAR(3) | NOT NULL | ISO 4217 货币代码 |
| description | TEXT | NULL | 备注 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'CREATED' | 当前状态 |
| error_code | VARCHAR(50) | NULL | 失败时的错误码 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 更新时间 |

### status_history 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| payment_id | VARCHAR(36) | FK → payments.id | 关联支付 |
| from_status | VARCHAR(20) | NULL | 原状态（首次创建时为 NULL） |
| to_status | VARCHAR(20) | NOT NULL | 新状态 |
| changed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 变更时间 |
| reason | TEXT | NULL | 变更原因/备注 |
| error_code | VARCHAR(50) | NULL | 失败时的错误码 |

### idempotency_keys 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| key | VARCHAR(64) | PK | 幂等键 |
| payment_id | VARCHAR(36) | FK → payments.id | 关联支付 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |

### 验证规则

1. **金额校验**：amount > 0 且 ≤ 1,000,000，最多 2 位小数
2. **账户校验**：源账户 ≠ 目标账户，账户号格式有效
3. **货币校验**：必须是有效 ISO 4217 代码（USD / EUR / GBP / CNY 等）
4. **状态转换校验**：严格按状态转换矩阵，非法转换返回 `INVALID_STATUS_TRANSITION`
5. **幂等性**：同一 `Idempotency-Key` 重复请求返回已存在的支付（HTTP 200）

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+
- npm / pnpm

### 后端启动

```bash
cd backend
# 修改 src/main/resources/application.yml 中的数据库连接信息
mvn spring-boot:run
# 启动后访问: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
# 启动后访问: http://localhost:5173
```

---

## 开发阶段

| 阶段 | 内容 | 说明 |
|------|------|------|
| **Phase 1** | 后端核心功能 | 支付 CRUD、状态机、幂等性、审计日志 |
| **Phase 2** | 功能验证 | 单元测试 + 集成测试，验证前后端联通 |
| **Phase 3** | 前端页面 | 创建支付、支付列表、支付详情、状态时间线 |
| **Phase 4** | AI 异常检测（扩展） | 规则引擎 + 统计分析 + 可选 LLM 推理 |
