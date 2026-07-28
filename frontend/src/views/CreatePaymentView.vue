<template>
  <div class="create-page">
    <PaymentForm :loading="loading" @submit="handleSubmit" />

    <section class="promo-banner" aria-label="HSBC payment protection banner">
      <div class="promo-badge" aria-hidden="true">
        <span class="promo-badge-mark" role="img" aria-label="HSBC logo">
          <img src="/logo.png" alt="HSBC logo" />
        </span>
        <span class="promo-badge-text">HSBC</span>
      </div>

      <div class="marquee" role="status" aria-live="polite">
        <div class="marquee-track">
          <span class="marquee-item">Every payment is protected by HSBC-grade security</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">每一笔支付都有保障</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">Risk monitoring · Idempotency · Audit trail</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">Secure transfer, trusted delivery, full visibility</span>
        </div>
        <div class="marquee-track" aria-hidden="true">
          <span class="marquee-item">Every payment is protected by HSBC-grade security</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">每一笔支付都有保障</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">Risk monitoring · Idempotency · Audit trail</span>
          <span class="marquee-dot">•</span>
          <span class="marquee-item">Secure transfer, trusted delivery, full visibility</span>
        </div>
      </div>
    </section>

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
.create-page {
  display: flex;
  flex-direction: column;
  gap: 32px;
  min-height: calc(100vh - 156px);
}

.result-card { display: flex; flex-direction: column; gap: 14px; padding: 12px 0; }
.result-row { display: flex; justify-content: space-between; align-items: center; font-family: Inter, system-ui, sans-serif; font-size: 14px; }
.lbl { color: #999; font-weight: 600; letter-spacing: 0.16px; }
.val { color: #191c1f; font-weight: 700; }
.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 13px; }

/* Bottom ad strip fills the empty space below the form and adds movement for the create page. */
.promo-banner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  padding: 18px 20px;
  border: 2px solid #f4f4f4;
  border-radius: 20px;
  background: linear-gradient(135deg, #fff 0%, #fff7f7 45%, #fff 100%);
  overflow: hidden;
}

.promo-badge {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 9999px;
  background: #f8f0f0;
  color: #8c1d18;
  font-family: Inter, system-ui, sans-serif;
  font-weight: 800;
  letter-spacing: 0.4px;
}

.promo-badge-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 98px;
  height: 40px;
  overflow: hidden;
}

.promo-badge-mark img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.promo-badge-text {
  font-size: 14px;
}

.marquee {
  position: relative;
  display: flex;
  gap: 24px;
  white-space: nowrap;
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent 0, #000 8%, #000 92%, transparent 100%);
}

.marquee-track {
  display: inline-flex;
  align-items: center;
  gap: 24px;
  min-width: 100%;
  flex-shrink: 0;
  animation: marquee-scroll 18s linear infinite;
}

.marquee-item {
  font-family: Inter, system-ui, sans-serif;
  font-size: 14px;
  font-weight: 700;
  color: #191c1f;
}

.marquee-dot {
  color: #d71920;
  font-weight: 900;
}

@keyframes marquee-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}

@media (max-width: 640px) {
  .create-page { min-height: auto; }
  .promo-banner { padding: 14px 16px; }
  .marquee-item { font-size: 13px; }
}
</style>
