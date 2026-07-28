export const PAYMENT_STATUS = {
  CREATED:   { label: 'Created',   color: '#191c1f', bgColor: '#f4f4f4', type: 'info' },
  VALIDATED: { label: 'Validated', color: '#494fdf', bgColor: '#eeefff', type: '' },
  SENT:      { label: 'Sent',      color: '#ec7e00', bgColor: '#fff8ee', type: 'warning' },
  COMPLETED: { label: 'Completed', color: '#00a87e', bgColor: '#eefff9', type: 'success' },
  FAILED:    { label: 'Failed',    color: '#e23b4a', bgColor: '#fff0f0', type: 'danger' },
  CANCELLED: { label: 'Cancelled', color: '#909399', bgColor: '#f4f4f4', type: 'info' },
  REVERSED:  { label: 'Reversed',  color: '#ec7e00', bgColor: '#fff8ee', type: 'warning' },
}

export const SUPPORTED_CURRENCIES = ['USD', 'EUR', 'GBP', 'CNY']

export const ERROR_CODE_MAP = {
  VALIDATION_FAILED:          'Validation Failed',
  INSUFFICIENT_FUNDS:         'Insufficient Funds',
  INVALID_ACCOUNT:            'Invalid Account',
  INVALID_CURRENCY:           'Invalid Currency',
  INVALID_AMOUNT:             'Invalid Amount',
  INVALID_CREDENTIALS:        'Source account password is incorrect',
  BENEFICIARY_MISMATCH:       'Recipient surname does not match',
  EXCHANGE_RATE_NOT_FOUND:    'Exchange rate is unavailable',
  DUPLICATE_ACCOUNT:          'Duplicate Account',
  DUPLICATE_PAYMENT:          'Duplicate Payment',
  INVALID_STATUS_TRANSITION:  'Invalid Status Transition',
  PAYMENT_NOT_FOUND:          'Payment Not Found',
  PROCESSING_ERROR:           'Processing Error',
  NETWORK_ERROR:              'Network Error',
  RISK_BLOCKED:               'Risk Blocked',
}

export const STATUS_ACTIONS = {
  CREATED:   [{ key: 'validate', label: 'Validate', icon: 'Check' },
              { key: 'fail',     label: 'Mark Failed', icon: 'Close', danger: true },
              { key: 'cancel',   label: 'Cancel', icon: 'Remove', danger: true }],
  VALIDATED: [{ key: 'send',     label: 'Send', icon: 'Promotion' },
              { key: 'fail',     label: 'Mark Failed', icon: 'Close', danger: true },
              { key: 'cancel',   label: 'Cancel', icon: 'Remove', danger: true }],
  SENT:      [{ key: 'complete', label: 'Complete', icon: 'CircleCheck' },
              { key: 'fail',     label: 'Mark Failed', icon: 'Close', danger: true },
              { key: 'cancel',   label: 'Cancel', icon: 'Remove', danger: true }],
  FAILED:    [{ key: 'edit',     label: 'Edit',  icon: 'Edit' },
              { key: 'retry',    label: 'Retry', icon: 'Refresh' }],
  COMPLETED: [{ key: 'reverse',  label: 'Reverse', icon: 'RefreshLeft', danger: true }],
  CANCELLED: [],
  REVERSED:  [],
}
