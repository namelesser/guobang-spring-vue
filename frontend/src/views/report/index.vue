<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">经营报表</div>
        <div class="page-subtitle">按月汇总车次、净重、运费与未核对数量，支持跳转明细。</div>
      </div>
      <n-space>
        <n-button :disabled="!filters.month" @click="goRecords()">查看明细</n-button>
      </n-space>
    </div>

    <div class="toolbar">
      <div class="filter-field">
        <span class="filter-label">月份</span>
        <app-month-picker v-model="filters.month" />
      </div>
      <div class="filter-field"><span class="filter-label">开单公司</span><n-select v-model:value="filters.company" clearable filterable :options="companyOpts" placeholder="全部开单公司" style="width:210px" /></div>
      <div class="filter-field"><span class="filter-label">发货单位</span><n-select v-model:value="filters.sender" clearable filterable :options="senderOpts" placeholder="全部发货单位" style="width:190px" /></div>
      <div class="filter-field"><span class="filter-label">收货单位</span><n-select v-model:value="filters.receiver" clearable filterable :options="receiverOpts" placeholder="全部收货单位" style="width:190px" /></div>
      <div class="filter-field"><span class="filter-label">车牌号</span><n-select v-model:value="filters.plate" clearable filterable :options="plateOpts" placeholder="全部车牌" style="width:150px" /></div>
      <n-button @click="resetFilters">重置筛选</n-button>
    </div>

    <div v-if="report" class="metric-grid">
      <div class="metric-card"><div class="metric-label">总车次</div><div class="metric-value">{{ gt.trips || 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">总净重(吨)</div><div class="metric-value">{{ fmt(gt.total_weight) }}</div></div>
      <div class="metric-card"><div class="metric-label">总运费(元)</div><div class="metric-value">{{ fmt(gt.total_freight) }}</div></div>
      <div class="metric-card"><div class="metric-label">均价(元/吨)</div><div class="metric-value">{{ avgRate }}</div></div>
    </div>

    <n-card class="soft-card" content-style="padding:0">
      <n-data-table :columns="columns" :data="report?.groups || []" :loading="loading" :row-key="(_: any, i: number) => i" striped />
    </n-card>
    <n-empty v-if="!report && !loading" description="请选择月份" style="padding:70px 0" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, NButton } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { collectionApi, reportApi } from '@/api'
import AppMonthPicker from '@/components/AppMonthPicker.vue'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const report = ref<any>(null)
let reportTimer: number | null = null
let requestSeq = 0
const collectionCache: Record<string, string[]> = reactive({})
const filters = reactive({ month: currentMonth(), company: '', sender: '', receiver: '', plate: '' })
const gt = computed(() => report.value?.grand_total || {})
const avgRate = computed(() => Number(gt.value.total_weight || 0) ? (Number(gt.value.total_freight || 0) / Number(gt.value.total_weight)).toFixed(2) : '0.00')
const opts = (cat: string) => computed(() => (collectionCache[cat] || []).map(value => ({ label: value, value })))
const companyOpts = opts('company')
const senderOpts = opts('sender')
const receiverOpts = opts('receiver')
const plateOpts = opts('plate')

const columns: DataTableColumns<any> = [
  { title: '线路', key: 'route', minWidth: 260, render: row => `${row.company || ''} -> ${row.receiver || ''}` },
  { title: '发货单位', key: 'sender', minWidth: 160 },
  { title: '收货单位', key: 'receiver', minWidth: 160 },
  { title: '车牌号', key: 'plate_no', width: 120 },
  { title: '车次', key: 'trips', width: 80, align: 'center' },
  { title: '总净重', key: 'total_weight', width: 120, align: 'right', render: row => fmt(row.total_weight) },
  { title: '总运费', key: 'total_freight', width: 120, align: 'right', render: row => fmt(row.total_freight) },
  { title: '均价', key: 'avg_rate', width: 100, align: 'right', render: row => fmt(row.avg_rate) },
  { title: '操作', key: 'actions', width: 100, fixed: 'right', render: row => h(NButton, { size: 'small', type: 'primary', onClick: () => goRecords(row) }, () => '明细') },
]

function fmt(value: any) {
  return Number(value || 0).toFixed(2)
}

function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

async function loadCollections() {
  const data = await collectionApi.all()
  for (const [cat, items] of Object.entries(data.collections || {}) as [string, any[]][]) {
    collectionCache[cat] = items.map(item => String(item.value || '').trim()).filter(v => v && v !== '未知').sort((a, b) => a.localeCompare(b, 'zh-CN'))
  }
}

async function loadReport() {
  if (!filters.month) {
    report.value = null
    return
  }
  const seq = ++requestSeq
  loading.value = true
  const params: Record<string, string> = { month: filters.month }
  if (filters.company) params.company = filters.company
  if (filters.sender) params.sender = filters.sender
  if (filters.receiver) params.receiver = filters.receiver
  if (filters.plate) params.plate_no = filters.plate
  try {
    const data = await reportApi.monthly(params)
    if (seq === requestSeq) report.value = data
  } catch (error: any) {
    if (seq === requestSeq) message.error(error?.message || '查询失败')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { company: '', sender: '', receiver: '', plate: '' })
}

function goRecords(group: any = null) {
  const query: Record<string, string> = {}
  if (filters.month) query.month = filters.month
  const company = group?.company || filters.company
  const sender = group?.sender || filters.sender
  const receiver = group?.receiver || filters.receiver
  const plate = group?.plate_no || filters.plate
  if (company) query.company = company
  if (sender) query.sender = sender
  if (receiver) query.receiver = receiver
  if (plate) query.plate = plate
  router.push({ path: '/records', query })
}

onMounted(async () => {
  await loadCollections()
  loadReport()
})

onBeforeUnmount(() => {
  if (reportTimer) window.clearTimeout(reportTimer)
})

watch(filters, () => {
  if (reportTimer) window.clearTimeout(reportTimer)
  reportTimer = window.setTimeout(() => {
    loadReport()
  }, 220)
})
</script>
