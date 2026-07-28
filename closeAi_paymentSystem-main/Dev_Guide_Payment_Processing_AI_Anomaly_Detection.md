# Payment Processing System + AI Anomaly Detection Agent — Development Guide

## 1. Project Overview

Build a Payment Processing REST API system that manages the complete lifecycle of financial payments (create → validate → send → complete/fail) with full state change audit trails. On top of this, integrate an **AI Agent for anomaly detection** that performs risk assessment on each transaction during the validation phase and flags suspicious payments.

### Core Deliverables

| Deliverable | Description |
|-------------|-------------|
| Payment REST API | Payment creation, querying, lifecycle tracking, status history |
| AI Risk Assessment Engine | Scores payment risk at VALIDATED stage, flags high-risk transactions |
| Audit Trail System | Records every state change and risk assessment result |
| Web Frontend | Payment creation, status viewing, search/filter, risk display |
| API Documentation | Swagger/OpenAPI |
| Test Suite | Unit tests + integration tests + anomaly detection tests |

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Web Frontend (React/Vue)              │
│   Create Payment │ Payment List │ Status Timeline │     │
│   Risk Detail Panel                                      │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP/REST
┌───────────────────────▼─────────────────────────────────┐
│                  REST API Layer (Express/Flask)          │
│   POST /payments │ GET /payments │ GET /payments/:id     │
│   POST /payments/:id/process │ GET /payments/:id/history │
├──────────────────────────────────────────────────────────┤
│                    Business Logic Layer                  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Payment      │  │ State Machine│  │ Idempotency   │  │
│  │ Validation   │  │ Service      │  │ Service       │  │
│  └──────┬───────┘  └──────────────┘  └───────────────┘  │
│         │                                                │
│         ▼                                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │          AI Risk Assessment Agent                │   │
│  │  ┌────────────┐ ┌────────────┐ ┌──────────────┐ │   │
│  │  │ Rule Engine│ │ Statistical│ │ LLM Inference│ │   │
│  │  │ (Baseline) │ │ (Anomaly)  │ │ (Optional)   │ │   │
│  │  └────────────┘ └────────────┘ └──────────────┘ │   │
│  │    → Output: risk score (0-100) + risk factors   │   │
│  └──────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────────┤
│                    Data Access Layer (Repository)        │
│   PaymentRepo │ StatusHistoryRepo │ RiskAssessmentRepo   │
│   AccountHistoryRepo (for AI to read)                   │
└───────────────────────┬──────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────┐
│              Database (PostgreSQL / MySQL)               │
│  payments │ status_history │ risk_assessments │ accounts │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Payment State Machine

### State Definition

```
CREATED → VALIDATED → SENT → COMPLETED
    ↓         ↓          ↓
  FAILED    FAILED     FAILED
```

### Valid State Transition Matrix

| Current \ Target | CREATED | VALIDATED | SENT | COMPLETED | FAILED |
|---|---|---|---|---|---|
| CREATED | - | ✅ | ❌ | ❌ | ✅ |
| VALIDATED | ❌ | - | ✅ | ❌ | ✅ |
| SENT | ❌ | ❌ | - | ✅ | ✅ |
| COMPLETED | ❌ | ❌ | ❌ | - | ❌ |
| FAILED | ❌ | ✅ (retry) | ❌ | ❌ | - |

> Note: FAILED → VALIDATED represents a retry scenario, which requires a new idempotency key.

### State Transition Rules

```javascript
const VALID_TRANSITIONS = {
  CREATED:   ['VALIDATED', 'FAILED'],
  VALIDATED: ['SENT', 'FAILED'],
  SENT:      ['COMPLETED', 'FAILED'],
  COMPLETED: [],  // Terminal state
  FAILED:    ['VALIDATED'],  // Retry only
};

function canTransition(from, to) {
  return VALID_TRANSITIONS[from]?.includes(to) ?? false;
}
```

---

## 4. Core Features & Implementation

### Feature 1: Create Payment

**API Design:**

```
POST /api/payments
Headers:
  Idempotency-Key: <client-generated-uuid>
Body:
{
  "sourceAccount": "ACC-001",
  "destinationAccount": "ACC-002",
  "amount": 5000.00,
  "currency": "USD",
  "description": "August rent"
}
```

**Implementation Points:**

1. **Idempotency check**: Query `idempotency_key` table first; if it already exists, return the existing payment (HTTP 200) without creating a duplicate
2. **Field validation** (at CREATED stage):
   - amount > 0 and ≤ 1,000,000
   - currency is a valid ISO 4217 code (USD/EUR/GBP/CNY)
   - sourceAccount ≠ destinationAccount
   - Account number format is valid
