import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue')
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchView.vue')
    },
    {
      path: '/category/:id',
      name: 'category',
      component: () => import('@/views/CategoryView.vue')
    },
    {
      path: '/articles/new',
      name: 'article-new',
      component: () => import('@/views/ArticleEditorView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/articles/:id/edit',
      name: 'article-edit',
      component: () => import('@/views/ArticleEditorView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/articles/:id',
      name: 'article-detail',
      component: () => import('@/views/ArticleDetailView.vue')
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/ai',
      name: 'ai-chat',
      component: () => import('@/views/AiChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/users/:userId/follows',
      name: 'follows',
      component: () => import('@/views/FollowListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/users/:userId',
      name: 'user-profile',
      component: () => import('@/views/UserProfileView.vue')
    },
    {
      path: '/users/:userId/fans',
      name: 'fans',
      component: () => import('@/views/FollowListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue')
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/admin/views/AdminLoginView.vue')
    },
    {
      path: '/admin/403',
      name: 'admin-403',
      component: () => import('@/admin/views/AdminForbiddenView.vue')
    },
    {
      path: '/admin',
      component: () => import('@/admin/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      children: [
        {
          path: '',
          redirect: { name: 'admin-dashboard' }
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/admin/views/AdminDashboardView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: 'articles',
          name: 'admin-articles',
          component: () => import('@/admin/views/AdminArticlesView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: 'comments',
          name: 'admin-comments',
          component: () => import('@/admin/views/AdminCommentsView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: () => import('@/admin/views/AdminCategoriesView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/admin/views/AdminUsersView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('@/admin/views/AdminSettingsView.vue'),
          meta: { requiresAdmin: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.initialized) {
    await authStore.restore()
  }

  if (to.meta.requiresAdmin) {
    if (!authStore.isAuthenticated) {
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
    if (!authStore.isAdmin) {
      return { name: 'admin-403' }
    }
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if ((to.name === 'login' || to.name === 'register') && authStore.isAuthenticated) {
    return { name: 'home' }
  }

  if (to.name === 'admin-login') {
    if (!authStore.isAuthenticated) {
      return true
    }
    return authStore.isAdmin ? { name: 'admin-dashboard' } : { name: 'admin-403' }
  }

  return true
})

export default router
