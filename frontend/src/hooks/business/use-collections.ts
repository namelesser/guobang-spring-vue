import { reactive } from 'vue';
import { fetchCollections } from '@/service/api/business';
import type { CollectionItem } from '@/service/api/types';

export function useCollections() {
  const collectionCache: Record<string, string[]> = reactive({});

  async function loadCollections(filterUnknown = false) {
    try {
      const data = await fetchCollections();
      const collections = data.collections || {};
      for (const [cat, items] of Object.entries(collections) as [string, CollectionItem[]][]) {
        if (!Array.isArray(items)) continue;
        collectionCache[cat] = items
          .map(item => String(item.value || '').trim())
          .filter(v => (filterUnknown ? v && v !== '未知' : Boolean(v)))
          .sort((a, b) => a.localeCompare(b, 'zh-CN'));
      }
    } catch (e) {
      console.error('加载基础资料失败:', e);
    }
  }

  function optionsFor(category: string) {
    return (collectionCache[category] || []).map(v => ({ label: v, value: v }));
  }

  return { collectionCache, loadCollections, optionsFor };
}
