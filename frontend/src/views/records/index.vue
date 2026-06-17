<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">运输记录</div>
        <div class="page-subtitle">按业务字段筛选台账，查看原图，人工修正 OCR 结果或导出明细。</div>
      </div>
      <n-space>
        <n-button @click="openCreate">手动录入</n-button>
      </n-space>
    </div>

    <div class="metric-grid">
      <div class="metric-card"><div class="metric-label">当前结果</div><div class="metric-value">{{ total }}</div></div>
      <div class="metric-card"><div class="metric-label">本页记录</div><div class="metric-value">{{ rows.length }}</div></div>
      <div class="metric-card"><div class="metric-label">已核对</div><div class="metric-value">{{ reviewedCount }}</div></div>
      <div class="metric-card"><div class="metric-label">未核对</div><div class="metric-value">{{ unreviewedCount }}</div></div>
    </div>

    <div class="toolbar record-toolbar">
      <div class="filter-field">
        <span class="filter-label">月份</span>
        <app-month-picker v-model="filters.month" />
      </div>
      <div class="filter-field"><span class="filter-label">核对状态</span><n-select v-model:value="filters.reviewed" clearable :options="reviewOptions" placeholder="全部状态" style="width:120px" /></div>
      <div class="filter-field"><span class="filter-label">记录来源</span><n-select v-model:value="filters.source" clearable :options="sourceOptions" placeholder="全部来源" style="width:130px" /></div>
      <div class="filter-field"><span class="filter-label">车牌号</span><n-select v-model:value="filters.plate" clearable filterable :options="plateOpts" placeholder="全部车牌" style="width:150px" /></div>
      <div class="filter-field"><span class="filter-label">发货单位</span><n-select v-model:value="filters.sender" clearable filterable :options="senderOpts" placeholder="全部发货单位" style="width:170px" /></div>
      <div class="filter-field"><span class="filter-label">收货单位</span><n-select v-model:value="filters.receiver" clearable filterable :options="receiverOpts" placeholder="全部收货单位" style="width:170px" /></div>
      <div class="filter-field"><span class="filter-label">开单公司</span><n-select v-model:value="filters.company" clearable filterable :options="companyOpts" placeholder="全部开单公司" style="width:210px" /></div>
      <div class="filter-field"><span class="filter-label">单号</span><n-input v-model:value="filters.orderNo" clearable placeholder="输入单号" style="width:150px" /></div>
      <div class="record-actions">
        <n-button @click="resetFilters">重置</n-button>
        <n-button @click="exportRecords('xls')">导出 XLS</n-button>
        <n-button @click="exportRecords('csv')">导出 CSV</n-button>
      </div>
    </div>

    <n-card class="soft-card" content-style="padding:0">
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :row-key="(row: any) => row.id"
        :max-height="620"
        remote
        striped
      />
      <div style="display:flex;justify-content:center;padding:12px">
        <n-pagination v-model:page="page" :page-count="totalPages" :page-size="PAGE_SIZE" @update:page="goPage" />
      </div>
    </n-card>

    <n-modal v-model:show="viewerOpen" preset="card" title="记录详情" style="width:92vw">
      <template v-if="viewer.record">
        <n-space justify="space-between" align="center" style="margin-bottom:12px">
          <n-button :disabled="viewer.index <= 0" @click="viewerMove(-1)">上一条</n-button>
          <n-text depth="3">ID {{ viewer.record.id }} · {{ viewer.index + 1 }} / {{ rows.length }}</n-text>
          <n-button :disabled="viewer.index >= rows.length - 1" @click="viewerMove(1)">下一条</n-button>
        </n-space>
        <div style="display:grid;grid-template-columns:minmax(360px,1.1fr) minmax(320px,.9fr);gap:16px">
          <div class="image-stage">
            <img v-if="viewer.image" :src="viewer.image" />
            <n-empty v-else description="暂无图片" />
          </div>
          <n-descriptions :column="1" bordered size="small">
            <n-descriptions-item v-for="[key, label] in detailFields" :key="key" :label="label">
              {{ viewer.record[key] ?? '' }}
            </n-descriptions-item>
          </n-descriptions>
        </div>
      </template>
    </n-modal>

    <n-modal v-model:show="createOpen" preset="card" title="手动录入记录" style="width:760px">
      <n-form label-placement="top" :show-feedback="false">
        <n-grid :cols="2" :x-gap="12">
          <n-gi>
            <n-form-item label="日期">
              <n-date-picker
                :formatted-value="createForm.record_date || null"
                type="date"
                value-format="yyyy-MM-dd"
                style="width:100%"
                @update:formatted-value="setCreateDate"
              />
            </n-form-item>
          </n-gi>
          <n-gi><n-form-item label="单号"><n-input v-model:value="createForm.order_no" /></n-form-item></n-gi>
          <n-gi><n-form-item label="发货单位"><n-select v-model:value="createForm.sender" filterable :options="senderOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="收货单位"><n-select v-model:value="createForm.receiver" filterable :options="receiverOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="开单公司"><n-select v-model:value="createForm.company" filterable :options="companyOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="车牌号"><n-select v-model:value="createForm.plate_no" filterable :options="plateOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="净重(吨)"><n-input-number v-model:value="createForm.net_weight" :min="0" :precision="2" style="width:100%" /></n-form-item></n-gi>
          <n-gi><n-form-item label="绕路加价"><n-input-number v-model:value="createForm.detour_surcharge" :min="0" :precision="2" style="width:100%" /></n-form-item></n-gi>
          <n-gi :span="2"><n-form-item label="备注"><n-input v-model:value="createForm.note" type="textarea" /></n-form-item></n-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="createOpen=false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="createRecord">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, NButton, NPopconfirm, NTag } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { collectionApi, download, imageApi, recordApi } from '@/api'
