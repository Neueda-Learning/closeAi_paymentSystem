<template>
  <div>
    <h2 class="page-title">Reports</h2>
    <div class="grid">
      <div class="card" v-for="r in reports" :key="r.key">
        <h3 class="card-title">{{ r.title }}</h3>
        <div v-if="r.loading" class="loading">Loading...</div>
        <div v-else class="metrics">
          <div v-for="(v, k) in r.data" :key="k" class="metric">
            <span class="k">{{ k }}</span>
            <span class="v">{{ v }}</span>
          </div>
        </div>
        <button class="btn" :disabled="r.loading" @click="load(r)">Refresh</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { getDailySummary, getSuccessRate, getAvgProcessingTime } from '../api/payment'

const reports = reactive([
  { key: 'daily', title: 'Daily Summary', loading: true, data: null, fn: getDailySummary },
  { key: 'rate', title: 'Success Rate', loading: true, data: null, fn: getSuccessRate },
  { key: 'avg', title: 'Avg Processing Time', loading: true, data: null, fn: getAvgProcessingTime },
])

async function load(r) {
  r.loading = true
  try {
    const res = await r.fn()
    if (res.success) r.data = res.data
  } finally { r.loading = false }
}

onMounted(() => reports.forEach(r => load(r)))
</script>

<style scoped>
.page-title { font-family: Inter, sans-serif; font-size: 28px; font-weight: 700; color: #191c1f; margin-bottom: 24px; letter-spacing: -0.28px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.card { border: 2px solid #f4f4f4; border-radius: 24px; padding: 24px; }
.card-title { font-family: Inter, sans-serif; font-size: 16px; font-weight: 700; color: #191c1f; margin-bottom: 16px; }
.loading { color: #999; font-size: 14px; }
.metrics { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.metric { display: flex; justify-content: space-between; font-family: Inter, sans-serif; font-size: 14px; }
.k { color: #999; font-weight: 600; }
.v { color: #191c1f; font-weight: 700; }
.btn { padding: 10px 28px; border-radius: 9999px; border: 2px solid #f4f4f4; background: #fff; font-family: Inter, sans-serif; font-size: 14px; font-weight: 600; color: #191c1f; cursor: pointer; }
.btn:hover { border-color: #494fdf; }
</style>
