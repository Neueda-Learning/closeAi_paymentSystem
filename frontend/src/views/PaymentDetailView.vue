<template>
  <div v-if="payment" class="detail">
    <h2 class="page-title">Payment Detail</h2>

    <div class="detail-layout">
      <!-- Left Sidebar: Status + Timeline -->
      <aside class="sidebar">
        <div class="status-section">
          <span class="status-label">Current Status</span>
          <StatusBadge :status="payment.status" />
        </div>

        <h3 class="timeline-title">Status History</h3>
        <div class="timeline-scroll">
          <StatusTimeline :history="[...(payment.statusHistory || [])].reverse()" :hide-title="true" />
        </div>
      </aside>

      <!-- Right: Payment Details -->
      <div class="main-content">
        <!-- Info Card -->
        <div class="card">
          <div class="card-header">
            <span class="card-id">{{ payment.id }}</span>
          </div>
          <div class="grid">
            <div class="kv"><span class="k">Amount</span><span class="v">{{ payment.amount }} {{ payment.currency }}</span></div>
            <div class="kv"><span class="k">Status</span><span class="v">{{ payment.status }}</span></div>
            <div class="kv"><span class="k">Source</span><span class="v">{{ payment.sourceAccount }}</span></div>
            <div class="kv"><span class="k">Destination</span><span class="v">{{ payment.destinationAccount }}</span></div>
            <div class="kv"><span class="k">Description</span><span class="v desc">{{ payment.description || '-' }}</span></div>
            <div class="kv"><span class="k">Created</span><span class="v">{{ formatTime(payment.createdAt) }}</span></div>
            <div class="kv"><span class="k">Retry Count</span><span class="v">{{ payment.retryCount || 0 }} / 3</span></div>
            <div class="kv"><span class="k">Updated</span><span class="v">{{ formatTime(payment.updatedAt) }}</span></div>
            <div class="kv"><span class="k">Idempotency Key</span><span class="v mono">{{ payment.idempotencyKey?.slice(0, 16) }}...</span></div>
            <div v-if="payment.errorCode" class="kv error-kv">
              <div class="error-row-inline">
                <div class="error-info">
                  <span class="k">Error</span>
                  <el-popover placement="bottom" :width="340" trigger="click">
                    <template #reference>
                      <span class="v error-trigger">{{ errorDescription }}</span>
                    </template>
                    <div class="error-popover">
                      <div class="ep-field">
                        <span class="ep-label">Error Code</span>
                        <span class="ep-value mono">{{ payment.errorCode }}</span>
                      </div>
                      <div class="ep-field">
                        <span class="ep-label">Description</span>
                        <span class="ep-value">{{ errorDescription }}</span>
                      </div>
                    </div>
                  </el-popover>
                </div>
                <ActionButtons inline :status="payment.status" :retry-count="payment.retryCount || 0" :loading="actionLoading" @action="handleAction" />
              </div>
            </div>
          </div>
        </div>


        <!-- Actions -->
        <ActionButtons v-if="!payment.errorCode" :status="payment.status" :retry-count="payment.retryCount || 0" :loading="actionLoading" @action="handleAction" />
      </div>
    </div>

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

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="Edit Payment" width="520px">
      <div class="dialog-field">
        <label class="dialog-label">Source Account</label>
        <input v-model="editForm.sourceAccount" class="filter-input" style="width:100%" placeholder="e.g. ACC-00001" />
      </div>
      <div class="dialog-field">
        <label class="dialog-label">Destination Account</label>
        <input v-model="editForm.destinationAccount" class="filter-input" style="width:100%" placeholder="e.g. ACC-00002" />
      </div>
      <div class="dialog-field">
        <label class="dialog-label">Amount</label>
        <input v-model.number="editForm.amount" class="filter-input" style="width:100%" type="number" step="0.01" min="0.01" />
      </div>
      <div class="dialog-field">
        <label class="dialog-label">Currency</label>
        <select v-model="editForm.currency" class="filter-select" style="width:100%">
          <option v-for="c in ['USD','EUR','GBP','CNY']" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>
      <div class="dialog-field">
        <label class="dialog-label">Description</label>
        <input v-model="editForm.description" class="filter-input" style="width:100%" placeholder="Optional..." />
      </div>
      <template #footer>
        <el-button @click="showEditDialog = false" :style="{ borderRadius:'9999px' }">Cancel</el-button>
        <el-button type="primary" @click="doEdit" :loading="actionLoading === 'edit'" :style="{ borderRadius:'9999px', background:'#191c1f', borderColor:'#191c1f' }">Save & Re-validate</el-button>
      </template>
    </el-dialog>
  </div>
  <div v-else-if="loading" class="loading">Loading...</div>
  <div v-else class="loading">Payment not found</div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import StatusBadge from '../components/StatusBadge.vue'
import ActionButtons from '../components/ActionButtons.vue'
import StatusTimeline from '../components/StatusTimeline.vue'
import { getPayment, updatePayment, validatePayment, sendPayment, completePayment, failPayment, retryPayment, cancelPayment, reversePayment } from '../api/payment'
import { ERROR_CODE_MAP } from '../utils/constants'

