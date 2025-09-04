import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import EventsView from '@/views/EventsView.vue'
import LoginView from '@/views/LoginView.vue'
import OrdersView from '@/views/OrdersView.vue'
import PerformancesView from '@/views/PerformancesView.vue'
import { getToken } from '@/api/client'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: LoginView },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/events' },
        { path: 'events', component: EventsView },
        { path: 'events/:eventId/performances', component: PerformancesView },
        { path: 'check-in', component: () => import('@/views/CheckInView.vue') },
        { path: 'orders', component: OrdersView },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/events' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return '/login'
  }
  if (to.path === '/login' && getToken()) {
    return '/events'
  }
})

export default router
