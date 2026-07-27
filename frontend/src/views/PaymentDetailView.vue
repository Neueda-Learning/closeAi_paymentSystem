<template>
  <div v-if="payment" class="detail">
    <h2 class="page-title">Payment Detail</h2>

    <!-- Info Card -->
    <div class="card">
      <div class="card-header">
        <span class="card-id">{{ payment.id }}</span>
        <StatusBadge :status="payment.status" />
      </div>
      <div class="grid">
        <div class="kv"><span class="k">Amount</span><span class="v">{{ payment.amount }} {{ payment.currency }}</span></div>
        <div class="kv"><span class="k">Status</span><span class="v">{{ payment.status }}</span></div>
        <div class="kv"><span class="k">Source</span><span class="v">{{ payment.sourceAccount }}</span></div>
        <div class="kv"><span class="k">Destination</span><span class="v">{{ payment.destinationAccount }}</span></div>
        <div class="kv"><span class="k">Description</span><span class="v desc">{{ payment.description || '-' }}</span></div>
        <div class="kv"><span class="k">Created</span><span class="v">{{ formatTime(payment.createdAt) }}</span></div>
        <div class="kv"><span class="k">Updated</span><span class="v">{{ formatTime(payment.updatedAt) }}</span></div>
        <div class="kv"><span class="k">Idempotency Key</span><span class="v mono">{{ payment.idempotencyKey?.slice(0, 16) }}...</span></div>
      </div>
    </div>

    <!-- Error panel -->
    <ErrorPanel :error-code="payment.errorCode" />

    <!-- Actions -->
    <ActionButtons :status="payment.status" :loading="actionLoading" @action="handleAction" />

    <!-- Fail Dialog -->
    <el-dialog v-model="showFailDialog" title="Mark as Failed" width="400px">
      <div class="dialog-field">
        <label class="dialog-label">Error Code</label>
        <select v-model="failErrorCode" class="filter-select" style="width:100%">
          <option v-for="(label, code) in ERROR_CODE_MAP" :key="code" :value="code">{{ code }}</option>
        </select>
      </div>
      <div class="dialog-field">
        <label class="dialog-label">Reason</label>
        <input v-model="failReason" class="filter-input" style="width:100%" placeholder="Optional..." />
      </div>
      <template #footer>
        <el-button @click="showFailDialog = false" :style="{ borderRadius:'9999px' }">Cancel</el-button>
        <el-button type="danger" @click="doFail" :style="{ borderRadius:'9999px', background:'#e23b4a', borderColor:'#e23b4a' }">Confirm Fail</el-button>
      </template>
    </el-dialog>

    <!-- Status Timeline -->
    <StatusTimeline :history="payment.statusHistory || []" />
  </div>
  <div v-else-if="loading" class="loading">Loading...</div>
  <div v-else class="loading">Payment not found</div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import StatusBadge from '../components/StatusBadge.vue'
import ErrorPanel from '../components/ErrorPanel.vue'
import ActionButtons from '../components/ActionButtons.vue'
import StatusTimeline from '../components/StatusTimeline.vue'
import { getPayment, validatePayment, sendPayment, completePayment, failPayment, retryPayment } from '../api/payment'
import { ERROR_CODE_MAP } from '../utils/constants'

const route = useRoute()
const payment = ref(null)
const loading = ref(true)
const actionLoading = ref(null)
const showFailDialog = ref(false)
const failErrorCode = ref('PROCESSING_ERROR')
const failReason = ref('')

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    const res = await getPayment(route.params.id)
    if (res.success) payment.value = res.data
  } finally { loading.value = false }
}

async function handleAction(action) {
  if (action === 'fail') { showFailDialog.value = true; return }
  try { await ElMessageBox.confirm(`Confirm: ${action} this payment?`, 'Action', { confirmButtonText: 'Yes', cancelButtonText: 'No' }) } catch { return }

  actionLoading.value = action
  try {
    const actions = {
      validate: () => validatePayment(route.params.id),
      send:     () => sendPayment(route.params.id),
      complete: () => completePayment(route.params.id),
      retry:    () => retryPayment(route.params.id, crypto.randomUUID()),
    }
    const res = await actions[action]()
    if (res.success) payment.value = res.data
  } finally { actionLoading.value = null }
}

async function doFail() {
  actionLoading.value = 'fail'
  try {
    const res = await failPayment(route.params.id, failErrorCode.value, failReason.value)
    if (res.success) { payment.value = res.data; showFailDialog.value = false }
  } finally { actionLoading.value = null }
}

function formatTime(t) { return t ? new Date(t).toLocaleString() : '' }
</script>

<style scoped>
.page-title { font-family: Inter, system-ui, sans-serif; font-size: 28px; font-weight: 700; color: #191c1f; margin-bottom: 24px; letter-spacing: -0.28px; }
.card { border: 2px solid #f4f4f4; border-radius: 24px; overflow: hidden; margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; background: #fafafa; border-bottom: 1px solid #f4f4f4; }
.card-id { font-family: 'SF Mono','Fira Code',monospace; font-size: 14px; color: #191c1f; font-weight: 600; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0; }
.kv { display: flex; flex-direction: column; gap: 4px; padding: 18px 24px; border-bottom: 1px solid #fafafa; }
.k { font-family: Inter, system-ui, sans-serif; font-size: 12px; font-weight: 600; color: #999; text-transform: uppercase; letter-spacing: 0.24px; }
.v { font-family: Inter, system-ui, sans-serif; font-size: 15px; font-weight: 700; color: #191c1f; letter-spacing: 0.16px; }
.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 13px; }
.desc { font-weight: 400; color: #666; }
.loading { text-align: center; padding: 60px; color: #999; font-family: Inter, system-ui, sans-serif; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; margin-bottom: 6px; font-family: Inter, sans-serif; font-size: 13px; font-weight: 600; color: #191c1f; }
.filter-select, .filter-input { padding: 12px 18px; border: 2px solid #f4f4f4; border-radius: 16px; font-family: Inter, sans-serif; font-size: 14px; color: #191c1f; outline: none; }
</style>
