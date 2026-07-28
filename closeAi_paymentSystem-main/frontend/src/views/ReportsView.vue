<template>
  <div class="reports">
    <div class="header-row">
      <h2 class="page-title">Payment Analytics</h2>
      <button class="refresh-btn" @click="loadAll">
        <span v-if="loading" class="spinner"></span>
        {{ loading ? 'Refreshing...' : 'Refresh All' }}
      </button>
    </div>

    <!-- KPI Stat Cards -->
    <div class="kpi-grid">
      <div class="kpi-card glass">
        <span class="kpi-label">Today's Payments</span>
        <span class="kpi-value">{{ daily?.totalPayments || 0 }}</span>
      </div>
      <div class="kpi-card glass success">
        <span class="kpi-label">Completed</span>
        <span class="kpi-value">{{ daily?.completedPayments || 0 }}</span>
      </div>
      <div class="kpi-card glass danger">
        <span class="kpi-label">Failed</span>
        <span class="kpi-value">{{ daily?.failedPayments || 0 }}</span>
      </div>
      <div class="kpi-card glass">
        <span class="kpi-label">Success Rate</span>
        <span class="kpi-value">{{ rate?.successRate || 'N/A' }}</span>
      </div>
      <div class="kpi-card glass">
        <span class="kpi-label">Avg Process Time</span>
        <span class="kpi-value">{{ avgTime?.avgProcessingSeconds || 'N/A' }}s</span>
      </div>
    </div>

    <!-- Charts Row -->
    <div class="chart-row">
      <div class="chart-card glass">
        <h3 class="chart-title">Today's Summary</h3>
        <v-chart v-if="daily && !loading" :option="barOption" autoresize class="chart" />
        <div v-else class="chart-placeholder">
          <div class="skeleton"></div>
        </div>
      </div>

      <div class="chart-card glass">
        <h3 class="chart-title">Success vs Failure</h3>
        <v-chart v-if="rate && !loading" :option="pieOption" autoresize class="chart" />
        <div v-else class="chart-placeholder">
          <div class="skeleton"></div>
        </div>
      </div>
    </div>

    <!-- Daily Trend Chart -->
    <div class="chart-card glass full-width">
      <h3 class="chart-title">Processing Time Trend</h3>
      <v-chart v-if="avgTime && !loading" :option="gaugeOption" autoresize class="chart chart-lg" />
      <div v-else class="chart-placeholder">
        <div class="skeleton skeleton-lg"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { getDailySummary, getSuccessRate, getAvgProcessingTime } from '../api/payment'

use([CanvasRenderer, BarChart, PieChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const daily = ref(null)
const rate = ref(null)
const avgTime = ref(null)
const loading = ref(false)

// Fintech color palette
const C = { gold: '#F59E0B', dark: '#0F172A', purple: '#8B5CF6', green: '#059669', red: '#EF4444', blue: '#3B82F6', slate: '#64748B', surface: '#F8FAFC' }

const barOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: C.dark, borderColor: C.dark, textStyle: { color: '#fff' } },
  grid: { left: '3%', right: '8%', bottom: '3%', top: '10%', containLabel: true },
  xAxis: { type: 'category', data: ['Total', 'Completed', 'Failed'], axisLine: { lineStyle: { color: C.slate } }, axisTick: { show: false } },
  yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#E8ECF1' } } },
  series: [{
    type: 'bar', barWidth: '40%',
    data: [
      { value: daily.value?.totalPayments || 0, itemStyle: { color: C.blue, borderRadius: [8, 8, 0, 0] } },
      { value: daily.value?.completedPayments || 0, itemStyle: { color: C.green, borderRadius: [8, 8, 0, 0] } },
      { value: daily.value?.failedPayments || 0, itemStyle: { color: C.red, borderRadius: [8, 8, 0, 0] } },
    ]
  }]
}))

