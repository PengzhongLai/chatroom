import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      name: 'Main',
      component: () => import('@/views/MainLayout.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/channel/:id',
      name: 'Channel',
      component: () => import('@/views/MainLayout.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/private/:chatId',
      name: 'PrivateChat',
      component: () => import('@/views/MainLayout.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')
  } else if (to.meta.guest && authStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

// Restore user on page refresh
let userRestored = false
router.beforeResolve(async (_to, _from, next) => {
  const authStore = useAuthStore()
  if (!userRestored && authStore.token) {
    userRestored = true
    await authStore.fetchUser()
  }
  next()
})

export default router