3. **Write to database**: payments table + status_history table (record CREATED status + timestamp)
4. **Return result**: HTTP 201 + complete payment object

**Data Model:**

```sql
CREATE TABLE payments (
  id              VARCHAR(36) PRIMARY KEY,       -- UUID
  idempotency_key VARCHAR(64) UNIQUE NOT NULL,   -- Client-provided
  source_account  VARCHAR(50) NOT NULL,
  destination_account VARCHAR(50) NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  currency        VARCHAR(3) NOT NULL,
  description     TEXT,
  status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE status_history (
  id          SERIAL PRIMARY KEY,
  payment_id  VARCHAR(36) REFERENCES payments(id),
  from_status VARCHAR(20),
  to_status   VARCHAR(20) NOT NULL,
  changed_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  reason      TEXT,           -- Failure reason / transition reason
  error_code  VARCHAR(50)     -- Error code on failure
);

CREATE TABLE idempotency_keys (
  key         VARCHAR(64) PRIMARY KEY,
  payment_id  VARCHAR(36) REFERENCES payments(id),
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

### Feature 2: AI Risk Assessment (Core Highlight)

**Trigger timing:** When a payment transitions from `CREATED → VALIDATED`, executed after validation passes but before the state change is committed.

**API Design:**

```
POST /api/payments/{id}/validate
→ Triggers validation flow
→ After validation passes, calls AI Agent for risk assessment
→ Decides next action based on risk score
```

**AI Agent Architecture (Three-Layer Progressive):**

```
┌─────────────────────────────────────────────────────────┐
│                AI Risk Assessment Agent                   │
│                                                          │
│  Input: payment + account_history (last 90 days)         │
│                                                          │
│  Layer 1: Rule Engine                      ← Required    │
│    → Heuristic scoring based on hardcoded rules          │
│    → Fastest execution, covers known risk patterns       │
│                                                          │
│  Layer 2: Statistical Anomaly Detection    ← Recommended │
│    → Statistical deviation detection from account history│
│    → z-score / moving average / IQR                      │
│                                                          │
│  Layer 3: LLM Inference (Optional)         ← Bonus       │
│    → Calls large language model for contextual analysis  │
│    → Handles complex scenarios beyond rules & statistics │
│                                                          │
│  Output: { riskScore: 0-100, riskLevel: LOW/MEDIUM/HIGH,│
│            riskFactors: [...], recommendation: APPROVE/  │
│            REVIEW/BLOCK }                                │
└─────────────────────────────────────────────────────────┘
```

**Layer 1 — Rule Engine Implementation:**

```javascript
// Rule engine: each rule evaluates independently, returns a sub-score
const riskRules = [
  {
    name: 'AMOUNT_ANOMALY',
    description: 'Amount far exceeds account historical average',
    evaluate(payment, history) {
      if (history.length === 0) return { score: 15, detail: 'No history data, new account' };
      const avg = history.reduce((s, t) => s + t.amount, 0) / history.length;
      if (payment.amount > avg * 5) return { score: 35, detail: `Amount is ${(payment.amount/avg).toFixed(1)}x historical average` };
      if (payment.amount > avg * 3) return { score: 20, detail: `Amount is ${(payment.amount/avg).toFixed(1)}x historical average` };
      return { score: 0, detail: 'Amount within normal range' };
    }
  },
  {
    name: 'UNUSUAL_TIME',
    description: 'Transaction at unusual time',
    evaluate(payment, history) {
      const hour = new Date(payment.created_at).getHours();
      if (hour >= 0 && hour < 6) return { score: 25, detail: `Transaction at ${hour}:00 (late night)` };
      return { score: 0, detail: 'Transaction during normal hours' };
    }
  },
  {
    name: 'NEW_PAYEE',
    description: 'Transfer to a new payee',
    evaluate(payment, history) {
      const knownPayees = new Set(history.map(t => t.destination_account));
      if (!knownPayees.has(payment.destination_account)) {
        return { score: 30, detail: 'First transfer to this payee' };
      }
      return { score: 0, detail: 'Known payee' };
    }
  },
  {
    name: 'VELOCITY_SPIKE',
    description: 'High-frequency transactions in short period',
    evaluate(payment, history) {
      const last10min = history.filter(t =>
        Date.now() - new Date(t.created_at).getTime() < 10 * 60 * 1000
      );
      if (last10min.length >= 5) return { score: 40, detail: `${last10min.length} transactions in last 10 minutes` };
      if (last10min.length >= 3) return { score: 20, detail: `${last10min.length} transactions in last 10 minutes` };
      return { score: 0, detail: 'Normal transaction frequency' };
    }
  },
  {
    name: 'HIGH_AMOUNT',
    description: 'Absolute high-value transaction',
    evaluate(payment, history) {
      if (payment.amount > 50000) return { score: 25, detail: 'Single transaction over $50,000' };
      if (payment.amount > 10000) return { score: 10, detail: 'Single transaction over $10,000' };
      return { score: 0, detail: 'Normal amount' };
    }
  }
];