import AppMonthPicker from '@/components/AppMonthPicker.vue'

const PAGE_SIZE = 50
const route = useRoute()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const saving = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const createOpen = ref(false)
const viewerOpen = ref(false)
const viewer = reactive({ record: null as any, index: 0, image: '' })
const collectionCache: Record<string, string[]> = reactive({})
let searchTimer: number | null = null
let requestSeq = 0
let applyingRouteQuery = false

const filters = reactive({
  month: '',
  reviewed: '',
  source: '',
  plate: '',
  sender: '',
  receiver: '',
  company: '',
  orderNo: '',
})

const createForm = reactive({
  record_date: today(),
  order_no: '',
  sender: '',
  receiver: '',
  company: '',
  plate_no: '',
  net_weight: null as number | null,
  detour_surcharge: 0,
  note: '',
})

const reviewOptions = [{ label: '已核对', value: '1' }, { label: '未核对', value: '0' }]
const sourceOptions = [{ label: '手动录入', value: 'manual' }, { label: 'OCR 扫描', value: 'ocr' }]
const opts = (cat: string) => computed(() => (collectionCache[cat] || []).map(v => ({ label: v, value: v })))
const plateOpts = opts('plate')
const senderOpts = opts('sender')
const receiverOpts = opts('receiver')
const companyOpts = opts('company')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const reviewedCount = computed(() => rows.value.filter(row => row.reviewed).length)
const unreviewedCount = computed(() => rows.value.filter(row => !row.reviewed).length)

const detailFields: [string, string][] = [
  ['record_date', '日期'], ['order_no', '单号'], ['sender', '发货单位'], ['receiver', '收货单位'],
  ['company', '开单公司'], ['plate_no', '车牌号'], ['net_weight', '净重'], ['freight_rate', '运费单价'],
  ['detour_surcharge', '绕路加价'], ['total_cost', '总费用'],
  ['source', '来源'], ['ocr_status', 'OCR状态'], ['review_note', '审核备注'], ['note', '备注'],
]

const columns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 70, fixed: 'left' },
  { title: '日期', key: 'record_date', width: 110, render: row => (row.record_date || '').slice(0, 10) },
  { title: '单号', key: 'order_no', minWidth: 130 },
  { title: '开单公司', key: 'company', minWidth: 180 },
  { title: '收货单位', key: 'receiver', minWidth: 160 },
  { title: '车牌', key: 'plate_no', width: 110 },
  { title: '净重', key: 'net_weight', width: 90, align: 'right', render: row => fmtNum(row.net_weight) },
  { title: '单价', key: 'freight_rate', width: 90, align: 'right', render: row => fmtNum(row.freight_rate) },
  { title: '总费用', key: 'total_cost', width: 110, align: 'right', render: row => fmtNum(row.total_cost) },
  { title: '来源', key: 'source', width: 95, render: row => h(NTag, { size: 'small', type: row.source === 'ocr' ? 'info' : 'default' }, () => row.source === 'ocr' ? 'OCR' : '手动') },
  { title: '状态', key: 'reviewed', width: 95, render: row => h(NTag, { size: 'small', type: row.reviewed ? 'success' : 'warning' }, () => row.reviewed ? '已核对' : '未核对') },
  {
    title: '操作',
    key: 'actions',
    width: 250,
    fixed: 'right',
    render: row => h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' }, [
      h(NButton, { size: 'small', onClick: () => openViewer(row) }, () => '详情'),
      h(NButton, { size: 'small', type: 'primary', onClick: () => router.push({ path: '/review', query: { id: String(row.id) } }) }, () => '核对'),
      h(NPopconfirm, { onPositiveClick: () => deleteRecord(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
        default: () => '确认删除这条记录？',
      }),
    ]),
  },
]

