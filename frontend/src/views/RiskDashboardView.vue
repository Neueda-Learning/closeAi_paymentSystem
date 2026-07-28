<template>
  <div class="risk-dashboard">
    <div class="header-row">
      <h2 class="page-title">Risk Assessment</h2>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <div class="kpi-card"><span class="kpi-label">Total</span><span class="kpi-value">{{ stats.totalAssessments || 0 }}</span></div>
      <div class="kpi-card danger"><span class="kpi-label">Blocked</span><span class="kpi-value">{{ stats.blockedCount || 0 }}</span></div>
      <div class="kpi-card warning"><span class="kpi-label">Review</span><span class="kpi-value">{{ stats.reviewCount || 0 }}</span></div>
      <div class="kpi-card success"><span class="kpi-label">Approved</span><span class="kpi-value">{{ stats.approveCount || 0 }}</span></div>
      <div class="kpi-card error"><span class="kpi-label">Block Rate</span><span class="kpi-value">{{ stats.blockRate || '0%' }}</span></div>
    </div>

    <!-- Layer Architecture -->
    <div class="arch-card">
      <h3>Three-Layer Risk Architecture</h3>
      <div class="layer-row">
        <div class="layer"><div class="layer-num">1</div><span>Rule Engine</span><small>5 rules, &lt;5ms</small></div>
        <span class="layer-arrow">→</span>
        <div class="layer"><div class="layer-num">2</div><span>Statistical</span><small>z-score/IQR, &lt;50ms</small></div>
        <span class="layer-arrow">→</span>
        <div class="layer"><div class="layer-num">3</div><span>AI Agent</span><small>LLM, &lt;5s</small></div>
      </div>
    </div>

    <!-- Blocked / Review tables -->
    <div class="tables-row">
      <div class="table-card">
        <h3 class="table-title danger-text">Blocked Payments</h3>
        <table class="risk-table">
          <thead><tr><th>Payment ID</th><th>Score</th><th>Level</th><th>Rules</th><th>Time</th></tr></thead>
          <tbody>
            <tr v-for="r in blocked" :key="r.id" class="tr-danger" @click="$router.push('/payments/' + r.paymentId)">
              <td class="mono">{{ r.paymentId?.slice(0,10) }}...</td>
              <td>{{ r.riskScore }}</td>
              <td><span :class="'badge badge-' + (r.riskLevel || '').toLowerCase()">{{ r.riskLevel }}</span></td>
              <td class="wrap">{{ truncate(r.triggeredRules, 40) }}</td>
              <td class="time">{{ fmt(r.assessedAt) }}</td>
            </tr>
            <tr v-if="!blocked.length"><td colspan="5" class="empty">No blocked payments</td></tr>
          </tbody>
        </table>
      </div>
      <div class="table-card">
        <h3 class="table-title warning-text">Pending Review</h3>
        <table class="risk-table">
          <thead><tr><th>Payment ID</th><th>Score</th><th>Level</th><th>Flags</th><th>Time</th></tr></thead>
          <tbody>
            <tr v-for="r in review" :key="r.id" class="tr-warning" @click="$router.push('/payments/' + r.paymentId)">
              <td class="mono">{{ r.paymentId?.slice(0,10) }}...</td>
              <td>{{ r.riskScore }}</td>
              <td><span :class="'badge badge-' + (r.riskLevel || '').toLowerCase()">{{ r.riskLevel }}</span></td>
              <td class="wrap">{{ truncate(r.statisticalFlags, 40) }}</td>
              <td class="time">{{ fmt(r.assessedAt) }}</td>
            </tr>
            <tr v-if="!review.length"><td colspan="5" class="empty">No payments pending review</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRiskStats, getBlockedPayments, getReviewPayments } from '../api/risk'

const stats = ref({})
const blocked = ref([])
const review = ref([])

onMounted(async () => {
  try {
    const [s, b, r] = await Promise.all([getRiskStats(), getBlockedPayments(), getReviewPayments()])
    if (s.data) stats.value = s.data
    if (b.data) blocked.value = b.data || []
    if (r.data) review.value = r.data || []
  } catch(e) { console.error(e) }
})

