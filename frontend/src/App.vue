<script setup lang="ts">
import { computed } from 'vue';
import { NConfigProvider } from 'naive-ui';
import { useAppStore } from './store/modules/app';
import { naiveDateLocales, naiveLocales } from './locales/naive';

defineOptions({
  name: 'App'
});

const appStore = useAppStore();

const naiveLocale = computed(() => naiveLocales[appStore.locale]);
const naiveDateLocale = computed(() => naiveDateLocales[appStore.locale]);
</script>

<template>
  <NConfigProvider :locale="naiveLocale" :date-locale="naiveDateLocale" class="h-full">
    <AppProvider>
      <RouterView />
    </AppProvider>
  </NConfigProvider>
</template>
