# Payment Processing System — Design Document

**Date:** 2026-07-24
**Status:** Approved

---

## 1. Project Overview

A full-stack payment processing system that manages the complete lifecycle of financial payments (create → validate → send → complete/fail) with full state change audit trails. An AI Anomaly Detection module will be integrated as a later extension.

### Core Deliverables

| Deliverable | Description | Phase |
|-------------|-------------|-------|
| Payment REST API | Payment CRUD, lifecycle state machine, idempotency, audit trail | Phase 1 |
| Backend Test Suite | Unit tests + integration tests | Phase 2 |
| Web Frontend | Payment creation, list with search/filter, detail with status timeline | Phase 3 |
| AI Risk Assessment | Rule engine + statistical anomaly detection + optional LLM (extension) | Phase 4 |

---

## 2. Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend Framework | Spring Boot | 3.x |
| Language | Java | 17+ |
| Database | MySQL | 8.0+ |
| ORM | MyBatis-Plus | 3.x |
| Build Tool | Maven | 3.8+ |
| Frontend Framework | Vue 3 + Composition API | 3.x |
| Build Tool (Frontend) | Vite | 5.x |
| UI Library | Element Plus / Naive UI | latest |
| State Management | Pinia | 2.x |
| HTTP Client | Axios | latest |
| Routing | Vue Router | 4.x |
| API Documentation | Swagger / OpenAPI 3 | latest |
| Testing (Backend) | JUnit 5 + Mockito | latest |

---

## 3. Repository Structure

Monorepo with `backend/` and `frontend/` in a single Git repository.

```
closeAi_paymentSystem/
├── backend/
│   ├── src/main/java/com/hsbc/payment/
│   │   ├── controller/           # PaymentController, PaymentProcessController
│   │   ├── service/              # PaymentService, StateMachineService,
│   │   │                           IdempotencyService, ValidationService
│   │   ├── mapper/               # PaymentMapper, StatusHistoryMapper,
│   │   │                           IdempotencyMapper
│   │   ├── entity/               # Payment, StatusHistory, IdempotencyRecord
│   │   ├── dto/
│   │   │   ├── request/          # CreatePaymentRequest, PaymentQueryRequest
│   │   │   └── response/         # PaymentResponse, StatusHistoryResponse,
│   │   │                           ErrorResponse
│   │   ├── enums/                # PaymentStatus, ErrorCode
│   │   ├── exception/            # GlobalExceptionHandler, BusinessException
│   │   └── config/               # MyBatisPlusConfig, SwaggerConfig, WebMvcConfig
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/         # SQL DDL scripts
├── frontend/
│   └── src/
│       ├── views/                # CreatePaymentView, PaymentListView,
│       │                           PaymentDetailView
│       ├── components/           # PaymentForm, PaymentTable, StatusTimeline,
│       │                           StatusBadge, ErrorPanel, ActionButtons
│       ├── api/                  # Axios instance + payment API module
│       ├── router/               # Vue Router config
│       ├── stores/               # Pinia stores
│       └── utils/                # Constants, validators
├── docs/
├── README.md
├── payment_processing.md
└── Dev_Guide_Payment_Processing_AI_Anomaly_Detection.md
```

---

## 4. Payment State Machine

### States

- **CREATED** — Payment submitted but not yet validated
- **VALIDATED** — Passed all validation rules, ready to send
- **SENT** — Transmitted to destination
- **COMPLETED** — Successfully processed (terminal)
- **FAILED** — Failed at some stage (retryable)

### State Diagram

```
CREATED → VALIDATED → SENT → COMPLETED
    ↓         ↓          ↓
  FAILED    FAILED     FAILED
```

### Valid Transition Matrix

| From \ To | CREATED | VALIDATED | SENT | COMPLETED | FAILED |
|-----------|---------|-----------|------|-----------|--------|
| CREATED   | -       | ✅ | ❌ | ❌ | ✅ |
| VALIDATED | ❌ | - | ✅ | ❌ | ✅ |
| SENT      | ❌ | ❌ | - | ✅ | ✅ |
| COMPLETED | ❌ | ❌ | ❌ | - | ❌ |
| FAILED    | ❌ | ✅ (retry) | ❌ | ❌ | - |

---

## 5. REST API Design

| Method | Path | Description | Request |
|--------|------|-------------|---------|
| `POST` | `/api/payments` | Create payment | `CreatePaymentRequest` + `Idempotency-Key` header |
| `GET` | `/api/payments` | List/search payments | Query params: `status`, `currency`, `keyword`, `page`, `limit` |
| `GET` | `/api/payments/{id}` | Get payment detail | — |
| `GET` | `/api/payments/{id}/history` | Get status history | — |
| `POST` | `/api/payments/{id}/validate` | Validate payment | — |
| `POST` | `/api/payments/{id}/send` | Send payment | — |
| `POST` | `/api/payments/{id}/complete` | Complete payment | — |
| `POST` | `/api/payments/{id}/fail` | Fail payment | Body: `{ "errorCode": "...", "reason": "..." }` |
| `POST` | `/api/payments/{id}/retry` | Retry failed payment | New `Idempotency-Key` header |

### Create Payment Request

```json
{
  "sourceAccount": "ACC-001",
  "destinationAccount": "ACC-002",
  "amount": 5000.00,
  "currency": "USD",
  "description": "August rent"
}
```

### Unified Error Response

