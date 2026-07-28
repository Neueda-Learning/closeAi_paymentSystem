# closeAi_paymentSystem 项目约定

## 技术栈
- 后端：Spring Boot 3.2 + Java 17 + MyBatis-Plus 3.5.5
- 前端：Vue 3 + Vite + Element Plus
- 测试库：H2（test scope 已扩展为 runtime，给 dev profile 用）

## dev profile 启动
- 数据库：H2 内存库 `jdbc:h2:mem:payment_system;DB_CLOSE_DELAY=-1;MODE=MySQL`
- schema：`backend/src/main/resources/db/schema-h2.sql`（无 ENGINE/CHARSET 子句）
- 种子账户 ACC-00001..00010，余额 0..10,000,000，多币种

## 环境注意
- bash 调 `mvn` 路径转换失败 → 用 `E:/Download/apache-maven-3.5.3/bin/mvn.cmd`
- 用户 shell 有 `SERVER__PORT=54172` 环境变量会覆盖 `server.port`，启动须显式传 `--server.port=8080`

## 支付状态机
CREATED → VALIDATED → SENT → COMPLETED
任意 → FAILED
FAILED → VALIDATED（retry，最多 3 次）

## 关键端点
- POST `/api/payments`（需 `Idempotency-Key` 头）
- POST `/api/payments/{id}/{validate|send|complete|fail|retry}`
- GET `/api/payments`、`/api/payments/{id}`、`/api/payments/{id}/history`
- Swagger: `/swagger-ui.html`，OpenAPI: `/v3/api-docs`

## 错误码
VALIDATION_FAILED / INSUFFICIENT_FUNDS / INVALID_ACCOUNT / INVALID_CURRENCY /
INVALID_AMOUNT / DUPLICATE_PAYMENT (409) / INVALID_STATUS_TRANSITION /
PAYMENT_NOT_FOUND (404) / PROCESSING_ERROR / NETWORK_ERROR / RISK_BLOCKED (403) / RETRY_EXHAUSTED