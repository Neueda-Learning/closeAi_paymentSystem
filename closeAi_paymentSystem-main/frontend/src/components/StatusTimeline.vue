<template>
  <div class="timeline">
    <h3 class="title">Status History</h3>
    <div v-if="history.length" class="steps">
      <div v-for="(item, idx) in history" :key="item.id || idx" class="step">
        <div class="dot" :class="{ error: item.errorCode }"></div>
        <div v-if="idx < history.length - 1" class="line"></div>
        <div class="content">
          <div class="status-row">
            <span class="to-status">{{ item.toStatus }}</span>
            <span v-if="item.fromStatus" class="from-status">from {{ item.fromStatus }}</span>
          </div>
          <div class="time">{{ formatTime(item.changedAt) }}</div>
          <div v-if="item.triggeredBy" class="trigger">Triggered by {{ item.triggeredBy }}</div>
          <div v-if="item.reason" class="reason">{{ item.reason }}</div>
          <div v-if="item.errorCode" class="error-code">{{ item.errorCode }}</div>
        </div>
      </div>
    </div>
    <div v-else class="empty">No history recorded</div>
  </div>
</template>

<script setup>
defineProps({ history: { type: Array, default: () => [] } })
function formatTime(t) { return t ? new Date(t).toLocaleString() : '' }
</script>

<style scoped>
.timeline { margin-top: 24px; }
.title { font-family: Inter, system-ui, sans-serif; font-size: 18px; font-weight: 700; color: #191c1f; margin-bottom: 20px; letter-spacing: 0.16px; }
.steps { display: flex; flex-direction: column; }
.step { display: flex; align-items: flex-start; gap: 12px; position: relative; padding-bottom: 24px; }
.dot { width: 14px; height: 14px; border-radius: 9999px; background: #494fdf; flex-shrink: 0; margin-top: 4px; }
.dot.error { background: #e23b4a; }
.line { position: absolute; left: 6px; top: 22px; width: 2px; height: calc(100% - 10px); background: #f4f4f4; }
.content { font-family: Inter, system-ui, sans-serif; }
.status-row { display: flex; gap: 8px; align-items: baseline; }
.to-status { font-size: 15px; font-weight: 700; color: #191c1f; letter-spacing: 0.16px; }
.from-status { font-size: 12px; color: #999; }
.time { font-size: 12px; color: #999; margin-top: 2px; }
.trigger { display: inline-block; margin-top: 5px; padding: 3px 7px; border-radius: 7px; background: #f4f4f4; color: #666; font-size: 10px; font-weight: 700; letter-spacing: .04em; }
.reason { font-size: 13px; color: #666; margin-top: 4px; }
.error-code { font-size: 13px; color: #e23b4a; font-weight: 600; margin-top: 2px; }
.empty { color: #999; font-size: 14px; }
</style>
