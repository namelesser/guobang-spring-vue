import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { router } from '@/router';
import { SetupStoreId } from '@/enum';
import { protectedRoutes } from '@/router/routes';
import {
  getBreadcrumbsByRoute,
  getGlobalMenusByRoutes,
  getSelectedMenuKeyPathByKey,
  transformMenuToSearchMenus,
  updateLocaleOfGlobalMenus
} from './shared';

export const useRouteStore = defineStore(SetupStoreId.Route, () => {
  const isInitConstantRoute = ref(false);
  const isInitAuthRoute = ref(false);
  const routeHome = ref(import.meta.env.VITE_ROUTE_HOME);
  const menus = ref<App.Global.Menu[]>([]);

  const searchMenus = computed(() => transformMenuToSearchMenus(menus.value));
  const breadcrumbs = computed(() => getBreadcrumbsByRoute(router.currentRoute.value, menus.value));

  function initRoutes() {
    if (menus.value.length === 0) {
      menus.value = getGlobalMenusByRoutes(protectedRoutes);
    }

    isInitConstantRoute.value = true;
    isInitAuthRoute.value = true;
  }

  function resetStore() {
    menus.value = getGlobalMenusByRoutes(protectedRoutes);
    isInitConstantRoute.value = true;
    isInitAuthRoute.value = true;
  }

  function updateGlobalMenusByLocale() {
    menus.value = updateLocaleOfGlobalMenus(menus.value);
  }

  function getSelectedMenuKeyPath(selectedKey: string) {
    return getSelectedMenuKeyPathByKey(selectedKey, menus.value);
  }

  async function onRouteSwitchWhenLoggedIn() {
    // no-op
  }

  async function onRouteSwitchWhenNotLoggedIn() {
    // no-op
  }

  return {
    resetStore,
    initRoutes,
    routeHome,
    menus,
    searchMenus,
    breadcrumbs,
    updateGlobalMenusByLocale,
    isInitConstantRoute,
    isInitAuthRoute,
    setIsInitAuthRoute(value: boolean) {
      isInitAuthRoute.value = value;
    },
    getSelectedMenuKeyPath,
    onRouteSwitchWhenLoggedIn,
    onRouteSwitchWhenNotLoggedIn
  };
});
