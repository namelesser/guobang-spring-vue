import { ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { localStg } from '@/utils/storage';
import { SetupStoreId } from '@/enum';
import { setLocale } from '@/locales';
import { setDayjsLocale } from '@/locales/dayjs';

export const useAppStore = defineStore(SetupStoreId.App, () => {
  const locale = ref<App.I18n.LangType>(localStg.get('lang') || 'zh-CN');

  const localeOptions: App.I18n.LangOption[] = [
    {
      label: '中文',
      key: 'zh-CN'
    },
    {
      label: 'English',
      key: 'en-US'
    }
  ];

  function changeLocale(lang: App.I18n.LangType) {
    locale.value = lang;
    setLocale(lang);
    localStg.set('lang', lang);
  }

  function init() {
    setDayjsLocale(locale.value);
  }

  watch(locale, () => {
    setDayjsLocale(locale.value);
  });

  // init
  init();

  return {
    locale,
    localeOptions,
    changeLocale
  };
});