function assessRisk(payment, accountHistory) {
  let totalScore = 0;
  const factors = [];

  for (const rule of riskRules) {
    const result = rule.evaluate(payment, accountHistory);
    totalScore += result.score;
    if (result.score > 0) {
      factors.push({ rule: rule.name, score: result.score, detail: result.detail });
    }
  }

  totalScore = Math.min(totalScore, 100); // Cap at 100

  let level, recommendation;
  if (totalScore >= 70) { level = 'HIGH'; recommendation = 'BLOCK'; }
  else if (totalScore >= 40) { level = 'MEDIUM'; recommendation = 'REVIEW'; }
  else { level = 'LOW'; recommendation = 'APPROVE'; }

  return { riskScore: totalScore, riskLevel: level, riskFactors: factors, recommendation };
}
```

**Layer 2 — Statistical Anomaly Detection Implementation:**

```javascript
// Statistical anomaly detection: based on z-score and IQR
function statisticalAnomalyCheck(payment, history) {
  if (history.length < 10) {
    return { score: 0, detail: 'Insufficient history data, skipping statistical detection' };
  }

  const amounts = history.map(t => t.amount);

  // z-score detection
  const mean = amounts.reduce((a, b) => a + b, 0) / amounts.length;
  const stdDev = Math.sqrt(amounts.reduce((s, a) => s + (a - mean) ** 2, 0) / amounts.length);
  const zScore = stdDev > 0 ? Math.abs((payment.amount - mean) / stdDev) : 0;

  // IQR detection
  const sorted = [...amounts].sort((a, b) => a - b);
  const q1 = sorted[Math.floor(sorted.length * 0.25)];
  const q3 = sorted[Math.floor(sorted.length * 0.75)];
  const iqr = q3 - q1;
  const upperBound = q3 + 1.5 * iqr;

  let score = 0;
  const details = [];

  if (zScore > 3) { score += 30; details.push(`z-score=${zScore.toFixed(2)} (extreme deviation)`); }
  else if (zScore > 2) { score += 15; details.push(`z-score=${zScore.toFixed(2)} (significant deviation)`); }

  if (payment.amount > upperBound) { score += 20; details.push(`Exceeds IQR upper bound (${upperBound.toFixed(2)})`); }

  return { score: Math.min(score, 50), detail: details.join('; ') || 'Statistical detection normal' };
}
```

**Layer 3 — LLM Inference Implementation (Optional):**

```javascript
// LLM inference: sends transaction context to a large language model for comprehensive analysis
async function llmRiskAssessment(payment, history, ruleResult) {
  const prompt = `
You are a payment fraud control expert. Please analyze the risk of the following payment transaction:

Current payment: ${JSON.stringify({ amount: payment.amount, currency: payment.currency, destination: payment.destination_account, time: payment.created_at })}

Account recent transaction statistics:
- Historical transaction count: ${history.length}
- Average amount: ${(history.reduce((s,t)=>s+t.amount,0)/Math.max(history.length,1)).toFixed(2)}
- Number of known payees: ${new Set(history.map(t=>t.destination_account)).size}

Rule engine score: ${ruleResult.riskScore}/100, risk factors: ${JSON.stringify(ruleResult.riskFactors)}

Please return in JSON format:
{
  "adjustedScore": 0-100,
  "reasoning": "Your analysis and reasoning process",
  "additionalRiskFactors": ["..."],
  "recommendation": "APPROVE" | "REVIEW" | "BLOCK"
}
`;

  const response = await callLLM(prompt);  // Call OpenAI/Claude API
  return JSON.parse(response);
}
```

**Risk Assessment Data Model:**

```sql
CREATE TABLE risk_assessments (
  id              SERIAL PRIMARY KEY,
  payment_id      VARCHAR(36) REFERENCES payments(id),
  risk_score      INTEGER NOT NULL,          -- 0-100
  risk_level      VARCHAR(10) NOT NULL,      -- LOW / MEDIUM / HIGH
  recommendation  VARCHAR(10) NOT NULL,      -- APPROVE / REVIEW / BLOCK
  risk_factors    JSONB NOT NULL,            -- Array of risk factor details
  assessed_at     TIMESTAMP NOT NULL DEFAULT NOW(),
  agent_version   VARCHAR(20) NOT NULL       -- Assessment engine version
);
```

**Risk Assessment & State Machine Integration Logic:**

```javascript
async function validatePayment(paymentId) {
  const payment = await paymentRepo.findById(paymentId);

  // 1. Basic validation (amount, account, currency)
  const validationResult = validatePaymentFields(payment);
  if (!validationResult.valid) {
    await transitionStatus(paymentId, 'CREATED', 'FAILED', validationResult.errorCode);
    return { success: false, error: validationResult };
  }

  // 2. Fetch account historical transactions (for AI analysis)
  const accountHistory = await paymentRepo.findByAccount(payment.source_account, 90); // Last 90 days

  // 3. AI risk assessment
  const ruleResult = assessRisk(payment, accountHistory);          // Layer 1
  const statResult = statisticalAnomalyCheck(payment, accountHistory); // Layer 2

  let finalScore = Math.min(ruleResult.riskScore + statResult.score, 100);
  let finalRecommendation = ruleResult.recommendation;

  // Layer 3 (optional)
  if (finalScore >= 40) {
    const llmResult = await llmRiskAssessment(payment, accountHistory, ruleResult);
    finalScore = llmResult.adjustedScore;
    finalRecommendation = llmResult.recommendation;
  }

  // 4. Save risk assessment result
  await riskAssessmentRepo.save({
    payment_id: paymentId,
    risk_score: finalScore,
    risk_level: finalScore >= 70 ? 'HIGH' : finalScore >= 40 ? 'MEDIUM' : 'LOW',
    recommendation: finalRecommendation,
    risk_factors: [...ruleResult.riskFactors, { rule: 'STATISTICAL', ...statResult }],
  });

  // 5. Determine state transition based on risk
  if (finalRecommendation === 'BLOCK') {
    await transitionStatus(paymentId, 'CREATED', 'FAILED', 'RISK_BLOCKED');
    return { success: false, riskBlocked: true, riskScore: finalScore };
  }

  // REVIEW: flag for manual review but still transition to VALIDATED
  // APPROVE: normal transition to VALIDATED
  await transitionStatus(paymentId, 'CREATED', 'VALIDATED');
  return { success: true, riskScore: finalScore, recommendation: finalRecommendation };
}
```

---

### Feature 3: State Transition Handling

**API Design:**

```
POST /api/payments/{id}/send       → VALIDATED → SENT
POST /api/payments/{id}/complete    → SENT → COMPLETED
POST /api/payments/{id}/fail        → ANY → FAILED
POST /api/payments/{id}/retry       → FAILED → VALIDATED (re-validate + risk assessment)
```

**Implementation Points:**

```javascript
async function transitionStatus(paymentId, fromStatus, toStatus, errorCode = null, reason = null) {
  // 1. Verify current status
  const payment = await paymentRepo.findById(paymentId);
  if (payment.status !== fromStatus) {
    throw new Error(`INVALID_STATUS_TRANSITION: current status is ${payment.status}, expected ${fromStatus}`);
  }

  // 2. Verify transition legality
  if (!canTransition(fromStatus, toStatus)) {
    throw new Error(`INVALID_STATUS_TRANSITION: ${fromStatus}→${toStatus} is not allowed`);
  }

  // 3. Update status + write audit log within a transaction
  await db.transaction(async (tx) => {
    await paymentRepo.updateStatus(paymentId, toStatus, tx);
    await statusHistoryRepo.save({
      payment_id: paymentId,
      from_status: fromStatus,
      to_status: toStatus,
      error_code: errorCode,
      reason: reason,
    }, tx);
  });
}
```

---

### Feature 4: Payment Query & Filtering

**API Design:**

```
GET /api/payments?status=FAILED&currency=USD&page=1&limit=20
GET /api/payments/{id}
GET /api/payments/{id}/history          -- Status change history
GET /api/payments/{id}/risk-assessment  -- Risk assessment details
```

---

### Feature 5: Error Handling System

**Unified Error Response Format:**

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Amount must be greater than 0",
    "details": {
      "field": "amount",
      "value": -100
    }
  }
}
```

