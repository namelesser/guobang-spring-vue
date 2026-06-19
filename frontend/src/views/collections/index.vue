<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { useMessage, NButton, NPopconfirm, NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import {
  fetchCollections,
  fetchAllCollections,
  createCollection,
  updateCollection,
  deleteCollection
} from '@/service/api/business';

defineOptions({ name: 'Collections' });

const message = useMessage();
const loading = ref(false);
const saving = ref(false);
const category = ref('company');
const keyword = ref('');
const newValue = ref('');
const editOpen = ref(false);
const editId = ref(0);
const editValue = ref('');
const items = ref<any[]>([]);
const allCollections = ref<Record<string, any[]>>({});

const page = ref(1);
const pageSize = 20;
const total = ref(0);

const categoryOptions = [
  { label: '开单公司', value: 'company' },
  { label: '发货单位', value: 'sender' },
  { label: '收货单位', value: 'receiver' },
  { label: '车牌号', value: 'plate_no' }
];

const categoryLabel = computed(() => categoryOptions.find(item => item.value === category.value)?.label || '');
const currentTitle = computed(() => `${categoryLabel.value}列表`);

const summary = computed(() =>
  categoryOptions.map(item => ({
    key: item.value,
    label: item.label,
    count: allCollections.value[item.value]?.length || 0
  }))
);

const filteredItems = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return items.value;
  return items.value.filter(
    item =>
      String(item.value || '')
        .toLowerCase()
        .includes(kw) || String(item.id).includes(kw)
  );
});

const columns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: '类别',
    key: 'category',
    width: 120,
    render: () => h(NTag, { size: 'small', type: 'info' }, () => categoryLabel.value)
  },
  { title: '资料值', key: 'value', minWidth: 260 },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right',
    render: row =>
      h('div', { style: 'display:flex;gap:6px' }, [
        h(NButton, { size: 'small', type: 'primary', onClick: () => openEdit(row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => deleteItem(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
            default: () => '确认删除该基础资料？'
          }
        )
      ])
  }
];

function sortRows(rows: any[]) {
  return rows.sort((a, b) => String(a.value || '').localeCompare(String(b.value || ''), 'zh-CN'));
}

async function loadAll() {
  loading.value = true;
  try {
    const data = await fetchAllCollections();
    allCollections.value = data.collections || {};
    await loadItems();
  } catch (error: any) {
    message.error(error?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadItems() {
  if (!category.value) return;
  try {
    const data = await fetchCollections(category.value, { offset: (page.value - 1) * pageSize, limit: pageSize });
    items.value = sortRows(data.items || []);
    total.value = data.total || 0;
  } catch (error: any) {
    message.error(error?.message || '加载失败');
  }
}

async function addItem() {
  const value = newValue.value.trim();
  if (!value) return message.warning('请输入新增值');
  saving.value = true;
  try {
    await createCollection({ category: category.value, value });
    message.success('已新增');
    newValue.value = '';
    await loadAll();
  } catch (error: any) {
    message.error(error?.message || '新增失败');
  } finally {
    saving.value = false;
  }
}

function openEdit(row: any) {
  editId.value = row.id;
  editValue.value = row.value || '';
  editOpen.value = true;
}

async function saveItem() {
  const value = editValue.value.trim();
  if (!value) return message.warning('请输入资料值');
  saving.value = true;
  try {
    await updateCollection(editId.value, { value });
    message.success('已保存');
    editOpen.value = false;
    await loadAll();
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function deleteItem(id: number) {
  try {
    await deleteCollection(id);
    message.success('已删除');
    await loadAll();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
  }
}

onMounted(loadAll);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto h-full">
    <NCard title="基础资料 / 车辆管理" :bordered="false" size="small" class="flex-1-hidden">
      <template #header-extra>
        <NButton @click="loadAll">刷新</NButton>
      </template>

      <!-- 统计卡片 -->
      <NGrid :cols="5" :x-gap="12" class="mb-16px">
        <NGi v-for="item in summary" :key="item.key">
          <NCard size="small" :bordered="false" class="text-center">
            <div class="text-12px text-gray-500">{{ item.label }}</div>
            <div class="text-24px font-bold mt-4px">{{ item.count }}</div>
          </NCard>
        </NGi>
      </NGrid>

      <!-- 筛选栏 -->
      <NSpace class="mb-16px" align="center" wrap>
        <NForm label-placement="left" :show-feedback="false" :show-label="true">
          <NSpace wrap>
            <NFormItem label="类别">
              <NSelect
                v-model:value="category"
                :options="categoryOptions"
                style="width: 160px"
                @update:value="loadItems"
              />
            </NFormItem>
            <NFormItem label="搜索">
              <NInput v-model:value="keyword" clearable placeholder="搜索值或 ID" style="width: 240px" />
            </NFormItem>
            <NFormItem label="新增">
              <NInput
                v-model:value="newValue"
                placeholder="输入新基础资料"
                style="width: 260px"
                @keydown.enter="addItem"
              />
            </NFormItem>
            <NButton type="primary" :loading="saving" :disabled="!category || !newValue.trim()" @click="addItem">
              新增
            </NButton>
          </NSpace>
        </NForm>
      </NSpace>

      <!-- 列表 -->
      <NCard :title="currentTitle" content-style="padding: 0" class="flex-1-hidden">
        <div class="flex-1-hidden">
          <NDataTable
            :columns="columns"
            :data="filteredItems"
            :loading="loading"
            :row-key="(row: any) => row.id"
            striped
            flex-height
          />
        </div>
        <div class="flex justify-end p-12px">
          <NPagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadItems" />
        </div>
      </NCard>
    </NCard>

    <!-- 编辑弹窗 -->
    <NModal v-model:show="editOpen" preset="card" title="编辑基础资料" style="width: 460px">
      <NForm label-placement="top" :show-feedback="false">
        <NFormItem label="资料值">
          <NInput v-model:value="editValue" placeholder="请输入基础资料值" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="editOpen = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="saveItem">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
