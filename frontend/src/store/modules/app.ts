import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'

export const useAppStore = defineStore('app', () => {
  const collapsed = ref(false)
  const loading = ref(false)
  const authenticated = ref(false)
  const loadingAuth = ref(true)
  const dark = ref(false)
  const tabs = ref<{ path: string; title: string }[]>([{ path: '/review', title: '人工核对' }])
  const activeTab = ref('/review')

  const themeClass = computed(() => dark.value ? 'theme-dark' : 'theme-light')

  function toggleSidebar() {
    collapsed.value = !collapsed.value
  }

  function toggleTheme() {
    dark.value = !dark.value
  }

  function addTab(path: string, title: string) {
    if (path === '/login') return
    activeTab.value = path
    if (!tabs.value.some(t => t.path === path)) {
      tabs.value.push({ path, title })
    }
  }

  function removeTab(path: string) {
    if (tabs.value.length <= 1) return activeTab.value
    const idx = tabs.value.findIndex(t => t.path === path)
    if (idx < 0) return activeTab.value
    tabs.value.splice(idx, 1)
    if (activeTab.value === path) {
      activeTab.value = tabs.value[Math.max(0, idx - 1)]?.path || tabs.value[0].path
    }
    return activeTab.value
  }

  async function checkAuth() {
    loadingAuth.value = true
    try {
      const data = await authApi.me()
      authenticated.value = Boolean(data.authenticated)
    } catch {
      authenticated.value = false
    } finally {
      loadingAuth.value = false
    }
  }

  async function login(password: string) {
    await authApi.login(password)
    authenticated.value = true
  }

  async function logout() {
    try { await authApi.logout() } catch {}
    authenticated.value = false
    tabs.value = [{ path: '/review', title: '人工核对' }]
    activeTab.value = '/review'
  }

  return {
    collapsed, loading, authenticated, loadingAuth, dark, tabs, activeTab, themeClass,
    toggleSidebar, toggleTheme, addTab, removeTab, checkAuth, login, logout,
  }
})
