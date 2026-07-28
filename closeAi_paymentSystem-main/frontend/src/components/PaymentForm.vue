<template>
  <div class="form-wrapper">
    <h2 class="page-title">Create Payment</h2>
    <form class="form" @submit.prevent="handleSubmit">
      <div class="field">
        <span class="field-label">Source Account</span>
        <el-autocomplete
          v-model="form.sourceAccount"
          class="account-autocomplete"
          :class="{ error: errors.sourceAccount }"
          :fetch-suggestions="querySourceAccounts"
          placeholder="Type an account number or name"
          clearable
          :trigger-on-focus="true"
          @input="sourceInput"
          @select="sourceSelected"
          @clear="sourceCleared"
        >
          <template #default="{ item }">
            <div class="suggestion">
              <div class="suggestion-main">
                <strong>{{ item.value }}</strong>
                <span>{{ item.name }}</span>
              </div>
              <span class="suggestion-meta">{{ item.balance }}</span>
            </div>
          </template>
        </el-autocomplete>
        <span v-if="errors.sourceAccount" class="field-error">{{ errors.sourceAccount }}</span>
        <span v-else class="field-hint">Type freely, then choose a matching suggestion.</span>
      </div>

      <div class="field">
        <span class="field-label">Destination Account</span>
        <el-autocomplete
          v-model="form.destinationAccount"
          class="account-autocomplete"
          :class="{ error: errors.destinationAccount }"
          :fetch-suggestions="queryDestinationAccounts"
          placeholder="Type a recipient account number or name"
          clearable
          :trigger-on-focus="true"
          @input="clearError('destinationAccount')"
          @select="destinationSelected"
        >
          <template #default="{ item }">
            <div class="suggestion">
              <div class="suggestion-main">
                <strong>{{ item.value }}</strong>
                <span>{{ item.name }}</span>
              </div>
              <span class="suggestion-meta">{{ item.currency }}</span>
            </div>
          </template>
        </el-autocomplete>
        <span v-if="errors.destinationAccount" class="field-error">{{ errors.destinationAccount }}</span>
        <span v-else class="field-hint">Search by account number or recipient name.</span>
      </div>

      <label class="field">
        <span class="field-label">Amount</span>
        <input
          v-model.number="form.amount"
          class="input"
          type="number"
          step="0.01"
          min="0.01"
          max="1000000"
          placeholder="0.00"
          :class="{ error: errors.amount }"
          @input="clearError('amount')"
        />
        <span v-if="errors.amount" class="field-error">{{ errors.amount }}</span>
      </label>

      <label class="field">
        <span class="field-label">Currency</span>
        <input v-model="form.currency" class="input readonly" readonly />
        <span class="field-hint">Currency is determined by the source account.</span>
        <span v-if="errors.currency" class="field-error">{{ errors.currency }}</span>
      </label>

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
import { reactive } from 'vue'

const emit = defineEmits(['submit'])
const props = defineProps({
  loading: { type: Boolean, default: false },
  accounts: { type: Array, default: () => [] },
})

const form = reactive({
  sourceAccount: '',
  destinationAccount: '',
  amount: null,
  currency: '',
  description: '',
})

const errors = reactive({
  sourceAccount: '',
  destinationAccount: '',
  amount: '',
  currency: '',
})

function clearError(field) {
  errors[field] = ''
}

function findAccount(value) {
  const normalized = value?.trim().toUpperCase()
  return props.accounts.find(
    (account) => account.accountNumber.toUpperCase() === normalized
  )
}

function formatBalance(account) {
  const balance = Number(account.balance).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return `${balance} ${account.currency}`
}

function toSuggestion(account, maskName = false) {
  return {
    value: account.accountNumber,
    name: maskName ? account.maskedAccountName : account.accountName,
    currency: account.currency,
    balance: formatBalance(account),
    searchText: [
      account.accountNumber,
      account.accountName,
      account.maskedAccountName,
      account.currency,
    ].filter(Boolean).join(' ').toLowerCase(),
  }
}

function filterSuggestions(query, accounts, maskName, callback) {
  const keyword = query.trim().toLowerCase()
  const suggestions = accounts
    .map((account) => toSuggestion(account, maskName))
    .filter((item) => !keyword || item.searchText.includes(keyword))
    .slice(0, 8)
  callback(suggestions)
}

