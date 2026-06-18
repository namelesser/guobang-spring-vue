<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">基础资料 / 车辆管理</div>
        <div class="page-subtitle">维护人工核对、手动录入和 OCR 解析使用的开单公司、发货单位、收货单位与车牌号。</div>
      </div>
    </div>

    <div class="metric-grid">
      <div v-for="item in summary" :key="item.key" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.count }}</div>
      </div>
    </div>

    <div class="toolbar">
      <div class="filter-field">
        <span class="filter-label">资料类别</span>
        <n-select v-model:value="category" :options="categoryOptions" style="width:160px" @update:value="loadItems" />
      </div>
      <div class="filter-field">
        <span class="filter-label">搜索</span>
        <n-input v-model:value="keyword" clearable placeholder="输入值或 ID" style="width:240px" />
      </div>
      <div class="filter-field">
        <span class="filter-label">新增值</span>
        <n-input v-model:value="newValue" placeholder="输入新基础资料" style="width:260px" @keydown.enter="addItem" />
      </div>
      <n-button type="primary" :loading="saving" :disabled="!category || !newValue" @click="addItem">新增</n-button>
      <n-button @click="keyword=''">清空搜索</n-button>
    </div>

    <n-card class="soft-card" :title="currentTitle" content-style="padding:0">
      <n-data-table :columns="columns" :data="filteredItems" :loading="loading" :row-key="(row: any) => row.id" striped />
    </n-card>

    <n-modal v-model:show="editOpen" preset="card" title="编辑基础资料" style="width:460px">
      <n-form label-placement="top" :show-feedback="false">
        <n-form-item label="资料值">
          <n-input v-model:value="editValue" placeholder="请输入基础资料值" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="editOpen=false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveItem">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useMessage, NButton, NPopconfirm, NTag } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { collectionApi } from '@/api'

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const category = ref('company')
const keyword = ref('')
const newValue = ref('')
const editOpen = ref(false)
const editId = ref(0)
const editValue = ref('')
const items = ref<any[]>([])
const allCollections = ref<Record<string, any[]>>({})

const categoryOptions = [
  { label: '开单公司', value: 'company' },
  { label: '发货单位', value: 'sender' },
  { label: '收货单位', value: 'receiver' },
  { label: '车牌号', value: 'plate' },
]

const categoryLabel = computed(() => categoryOptions.find(item => item.value === category.value)?.label || '')
const currentTitle = computed(() => `${categoryLabel.value}列表`)

const summary = computed(() => categoryOptions.map(item => ({
  key: item.value,
  label: item.label,
  count: allCollections.value[item.value]?.length || 0,
})))

const filteredItems = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return items.value
  return items.value.filter(item => String(item.value || '').toLowerCase().includes(kw) || String(item.id).includes(kw))
})

const columns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 90 },
  { title: '类别', key: 'category', width: 120, render: () => h(NTag, { size: 'small', type: 'info' }, () => categoryLabel.value) },
  { title: '资料值', key: 'value', minWidth: 260 },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right',
    render: row => h('div', { style: 'display:flex;gap:6px' }, [
      h(NButton, { size: 'small', type: 'primary', onClick: () => openEdit(row) }, () => '编辑'),
      h(NPopconfirm, { onPositiveClick: () => deleteItem(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
        default: () => '确认删除该基础资料？',
      }),
    ]),
  },
]

function sortRows(rows: any[]) {
  return rows.sort((a, b) => String(a.value || '').localeCompare(String(b.value || ''), 'zh-CN'))
}

async function loadAll() {
  loading.value = true
  try {
    const data = await collectionApi.all()
    allCollections.value = data.collections || {}
    await loadItems()
  } catch (error: any) {
    message.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  if (!category.value) return
  try {
    const data = await collectionApi.list(category.value)
    items.value = sortRows(data.items || [])
  } catch (error: any) {
    message.error(error?.message || '加载失败')
  }
}

async function addItem() {
  const value = newValue.value.trim()
  if (!value) return message.warning('请输入新增值')
  saving.value = true
  try {
    await collectionApi.create(category.value, value)
    message.success('已新增')
    newValue.value = ''
    await loadAll()
  } catch (error: any) {
    message.error(error?.message || '新增失败')
  } finally {
    saving.value = false
  }
}

function openEdit(row: any) {
  editId.value = row.id
  editValue.value = row.value || ''
  editOpen.value = true
}

async function saveItem() {
  const value = editValue.value.trim()
  if (!value) return message.warning('请输入资料值')
  saving.value = true
  try {
    await collectionApi.update(editId.value, value)
    message.success('已保存')
    editOpen.value = false
    await loadAll()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteItem(id: number) {
  await collectionApi.delete(id)
  message.success('已删除')
  await loadAll()
}

onMounted(loadAll)
</script>
