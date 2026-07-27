<template>
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
</template>

<script setup>
import { computed } from 'vue'
import { STATUS_ACTIONS } from '../utils/constants'

const props = defineProps({ status: { type: String, required: true }, loading: { type: String, default: null } })
defineEmits(['action'])

const actions = computed(() => STATUS_ACTIONS[props.status] || [])
</script>

<style scoped>
.action-bar { display: flex; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
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