**Error Code Table:**

| Error Code | Description | HTTP Status |
|-----------|-------------|-------------|
| VALIDATION_FAILED | Payment validation check failed | 400 |
| INSUFFICIENT_FUNDS | Source account has insufficient funds | 400 |
| INVALID_ACCOUNT | Account number invalid or does not exist | 400 |
| INVALID_CURRENCY | Currency code not supported | 400 |
| INVALID_AMOUNT | Amount is zero, negative, or invalid | 400 |
| DUPLICATE_PAYMENT | Payment with same idempotency key already exists | 409 |
| INVALID_STATUS_TRANSITION | Status transition is illegal | 400 |
| PAYMENT_NOT_FOUND | Payment ID does not exist | 404 |
| PROCESSING_ERROR | Internal processing error | 500 |
| NETWORK_ERROR | Network communication failure | 503 |
| RISK_BLOCKED | AI risk assessment blocked the payment | 403 |

---

## 5. Frontend Features

### Page 1: Create Payment

- Form: source account, destination account, amount, currency, description
- Real-time form validation
- Display payment ID and risk assessment result after submission

### Page 2: Payment List

- Table columns: ID, amount, currency, status, risk level, created time
- Filter by status (CREATED / VALIDATED / SENT / COMPLETED / FAILED)
- Filter by risk level (LOW / MEDIUM / HIGH)
- Search (by ID or description)
- Pagination

