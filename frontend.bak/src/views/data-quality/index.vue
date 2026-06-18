<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">数据质量</div>
        <div class="page-subtitle">检查基础资料异常、未来日期、OCR 缺图片、缺净重、缺运价、OCR 卡住任务和重复单号公司。</div>
      </div>
      <n-button type="primary" :loading="loading" @click="load">刷新检查</n-button>
    </div>

    <div class="metric-grid">
      <div class="metric-card"><div class="metric-label">基础资料异常</div><div class="metric-value">{{ collectionRows.length }}</div></div>
      <div class="metric-card"><div class="metric-label">未来日期</div><div class="metric-value">{{ report.future_dates?.length || 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">OCR 缺图片</div><div class="metric-value">{{ report.missing_images?.length || 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">缺净重记录</div><div class="metric-value">{{ report.missing_weights?.length || 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">缺运价记录</div><div class="metric-value">{{ report.missing_rates?.length || 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">OCR 卡住任务</div><div class="metric-value">{{ report.stale_ocr_tasks?.length || 0 }}</div></div>
    </div>

    <n-spin :show="loading">
      <n-card class="soft-card" title="基础资料异常" content-style="padding:0">
        <n-data-table :columns="collectionCols" :data="collectionRows" :row-key="(_: any, i: number) => i" size="small" striped />
      </n-card>

      <n-card v-for="section in sections" :key="section.title" class="soft-card" :title="section.title" content-style="padding:0">
        <n-data-table :columns="section.columns" :data="section.rows" :row-key="(_: any, i: number) => i" size="small" striped />
      </n-card>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, NButton } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { dataQualityApi } from '@/api'

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const report = ref<any>({})

const collectionRows = computed(() => {
  const rows: any[] = []
  for (const check of report.value.collection_checks || []) {
    for (const item of check.items || []) rows.push({ field: check.field, label: check.label, ...item })
  }
  return rows
})

const collectionCols: DataTableColumns<any> = [
  { title: '字段', key: 'label', width: 140 },
  { title: '异常值', key: 'value', minWidth: 240 },
  { title: '出现次数', key: 'cnt', width: 110, align: 'center' },
]

function simpleColumns(cols: [string, string][]): DataTableColumns<any> {
  const base = cols.map(([key, title]) => ({ key, title, minWidth: key === 'id' ? 70 : 130 }))
  return [
    ...base,
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (row: any) => {
        const id = row.first_id || row.record_id || row.id
        return id ? h(NButton, { size: 'small', type: 'primary', onClick: () => router.push({ path: '/review', query: { id: String(id) } }) }, () => '核对') : null
      },
    },
  ] as DataTableColumns<any>
}

const sections = computed(() => [
  { title: '未来日期', rows: report.value.future_dates || [], columns: simpleColumns([['id', 'ID'], ['record_date', '日期'], ['order_no', '单号'], ['company', '开单公司']]) },
  { title: 'OCR 缺图片记录', rows: report.value.missing_images || [], columns: simpleColumns([['id', 'ID'], ['record_date', '日期'], ['order_no', '单号'], ['company', '开单公司'], ['ocr_status', 'OCR状态']]) },
  { title: '缺净重记录', rows: report.value.missing_weights || [], columns: simpleColumns([['id', 'ID'], ['record_date', '日期'], ['order_no', '单号'], ['company', '开单公司'], ['receiver', '收货单位'], ['ocr_status', 'OCR状态'], ['net_weight', '净重']]) },
  { title: '缺运价记录', rows: report.value.missing_rates || [], columns: simpleColumns([['id', 'ID'], ['record_date', '日期'], ['order_no', '单号'], ['company', '开单公司'], ['receiver', '收货单位'], ['freight_rate', '运价']]) },
  { title: 'OCR 卡住任务', rows: report.value.stale_ocr_tasks || [], columns: simpleColumns([['record_id', '记录ID'], ['image_id', '图片ID'], ['file_name', '文件名'], ['retry_count', '重试次数'], ['started_at', '开始时间']]) },
  { title: '重复单号 + 开单公司', rows: report.value.duplicate_order_company || [], columns: simpleColumns([['order_no', '单号'], ['company', '开单公司'], ['cnt', '重复数'], ['first_id', '首条ID']]) },
])

async function load() {
  loading.value = true
  try {
    const data = await dataQualityApi.check()
    report.value = data.report || {}
  } catch (error: any) {
    message.error(error?.message || '检查失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
