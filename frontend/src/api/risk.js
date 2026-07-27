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