function querySourceAccounts(query, callback) {
  filterSuggestions(query, props.accounts, false, callback)
}

function queryDestinationAccounts(query, callback) {
  const sourceNumber = form.sourceAccount.trim().toUpperCase()
  const candidates = props.accounts.filter(
    (account) => account.accountNumber.toUpperCase() !== sourceNumber
  )
  filterSuggestions(query, candidates, true, callback)
}

function sourceInput(value) {
  clearError('sourceAccount')
  const account = findAccount(value)
  form.currency = account?.currency || ''
  if (form.destinationAccount.trim().toUpperCase() === value.trim().toUpperCase()) {
    form.destinationAccount = ''
  }
}

function sourceSelected(item) {
  form.sourceAccount = item.value
  sourceInput(item.value)
}

function sourceCleared() {
  form.currency = ''
  clearError('sourceAccount')
}

function destinationSelected(item) {
  form.destinationAccount = item.value
  clearError('destinationAccount')
}

function validate() {
  let valid = true
  const sourceAccount = findAccount(form.sourceAccount)
  const destinationAccount = findAccount(form.destinationAccount)

  if (!form.sourceAccount.trim()) {
    errors.sourceAccount = 'Source account is required'
    valid = false
  } else if (!sourceAccount) {
    errors.sourceAccount = 'Enter an existing source account'
    valid = false
  }

  if (!form.destinationAccount.trim()) {
    errors.destinationAccount = 'Destination account is required'
    valid = false
  } else if (!destinationAccount) {
    errors.destinationAccount = 'Enter an existing destination account'
    valid = false
  }

  if (sourceAccount) {
    form.sourceAccount = sourceAccount.accountNumber
    form.currency = sourceAccount.currency
  }
  if (destinationAccount) {
    form.destinationAccount = destinationAccount.accountNumber
  }

  if (
    sourceAccount &&
    destinationAccount &&
    sourceAccount.accountNumber === destinationAccount.accountNumber
  ) {
    errors.destinationAccount = 'Must differ from source account'
    valid = false
  }

  if (form.amount === null || form.amount === '') {
    errors.amount = 'Amount is required'
    valid = false
  } else if (form.amount <= 0) {
    errors.amount = 'Must be greater than 0'
    valid = false
  } else if (form.amount > 1000000) {
    errors.amount = 'Must not exceed 1,000,000'
    valid = false
  }

  return valid
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
  font-size: 15px;
  color: #191c1f;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}
.input:focus { border-color: #494fdf; }
.input.error { border-color: #e23b4a; }
.account-autocomplete { width: 100%; }
.account-autocomplete :deep(.el-input__wrapper) {
  padding: 4px 16px;
  border: 2px solid #f4f4f4;
  border-radius: 16px;
  box-shadow: none;
  min-height: 52px;
  transition: border-color 0.15s;
}
.account-autocomplete :deep(.el-input__wrapper.is-focus) {
  border-color: #494fdf;
  box-shadow: none;
}
.account-autocomplete.error :deep(.el-input__wrapper) { border-color: #e23b4a; }
.account-autocomplete :deep(.el-input__inner) {
  font-family: Inter, system-ui, sans-serif;
  font-size: 15px;
  color: #191c1f;
}
.suggestion { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 5px 0; }
.suggestion-main { display: flex; flex-direction: column; min-width: 0; line-height: 1.4; }
.suggestion-main strong { color: #191c1f; font-size: 13px; }
.suggestion-main span { overflow: hidden; color: #888; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.suggestion-meta { flex: 0 0 auto; color: #666; font-size: 12px; font-weight: 600; }
.readonly { background: #fafafa; color: #777; }
.field-hint { color: #999; font-size: 12px; font-weight: 400; }
.field-error { font-size: 12px; color: #e23b4a; font-family: Inter, system-ui, sans-serif; }
.form-actions { margin-top: 8px; }
.btn {
  padding: 18px 48px;
  border-radius: 9999px;
  border: none;
  background: #191c1f;
  color: #fff;
  font-family: Inter, system-ui, sans-serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.16px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn:hover { opacity: 0.85; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
