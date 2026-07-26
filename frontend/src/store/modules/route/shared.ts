import type { RouteLocationNormalizedLoaded, RouteRecordRaw, _RouteRecordBase } from 'vue-router';
import type { RouteKey, RoutePath } from '@/router/route-map';
import { useSvgIcon } from '@/hooks/common/icon';
import { $t } from '@/locales';

export function getGlobalMenusByRoutes(routes: RouteRecordRaw[]): App.Global.Menu[] {
  return routes
    .filter(route => !route.meta?.hideInMenu)
    .sort((a, b) => (Number(a.meta?.order) || 0) - (Number(b.meta?.order) || 0))
    .map(route => getGlobalMenuByRoute(route));
}

export function updateLocaleOfGlobalMenus(menus: App.Global.Menu[]): App.Global.Menu[] {
  return menus.map(menu => ({
    ...menu,
    label: menu.i18nKey ? $t(menu.i18nKey) : menu.label,
    children: menu.children ? updateLocaleOfGlobalMenus(menu.children) : undefined
  }));
}

function getGlobalMenuByRoute(route: RouteRecordRaw): App.Global.Menu {
  const { SvgIconVNode } = useSvgIcon();
  const { title, i18nKey, icon = import.meta.env.VITE_MENU_ICON, localIcon, iconFontSize } = route.meta ?? {};

  return {
    key: String(route.name),
    label: i18nKey ? $t(i18nKey) : String(title || route.name || ''),
    i18nKey,
    routeKey: route.name as RouteKey,
    routePath: route.path as RoutePath,
    icon: SvgIconVNode({ icon, localIcon, fontSize: iconFontSize || 20 })
  } satisfies App.Global.Menu;
}

export function getSelectedMenuKeyPathByKey(selectedKey: string, menus: App.Global.Menu[]): string[] {
  const target = menus.find(menu => menu.key === selectedKey);
  return target ? [target.key] : [];
}

function transformMenuToBreadcrumb(menu: App.Global.Menu): App.Global.Breadcrumb {
  const { children, ...rest } = menu;
  const breadcrumb: App.Global.Breadcrumb = { ...rest };

  if (children?.length) {
    breadcrumb.options = children.map(transformMenuToBreadcrumb);
  }

  return breadcrumb;
}

export function getBreadcrumbsByRoute(
  route: RouteLocationNormalizedLoaded,
  menus: App.Global.Menu[]
): App.Global.Breadcrumb[] {
  const breadcrumbs: App.Global.Breadcrumb[] = [];

  route.matched.forEach(item => {
    if (!item.name || item.meta?.hideInMenu) return;

    const menu = menus.find(entry => entry.routeKey === item.name);
    if (menu) {
      breadcrumbs.push(transformMenuToBreadcrumb(menu));
      return;
    }

    const { title, i18nKey, icon, localIcon } = item.meta;
    breadcrumbs.push({
      key: String(item.name),
      label: i18nKey ? $t(i18nKey) : String(title || item.name),
      i18nKey,
      routeKey: item.name as RouteKey,
      routePath: item.path as RoutePath,
      icon: icon || localIcon ? useSvgIcon().SvgIconVNode({ icon, localIcon }) : undefined
    });
  });

  return breadcrumbs;
}

export function transformMenuToSearchMenus(menus: App.Global.Menu[]): App.Global.Menu[] {
  const searchMenus: App.Global.Menu[] = [];

  function traverse(items: App.Global.Menu[]) {
    items.forEach(item => {
      searchMenus.push(item);
      if (item.children?.length) {
        traverse(item.children);
      }
    });
  }

  traverse(menus);
  return searchMenus.map(menu => ({ ...menu, children: undefined }));
}