function truncate(s, n) { return s ? (s.length > n ? s.slice(0,n)+'...' : s) : 'N/A' }
function fmt(t) { return t ? new Date(t).toLocaleString() : 'N/A' }
</script>

<style scoped>
.risk-dashboard { width:100%; max-width:none; margin:0 auto; transform:translateY(-20px); margin-bottom:-20px; }
.header-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; }
.page-title { font-family:Inter,sans-serif; font-size:28px; font-weight:700; color:#191c1f; }
.kpi-grid { display:grid; grid-template-columns:repeat(5,1fr); gap:16px; margin-bottom:24px; }
.kpi-card { min-height:100px; padding:14px 18px; border-radius:24px; border:2px solid #f4f4f4; background:#fff; display:flex; flex-direction:column; justify-content:center; gap:4px; }
.kpi-card.danger { border-color:#fecaca; background:#fff5f5; }
.kpi-card.warning { border-color:#fde68a; background:#fffdf5; }
.kpi-card.success { border-color:#a7f3d0; background:#f5fff9; }
.kpi-card.error { border-color:#fecaca; }
.kpi-label { font-family:Inter,sans-serif; font-size:12px; font-weight:600; color:#999; text-transform:uppercase; letter-spacing:.24px; }
.kpi-value { font-family:Inter,sans-serif; font-size:28px; font-weight:800; color:#191c1f; }
.danger .kpi-value { color:#e23b4a; }
.warning .kpi-value { color:#ec7e00; }
.success .kpi-value { color:#059669; }

.arch-card { border:2px solid #f4f4f4; border-radius:24px; padding:20px 24px; margin-bottom:24px; background:#fff; }
.arch-card h3 { font-family:Inter,sans-serif; font-size:16px; font-weight:700; color:#191c1f; margin-bottom:16px; }
.layer-row { display:flex; align-items:center; gap:12px; font-family:Inter,sans-serif; }
.layer { flex:1; text-align:center; padding:16px 12px; border-radius:16px; background:#fafafa; border:2px solid #f4f4f4; display:flex; flex-direction:column; gap:4px; }
.layer-num { width:32px; height:32px; border-radius:9999px; background:#191c1f; color:#fff; display:grid; place-items:center; font-weight:800; font-size:15px; margin:0 auto 4px; }
.layer span { font-size:14px; font-weight:700; color:#191c1f; }
.layer small { font-size:11px; color:#999; }
.layer-arrow { font-size:20px; color:#999; }

.tables-row { display:grid; grid-template-columns:1fr 1fr; gap:20px; }
@media(max-width:768px){ .tables-row,.kpi-grid{grid-template-columns:1fr} }
.table-card { border:2px solid #f4f4f4; border-radius:24px; padding:20px 24px; background:#fff; }
.table-title { font-family:Inter,sans-serif; font-size:16px; font-weight:700; margin-bottom:12px; }
.danger-text { color:#e23b4a; }
.warning-text { color:#ec7e00; }
.risk-table { width:100%; border-collapse:collapse; font-family:Inter,sans-serif; font-size:13px; }
.risk-table th { text-align:left; padding:8px 12px; color:#999; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:.24px; border-bottom:1px solid #f4f4f4; }
.risk-table td { padding:10px 12px; border-bottom:1px solid #fafafa; }
.tr-danger { cursor:pointer; } .tr-danger:hover { background:#fff5f5; }
.tr-warning { cursor:pointer; } .tr-warning:hover { background:#fffdf5; }
.mono { font-family:'SF Mono',monospace; font-size:12px; }
.time { color:#999; font-size:12px; }
.wrap { max-width:160px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.empty { color:#999; text-align:center; padding:20px; }
.badge { display:inline-block; padding:2px 10px; border-radius:9999px; font-size:11px; font-weight:700; }
.badge-low { background:#f0fdf4; color:#059669; }
.badge-medium { background:#fffbeb; color:#ec7e00; }
.badge-high { background:#fff5f5; color:#e23b4a; }
.badge-critical { background:#fdf2f8; color:#be185d; }
</style>
