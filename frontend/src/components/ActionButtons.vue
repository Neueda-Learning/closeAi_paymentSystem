<template>
  <div>
    <!-- Exhaustion banner -->
    <div v-if="isExhausted" class="exhausted-banner">
      <el-icon :size="20"><WarningFilled /></el-icon>
      <span>Retry limit reached ({{ retryCount }}/{{ MAX_RETRIES }}). This payment has permanently failed and cannot be retried or edited.</span>
    </div>

    <div v-if="actions.length" class="action-bar">
      <button
        v-for="act in actions" :key="act.key"
        class="btn"
        :class="{ danger: act.danger }"
        :disabled="!!loading"
        @click="$emit('action', act.key)"
      >
        {{ loading === act.key ? 'Processing...' : act.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { STATUS_ACTIONS } from '../utils/constants'

const MAX_RETRIES = 3

const props = defineProps({
  status: { type: String, required: true },
  retryCount: { type: Number, default: 0 },
  loading: { type: String, default: null },
})
defineEmits(['action'])

const isExhausted = computed(() => props.status === 'FAILED' && props.retryCount >= MAX_RETRIES)

const actions = computed(() => {
  if (isExhausted.value) return []  // No actions available when exhausted
  return STATUS_ACTIONS[props.status] || []
})
</script>

<style scoped>
.action-bar { display: flex; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
.exhausted-banner {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 20px;
  margin-top: 16px;
  border: 2px solid #e23b4a;
  border-radius: 16px;
  background: #fff0f0;
  color: #e23b4a;
  font-family: Inter, system-ui, sans-serif;
  font-size: 14px; font-weight: 600;
  letter-spacing: 0.16px;
}
.btn {
  padding: 14px 34px;
  border-radius: 9999px;
  border: none;
  background: #191c1f;
  color: #fff;
  font-family: Inter, system-ui, sans-serif;
  font-size: 15px; font-weight: 600;
  letter-spacing: 0.16px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn:hover { opacity: 0.85; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn.danger { background: #e23b4a; }
</style>
