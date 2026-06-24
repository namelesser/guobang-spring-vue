import type { PluginOption } from 'vite';
import vue from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';
import { setupUnocss } from './unocss';
import { setupUnplugin } from './unplugin';
import { setupHtmlPlugin } from './html';

export function setupVitePlugins(viteEnv: Env.ImportMeta, buildTime: string) {
  const plugins: PluginOption = [
    vue(),
    vueJsx(),
    setupUnocss(viteEnv),
    ...setupUnplugin(viteEnv),
    setupHtmlPlugin(buildTime)
  ];

  return plugins;
}
