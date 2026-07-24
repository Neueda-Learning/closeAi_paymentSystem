<template>
  <div v-if="errorCode" class="error-panel">
    <div class="error-header">
      <el-icon :size="20"><WarningFilled /></el-icon>
      <span>Payment Failed</span>
    </div>
    <div class="error-body">
      <div class="error-row"><span class="label">Error Code</span><span class="value">{{ errorCode }}</span></div>
      <div v-if="reason" class="error-row"><span class="label">Reason</span><span class="value">{{ reason }}</span></div>
      <div class="error-row"><span class="label">Description</span><span class="value">{{ description }}</span></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ERROR_CODE_MAP } from '../utils/constants'

const props = defineProps({ errorCode: { type: String, default: null }, reason: { type: String, default: null } })

const description = computed(() => ERROR_CODE_MAP[props.errorCode] || props.errorCode)
</script>

<style scoped>
.error-panel {
  margin-top: 16px;
  border: 2px solid #e23b4a;
  border-radius: 16px;
  overflow: hidden;
}
.error-header {
  display: flex; align-items: center; gap: 8px;
  padding: 16px 20px;
  background: #e23b4a;
  color: #fff;
  font-family: Inter, system-ui, sans-serif;
  font-size: 16px; font-weight: 700;
}
.error-body { padding: 20px; display: flex; flex-direction: column; gap: 10px; }
.error-row { display: flex; font-family: Inter, system-ui, sans-serif; font-size: 14px; }
.error-row .label { color: #191c1f; font-weight: 600; min-width: 120px; letter-spacing: 0.16px; }
.error-row .value { color: #e23b4a; letter-spacing: 0.16px; }
</style>
