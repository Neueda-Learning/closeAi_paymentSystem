<template>
  <div class="list-page">
    <h2 class="page-title">Payment List</h2>

    <!-- Filters -->
    <div class="filters">
      <select v-model="filters.status" class="filter-select" @change="searchDebounced">
        <option value="">All Statuses</option>
        <option v-for="(info, key) in PAYMENT_STATUS" :key="key" :value="key">{{ info.label }}</option>
      </select>
      <select v-model="filters.currency" class="filter-select" @change="searchDebounced">
        <option value="">All Currencies</option>
        <option v-for="c in SUPPORTED_CURRENCIES" :key="c" :value="c">{{ c }}</option>
      </select>
      <input v-model="filters.keyword" class="filter-input" placeholder="Search by ID or description..." @input="searchDebounced" />
    </div>

    <PaymentTable :payments="payments" :total="total" :page="filters.page" :limit="filters.limit" :loading="loading" @page-change="onPageChange" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import PaymentTable from '../components/PaymentTable.vue'
import { listPayments } from '../api/payment'
import { PAYMENT_STATUS, SUPPORTED_CURRENCIES } from '../utils/constants'

const payments = ref([])
const total = ref(0)
const loading = ref(false)
let timer = null

const filters = reactive({ status: '', currency: '', keyword: '', page: 1, limit: 20 })

onMounted(() => doSearch())

function searchDebounced() {
  clearTimeout(timer)
  timer = setTimeout(doSearch, 300)
}

async function doSearch() {
  loading.value = true
  try {
    const params = { page: filters.page, limit: filters.limit }
    if (filters.status) params.status = filters.status
    if (filters.currency) params.currency = filters.currency
    if (filters.keyword) params.keyword = filters.keyword

    const res = await listPayments(params)
    if (res.success) {
      payments.value = res.data || []
      total.value = res.total || 0
    }
  } finally { loading.value = false }
}

function onPageChange(p) { filters.page = p; doSearch() }
</script>

<style scoped>
.list-page { transform: translateY(-20px); margin-bottom: -20px; }
.page-title { font-family: Inter, system-ui, sans-serif; font-size: 28px; font-weight: 700; color: #191c1f; margin-bottom: 16px; letter-spacing: -0.28px; }
.filters { display: flex; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
.filter-select, .filter-input {
  padding: 12px 18px;
  border: 2px solid #f4f4f4;
  border-radius: 16px;
  font-family: Inter, system-ui, sans-serif;
  font-size: 14px; color: #191c1f;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.filter-select:focus, .filter-input:focus { border-color: #494fdf; }
.filter-input { min-width: 260px; }
</style>
