<template>
  <div class="form-wrapper">
    <h2 class="page-title">Create Payment</h2>
    <form class="form" @submit.prevent="handleSubmit">
      <!-- Source Account -->
      <label class="field">
        <span class="field-label">Source Account</span>
        <input v-model="form.sourceAccount" class="input" placeholder="e.g. ACC-001" :class="{ error: errors.sourceAccount }" @input="clearError('sourceAccount')" />
        <span v-if="errors.sourceAccount" class="field-error">{{ errors.sourceAccount }}</span>
      </label>

      <!-- Destination Account -->
      <label class="field">
        <span class="field-label">Destination Account</span>
        <input v-model="form.destinationAccount" class="input" placeholder="e.g. ACC-002" :class="{ error: errors.destinationAccount }" @input="clearError('destinationAccount')" />
        <span v-if="errors.destinationAccount" class="field-error">{{ errors.destinationAccount }}</span>
      </label>

      <!-- Amount -->
      <label class="field">
        <span class="field-label">Amount</span>
        <input v-model.number="form.amount" class="input" type="number" step="0.01" min="0.01" max="1000000" placeholder="0.00" :class="{ error: errors.amount }" @input="clearError('amount')" />
        <span v-if="errors.amount" class="field-error">{{ errors.amount }}</span>
      </label>

      <!-- Currency -->
      <label class="field">
        <span class="field-label">Currency</span>
        <select v-model="form.currency" class="input select" :class="{ error: errors.currency }" @change="clearError('currency')">
          <option v-for="c in SUPPORTED_CURRENCIES" :key="c" :value="c">{{ c }}</option>
        </select>
        <span v-if="errors.currency" class="field-error">{{ errors.currency }}</span>
      </label>

      <!-- Description -->
      <label class="field">
        <span class="field-label">Description <small>(optional)</small></span>
        <input v-model="form.description" class="input" placeholder="e.g. Invoice payment" />
      </label>

      <div class="form-actions">
        <button type="submit" class="btn" :disabled="loading">
          {{ loading ? 'Creating...' : 'Create Payment' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { SUPPORTED_CURRENCIES } from '../utils/constants'

const emit = defineEmits(['submit'])
const props = defineProps({ loading: { type: Boolean, default: false } })

const form = reactive({
  sourceAccount: '',
  destinationAccount: '',
  amount: null,
  currency: 'USD',
  description: '',
})

const errors = reactive({
  sourceAccount: '',
  destinationAccount: '',
  amount: '',
  currency: '',
})

function clearError(field) { errors[field] = '' }

function validate() {
  let ok = true
  if (!form.sourceAccount.trim()) { errors.sourceAccount = 'Source account is required'; ok = false }
  if (!form.destinationAccount.trim()) { errors.destinationAccount = 'Destination account is required'; ok = false }
  if (form.sourceAccount.trim() && form.sourceAccount.trim() === form.destinationAccount.trim()) {
    errors.destinationAccount = 'Must differ from source account'; ok = false
  }
  if (form.amount === null || form.amount === '') { errors.amount = 'Amount is required'; ok = false }
  else if (form.amount <= 0) { errors.amount = 'Must be greater than 0'; ok = false }
  else if (form.amount > 1000000) { errors.amount = 'Must not exceed 1,000,000'; ok = false }
  return ok
}

function handleSubmit() {
  if (validate()) emit('submit', { ...form })
}
</script>

<style scoped>
.form-wrapper { max-width: 560px; margin: 0 auto; }
.page-title { font-family: Inter, system-ui, sans-serif; font-size: 28px; font-weight: 700; color: #191c1f; margin-bottom: 28px; letter-spacing: -0.28px; }
.form { display: flex; flex-direction: column; gap: 20px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field-label { font-family: Inter, system-ui, sans-serif; font-size: 14px; font-weight: 600; color: #191c1f; letter-spacing: 0.16px; }
.field-label small { font-weight: 400; color: #999; }
.input {
  padding: 14px 18px;
  border: 2px solid #f4f4f4;
  border-radius: 16px;
  font-family: Inter, system-ui, sans-serif;
  font-size: 15px; color: #191c1f;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.input:focus { border-color: #494fdf; }
.input.error { border-color: #e23b4a; }
.select { cursor: pointer; appearance: auto; }
.field-error { font-size: 12px; color: #e23b4a; font-family: Inter, system-ui, sans-serif; }
.form-actions { margin-top: 8px; }
.btn {
  padding: 18px 48px;
  border-radius: 9999px;
  border: none;
  background: #191c1f;
  color: #fff;
  font-family: Inter, system-ui, sans-serif;
  font-size: 16px; font-weight: 700;
  letter-spacing: 0.16px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn:hover { opacity: 0.85; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
