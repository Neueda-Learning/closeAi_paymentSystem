import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/payments' },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportsView.vue'),
  },
  {
    path: '/payments',
    name: 'PaymentList',
    component: () => import('../views/PaymentListView.vue'),
  },
  {
    path: '/payments/create',
    name: 'CreatePayment',
    component: () => import('../views/CreatePaymentView.vue'),
  },
  {
    path: '/payments/:id',
    name: 'PaymentDetail',
    component: () => import('../views/PaymentDetailView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
