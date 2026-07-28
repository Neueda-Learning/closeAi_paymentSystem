<template>
  <div class="accounts-page">
    <section class="form-panel">
      <p class="eyebrow">Account management</p>
      <h1>Add a new account</h1>
      <p class="intro">Create an account that can be used as a source or destination for payments.</p>

      <form class="account-form" @submit.prevent="submit">
        <label class="field">
          <span>Account number</span>
          <input
            v-model.trim="form.accountNumber"
            class="input"
            :class="{ error: errors.accountNumber }"
            placeholder="ACC-10001"
            maxlength="14"
            @input="normalizeAccountNumber"
          />
          <small v-if="errors.accountNumber" class="field-error">{{ errors.accountNumber }}</small>
          <small v-else class="hint">Use ACC- followed by 3 to 10 digits.</small>
        </label>

        <label class="field">
          <span>Account name</span>
          <input
            v-model.trim="form.accountName"
            class="input"
            :class="{ error: errors.accountName }"
            placeholder="Customer or business name"
            maxlength="100"
            @input="errors.accountName = ''"
          />
          <small v-if="errors.accountName" class="field-error">{{ errors.accountName }}</small>
        </label>

        <label class="field">
          <span>Account holder surname</span>
          <input v-model.trim="form.holderLastName" class="input" :class="{ error: errors.holderLastName }" placeholder="Used to verify incoming payments" maxlength="50" @input="errors.holderLastName = ''" />
          <small v-if="errors.holderLastName" class="field-error">{{ errors.holderLastName }}</small>
        </label>

        <label class="field">
          <span>Account password</span>
          <input v-model="form.password" class="input" :class="{ error: errors.password }" type="password" autocomplete="new-password" placeholder="At least 8 characters" maxlength="128" @input="errors.password = ''" />
          <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
        </label>

        <div class="field-row">
          <label class="field">
            <span>Initial balance</span>
            <input
              v-model="form.balance"
              class="input"
              :class="{ error: errors.balance }"
              type="number"
              min="0"
              step="0.01"
              placeholder="0.00"
              @input="errors.balance = ''"
            />
            <small v-if="errors.balance" class="field-error">{{ errors.balance }}</small>
          </label>

          <label class="field">
            <span>Currency</span>
            <select v-model="form.currency" class="input select">
              <option v-for="currency in SUPPORTED_CURRENCIES" :key="currency" :value="currency">
                {{ currency }}
              </option>
            </select>
          </label>
        </div>

        <button class="submit-btn" type="submit" :disabled="saving">
          {{ saving ? 'Adding account...' : 'Add account' }}
        </button>
      </form>
    </section>

    <section class="list-panel">
      <div class="list-heading">
        <div>
          <p class="eyebrow">Directory</p>
          <h2>Accounts</h2>
        </div>
        <span class="count">{{ accounts.length }}</span>
      </div>

      <div v-if="loading" class="empty">Loading accounts...</div>
      <div v-else-if="!accounts.length" class="empty">No accounts yet.</div>
      <div v-else class="account-list">
        <article v-for="account in accounts" :key="account.accountNumber" class="account-card">
          <div class="avatar">{{ initials(account.accountName) }}</div>
          <div class="account-info">
            <strong>{{ account.accountName }}</strong>
            <span>{{ account.accountNumber }}</span>
          </div>
          <div class="balance">
            <strong>{{ formatBalance(account.balance) }}</strong>
            <span>{{ account.currency }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAccount, listAccounts } from '../api/account'
import { SUPPORTED_CURRENCIES } from '../utils/constants'

const accounts = ref([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  accountNumber: '',
  accountName: '',
  balance: '0.00',
  currency: 'USD',
  holderLastName: '',
  password: '',
})
const errors = reactive({ accountNumber: '', accountName: '', balance: '', holderLastName: '', password: '' })

function normalizeAccountNumber() {
  form.accountNumber = form.accountNumber.toUpperCase()
  errors.accountNumber = ''
}

