<template>
  <div>
    <PaymentForm :loading="loading || accountsLoading" :accounts="accounts" @submit="openConfirmation" />

    <el-dialog v-model="showConfirmation" title="Confirm recipient" width="480px" center>
      <div v-if="pendingPayment" class="confirm-card">
        <div class="recipient-avatar">{{ recipientInitial }}</div>
        <h3>{{ recipient?.maskedAccountName }}</h3>
        <p class="recipient-account">{{ recipient?.accountNumber }}</p>
        <div class="transfer-summary">
          <div><span>You send</span><strong>{{ pendingPayment.amount }} {{ pendingPayment.currency }}</strong></div>
          <div><span>Recipient gets</span><strong>{{ quote?.settlementAmount ?? '—' }} {{ recipient?.currency }}</strong></div>
          <div><span>Rate</span><strong>1 {{ pendingPayment.currency }} = {{ quote?.rate ?? '—' }} {{ recipient?.currency }}</strong></div>
        </div>
        <label class="confirm-field">
          <span>Recipient surname</span>
          <input v-model.trim="confirmation.recipientLastName" class="confirm-input" autocomplete="off" placeholder="Enter surname to verify" />
        </label>
        <label class="confirm-field">
          <span>Source account password</span>
          <input v-model="confirmation.sourceAccountPassword" class="confirm-input" type="password" autocomplete="current-password" placeholder="Enter account password" />
        </label>
        <p class="security-note">Credentials are verified securely and are never stored with the payment.</p>
      </div>
      <template #footer>
        <el-button @click="closeConfirmation">Cancel</el-button>
        <el-button type="primary" :loading="loading" :disabled="!canConfirm" @click="handleSubmit">Confirm payment</el-button>
      </template>
    </el-dialog>

    <!-- Success Modal -->
    <el-dialog v-model="showResult" title="Payment Created" width="480px" center>
      <div v-if="result" class="result-card">
        <div class="result-row"><span class="lbl">Payment ID</span><span class="val mono">{{ result.id }}</span></div>
        <div class="result-row"><span class="lbl">Status</span><StatusBadge :status="result.status" /></div>
        <div class="result-row"><span class="lbl">Amount</span><span class="val">{{ result.amount }} {{ result.currency }}</span></div>
        <div class="result-row"><span class="lbl">Recipient gets</span><span class="val">{{ result.settlementAmount }} {{ result.settlementCurrency }}</span></div>
        <div class="result-row"><span class="lbl">Locked rate</span><span class="val">{{ result.exchangeRate }}</span></div>
        <div class="result-row"><span class="lbl">Source</span><span class="val">{{ result.sourceAccount }}</span></div>
        <div class="result-row"><span class="lbl">Destination</span><span class="val">{{ result.destinationAccount }}</span></div>
      </div>
      <template #footer>
        <el-button @click="showResult = false" :style="{ borderRadius: '9999px', padding: '12px 28px', fontWeight: 600 }">Close</el-button>
        <el-button type="primary" @click="goDetail" :style="{ borderRadius: '9999px', padding: '12px 28px', fontWeight: 600, background: '#191c1f', borderColor: '#191c1f' }">View Details</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PaymentForm from '../components/PaymentForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { createPayment, getExchangeRateQuote } from '../api/payment'
import { listAccounts } from '../api/account'

const router = useRouter()
const loading = ref(false)
const showResult = ref(false)
const result = ref(null)
const accounts = ref([])
const accountsLoading = ref(false)
const showConfirmation = ref(false)
const pendingPayment = ref(null)
const quote = ref(null)
const confirmation = reactive({ recipientLastName: '', sourceAccountPassword: '' })

const recipient = computed(() =>
  accounts.value.find((account) => account.accountNumber === pendingPayment.value?.destinationAccount)
)
const recipientInitial = computed(() => recipient.value?.maskedAccountName?.charAt(0) || '?')
const canConfirm = computed(() =>
  confirmation.recipientLastName && confirmation.sourceAccountPassword && quote.value
)

async function openConfirmation(formData) {
  pendingPayment.value = formData
  confirmation.recipientLastName = ''
  confirmation.sourceAccountPassword = ''
  quote.value = null
  showConfirmation.value = true
  try {
    const res = await getExchangeRateQuote(
      formData.currency,
      recipient.value.currency,
      formData.amount,
    )
    quote.value = res.data
  } catch {
    showConfirmation.value = false
  }
}

async function handleSubmit() {
  loading.value = true
  try {
    const idempotencyKey = crypto.randomUUID()
    const res = await createPayment({
      ...pendingPayment.value,
      recipientLastName: confirmation.recipientLastName,
      sourceAccountPassword: confirmation.sourceAccountPassword,
    }, idempotencyKey)
    if (res.success && res.data) {
      result.value = res.data
      showConfirmation.value = false
      confirmation.sourceAccountPassword = ''
      showResult.value = true
    }
  } catch (err) { /* shown by interceptor */ }
  finally { loading.value = false }
}

function closeConfirmation() {
  showConfirmation.value = false
  confirmation.sourceAccountPassword = ''
}

async function loadAccounts() {
  accountsLoading.value = true
  try {
    const response = await listAccounts()
    accounts.value = response.data || []
  } finally {
    accountsLoading.value = false
  }
}

onMounted(loadAccounts)

function goDetail() {
  showResult.value = false
  if (result.value) router.push(`/payments/${result.value.id}`)
}
</script>

<style scoped>
.result-card { display: flex; flex-direction: column; gap: 14px; padding: 12px 0; }
.result-row { display: flex; justify-content: space-between; align-items: center; font-family: Inter, system-ui, sans-serif; font-size: 14px; }
.lbl { color: #999; font-weight: 600; letter-spacing: 0.16px; }
.val { color: #191c1f; font-weight: 700; }
.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 13px; }
.confirm-card { text-align: center; }
.recipient-avatar { display: grid; place-items: center; width: 64px; height: 64px; margin: 0 auto 12px; border-radius: 22px; background: #eef0ff; color: #494fdf; font-size: 24px; font-weight: 800; }
.recipient-account { margin-top: 5px; color: #999; font-size: 13px; }
.transfer-summary { margin: 22px 0; padding: 16px; border-radius: 16px; background: #fafafa; text-align: left; }
.transfer-summary div { display: flex; justify-content: space-between; gap: 20px; padding: 6px 0; font-size: 13px; }
.transfer-summary span { color: #888; }
.confirm-field { display: flex; flex-direction: column; gap: 7px; margin-top: 14px; text-align: left; font-size: 13px; font-weight: 600; }
.confirm-input { padding: 13px 15px; border: 2px solid #f0f0f0; border-radius: 14px; outline: none; }
.confirm-input:focus { border-color: #494fdf; }
.security-note { margin-top: 14px; color: #999; font-size: 11px; line-height: 1.5; }
</style>
