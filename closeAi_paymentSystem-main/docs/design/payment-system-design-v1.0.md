# Payment Processing System — System Design Document v1.0

**Date:** 2026-07-26
**Version:** 1.0
**Status:** Approved

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture Design](#2-architecture-design)
3. [Payment State Machine](#3-payment-state-machine)
4. [Data Model](#4-data-model)
5. [REST API Design](#5-rest-api-design)
6. [Boundary Conditions & Constraints](#6-boundary-conditions--constraints)
7. [Failure Modes & Fallback Strategies](#7-failure-modes--fallback-strategies)
8. [Idempotency Design](#8-idempotency-design)
9. [Error Handling Framework](#9-error-handling-framework)
10. [AI Risk Assessment (Phase 4)](#10-ai-risk-assessment-phase-4)
11. [Observability & Monitoring](#11-observability--monitoring)
12. [Security Considerations](#12-security-considerations)
13. [Development Phases](#13-development-phases)
14. [References](#14-references)

---

## 1. System Overview

### 1.1 What We're Building

A full-stack payment processing system that manages the complete lifecycle of financial payments — from creation through validation, transmission, and final settlement (or failure). The system enforces strict state-machine rules, provides at-least-once processing guarantees via idempotency, maintains a complete append-only audit trail, and integrates an optional AI-powered anomaly detection engine.

### 1.2 Core Design Principles

| Principle | Implementation |
|-----------|---------------|
| **Correctness over throughput** | Every state transition is validated; no invalid state is writable to the database |
| **Exactly-once semantics** | Client-provided idempotency keys prevent duplicate payment creation |
| **Append-only audit** | `status_history` is write-once, never updated or deleted |
| **Fail-fast validation** | Invalid requests are rejected at the controller layer before reaching business logic |
| **Transactional integrity** | Status updates + history records are committed atomically |
| **Defense in depth** | Validation happens at three levels: DTO annotations → business rules → database constraints |

### 1.3 Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Backend Framework | Spring Boot 3.2 | Mature ecosystem, declarative transactions, validation |
| Language | Java 17 | LTS, records, pattern matching |
| Database | MySQL 8.0 | ACID guarantees, row-level locking |
| ORM | MyBatis-Plus 3.5 | Pagination plugin, code generation, Lambda query wrappers |
| Frontend | Vue 3 + Vite + Element Plus | Composition API, fast dev server, mature UI library |
| Build (Backend) | Maven 3.8+ | Dependency management, plugin ecosystem |
| API Docs | SpringDoc OpenAPI 3 | Auto-generated Swagger UI |
| Testing | JUnit 5 + Mockito + H2 | Unit tests (mocked) + integration tests (in-memory DB) |

---

## 2. Architecture Design

### 2.1 Logical Architecture (Layered)

```
┌──────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                         │
│  ┌─────────────────────┐  ┌──────────────────────────────┐  │
│  │   Vue 3 SPA          │  │   Swagger UI                 │  │
│  │   (Browser)          │  │   (http://host:8080/swagger) │  │
│  └──────────┬───────────┘  └──────────────┬───────────────┘  │
│             │                             │                   │
│             └──────────┬──────────────────┘                   │
│                        │ HTTP/REST                            │
├────────────────────────┼──────────────────────────────────────┤
│              CONTROLLER LAYER                                 │
│  ┌─────────────────────┐  ┌──────────────────────────────┐   │
│  │ PaymentController   │  │ PaymentProcessController     │   │
│  │ (CRUD + Query)      │  │ (State Transitions)          │   │
│  └──────────┬───────────┘  └──────────────┬───────────────┘   │
│             │                             │                    │
│             └──────────┬──────────────────┘                    │
│                        │                                      │
├────────────────────────┼──────────────────────────────────────┤
│                SERVICE LAYER                                  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              PaymentServiceImpl                      │    │
│  │  (Orchestrates: validation → idempotency → state     │    │
│  │   transition → audit record → response)              │    │
│  └───┬──────────┬────────────┬─────────────┬───────────┘    │
│      │          │            │             │                  │
│  ┌───▼──┐ ┌────▼────┐ ┌────▼──────┐ ┌───▼──────────┐        │
│  │State │ │Idempot. │ │Validation │ │AI Risk Engine│        │
│  │Mach. │ │Service  │ │Service    │ │(Phase 4)     │        │
│  └──────┘ └─────────┘ └───────────┘ └──────────────┘        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                DATA ACCESS LAYER                              │
│  ┌──────────────┐ ┌───────────────────┐ ┌────────────────┐   │
│  │PaymentMapper │ │StatusHistoryMapper│ │IdempotencyMapper│  │
│  └──────┬───────┘ └────────┬──────────┘ └───────┬────────┘   │
│         │                  │                    │             │
├─────────┼──────────────────┼────────────────────┼─────────────┤
│         └──────────────────┼────────────────────┘             │
│                    DATABASE LAYER                             │
│  ┌───────────────────────────────────────────────────────┐   │
│  │                   MySQL 8.0                           │   │
│  │  ┌──────────┐  ┌───────────────┐  ┌────────────────┐  │   │
│  │  │ payments │  │status_history │  │idempotency_keys│  │   │
│  │  └──────────┘  └───────────────┘  └────────────────┘  │   │
│  └───────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Request Flow: Create Payment

```
Client                  Controller              Service                 Database
  │                         │                      │                       │
  │ POST /api/payments      │                      │                       │
  │ Idempotency-Key: X      │                      │                       │
  │────────────────────────→│                      │                       │
  │                         │ createPayment(req,X) │                       │
  │                         │─────────────────────→│                       │
  │                         │                      │ 1. findPaymentIdByKey │
  │                         │                      │──────────────────────→│
  │                         │                      │←──────────────────────│
  │                         │                      │   (null = new key)    │
  │                         │                      │                       │
  │                         │                      │ 2. validate(request)  │
  │                         │                      │   (accounts, currency)│
  │                         │                      │                       │
  │                         │                      │ 3. INSERT payment     │
  │                         │                      │──────────────────────→│
  │                         │                      │←──────────────────────│
  │                         │                      │                       │
  │                         │                      │ 4. INSERT idempotency │
  │                         │                      │──────────────────────→│
  │                         │                      │←──── (or DuplicateKey)│
  │                         │                      │                       │
  │                         │                      │ 5. INSERT status_hist │
  │                         │                      │──────────────────────→│
  │                         │                      │←──────────────────────│
  │                         │                      │                       │
  │                         │                      │  {All in one @Tx}     │
  │                         │←─────────────────────│                       │
  │←── 201 Created ────────│                      │                       │
```

### 2.3 Transaction Boundaries

Every state-changing operation runs within a Spring `@Transactional` boundary:

```
@Transactional  // <-- Entire method is one ACID unit
public PaymentResponse processValidate(String paymentId) {
    Payment payment = findPaymentById(paymentId);   // SELECT (within tx)
    validateTransition(from, to);                   // In-memory check
    updatePaymentStatus(payment, newStatus, null);  // UPDATE payments
    recordStatusHistory(...);                        // INSERT status_history
    // If either write fails → full rollback
}
```

**Why this matters:** A state transition must be atomic. If the `payments` row is updated but the `status_history` insert fails, we have an inconsistent audit trail — the database claims the payment is VALIDATED but there's no record of when or why. The transaction boundary prevents this.

---

## 3. Payment State Machine

### 3.1 State Definitions

| State | Meaning | Allowed Duration | Terminal? |
|-------|---------|-----------------|-----------|
| `CREATED` | Payment submitted, awaiting validation | Indefinite | No |
| `VALIDATED` | All business rules passed, ready to send | Indefinite | No |
| `SENT` | Transmitted to destination | Indefinite | No |
| `COMPLETED` | Successfully processed and confirmed | Forever | **Yes** |
| `FAILED` | Failed at some stage with error code | Indefinite (retryable) | No |

### 3.2 State Diagram

```
┌─────────┐    validate    ┌───────────┐     send     ┌─────────┐   complete   ┌───────────┐
│ CREATED │──────────────→│ VALIDATED │─────────────→│  SENT   │─────────────→│ COMPLETED │
└────┬─────┘               └─────┬─────┘              └───┬─────┘              └───────────┘
     │ fail                      │ fail                   │ fail                    ▲
     │                           │                        │                         │
     ▼                           ▼                        ▼                      TERMINAL
┌─────────┐               ┌──────────────────────────────────┐
│ FAILED  │──────retry───→│ Back to VALIDATED (new key req.) │
└─────────┘               └──────────────────────────────────┘
```

### 3.3 Valid Transition Matrix

| From \ To | CREATED | VALIDATED | SENT | COMPLETED | FAILED |
|-----------|---------|-----------|------|-----------|--------|
| **CREATED** | — | ✅ | ❌ | ❌ | ✅ |
| **VALIDATED** | ❌ | — | ✅ | ❌ | ✅ |
| **SENT** | ❌ | ❌ | — | ✅ | ✅ |
| **COMPLETED** | ❌ | ❌ | ❌ | — | ❌ |
| **FAILED** | ❌ | ✅ (retry) | ❌ | ❌ | — |

### 3.4 Key Design Decisions

1. **COMPLETED is immutable.** Once a payment reaches COMPLETED, no further state changes are allowed. This is enforced at the `StateMachineService` level — `COMPLETED` maps to an empty `Set<PaymentStatus>`.

2. **FAILED is retryable, but only to VALIDATED.** A retry creates a new idempotency key and re-enters the validation flow. This ensures the retried payment goes through the same rigorous validation (and risk assessment, in Phase 4) as a fresh one.

3. **No skip-step transitions.** CREATED → SENT is illegal. Payments must progress through each intermediate state. This prevents accidental processing of unvalidated payments.

4. **Any non-terminal state can fail.** CREATED, VALIDATED, and SENT can all transition to FAILED. This reflects reality — failure can occur at any stage.

### 3.5 State Machine Implementation

```java
// StateMachineService.java
private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
    PaymentStatus.CREATED,   Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
    PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
    PaymentStatus.SENT,      Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
    PaymentStatus.COMPLETED, Set.of(),  // Terminal — no exits
    PaymentStatus.FAILED,    Set.of(PaymentStatus.VALIDATED)  // Retry only
);
```

The state machine is **immutable** — defined as a static final map, never modified at runtime. This eliminates the entire class of bugs related to runtime configuration changes.

---

## 4. Data Model

### 4.1 ER Diagram

```
┌──────────────────────┐       ┌──────────────────────────┐
│      payments        │       │     status_history        │
├──────────────────────┤       ├──────────────────────────┤
│ id (PK, VARCHAR 36)  │──┐    │ id (PK, BIGINT AUTO_INC) │
│ idempotency_key (UQ) │  │    │ payment_id (FK)          │──┘
│ source_account       │  ├───→│ from_status (NULLABLE)   │
│ destination_account  │  │    │ to_status                │
│ amount (DECIMAL)     │  │    │ changed_at (TIMESTAMP)   │
│ currency (VARCHAR 3) │  │    │ reason (TEXT)            │
│ description (TEXT)   │  │    │ error_code               │
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

### 4.2 Table Definitions

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

### 4.3 Data Model Design Decisions

1. **VARCHAR(36) for payment ID** — Uses `UUID.randomUUID().toString()`, generated server-side. Avoids sequential ID leakage and is safe for distributed environments.

2. **DECIMAL(15,2) for amount** — Never use floating-point for money. DECIMAL(15,2) supports amounts up to 9,999,999,999,999.99 with exact precision.

3. **VARCHAR(3) for currency** — ISO 4217 currency codes are always 3 uppercase letters.

4. **status_history.from_status is NULLABLE** — When a payment is first created, there is no "from" status. This is semantically correct: the initial CREATED entry represents creation ex nihilo.

5. **No soft deletes** — Payments are never deleted. The system is append-only at the data layer.

---

## 5. REST API Design

### 5.1 Endpoint Summary

| Method | Path | Purpose | Idempotency Required |
|--------|------|---------|---------------------|
| `POST` | `/api/payments` | Create payment | **Yes** (`Idempotency-Key` header) |
| `GET` | `/api/payments` | List/search payments | No (read-only) |
| `GET` | `/api/payments/{id}` | Get payment detail + history | No (read-only) |
| `GET` | `/api/payments/{id}/history` | Get status history only | No (read-only) |
| `POST` | `/api/payments/{id}/validate` | CREATED → VALIDATED | No (idempotent by state) |
| `POST` | `/api/payments/{id}/send` | VALIDATED → SENT | No (idempotent by state) |
| `POST` | `/api/payments/{id}/complete` | SENT → COMPLETED | No (idempotent by state) |
| `POST` | `/api/payments/{id}/fail` | Mark as FAILED | No (idempotent by state) |
| `POST` | `/api/payments/{id}/retry` | FAILED → VALIDATED | **Yes** (new idempotency key) |

### 5.2 Unified Response Format

**Success:**
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "total": 0
}
```

**Error:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "Amount must be greater than 0",
    "details": { "field": "amount", "value": -100 }
  },
  "total": 0
}
```

### 5.3 HTTP Status Code Mapping

| Status Code | When |
|-------------|------|
| `201 Created` | Payment successfully created |
| `200 OK` | Successful query, state transition, or duplicate idempotency return |
| `400 Bad Request` | Validation failure, invalid state transition |
| `403 Forbidden` | Risk blocked (Phase 4) |
| `404 Not Found` | Payment ID does not exist |
| `409 Conflict` | Duplicate idempotency key with different request body |
| `500 Internal Server Error` | Unexpected processing error |
| `503 Service Unavailable` | Network/downstream failure |

---

## 6. Boundary Conditions & Constraints

### 6.1 Input Boundaries

| Field | Constraint | Enforcement Layer |
|-------|-----------|-------------------|
| `Idempotency-Key` | Required, max 255 chars | Controller (manual check) |
| `sourceAccount` | Required, max 50 chars | DTO (`@NotBlank`, `@Size`) |
| `destinationAccount` | Required, max 50 chars, ≠ sourceAccount | DTO + ValidationService |
| `amount` | > 0, ≤ 1,000,000, max 2 decimal places | DTO (`@DecimalMin`, `@DecimalMax`, `@Digits`) |
| `currency` | Exactly 3 chars, must be USD/EUR/GBP/CNY | DTO + ValidationService |
| `description` | Optional, max 500 chars | DTO (`@Size`) |

### 6.2 System Boundaries

| Boundary | Value | Rationale |
|----------|-------|-----------|
| Max payment amount | 1,000,000 | Prevents fat-finger errors; adjustable per business needs |
| Supported currencies | USD, EUR, GBP, CNY | Training scope; easily extensible |
| Max page size | 100 | Default 20; prevents memory exhaustion |
| Idempotency key retention | 30 days | Balances storage vs. retry window; aligns with Stripe v2 |
| Max description length | 500 chars | Practical limit for payment references |
| Status history retention | Indefinite | Audit trail must be permanent |

### 6.3 Concurrency Boundaries

| Scenario | Handling |
|----------|----------|
| Concurrent POST with same idempotency key | MySQL UNIQUE constraint on `key_record` → first writer wins; second gets `DuplicateKeyException` → rolls back |
| Concurrent state transitions on same payment | `@Transactional` + row-level lock from `SELECT ... FOR UPDATE` (implicit via `updateById`) |
| Read-during-write | MySQL MVCC provides consistent read snapshots |

### 6.4 What We Explicitly Do NOT Support

- ❌ **Authentication/Authorization** — Per project spec: single user, no auth
- ❌ **Real payment gateway integration** — Internal simulation only
- ❌ **Multi-tenancy** — Single logical deployment
- ❌ **Payment reversal/cancellation of COMPLETED** — Terminal state by design
- ❌ **Batch payments** — One payment per request
- ❌ **Scheduled/future-dated payments** — Immediate processing only

---

## 7. Failure Modes & Fallback Strategies

This section documents every possible failure point in the payment lifecycle and the corresponding recovery strategy. This is the most critical section of the design document.

### 7.1 Failure Mode Matrix

| # | Stage | Failure | Impact | Fallback Strategy |
|---|-------|---------|--------|-------------------|
| 1 | Create | Idempotency key already exists | None — existing payment returned | Return existing payment (HTTP 200). Client transparently gets same result. |
| 2 | Create | Validation fails (bad amount, currency, etc.) | Payment not created | Return 400 with error code + field-level details. Client fixes and resubmits with new key. |
| 3 | Create | Database unavailable during INSERT | Payment not persisted | Transaction rolls back. Client retries with same idempotency key (exponential backoff: 1s → 2s → 4s → 8s, max 3 attempts). |
| 4 | Create | Idempotency INSERT fails (concurrent duplicate) | Payment INSERT succeeded, idempotency failed | `@Transactional` ensures BOTH roll back. Client retries with same key → idempotency check finds no record → creates new payment. **Potential duplicate if retries are unlucky** → mitigated by idempotency key uniqueness constraint. |
| 5 | Validate | Invalid transition (e.g., from COMPLETED) | No state change | Return 400 with `INVALID_STATUS_TRANSITION`. Client inspects current state and adjusts. |
| 6 | Validate | Risk BLOCKED (Phase 4) | Payment → FAILED with `RISK_BLOCKED` | Return 403. Payment enters FAILED state with error code. Manual review required before retry. |
| 7 | Send | Simulated send fails | Payment stuck in VALIDATED | Return 500 with `PROCESSING_ERROR`. Payment remains VALIDATED, client can retry `/send`. **Alternative:** auto-transition to FAILED after 3 failed send attempts (configurable). |
| 8 | Complete | Simulated complete fails | Payment stuck in SENT | Return 500 with `PROCESSING_ERROR`. Payment remains SENT, client can retry `/complete`. |
| 9 | Fail | Error code missing | Fail request rejected | Return 400. Fail operation requires an error code for audit trail completeness. |
| 10 | Retry | Idempotency key reused | Retry rejected | Return 409 `DUPLICATE_PAYMENT`. Client must generate a fresh key for each retry attempt. |
| 11 | Any | Database connection lost mid-transaction | Partial update impossible | Transaction guaranteed to roll back by MySQL InnoDB. State unchanged. Client retries. |
| 12 | Any | Network timeout (client ↔ server) | Client uncertain | Client retries with same idempotency key. Server-side idempotency layer handles deduplication. |

### 7.2 Compensation Actions per Stage

While our system is a monolith (not distributed microservices), we still model explicit compensation for each stage. This prepares the system for future decomposition.

| Stage | Forward Action | Compensation (if downstream fails) |
|-------|---------------|-----------------------------------|
| CREATED → VALIDATED | Validate payment fields, run risk assessment | Mark FAILED with error code. No external side effects to undo. |
| VALIDATED → SENT | Simulate transmission | Mark FAILED with `PROCESSING_ERROR`. Payment reverts to retryable state. |
| SENT → COMPLETED | Confirm settlement | Mark FAILED with `NETWORK_ERROR`. SENT is a recoverable state — client can retry. |
| FAILED → VALIDATED (retry) | Create new idempotency key, re-validate | Mark FAILED again with new error details if re-validation fails. |

### 7.3 Retry Strategy

```
CLIENT RETRY POLICY
├── Max attempts: 3
├── Backoff: Exponential with full jitter
│   ├── Attempt 1: immediate
│   ├── Attempt 2: rand(0, 2000ms)
│   └── Attempt 3: rand(0, 4000ms)
├── Idempotency key: REUSE SAME KEY for all retries of the same logical operation
├── On 4xx: DO NOT RETRY (client error, same request will fail again)
├── On 5xx: RETRY (server error, may succeed on retry)
└── On timeout: RETRY (request may or may not have been processed)
```

**Critical rule:** The client MUST reuse the same idempotency key on retry. Generating a new key defeats the idempotency guarantee and can create duplicate payments.

### 7.4 Server-Side Recovery: Background Completer (Future Enhancement)

In a production system, a background process would scan for payments stuck in non-terminal states beyond a grace period:

```
Completer Process (runs every 5 minutes)
├── Find payments where:
│   ├── status IN ('VALIDATED', 'SENT')
│   └── updated_at < NOW() - 5 minutes
├── For each stuck payment:
│   ├── Check if downstream operation actually completed
│   ├── If yes → advance to next state
│   └── If no → mark FAILED with PROCESSING_TIMEOUT
```

This is noted here as a design pattern for future productionization. Not implemented in the training scope.

---

## 8. Idempotency Design

### 8.1 The Problem

```
Network failures have three indistinguishable outcomes from the client's perspective:

  Client ──POST──→ [Network Cloud] ──→ Server
  
  Outcome A: Request never reaches server        → Safe to retry
  Outcome B: Server processes, response lost     → Retry creates DUPLICATE
  Outcome C: Server crashes mid-processing       → State is UNKNOWN
```

Without idempotency, the client cannot distinguish A from B. Retrying creates real business duplicates.

### 8.2 The Solution: Idempotency Key Pattern

```
Client picks a UUID per logical operation.
Server guarantees: same key → same observable outcome.

  Key: "abc-123" + Body → Server processes → Stores: {key: "abc-123", result: {...}}
  Key: "abc-123" + Body → Server looks up   → Returns stored result (no re-processing)
```

### 8.3 Implementation

**Client side:**
```javascript
const idempotencyKey = crypto.randomUUID();
const response = await axios.post('/api/payments', body, {
  headers: { 'Idempotency-Key': idempotencyKey }
});
```

**Server side (two-phase approach):**

```
Phase 1 — Fast lookup (READ):
  SELECT payment_id FROM idempotency_keys WHERE key_record = ?
  → Found? Return existing payment (HTTP 200). Done.
  → Not found? Proceed to Phase 2.

Phase 2 — Atomic create (WRITE, within @Transactional):
  INSERT INTO payments (...)
  INSERT INTO idempotency_keys (key_record, payment_id)
  INSERT INTO status_history (...)
  → If DuplicateKeyException on idempotency_keys: ROLLBACK ALL
    → This handles the race where two requests with the same key
      pass Phase 1 simultaneously.
```

### 8.4 Idempotency Key Lifecycle

| Phase | Duration | Behavior |
|-------|----------|----------|
| Active | 0–30 days | Key returns existing payment |
| Expired | 30+ days | Key deleted (cleanup job); new request with same key creates new payment |

### 8.5 What Idempotency Does NOT Cover

- **Different keys, same logical payment.** The system cannot detect that `key-A` and `key-B` represent the same business intent. The client is responsible for key management.
- **Same key, different body.** Currently not validated. Future enhancement: hash the request body and compare; reject if mismatch.
- **Keys across different payment operations.** A key used for `create` cannot be used for `retry`. Each operation type should use its own key space.

---

## 9. Error Handling Framework

### 9.1 Error Classification

```
ERROR HIERARCHY
├── 4xx — Client Error (fix your request)
│   ├── 400 VALIDATION_FAILED        — Field-level validation failure
│   ├── 400 INVALID_AMOUNT           — Amount out of bounds
│   ├── 400 INVALID_ACCOUNT          — Account format or equality check failed
│   ├── 400 INVALID_CURRENCY         — Unsupported currency code
│   ├── 400 INVALID_STATUS_TRANSITION— State machine rejected the transition
│   ├── 403 RISK_BLOCKED             — AI risk assessment blocked the payment
│   ├── 404 PAYMENT_NOT_FOUND        — Payment ID does not exist
│   └── 409 DUPLICATE_PAYMENT        — Idempotency key already consumed
│
└── 5xx — Server Error (retry with same key)
    ├── 500 PROCESSING_ERROR         — Unexpected internal error
    └── 503 NETWORK_ERROR            — Simulated downstream failure
```

### 9.2 Exception Flow

```
Controller
  │
  ├── @Valid on DTO fails
  │   └── MethodArgumentNotValidException
  │       └── GlobalExceptionHandler.handleValidationException()
  │           └── 400 + field-level error details
  │
  ├── Business logic throws BusinessException
  │   └── GlobalExceptionHandler.handleBusinessException()
  │       └── Maps ErrorCode → HTTP status + unified error response
  │
  └── Unexpected exception
      └── GlobalExceptionHandler.handleGenericException()
          └── 500 + PROCESSING_ERROR (message sanitized in production)
```

---

## 10. AI Risk Assessment (Phase 4)

### 10.1 Integration Point

The AI risk engine fires during the `CREATED → VALIDATED` transition, AFTER basic field validation passes but BEFORE the status change is committed.

```
POST /api/payments/{id}/validate
  │
  ├── 1. Find payment
  ├── 2. Validate state transition (CREATED → VALIDATED)
  ├── 3. AI Risk Assessment ←── INTEGRATION POINT
  │     ├── Fetch account history (last 90 days)
  │     ├── Layer 1: Rule engine (5 rules)
  │     ├── Layer 2: Statistical anomaly detection
  │     ├── Layer 3: LLM inference (optional, for HIGH risk only)
  │     └── Output: riskScore (0-100) + recommendation
  │
  ├── 4. Decision:
  │     ├── BLOCK  → transition to FAILED (RISK_BLOCKED)
  │     ├── REVIEW → transition to VALIDATED (flag for manual review)
  │     └── APPROVE → transition to VALIDATED (normal flow)
  │
  └── 5. Record risk_assessment + status_history (atomic)
```

### 10.2 Rule Engine (Layer 1)

| Rule | Trigger Condition | Score |
|------|-------------------|-------|
| AMOUNT_ANOMALY | Amount > 5× account historical average | 35 |
| UNUSUAL_TIME | Transaction between 00:00–06:00 | 25 |
| NEW_PAYEE | First transfer to this destination account | 30 |
| VELOCITY_SPIKE | ≥ 5 transactions in 10 minutes | 40 |
| HIGH_AMOUNT | Single transaction > $50,000 | 25 |

### 10.3 Statistical Detection (Layer 2)

- **Z-score:** Triggers when |z| > 2 (significant) or |z| > 3 (extreme)
- **IQR:** Triggers when amount > Q3 + 1.5×IQR
- Minimum 10 historical transactions required for statistical validity

### 10.4 Risk Decision Matrix

| Risk Score | Level | Recommendation | Action |
|------------|-------|---------------|--------|
| 0–39 | LOW | APPROVE | Normal transition to VALIDATED |
| 40–69 | MEDIUM | REVIEW | Transition to VALIDATED; flagged for manual review |
| 70–100 | HIGH | BLOCK | Transition to FAILED with `RISK_BLOCKED` error code |

---

## 11. Observability & Monitoring

### 11.1 What to Monitor (Production Readiness)

| Metric | Signal | Alert Threshold |
|--------|--------|-----------------|
| Payment creation rate | Payments created / minute | > 50% deviation from baseline |
| Failure rate | FAILED payments / total payments | > 10% in 5-minute window |
| Avg processing time | Creation → COMPLETED duration | > 30 seconds (p50) |
| Idempotency hit rate | Duplicate keys / total keys | > 5% (may indicate client retry storms) |
| Risk block rate | RISK_BLOCKED / total VALIDATED | > 20% (may indicate rule misconfiguration) |
| DB connection pool | Active / idle connections | > 80% utilization |

### 11.2 Logging Standards

```
LEVEL   WHEN
ERROR   Unexpected exception, transaction rollback, database failure
WARN    Business exception (validation failure, invalid transition), risk BLOCK
INFO    Every state transition: "Payment {id} CREATED → VALIDATED"
DEBUG   Full request/response bodies (disabled in production)
```

---

## 12. Security Considerations

### 12.1 Current State (Training Project)

- No authentication (per project spec)
- No user session management
- No role-based access control

### 12.2 Production Hardening Checklist

For production deployment, the following would be required:

- [ ] **Authentication:** OAuth 2.0 / JWT-based auth for all endpoints
- [ ] **Authorization:** Payment ownership — users can only access their own payments
- [ ] **Input sanitization:** Prevent SQL injection (MyBatis-Plus parameterized queries already handle this)
- [ ] **Rate limiting:** Per-user rate limits on POST endpoints (prevent abuse)
- [ ] **HTTPS:** TLS 1.3 for all communications
- [ ] **Secrets management:** Database credentials via environment variables / vault, not in `application.yml`
- [ ] **Audit logging:** Log all access to payment data for compliance
- [ ] **PCI DSS:** Tokenize account numbers; never store raw PANs

### 12.3 Data Sensitivity

| Data | Classification | Handling |
|------|---------------|----------|
| Payment ID | Internal | Safe to log |
| Account numbers | **Sensitive** | Mask in logs: `ACC-***-001` |
| Amount + Currency | Internal | Safe to log |
| Description | **Potentially PII** | Do not log in production |
| Error codes | Internal | Safe to log |

---

## 13. Development Phases

| Phase | Focus | Key Deliverables | Status |
|-------|-------|-----------------|--------|
| **Phase 1** | Backend Core | Project scaffold, DB schema, entities, mappers, state machine, idempotency, CRUD, validation, exception handling, Swagger | ✅ Complete |
| **Phase 2** | Verification | Unit tests (state machine, service), integration tests (full lifecycle, fail+retry), manual smoke tests | In Progress |
| **Phase 3** | Frontend | Vue 3 project, create/list/detail pages, API integration, status timeline, action buttons | Pending |
| **Phase 4** | AI Anomaly Detection | Rule engine (5 rules), statistical detection (z-score + IQR), risk_assessments table, LLM inference (optional) | Pending |

---

## 14. References

### 14.1 Industry Best Practices

1. **Stripe API — Idempotent Requests.** *"An idempotency key is a unique value generated by the client which the server uses to recognize subsequent retries of the same request."* [docs.stripe.com/api/idempotent_requests](https://docs.stripe.com/api/idempotent_requests)

2. **Stripe — Idempotency & Reliability Patterns.** *"Commit local state before initiating any foreign-state mutation."* Key concepts: atomic phases, recovery points as DAG, transactionally-staged job drains.

3. **Payment System Design (System Design Handbook).** *"UPDATE balance instead of posting entries is a fatal mistake."* Core concepts: double-entry ledger, saga orchestration, compensation transactions.

4. **Saga Pattern.** *"Compensations are explicit code, not implicit rollback."* Used for distributed transactions across payment stages when monolithic → microservice decomposition occurs.

### 14.2 Project Documents

- `payment_processing.md` — Original training requirements
- `Dev_Guide_Payment_Processing_AI_Anomaly_Detection.md` — Development guide with code examples
- `docs/superpowers/specs/2026-07-24-payment-processing-system-design.md` — Original design spec
- `docs/superpowers/plans/2026-07-24-payment-processing-system-plan.md` — Implementation plan

---

## Appendix A: Glossary

| Term | Definition |
|------|-----------|
| **Idempotency** | Property where multiple identical requests produce the same result as a single request |
| **Idempotency Key** | Client-generated unique value sent via HTTP header to enable safe retries |
| **State Machine** | Formal model defining valid states and transitions for payment lifecycle |
| **Saga** | Pattern for distributed transactions using a sequence of local transactions + compensating actions |
| **Compensation** | An undo-action that reverses a previously committed step in a saga |
| **Audit Trail** | Append-only chronological record of all state changes |
| **Terminal State** | A state from which no further transitions are allowed (COMPLETED) |
| **Exponential Backoff** | Retry strategy where wait time doubles after each failure |
| **Jitter** | Random variation added to retry delays to prevent thundering herd |

---

## Appendix B: Quick Reference — All Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `VALIDATION_FAILED` | 400 | Field validation failed |
| `INSUFFICIENT_FUNDS` | 400 | Source account has insufficient funds |
| `INVALID_ACCOUNT` | 400 | Account number invalid or accounts equal |
| `INVALID_CURRENCY` | 400 | Currency code not supported |
| `INVALID_AMOUNT` | 400 | Amount zero, negative, or exceeds limit |
| `DUPLICATE_PAYMENT` | 409 | Idempotency key already used |
| `INVALID_STATUS_TRANSITION` | 400 | State machine rejected the transition |
| `PAYMENT_NOT_FOUND` | 404 | Payment ID does not exist |
| `PROCESSING_ERROR` | 500 | Unexpected internal error |
| `NETWORK_ERROR` | 503 | Downstream communication failure |
| `RISK_BLOCKED` | 403 | AI risk assessment blocked (Phase 4) |
