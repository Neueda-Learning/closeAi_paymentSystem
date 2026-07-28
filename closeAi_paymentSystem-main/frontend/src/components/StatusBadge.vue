<template>
  <span class="status-badge" :style="{ color: info.color, background: info.bgColor }">
    {{ info.label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { PAYMENT_STATUS } from '../utils/constants'

const props = defineProps({ status: { type: String, required: true } })

const info = computed(() => PAYMENT_STATUS[props.status] || { label: props.status, color: '#191c1f', bgColor: '#f4f4f4' })
</script>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 9999px;
  font-family: Inter, system-ui, sans-serif;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.16px;
}
</style>