```json
{
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "Amount must be greater than 0",
    "details": { "field": "amount", "value": -100 }
  }
}
```

### Error Codes

| Code | HTTP Status |
|------|-------------|
| VALIDATION_FAILED | 400 |
| INSUFFICIENT_FUNDS | 400 |
| INVALID_ACCOUNT | 400 |
| INVALID_CURRENCY | 400 |
| INVALID_AMOUNT | 400 |
| DUPLICATE_PAYMENT | 409 |
| INVALID_STATUS_TRANSITION | 400 |
| PAYMENT_NOT_FOUND | 404 |
| PROCESSING_ERROR | 500 |
| NETWORK_ERROR | 503 |
| RISK_BLOCKED | 403 (Phase 4) |

---

## 6. Data Model

### payments

```sql
CREATE TABLE payments (
  id                  VARCHAR(36)  PRIMARY KEY,
  idempotency_key     VARCHAR(64)  UNIQUE NOT NULL,
  source_account      VARCHAR(50)  NOT NULL,
  destination_account VARCHAR(50)  NOT NULL,
  amount              DECIMAL(15,2) NOT NULL,
  currency            VARCHAR(3)   NOT NULL,
  description         TEXT,
  status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
  error_code          VARCHAR(50),
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### status_history

```sql
CREATE TABLE status_history (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_id  VARCHAR(36) NOT NULL,
  from_status VARCHAR(20),
  to_status   VARCHAR(20) NOT NULL,
  changed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason      TEXT,
  error_code  VARCHAR(50),
  FOREIGN KEY (payment_id) REFERENCES payments(id)
);
```

### idempotency_keys

```sql
CREATE TABLE idempotency_keys (
  key_record VARCHAR(64) PRIMARY KEY,
  payment_id VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (payment_id) REFERENCES payments(id)
);
```

### Validation Rules

1. **Amount**: > 0, ≤ 1,000,000, max 2 decimal places
2. **Account**: source ≠ destination, valid format
3. **Currency**: valid ISO 4217 code (USD, EUR, GBP, CNY)
4. **Status Transition**: must match the valid transition matrix
5. **Idempotency**: duplicate `Idempotency-Key` returns existing payment (HTTP 200), no new record created

---

## 7. Frontend Design

### Page Map

| Route | View | Description |
|-------|------|-------------|
| `/` | redirect → `/payments` | Default to payment list |
| `/payments` | PaymentListView | Payment list with search, filter, pagination |
| `/payments/create` | CreatePaymentView | Create new payment form |
| `/payments/:id` | PaymentDetailView | Payment detail, status timeline, actions |

### Requirement Coverage

| Requirement (Priority) | Implementation |
|------------------------|---------------|
| 1. Create a new payment | `CreatePaymentView` with form validation |
| 2. View payment status and details | `PaymentDetailView` — info card with status badge (color-coded) |
| 3. View payment history (all status transitions) | `StatusTimeline` component — vertical timeline with timestamps |
| 4. Search/filter payments by status | `PaymentListView` — status dropdown filter + keyword search + pagination |
| 5. View error details for failed payments | `ErrorPanel` component — error code + reason description |

### Components

| Component | Role |
|-----------|------|
| `PaymentForm` | Reusable payment form with validation |
| `PaymentTable` | Data table with status-colored rows |
| `StatusTimeline` | Vertical timeline showing all status transitions |
| `StatusBadge` | Color-coded status chip (green=COMPLETED, yellow=in-progress, red=FAILED) |
| `ErrorPanel` | Failure details display |
| `ActionButtons` | Dynamic action buttons based on current status |

---

## 8. Development Phases

| Phase | Focus | Key Tasks |
|-------|-------|-----------|
| **Phase 1** | Backend Core | Project scaffold, DB schema, CRUD, state machine, idempotency, audit trail, validation |
| **Phase 2** | Verification | Unit tests (state machine, validation, service layer), integration tests (lifecycle), Swagger docs |
| **Phase 3** | Frontend | Vue 3 project, create/list/detail pages, API integration, end-to-end connectivity |
| **Phase 4** | AI Anomaly Detection (Extension) | Rule engine (Layer 1), statistical detection (Layer 2), risk_assessments table, risk API |

---

## 9. Frontend-to-Phase Mapping

Phase 3 frontend pages depend on the following backend endpoints being operational:

| Frontend Page | Depends On (Backend API) |
|---------------|--------------------------|
| CreatePaymentView | `POST /api/payments` |
| PaymentListView | `GET /api/payments` |
| PaymentDetailView | `GET /api/payments/{id}`, `GET /api/payments/{id}/history` |
| ActionButtons | `POST /api/payments/{id}/validate\|send\|complete\|fail\|retry` |

---

## 10. Key Design Decisions

1. **Monorepo** — Simpler for a small team, single commit history, easier CI setup
2. **Idempotency via header** — `Idempotency-Key` header pattern, not in body, per IETF draft standard
3. **State machine in service layer** — Not in database triggers or constraints. Business logic stays in Java, testable without DB
4. **MyBatis-Plus over raw MyBatis** — Reduces boilerplate CRUD code, built-in pagination
5. **Simulated processing** — No real payment gateway integration; send/complete operations are simulated internally
6. **No authentication** — Single user, no account management (per project requirements)
7. **AI deferred to Phase 4** — Core system fully functional before adding anomaly detection
