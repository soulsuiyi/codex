import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'cases',
          name: 'cases',
          component: () => import('@/views/CaseListView.vue'),
          meta: { title: '案件管理' },
        },
        {
          path: 'cases/:caseNo/files',
          name: 'case-files',
          component: () => import('@/views/CaseFilesView.vue'),
          meta: { title: '案件文件' },
        },
        {
          path: 'archives',
          name: 'archives',
          component: () => import('@/views/ArchiveListView.vue'),
          meta: { title: '归档管理' },
        },
        {
          path: 'borrows',
          name: 'borrows',
          component: () => import('@/views/BorrowListView.vue'),
          meta: { title: '借阅管理' },
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/SearchView.vue'),
          meta: { title: '全文检索' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const logged = !!getToken()
  if (!to.meta.public && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && logged) {
    return { path: '/' }
  }
  return true
})

export default router