const route = useRoute()
const router = useRouter()
const payment = ref(null)
const loading = ref(true)
const actionLoading = ref(null)
const showFailDialog = ref(false)
const showEditDialog = ref(false)
const failErrorCode = ref('PROCESSING_ERROR')
const failReason = ref('')
const editForm = ref({ sourceAccount: '', destinationAccount: '', amount: 0, currency: 'USD', description: '' })
const errorDescription = computed(() => ERROR_CODE_MAP[payment.value?.errorCode] || payment.value?.errorCode || '')

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
  if (action === 'edit') {
    // Pre-fill edit form with current payment data
    editForm.value = {
      sourceAccount: payment.value.sourceAccount,
      destinationAccount: payment.value.destinationAccount,
      amount: payment.value.amount,
      currency: payment.value.currency,
      description: payment.value.description || '',
    }
    showEditDialog.value = true
    return
  }
  try { await ElMessageBox.confirm(`Confirm: ${action} this payment?`, 'Action', { confirmButtonText: 'Yes', cancelButtonText: 'No' }) } catch { return }

  actionLoading.value = action
  try {
    const actions = {
      validate: () => validatePayment(route.params.id),
      send:     () => sendPayment(route.params.id),
      complete: () => completePayment(route.params.id),
      retry:    () => retryPayment(route.params.id, crypto.randomUUID()),
      cancel:   () => cancelPayment(route.params.id),
      reverse:  () => reversePayment(route.params.id),
    }
    const res = await actions[action]()
    if (res.success) payment.value = res.data
  } catch (err) {
    // If retry exhausted, show dialog and redirect
    if (err?.code === 'RETRY_EXHAUSTED') {
      ElMessageBox.alert(
        'This payment has exceeded the maximum retry limit (3/3). It is now permanently failed and cannot be retried.',
        'Retry Limit Exhausted',
        { confirmButtonText: 'Back to Payments', type: 'error', center: true }
      ).then(() => { router.push('/payments') })
    }
  } finally { actionLoading.value = null }
}

async function doEdit() {
  actionLoading.value = 'edit'
  try {
    // Step 1: Update payment with edited data
    const updateRes = await updatePayment(route.params.id, editForm.value)
    if (!updateRes.success) return

    // Step 2: Re-validate (only if status is now CREATED)
    if (updateRes.data.status === 'CREATED') {
      const validateRes = await validatePayment(route.params.id)
      if (validateRes.success) payment.value = validateRes.data
    } else {
      payment.value = updateRes.data
    }
    showEditDialog.value = false
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
.detail { overflow: hidden; transform: translateY(-20px); margin-bottom: -20px; }
.page-title { font-family: Inter, system-ui, sans-serif; font-size: 28px; font-weight: 700; color: #191c1f; margin-bottom: 16px; letter-spacing: -0.28px; }

/* Two-column layout */
.detail-layout { display: flex; gap: 28px; align-items: flex-start; height: calc(100vh - 175px); }
.sidebar { width: 280px; flex-shrink: 0; align-self: stretch; display: flex; flex-direction: column; overflow: hidden; }
.status-section {
  flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 18px 20px;
  background: #fafafa;
  border: 2px solid #f4f4f4;
  border-radius: 16px;
  margin-bottom: 20px;
}
.timeline-scroll { flex: 1; overflow-y: auto; min-height: 0; }
.main-content { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.status-label {
  font-family: Inter, system-ui, sans-serif;
  font-size: 11px; font-weight: 600; color: #999;
  text-transform: uppercase; letter-spacing: 0.24px;
}
.timeline-title {
  font-family: Inter, system-ui, sans-serif;
  font-size: 18px; font-weight: 700; color: #191c1f;
  letter-spacing: 0.16px;
  margin: 0 0 10px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .detail-layout { flex-direction: column; height: auto; }
  .sidebar { width: 100%; align-self: auto; overflow: visible; }
  .timeline-scroll { flex: none; max-height: none; overflow-y: visible; }
  .grid { grid-template-columns: 1fr 1fr; }
}

.card { border: 2px solid #f4f4f4; border-radius: 24px; overflow: hidden; margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 20px; background: #fafafa; border-bottom: 1px solid #f4f4f4; }
.card-id { font-family: 'SF Mono','Fira Code',monospace; font-size: 14px; color: #191c1f; font-weight: 600; }
.grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0; }
.kv { display: flex; flex-direction: column; gap: 2px; padding: 12px 18px; border-bottom: 1px solid #fafafa; }
.k { font-family: Inter, system-ui, sans-serif; font-size: 12px; font-weight: 600; color: #999; text-transform: uppercase; letter-spacing: 0.24px; }
.v { font-family: Inter, system-ui, sans-serif; font-size: 15px; font-weight: 700; color: #191c1f; letter-spacing: 0.16px; }
.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 13px; }
.desc { font-weight: 400; color: #666; }

/* Error row inline in card */
.error-kv { grid-column: 1 / -1; background: #fff0f0; }
.error-row-inline { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.error-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.error-trigger { color: #e23b4a; cursor: pointer; text-decoration: underline; text-decoration-style: dotted; text-underline-offset: 3px; }
.error-trigger:hover { color: #c21d2c; }
.error-popover { display: flex; flex-direction: column; gap: 12px; padding: 4px 0; }
.ep-field { display: flex; flex-direction: column; gap: 2px; }
.ep-label { font-family: Inter, sans-serif; font-size: 11px; font-weight: 600; color: #999; text-transform: uppercase; letter-spacing: 0.24px; }
.ep-value { font-family: Inter, sans-serif; font-size: 13px; font-weight: 600; color: #191c1f; }
.ep-value.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 12px; }
.loading { text-align: center; padding: 60px; color: #999; font-family: Inter, system-ui, sans-serif; }
.dialog-field { margin-bottom: 16px; }
.dialog-label { display: block; margin-bottom: 6px; font-family: Inter, sans-serif; font-size: 13px; font-weight: 600; color: #191c1f; }
.filter-select, .filter-input { padding: 12px 18px; border: 2px solid #f4f4f4; border-radius: 16px; font-family: Inter, sans-serif; font-size: 14px; color: #191c1f; outline: none; }
</style>
