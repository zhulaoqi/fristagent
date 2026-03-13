import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/components/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'scans', name: 'scans', component: () => import('@/views/ScanList.vue') },
        { path: 'scans/:id', name: 'scan-detail', component: () => import('@/views/ScanDetail.vue') },
        { path: 'chat', name: 'chat', component: () => import('@/views/Chat.vue') },
        { path: 'config/repos', name: 'config-repos', component: () => import('@/views/config/Repos.vue') },
        { path: 'config/skills', name: 'config-skills', component: () => import('@/views/config/Skills.vue') },
        { path: 'config/model', name: 'config-model', component: () => import('@/views/config/Model.vue') },
        { path: 'config/notify', name: 'config-notify', component: () => import('@/views/config/Notify.vue') },
      ],
    },
  ],
})

export default router