const pieOption = computed(() => ({
  tooltip: { trigger: 'item', backgroundColor: C.dark, borderColor: C.dark, textStyle: { color: '#fff' } },
  legend: { bottom: 0, textStyle: { color: C.slate, fontSize: 12 } },
  series: [{
    type: 'pie', radius: ['55%', '78%'], center: ['50%', '48%'], avoidLabelOverlap: false,
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold' } },
    data: [
      { value: rate.value?.completed || 0, name: 'Completed', itemStyle: { color: C.green } },
      { value: rate.value?.failed || 0, name: 'Failed', itemStyle: { color: C.red } },
    ]
  }]
}))

const gaugeOption = computed(() => ({
  tooltip: { formatter: '{b}: {c}s' },
  series: [{
    type: 'gauge', startAngle: 210, endAngle: -30, center: ['50%', '60%'], radius: '85%',
    axisLine: { lineStyle: { width: 28, color: [[0.3, C.green], [0.6, C.gold], [1, C.red]] } },
    axisTick: { distance: -28, length: 6, lineStyle: { width: 2, color: C.slate } },
    splitLine: { distance: -32, length: 14, lineStyle: { width: 3, color: C.slate } },
    axisLabel: { color: C.slate, distance: 35, fontSize: 11 },
    pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '70%', width: 6, offsetCenter: [0, '-8%'], itemStyle: { color: C.dark } },
    detail: { valueAnimation: true, formatter: '{value}s', fontSize: 24, color: C.dark, offsetCenter: [0, '70%'] },
    data: [{ value: parseFloat(avgTime.value?.avgProcessingSeconds || '0'), name: 'Avg Processing Time' }]
  }]
}))

async function loadAll() {
  loading.value = true
  try {
    const [d, r, a] = await Promise.all([getDailySummary(), getSuccessRate(), getAvgProcessingTime()])
    if (d.success) daily.value = d.data
    if (r.success) rate.value = r.data
    if (a.success) avgTime.value = a.data
  } finally { loading.value = false }
}

onMounted(() => loadAll())
</script>

<style scoped>
.reports { max-width: 1120px; margin: 0 auto; }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px; }
.page-title { font-family: Inter, system-ui, sans-serif; font-size: 28px; font-weight: 700; color: #0F172A; letter-spacing: -0.28px; }
.refresh-btn {
  padding: 12px 28px; border-radius: 9999px; border: 2px solid #E8ECF1;
  background: #fff; font-family: Inter, sans-serif; font-size: 14px; font-weight: 600;
  color: #0F172A; cursor: pointer; display: flex; align-items: center; gap: 8px; transition: all 0.15s;
}
.refresh-btn:hover { border-color: #8B5CF6; color: #8B5CF6; }
.spinner { width: 14px; height: 14px; border: 2px solid #E8ECF1; border-top-color: #8B5CF6; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* KPI */
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 16px; margin-bottom: 28px; }
.kpi-card { padding: 20px 24px; border-radius: 20px; display: flex; flex-direction: column; gap: 6px; }
.kpi-label { font-family: Inter, sans-serif; font-size: 12px; font-weight: 600; color: #64748B; text-transform: uppercase; letter-spacing: 0.24px; }
.kpi-value { font-family: Inter, sans-serif; font-size: 28px; font-weight: 800; color: #0F172A; letter-spacing: -0.28px; }
.kpi-card.success .kpi-value { color: #059669; }
.kpi-card.danger .kpi-value { color: #EF4444; }

/* Glass */
.glass { background: rgba(255,255,255,0.7); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.3); }
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
@media (max-width: 768px) { .chart-row { grid-template-columns: 1fr; } .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
.chart-card { border-radius: 24px; padding: 24px; }
.chart-card.full-width { width: 100%; margin-bottom: 20px; }
.chart-title { font-family: Inter, sans-serif; font-size: 16px; font-weight: 700; color: #0F172A; margin-bottom: 12px; }
.chart { height: 320px; width: 100%; }
.chart-lg { height: 380px; }
.chart-placeholder { height: 320px; display: flex; align-items: center; justify-content: center; }
.skeleton { width: 100%; height: 260px; background: linear-gradient(90deg, #F1F5F9 25%, #E2E8F0 50%, #F1F5F9 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; border-radius: 16px; }
.skeleton-lg { height: 320px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
