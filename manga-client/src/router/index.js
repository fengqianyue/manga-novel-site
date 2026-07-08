import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/',          name: 'home',         component: () => import('../views/HomeView.vue') },
    { path: '/register',  name: 'register',     component: () => import('../views/RegisterView.vue') },
    { path: '/user',      name: 'user',         component: () => import('../views/UserView.vue') },
    { path: '/admin',     name: 'admin',        component: () => import('../views/AdminView.vue'),
      meta: { requiresAdmin: true } },
    { path: '/ranking',    name: 'ranking',      component: () => import('../views/RankingView.vue') },
    { path: '/category',   name: 'category',     component: () => import('../views/CategoryView.vue') },
    { path: '/bookshelf',  name: 'bookshelf',    component: () => import('../views/BookshelfView.vue') },
    { path: '/search',     name: 'search',       component: () => import('../views/SearchView.vue') },
    { path: '/work/:id',          name: 'work-detail', component: () => import('../views/WorkDetailView.vue') },
    { path: '/reader/:workId/:chapterId/:pageNum?', name: 'reader', component: () => import('../views/ReaderView.vue') },
  ],
})

// 全局路由守卫
router.beforeEach((to) => {
  if (to.meta.requiresAdmin) {
    const store = useUserStore()
    if (!store.isAdminAuth) {
      return { path: '/admin' } // 停留在 /admin，由组件自身显示登录表单
    }
  }
})

export default router
