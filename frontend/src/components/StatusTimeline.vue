<template>
  <div class="timeline">
    <h3 v-if="!hideTitle" class="title">Status History</h3>
    <div v-if="history.length" class="steps">
      <div v-for="(item, idx) in history" :key="item.id || idx" class="step">
        <el-icon v-if="!item.errorCode" :size="18" class="step-icon step-ok"><CircleCheckFilled /></el-icon>
        <el-icon v-else :size="18" class="step-icon step-err"><CircleCloseFilled /></el-icon>
        <div v-if="idx < history.length - 1" class="line"></div>
        <div class="content">
          <div class="status-row">
            <span class="to-status">{{ item.toStatus }}</span>
            <span v-if="item.fromStatus" class="from-status">from {{ item.fromStatus }}</span>
          </div>
          <div class="time">{{ formatTime(item.changedAt) }}</div>
          <div v-if="item.reason" class="reason">{{ item.reason }}</div>
          <div v-if="item.errorCode" class="error-code">{{ item.errorCode }}</div>
        </div>
      </div>
    </div>
    <div v-else class="empty">No history recorded</div>
  </div>
</template>

<script setup>
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
defineProps({ history: { type: Array, default: () => [] }, hideTitle: { type: Boolean, default: false } })
function formatTime(t) { return t ? new Date(t).toLocaleString() : '' }
</script>

<style scoped>
.timeline { margin-top: 0; }
.title { font-family: Inter, system-ui, sans-serif; font-size: 18px; font-weight: 700; color: #191c1f; margin-bottom: 12px; letter-spacing: 0.16px; }
.steps { display: flex; flex-direction: column; }
.step { display: flex; align-items: flex-start; gap: 12px; position: relative; padding-bottom: 24px; }
.step-icon { flex-shrink: 0; margin-top: 0; }
.step-ok { color: #059669; }
.step-err { color: #e23b4a; }
.line { position: absolute; left: 8px; top: 22px; width: 2px; height: calc(100% - 10px); background: #f4f4f4; }
.content { font-family: Inter, system-ui, sans-serif; }
.status-row { display: flex; gap: 8px; align-items: baseline; }
.to-status { font-size: 15px; font-weight: 700; color: #191c1f; letter-spacing: 0.16px; }
.from-status { font-size: 12px; color: #999; }
.time { font-size: 12px; color: #999; margin-top: 2px; }
.reason { font-size: 13px; color: #666; margin-top: 4px; }
.error-code { font-size: 13px; color: #e23b4a; font-weight: 600; margin-top: 2px; }
.empty { color: #999; font-size: 14px; }
</style>
