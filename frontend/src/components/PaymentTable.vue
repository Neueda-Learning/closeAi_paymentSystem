<template>
  <div>
    <div v-if="loading" class="loading">Loading...</div>
    <table v-else-if="payments.length" class="table">
      <thead>
        <tr>
          <th>Payment ID</th>
          <th>Amount</th>
          <th>Currency</th>
          <th>Status</th>
          <th>Description</th>
          <th>Created</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in payments" :key="row.id" @click="$router.push(`/payments/${row.id}`)" class="row">
          <td class="mono">{{ row.id?.slice(0, 8) }}...</td>
          <td class="num">{{ row.amount }}</td>
          <td>{{ row.currency }}</td>
          <td><StatusBadge :status="row.status" /></td>
          <td class="desc">{{ row.description || '-' }}</td>
          <td class="time">{{ formatTime(row.createdAt) }}</td>
          <td><span class="link">Detail →</span></td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty">No payments found</div>

    <div v-if="total > 0" class="pager">
      <span class="info">{{ total }} total</span>
      <div class="controls">
        <button :disabled="page <= 1" @click="$emit('pageChange', page - 1)">Prev</button>
        <span class="info">Page {{ page }}</span>
        <button :disabled="page * limit >= total" @click="$emit('pageChange', page + 1)">Next</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import StatusBadge from './StatusBadge.vue'

defineProps({
  payments: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  limit: { type: Number, default: 20 },
  loading: { type: Boolean, default: false },
})
defineEmits(['pageChange'])

function formatTime(t) { return t ? new Date(t).toLocaleDateString() : '' }
</script>

<style scoped>
.loading, .empty { text-align: center; padding: 60px 0; color: #999; font-family: Inter, system-ui, sans-serif; font-size: 16px; }
.table { width: 100%; border-collapse: collapse; }
.table th { text-align: left; padding: 14px 16px; font-family: Inter, system-ui, sans-serif; font-size: 12px; font-weight: 700; color: #999; letter-spacing: 0.24px; text-transform: uppercase; border-bottom: 1px solid #f4f4f4; }
.row { cursor: pointer; transition: background 0.1s; }
.row:hover { background: #fafafa; }
.table td { padding: 16px; font-family: Inter, system-ui, sans-serif; font-size: 14px; color: #191c1f; letter-spacing: 0.16px; border-bottom: 1px solid #fafafa; }
.mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 13px; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.desc { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #666; }
.time { color: #999; font-size: 13px; }
.link { color: #494fdf; font-weight: 600; font-size: 13px; }
.pager { display: flex; justify-content: space-between; align-items: center; margin-top: 24px; font-family: Inter, system-ui, sans-serif; }
.info { font-size: 13px; color: #999; letter-spacing: 0.16px; }
.controls { display: flex; gap: 12px; align-items: center; }
.controls button { padding: 10px 22px; border-radius: 9999px; border: 2px solid #f4f4f4; background: #fff; font-family: Inter, system-ui, sans-serif; font-size: 14px; font-weight: 600; color: #191c1f; cursor: pointer; transition: border-color 0.15s; }
.controls button:hover:not(:disabled) { border-color: #494fdf; }
.controls button:disabled { opacity: 0.3; cursor: not-allowed; }
</style>
