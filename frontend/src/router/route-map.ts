export const routeMap = {
  root: '/',
  'not-found': '/:pathMatch(.*)*',
  '403': '/403',
  '404': '/404',
  '500': '/500',
  collections: '/collections',
  'data-quality': '/data-quality',
  home: '/home',
  'iframe-page': '/iframe-page/:url',
  images: '/images',
  login: '/login/:module(pwd-login)?',
  ocr: '/ocr',
  rates: '/rates',
  records: '/records',
  report: '/report',
  review: '/review'
} as const;

export type RouteKey = keyof typeof routeMap;
export type RoutePath = (typeof routeMap)[RouteKey];
export type LastLevelRouteKey = Exclude<RouteKey, 'root' | 'not-found'>;

export function getRoutePath<T extends RouteKey>(name: T) {
  return routeMap[name];
}

export function getRouteName(path: string): RouteKey | null {
  const entry = Object.entries(routeMap).find(([, routePath]) => routePath === path);
  return (entry?.[0] as RouteKey | undefined) || null;
}
