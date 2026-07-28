import api from './index'

/** PUT /api/payments/{id} — update payment (only CREATED or FAILED) */
export function updatePayment(id, data) {
  return api.put(`/payments/${id}`, data)
}

/** POST /api/payments — create a new payment */
export function createPayment(data, idempotencyKey) {
  return api.post('/payments', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/** GET /api/payments — list/search payments */
export function listPayments(params = {}) {
  return api.get('/payments', { params })
}

/** GET /api/payments/{id} — get payment detail with status history */
export function getPayment(id) {
  return api.get(`/payments/${id}`)
}

/** GET /api/payments/{id}/history — get slim status history array */
export function getPaymentHistory(id) {
  return api.get(`/payments/${id}/history`)
}

/** POST /api/payments/{id}/validate — CREATED → VALIDATED */
export function validatePayment(id) {
  return api.post(`/payments/${id}/validate`)
}

/** POST /api/payments/{id}/send — VALIDATED → SENT */
export function sendPayment(id) {
  return api.post(`/payments/${id}/send`)
}

/** POST /api/payments/{id}/complete — SENT → COMPLETED */
export function completePayment(id) {
  return api.post(`/payments/${id}/complete`)
}

/** POST /api/payments/{id}/fail — any → FAILED */
export function failPayment(id, errorCode, reason) {
  return api.post(`/payments/${id}/fail`, { errorCode, reason })
}

/** POST /api/payments/{id}/retry — FAILED → VALIDATED */
export function retryPayment(id, idempotencyKey) {
  return api.post(`/payments/${id}/retry`, null, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

/** POST /api/payments/{id}/cancel — cancel before COMPLETED */
export function cancelPayment(id) {
  return api.post(`/payments/${id}/cancel`)
}

/** POST /api/payments/{id}/reverse — reverse COMPLETED payment */
export function reversePayment(id) {
  return api.post(`/payments/${id}/reverse`)
}

/** POST /api/payments/batch — batch create payments */
export function createBatch(payments) {
  return api.post('/payments/batch', { payments })
}

export function getExchangeRateQuote(from, to, amount) {
  return api.get('/exchange-rates/quote', { params: { from, to, amount } })
}

/** GET /api/reports/daily-summary */
export function getDailySummary() {
  return api.get('/reports/daily-summary')
}

/** GET /api/reports/success-rate */
export function getSuccessRate() {
  return api.get('/reports/success-rate')
}

/** GET /api/reports/avg-processing-time */
export function getAvgProcessingTime() {
  return api.get('/reports/avg-processing-time')
}
