import api from './index'

export function createAccount(data) {
  return api.post('/accounts', data)
}

export function listAccounts() {
  return api.get('/accounts')
}
