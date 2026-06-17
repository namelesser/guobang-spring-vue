import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/review',
    children: [
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '人工核对', icon: 'ShieldCheckmarkOutline', keepAlive: true },
      },
      {
        path: 'records',
        name: 'Records',
        component: () => import('@/views/records/index.vue'),
        meta: { title: '运输记录', icon: 'DocumentTextOutline', keepAlive: true },
      },
      {
        path: 'images',
        name: 'Images',
        component: () => import('@/views/images/index.vue'),
        meta: { title: '图片资产', icon: 'ImagesOutline', keepAlive: true },
      },
      {
        path: 'ocr',
        name: 'OCR',
        component: () => import('@/views/ocr/index.vue'),
        meta: { title: 'OCR 扫描', icon: 'ScanOutline' },
      },
      {
        path: 'rates',
        name: 'Rates',
        component: () => import('@/views/rates/index.vue'),
        meta: { title: '运价资料', icon: 'PricetagOutline', keepAlive: true },
      },
      {
        path: 'collections',
        name: 'Collections',
        component: () => import('@/views/collections/index.vue'),
        meta: { title: '基础资料', icon: 'ListOutline', keepAlive: true },
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '经营报表', icon: 'BarChartOutline' },
      },
      {
        path: 'data-quality',
        name: 'DataQuality',
        component: () => import('@/views/data-quality/index.vue'),
        meta: { title: '数据质量', icon: 'PulseOutline' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/review' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
