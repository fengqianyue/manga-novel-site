import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/',          name: 'home',         component: () => import('../views/HomeView.vue') },
    { path: '/register',  name: 'register',     component: () => import('../views/RegisterView.vue') },
    { path: '/user',      name: 'user',         component: () => import('../views/UserView.vue') },
    { path: '/author',    name: 'author',        component: () => import('../views/AuthorView.vue'),
      meta: { requiresAuthor: true } },
    { path: '/admin',     name: 'admin',        component: () => import('../views/AdminView.vue'),
      meta: { requiresAdmin: true } },
    { path: '/ranking',    name: 'ranking',      component: () => import('../views/RankingView.vue') },
    { path: '/category',   name: 'category',     component: () => import('../views/CategoryView.vue') },
    { path: '/bookshelf',  name: 'bookshelf',    component: () => import('../views/BookshelfView.vue') },
    { path: '/search',     name: 'search',       component: () => import('../views/SearchView.vue') },
    { path: '/work/:id(\\d+)',          name: 'work-detail', component: () => import('../views/WorkDetailView.vue') },
    { path: '/reader/:workId(\\d+)/:chapterId(\\d+)/:pageNum(\\d+)?', name: 'reader', component: () => import('../views/ReaderView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', redirect: '/' },
  ],
})

// 全局路由守卫
router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.requiresAdmin && !store.isAdminAuth) {
    return { name: 'home' }
  }
  if (to.meta.requiresAuthor && (!store.user || (store.user.role || 0) < 1)) {
    return { name: 'home' }
  }
})

export default router
