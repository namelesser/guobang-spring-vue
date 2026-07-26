import type { RouteRecordRaw } from 'vue-router';
import BaseLayout from '@/layouts/base-layout/index.vue';
import LoginView from '@/views/_builtin/login/index.vue';
import ForbiddenView from '@/views/_builtin/403/index.vue';
import NotFoundView from '@/views/_builtin/404/index.vue';
import ErrorView from '@/views/_builtin/500/index.vue';
import IframePageView from '@/views/_builtin/iframe-page/[url].vue';
import CollectionsView from '@/views/collections/index.vue';
import DataQualityView from '@/views/data-quality/index.vue';
import HomeView from '@/views/home/index.vue';
import ImagesView from '@/views/images/index.vue';
import OcrView from '@/views/ocr/index.vue';
import RatesView from '@/views/rates/index.vue';
import RecordsView from '@/views/records/index.vue';
import ReportView from '@/views/report/index.vue';
import ReviewView from '@/views/review/index.vue';

export const protectedRoutes: RouteRecordRaw[] = [
  {
    name: 'home',
    path: '/home',
    component: HomeView,
    meta: { title: '首页', i18nKey: 'route.home', icon: 'mdi:monitor-dashboard', order: 1 }
  },
  {
    name: 'records',
    path: '/records',
    component: RecordsView,
    meta: { title: '运输记录', i18nKey: 'route.records', icon: 'mdi:file-document', order: 2 }
  },
  {
    name: 'review',
    path: '/review',
    component: ReviewView,
    meta: { title: '人工核对', i18nKey: 'route.review', icon: 'mdi:check-circle', order: 3 }
  },
  {
    name: 'images',
    path: '/images',
    component: ImagesView,
    meta: { title: '图片资产', i18nKey: 'route.images', icon: 'mdi:image', order: 4 }
  },
  {
    name: 'ocr',
    path: '/ocr',
    component: OcrView,
    meta: { title: 'OCR 扫描', i18nKey: 'route.ocr', icon: 'mdi:scan-helper', order: 5 }
  },
  {
    name: 'rates',
    path: '/rates',
    component: RatesView,
    meta: { title: '运费费率', i18nKey: 'route.rates', icon: 'mdi:cash', order: 6 }
  },
  {
    name: 'collections',
    path: '/collections',
    component: CollectionsView,
    meta: { title: '枚举值管理', i18nKey: 'route.collections', icon: 'mdi:format-list-bulleted', order: 7 }
  },
  {
    name: 'report',
    path: '/report',
    component: ReportView,
    meta: { title: '月度报表', i18nKey: 'route.report', icon: 'mdi:chart-bar', order: 8 }
  },
  {
    name: 'data-quality',
    path: '/data-quality',
    component: DataQualityView,
    meta: { title: '数据质量', i18nKey: 'route.data-quality', icon: 'mdi:shield-check', order: 9 }
  }
];

export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: BaseLayout,
    children: [
      {
        name: 'root',
        path: '',
        redirect: '/home',
        meta: { title: 'root', constant: true, hideInMenu: true }
      },
      ...protectedRoutes
    ]
  },
  {
    name: 'login',
    path: '/login/:module(pwd-login)?',
    component: LoginView,
    props: true,
    meta: { title: 'login', i18nKey: 'route.login', constant: true, hideInMenu: true }
  },
  {
    name: '403',
    path: '/403',
    component: ForbiddenView,
    meta: { title: '403', i18nKey: 'route.403', constant: true, hideInMenu: true }
  },
  {
    name: '404',
    path: '/404',
    component: NotFoundView,
    meta: { title: '404', i18nKey: 'route.404', constant: true, hideInMenu: true }
  },
  {
    name: '500',
    path: '/500',
    component: ErrorView,
    meta: { title: '500', i18nKey: 'route.500', constant: true, hideInMenu: true }
  },
  {
    name: 'iframe-page',
    path: '/iframe-page/:url',
    component: IframePageView,
    props: true,
    meta: { title: 'iframe-page', i18nKey: 'route.iframe-page', constant: true, hideInMenu: true, keepAlive: true }
  },
  {
    name: 'not-found',
    path: '/:pathMatch(.*)*',
    component: NotFoundView,
    meta: { title: 'not-found', constant: true, hideInMenu: true }
  }
];
