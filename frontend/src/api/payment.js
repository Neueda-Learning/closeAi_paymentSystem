import api from './index'

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
