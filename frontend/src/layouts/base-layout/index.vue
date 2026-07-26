<script setup lang="ts">
import { computed, h } from 'vue';
import type { MenuOption } from 'naive-ui';
import { NButton, NDropdown } from 'naive-ui';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { fetchLogout } from '@/service/api';
import { useAuthStore } from '@/store/modules/auth';
import { useRouteStore } from '@/store/modules/route';

defineOptions({
  name: 'BaseLayout'
});

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const routeStore = useRouteStore();

const menuOptions = computed<MenuOption[]>(() => mapMenus(routeStore.menus));
const activeKey = computed(() => String(route.name || ''));
const breadcrumbItems = computed(() => routeStore.breadcrumbs);
const pageTitle = computed(() => String(route.meta.title || ''));
const userOptions: MenuOption[] = [{ label: '退出登录', key: 'logout' }];

function mapMenus(menus: App.Global.Menu[]): MenuOption[] {
  return menus.map(menu => ({
    key: menu.key,
    label: () =>
      h(
        RouterLink,
        {
          to: menu.routePath,
          class: 'menu-link'
        },
        { default: () => menu.label }
      ),
    icon: menu.icon,
    children: menu.children ? mapMenus(menu.children) : undefined
  }));
}

function handleMenuSelect(key: string) {
  const target = findMenu(routeStore.menus, key);
  if (target) {
    router.push(target.routePath);
  }
}

function findMenu(menus: App.Global.Menu[], key: string): App.Global.Menu | null {
  for (const menu of menus) {
    if (menu.key === key) return menu;
    if (menu.children?.length) {
      const child = findMenu(menu.children, key);
      if (child) return child;
    }
  }
  return null;
}

async function handleUserAction(key: string) {
  if (key !== 'logout') return;
  try {
    await fetchLogout();
  } finally {
    await authStore.resetStore();
  }
}
</script>

<template>
  <NLayout has-sider class="min-h-screen bg-#f5f6f8">
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="232"
      content-style="display:flex;flex-direction:column;height:100%;"
      class="border-r border-#e8e8e8 bg-white"
    >
      <div class="flex items-center gap-12px px-18px py-18px border-b border-#f0f0f0">
        <SystemLogo class="size-36px shrink-0" />
        <div class="min-w-0">
          <div class="truncate text-15px font-600 text-#111">国邦运输</div>
          <div class="truncate text-12px text-#888">运输管理系统</div>
        </div>
      </div>

      <NMenu
        :value="activeKey"
        :options="menuOptions"
        :collapsed-width="64"
        :collapsed-icon-size="18"
        class="flex-1 overflow-auto py-12px"
        @update:value="handleMenuSelect"
      />
    </NLayoutSider>

    <NLayout>
      <NLayoutHeader bordered class="h-64px bg-white px-24px">
        <div class="flex h-full items-center justify-between gap-16px">
          <div class="min-w-0">
            <div class="truncate text-18px font-600 text-#111">{{ pageTitle }}</div>
            <NBreadcrumb v-if="breadcrumbItems.length > 1" class="mt-2px text-12px">
              <NBreadcrumbItem v-for="item in breadcrumbItems" :key="item.key">
                {{ item.label }}
              </NBreadcrumbItem>
            </NBreadcrumb>
          </div>

          <NDropdown :options="userOptions" @select="handleUserAction">
            <NButton quaternary>
              {{ authStore.userInfo.userName || '管理员' }}
            </NButton>
          </NDropdown>
        </div>
      </NLayoutHeader>

      <NLayoutContent content-style="padding: 20px;">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
:deep(.menu-link) {
  display: block;
  width: 100%;
  color: inherit;
  text-decoration: none;
}
</style>