function fmtNum(v: any) {
  return v == null || v === '' ? '' : Number(v).toFixed(2)
}

function today() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function firstImageId(record: any) {
  return record.first_image_id || String(record.image_id || '').split(',')[0].trim()
}

async function loadCollections() {
  const data = await collectionApi.all()
  for (const [cat, items] of Object.entries(data.collections || {}) as [string, any[]][]) {
    collectionCache[cat] = items.map(item => String(item.value || '').trim()).filter(Boolean).sort((a, b) => a.localeCompare(b, 'zh-CN'))
  }
}

function buildParams(includePaging = true) {
  const params: Record<string, string> = {}
  if (filters.month) params.month = filters.month
  if (filters.reviewed) params.reviewed = filters.reviewed
  if (filters.source) params.source = filters.source
  if (filters.plate) params.plate = filters.plate
  if (filters.sender) params.sender = filters.sender
  if (filters.receiver) params.receiver = filters.receiver
  if (filters.company) params.company = filters.company
  if (filters.orderNo) params.order_no = filters.orderNo
  if (includePaging) {
    params.limit = String(PAGE_SIZE)
    params.offset = String((page.value - 1) * PAGE_SIZE)
  }
  return params
}

async function search() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = null
  const seq = ++requestSeq
  loading.value = true
  try {
    const data = await recordApi.list(buildParams())
    if (seq === requestSeq) {
      rows.value = data.records || []
      total.value = data.total || 0
    }
  } catch (error: any) {
    if (seq === requestSeq) message.error(error?.message || '查询失败')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function scheduleSearch() {
  if (applyingRouteQuery) return
  page.value = 1
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    search()
  }, 220)
}

function goPage(nextPage: number) {
  page.value = nextPage
  search()
}

function resetFilters() {
  Object.assign(filters, { month: '', reviewed: '', source: '', plate: '', sender: '', receiver: '', company: '', orderNo: '' })
}

async function exportRecords(format: 'xls' | 'csv') {
  const params = buildParams(false)
  params.format = format
  try {
    await download('/api/records/export?' + new URLSearchParams(params).toString(), `records.${format}`)
    message.success('导出已开始')
  } catch (error: any) {
    message.error(error?.message || '导出失败')
  }
}

async function openViewer(row: any) {
  viewer.record = row
  viewer.index = rows.value.indexOf(row)
  viewer.image = ''
  viewerOpen.value = true
  const id = firstImageId(row)
  if (!id) return
  try {
    const data = await imageApi.get(Number(id))
    viewer.image = data.image_base64 || ''
  } catch {}
}

function viewerMove(step: number) {
  const index = viewer.index + step
  if (index < 0 || index >= rows.value.length) return
  openViewer(rows.value[index])
}

function openCreate() {
  Object.assign(createForm, { record_date: today(), order_no: '', sender: '', receiver: '', company: '', plate_no: '', net_weight: null, detour_surcharge: 0, note: '' })
  createOpen.value = true
}

function setCreateDate(value: string | null) {
  createForm.record_date = value || ''
}

async function createRecord() {
  if (!createForm.record_date || !createForm.sender || !createForm.receiver || !createForm.company || !createForm.plate_no || createForm.net_weight == null || createForm.net_weight <= 0) {
    message.warning('请补全日期、基础资料和净重')
    return
  }
  saving.value = true
  try {
    await recordApi.create({ ...createForm, net_weight: Number(createForm.net_weight), detour_surcharge: Number(createForm.detour_surcharge || 0) })
    message.success('记录已创建')
    createOpen.value = false
    search()
  } catch (error: any) {
    message.error(error?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function deleteRecord(id: number) {
  try {
    await recordApi.delete(id)
    message.success('记录已删除')
    search()
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  }
}

function applyRouteQuery() {
  const q = route.query as Record<string, string>
  applyingRouteQuery = true
  Object.assign(filters, {
    month: String(q.month || ''),
    reviewed: String(q.reviewed || ''),
    source: String(q.source || ''),
    plate: String(q.plate || ''),
    sender: String(q.sender || ''),
    receiver: String(q.receiver || ''),
    company: String(q.company || ''),
    orderNo: String(q.order_no || ''),
  })
  page.value = 1
  applyingRouteQuery = false
}

onMounted(async () => {
  await loadCollections()
  applyRouteQuery()
  search()
})

watch(() => route.query, () => {
  applyRouteQuery()
  search()
})

watch(filters, scheduleSearch)

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer)
})
</script>

<style scoped>
.record-toolbar { align-items: flex-end; }
.record-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 2px;
}
</style>
