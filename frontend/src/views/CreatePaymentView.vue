<template>
  <div>
    <PaymentForm :loading="loading" @submit="handleSubmit" />

    <!-- Success Modal -->
    <el-dialog v-model="showResult" title="Payment Created" width="480px" center>
      <div v-if="result" class="result-card">
        <div class="result-row"><span class="lbl">Payment ID</span><span class="val mono">{{ result.id }}</span></div>
        <div class="result-row"><span class="lbl">Status</span><StatusBadge :status="result.status" /></div>
        <div class="result-row"><span class="lbl">Amount</span><span class="val">{{ result.amount }} {{ result.currency }}</span></div>
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PaymentForm from '../components/PaymentForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { createPayment } from '../api/payment'

const router = useRouter()
const loading = ref(false)
const showResult = ref(false)
const result = ref(null)

async function handleSubmit(formData) {
  loading.value = true
  try {
    const idempotencyKey = crypto.randomUUID()
    const res = await createPayment(formData, idempotencyKey)
    if (res.success && res.data) {
      result.value = res.data
      showResult.value = true
    }
  } catch (err) { /* shown by interceptor */ }
  finally { loading.value = false }
}

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
</style>