### Page 3: Payment Detail

- Basic info card (ID, amount, status, accounts)
- **Risk assessment panel** (risk score gauge + risk factors list)
- Status history timeline (each status + timestamp + duration)
- If failed: display error code and description
- Action buttons (dynamically shown based on current status): Validate → Send → Complete / Mark Failed / Retry

### Page 4: Risk Monitoring Dashboard (Bonus)

- High-risk payment list
- Risk score distribution chart
- Blocked payment statistics

---

## 6. Development Steps

### Phase 1: Basic Skeleton (Day 1-2)

1. Initialize project structure (backend + frontend)
2. Create Git repository, push skeleton code
3. Set up database, create table structure
4. Implement minimal CRUD: create payment (only id + amount + currency + status)
5. Verify end-to-end flow works

### Phase 2: State Machine + Audit (Day 3-4)

1. Implement state transition logic + legality validation
2. Implement status_history audit log
3. Implement idempotency mechanism
4. Implement field validation (amount, account, currency)

### Phase 3: AI Risk Assessment (Day 5-6)

1. Implement Layer 1 rule engine (5 rules)
2. Implement Layer 2 statistical anomaly detection
3. Create risk_assessments table
4. Integrate risk assessment into validation flow
5. Write risk assessment API endpoint
6. (Optional) Implement Layer 3 LLM inference

### Phase 4: Frontend (Day 7-8)

1. Create payment form
2. Payment list + filtering
3. Payment detail + status timeline
4. Risk assessment panel
5. Risk monitoring dashboard

### Phase 5: Testing + Documentation (Day 9-10)

1. Unit tests (state machine, validation, risk assessment)
2. Integration tests (full payment lifecycle)
3. Anomaly detection tests (construct various risk scenarios)
4. Swagger/OpenAPI documentation
5. Project demo preparation

---

## 7. Recommended Tech Stack

| Layer | Recommended | Alternative |
|-------|-------------|-------------|
| Backend Framework | Express.js (Node.js) | Flask (Python) / Spring Boot (Java) |
| Database | PostgreSQL | MySQL / SQLite (dev) |
| ORM | Prisma / Sequelize | SQLAlchemy (Flask) / JPA (Spring) |
| Frontend Framework | React + Vite | Vue 3 / Angular |
| Chart Library | Recharts / Chart.js | ECharts |
| API Docs | Swagger UI + swagger-autogen | Hand-written OpenAPI |
| LLM (Optional) | OpenAI API / Claude API | Ollama (local model) |
| Testing | Jest (Node) / PyTest (Python) | Mocha |

---

## 8. Test Scenarios

### Happy Path

1. Create payment → validate (low risk) → send → complete
2. Verify complete status history is recorded

### Validation Failures

3. Negative amount → VALIDATION_FAILED
4. Source account = destination account → VALIDATION_FAILED
5. Invalid currency code → VALIDATION_FAILED

### Idempotency

6. Same Idempotency-Key submitted twice → second request returns existing payment

### State Transitions

7. COMPLETED → CREATED → INVALID_STATUS_TRANSITION
8. FAILED → VALIDATED (retry) → success

### AI Risk Detection

9. Large transaction (>$50,000) → risk score ≥ 25
10. Late night + new payee + large amount → risk score ≥ 70 → BLOCK
11. Normal amount + known payee + regular hours → risk score < 20 → APPROVE
12. 6 transactions within 10 minutes → VELOCITY_SPIKE triggered
13. Historical average $100, sudden $5,000 → AMOUNT_ANOMALY + STATISTICAL both triggered
