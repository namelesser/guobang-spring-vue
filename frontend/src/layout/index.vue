<template>
  <div class="admin-shell">
    <aside class="sider" :class="{ collapsed: store.collapsed }">
      <div class="brand">
        <div class="brand-mark">T</div>
        <div v-if="!store.collapsed">
          <div class="brand-name">国邦运输</div>
          <div class="brand-desc">Transport Console</div>
        </div>
      </div>

      <n-menu
        :collapsed="store.collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="route.path"
        @update:value="go"
      />
    </aside>

    <section class="workspace">
      <header class="topbar">
        <n-space align="center" :size="10">
          <n-button quaternary circle @click="store.toggleSidebar">
            <template #icon><n-icon><MenuOutline /></n-icon></template>
          </n-button>
          <div>
            <div class="top-title">{{ currentTitle }}</div>
            <div class="top-subtitle">{{ currentHint }}</div>
          </div>
        </n-space>

        <n-space align="center" :size="8">
          <n-button quaternary circle @click="refreshPage">
            <template #icon><n-icon><RefreshOutline /></n-icon></template>
          </n-button>
          <n-button quaternary circle @click="store.toggleTheme">
            <template #icon><n-icon><component :is="store.dark ? SunnyOutline : MoonOutline" /></n-icon></template>
          </n-button>
          <n-dropdown :options="userOptions" @select="handleUserAction">
            <n-button quaternary>
              <template #icon><n-avatar round :size="28" color="#2563eb">运</n-avatar></template>
              管理员
            </n-button>
          </n-dropdown>
        </n-space>
      </header>

      <div class="tabs">
        <n-tag
          v-for="tab in store.tabs"
          :key="tab.path"
          :type="tab.path === route.path ? 'primary' : 'default'"
          :closable="store.tabs.length > 1"
          round
          @click="go(tab.path)"
          @close.stop="closeTab(tab.path)"
        >
          {{ tab.title }}
        </n-tag>
      </div>

      <main class="content">
        <router-view :key="`${route.fullPath}:${refreshKey}`" />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog, NIcon, type DropdownOption, type MenuOption } from 'naive-ui'
import {
  BarChartOutline,
  DocumentTextOutline,
  ImagesOutline,
  ListOutline,
  LogOutOutline,
  MenuOutline,
  MoonOutline,
  PricetagOutline,
  PulseOutline,
  RefreshOutline,
  ScanOutline,
  ShieldCheckmarkOutline,
  SunnyOutline,
} from '@vicons/ionicons5'
import { useAppStore } from '@/store'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const dialog = useDialog()
const refreshKey = ref(0)

const pages = [
  { path: '/review', title: '人工核对', hint: '逐单校验 OCR 结果、图片和运价', icon: ShieldCheckmarkOutline },
  { path: '/records', title: '运输记录', hint: '查询、导出和维护完整运输台账', icon: DocumentTextOutline },
  { path: '/images', title: '图片资产', hint: '管理原始过磅单图片与 OCR 状态', icon: ImagesOutline },
  { path: '/ocr', title: 'OCR 扫描', hint: '批量上传图片并跟踪识别队列', icon: ScanOutline },
  { path: '/rates', title: '运价资料', hint: '维护线路单价和有效期', icon: PricetagOutline },
  { path: '/collections', title: '基础资料', hint: '维护开单公司、发货单位、收货单位和车辆', icon: ListOutline },
  { path: '/report', title: '经营报表', hint: '按月汇总车次、吨位和运费', icon: BarChartOutline },
  { path: '/data-quality', title: '数据质量', hint: '发现缺图片、缺运价、重复和异常资料', icon: PulseOutline },
]

const menuOptions: MenuOption[] = pages.map(item => ({
  key: item.path,
  label: item.title,
  icon: () => h(NIcon, null, { default: () => h(item.icon) }),
}))

const current = computed(() => pages.find(item => item.path === route.path) || pages[0])
const currentTitle = computed(() => current.value.title)
const currentHint = computed(() => current.value.hint)

const userOptions: DropdownOption[] = [
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) },
]

function go(path: string) {
  router.push(path)
}

function closeTab(path: string) {
  const next = store.removeTab(path)
  if (next && next !== route.path) router.push(next)
}

function refreshPage() {
  refreshKey.value += 1
}

async function handleUserAction(key: string) {
  if (key !== 'logout') return
  dialog.warning({
    title: '退出登录',
    content: '确认退出当前管理会话？',
    positiveText: '退出',
    negativeText: '取消',
    onPositiveClick: async () => {
      await store.logout()
      router.replace('/login')
    },
  })
}

watch(
  () => route.path,
  path => store.addTab(path, currentTitle.value),
  { immediate: true },
)
</script>

<style scoped>
.admin-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 8%, rgba(37, 99, 235, 0.12), transparent 24%),
    linear-gradient(180deg, #f8fafc 0%, #eef3fb 100%);
}
.theme-dark .admin-shell {
  background:
    radial-gradient(circle at 18% 8%, rgba(59, 130, 246, 0.18), transparent 24%),
    linear-gradient(180deg, #020617 0%, #0f172a 100%);
}
.sider {
  width: 240px;
  min-width: 240px;
  padding: 14px 10px;
  border-right: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
  transition: width 0.2s, min-width 0.2s;
}
.theme-dark .sider {
  border-right-color: rgba(51, 65, 85, 0.9);
  background: rgba(15, 23, 42, 0.88);
}
.sider.collapsed {
  width: 72px;
  min-width: 72px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 52px;
  padding: 0 8px 12px;
  margin-bottom: 8px;
}
.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  font-weight: 900;
  background: linear-gradient(135deg, #2563eb, #0891b2);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.28);
}
.brand-name {
  font-size: 16px;
  font-weight: 800;
}
.brand-desc {
  color: #64748b;
  font-size: 12px;
}
.workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
}
.theme-dark .topbar {
  border-bottom-color: rgba(51, 65, 85, 0.9);
  background: rgba(15, 23, 42, 0.72);
}
.top-title {
  font-size: 18px;
  font-weight: 800;
}
.top-subtitle {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}
.tabs {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 22px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  overflow-x: auto;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 18px 22px 28px;
}
</style>
