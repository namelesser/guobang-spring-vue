<template>
  <n-config-provider :theme="store.dark ? darkTheme : null" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <div :class="store.themeClass">
            <router-view />
          </div>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/store'
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const store = useAppStore()

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#2563eb',
    primaryColorHover: '#3b82f6',
    primaryColorPressed: '#1d4ed8',
    infoColor: '#0891b2',
    successColor: '#16a34a',
    warningColor: '#d97706',
    errorColor: '#dc2626',
    borderRadius: '8px',
  },
}

function handleAuthRequired() {
  store.logout()
  router.push('/login')
}

onMounted(async () => {
  window.addEventListener('auth-required', handleAuthRequired)
  await store.checkAuth()
  if (!store.authenticated && route.path !== '/login') {
    router.replace('/login')
  } else if (store.authenticated && route.path === '/login') {
    router.replace('/')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('auth-required', handleAuthRequired)
})
</script>