function validate() {
  errors.accountNumber = /^ACC-\d{3,10}$/.test(form.accountNumber)
    ? ''
    : 'Enter ACC- followed by 3 to 10 digits'
  errors.accountName = form.accountName ? '' : 'Account name is required'
  errors.holderLastName = form.holderLastName ? '' : 'Account holder surname is required'
  errors.password = form.password.length >= 8 ? '' : 'Password must contain at least 8 characters'
  const balance = Number(form.balance)
  errors.balance = form.balance !== '' && Number.isFinite(balance) && balance >= 0
    ? ''
    : 'Balance must be zero or greater'
  return !Object.values(errors).some(Boolean)
}

async function loadAccounts() {
  loading.value = true
  try {
    const response = await listAccounts()
    accounts.value = response.data || []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!validate()) return
  saving.value = true
  try {
    await createAccount({
      ...form,
      balance: Number(form.balance),
    })
    ElMessage.success('Account added successfully')
    Object.assign(form, { accountNumber: '', accountName: '', balance: '0.00', currency: 'USD', holderLastName: '', password: '' })
    await loadAccounts()
  } catch {
    // The shared API interceptor displays the server error.
  } finally {
    saving.value = false
  }
}

function initials(name) {
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function formatBalance(value) {
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value))
}

onMounted(loadAccounts)
</script>

<style scoped>
.accounts-page { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 0.92fr); gap: 72px; align-items: start; }
.eyebrow { color: #777; font-size: 12px; font-weight: 700; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 10px; }
h1 { font-size: 34px; letter-spacing: -0.04em; line-height: 1.1; }
.intro { color: #777; line-height: 1.6; margin: 12px 0 30px; max-width: 480px; }
.account-form { display: flex; flex-direction: column; gap: 20px; }
.field-row { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 7px; font-size: 14px; font-weight: 600; }
.input { width: 100%; padding: 14px 16px; border: 2px solid #f0f0f0; border-radius: 15px; background: #fff; color: #191c1f; font: inherit; font-weight: 500; outline: none; transition: border-color .15s, box-shadow .15s; }
.input:focus { border-color: #494fdf; box-shadow: 0 0 0 4px rgba(73,79,223,.08); }
.input.error { border-color: #e23b4a; }
.select { cursor: pointer; }
.hint { color: #999; font-weight: 400; }
.field-error { color: #e23b4a; font-weight: 500; }
.submit-btn { align-self: flex-start; margin-top: 6px; padding: 16px 32px; border: 0; border-radius: 999px; background: #191c1f; color: #fff; font-size: 15px; font-weight: 700; cursor: pointer; }
.submit-btn:disabled { cursor: not-allowed; opacity: .5; }
.list-panel { border: 1px solid #eee; border-radius: 24px; padding: 24px; background: #fafafa; }
.list-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.list-heading h2 { font-size: 24px; }
.count { display: grid; place-items: center; min-width: 34px; height: 34px; padding: 0 10px; border-radius: 99px; background: #191c1f; color: #fff; font-size: 13px; font-weight: 700; }
.account-list { display: flex; flex-direction: column; gap: 10px; max-height: 570px; overflow: auto; }
.account-card { display: flex; align-items: center; gap: 12px; padding: 15px; border: 1px solid #eee; border-radius: 17px; background: #fff; }
.avatar { display: grid; place-items: center; flex: 0 0 40px; height: 40px; border-radius: 13px; background: #eef0ff; color: #494fdf; font-size: 12px; font-weight: 800; }
.account-info, .balance { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.account-info { flex: 1; }
.account-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.account-info span, .balance span { color: #999; font-size: 12px; }
.balance { align-items: flex-end; }
.balance strong { font-size: 14px; font-variant-numeric: tabular-nums; }
.empty { padding: 48px 10px; text-align: center; color: #999; }
@media (max-width: 820px) {
  .accounts-page { grid-template-columns: 1fr; gap: 42px; }
}
@media (max-width: 520px) {
  .field-row { grid-template-columns: 1fr; }
}
</style>
