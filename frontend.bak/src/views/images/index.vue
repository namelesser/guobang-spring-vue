<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">图片资产</div>
        <div class="page-subtitle">查询图片、查看 OCR 原文，支持重新识别、恢复未核对和批量导出。</div>
      </div>
      <n-space>
        <n-button @click="exportImages('zip')">导出图片</n-button>
      </n-space>
    </div>

    <div class="toolbar">
      <div class="filter-field">
        <span class="filter-label">月份</span>
        <app-month-picker v-model="filters.month" />
      </div>
      <div class="filter-field"><span class="filter-label">OCR 状态</span><n-select v-model:value="filters.ocrStatus" clearable :options="statusOptions" placeholder="全部状态" style="width:140px" /></div>
      <div class="filter-field"><span class="filter-label">文件名</span><n-input v-model:value="filters.fileName" clearable placeholder="输入文件名" style="width:220px" /></div>
      <div class="filter-field"><span class="filter-label">单号</span><n-input v-model:value="filters.orderNo" clearable placeholder="输入单号" style="width:180px" /></div>
      <n-button @click="resetFilters">重置</n-button>
      <n-button @click="exportImages('xls')">导出 XLS</n-button>
      <n-button @click="exportImages('csv')">导出 CSV</n-button>
    </div>

    <n-card class="soft-card" content-style="padding:0">
      <n-data-table :columns="columns" :data="rows" :loading="loading" :row-key="(row: any) => row.id" remote striped />
      <div style="display:flex;justify-content:center;padding:12px">
        <n-pagination v-model:page="page" :page-count="totalPages" :page-size="PAGE_SIZE" @update:page="goPage" />
      </div>
    </n-card>

    <n-modal v-model:show="editorOpen" preset="card" title="图片查看与编辑" style="width:95vw;height:92vh">
      <template #header-extra>
        <n-space align="center">
          <n-button size="small" @click="rotate(-90)">左转</n-button>
          <n-button size="small" @click="rotate(90)">右转</n-button>
          <n-button size="small" @click="resetEditor">重置</n-button>
          <n-button size="small" type="primary" @click="applyCrop">应用裁剪</n-button>
          <n-button size="small" type="success" @click="saveImage">保存</n-button>
        </n-space>
      </template>
      <div class="editor-stage">
        <canvas ref="canvasEl" class="editor-canvas" @mousedown="startDrag" @mousemove="drag" @mouseup="endDrag" @mouseleave="endDrag" />
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, NButton, NPopconfirm, NTag, NTooltip } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { download, imageApi } from '@/api'
import AppMonthPicker from '@/components/AppMonthPicker.vue'

const PAGE_SIZE = 20
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const rows = ref<any[]>([])
const page = ref(1)
const total = ref(0)
const editorOpen = ref(false)
const editorImg = ref<any>(null)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const filters = reactive({ month: '', ocrStatus: '', fileName: '', orderNo: '' })
let searchTimer: number | null = null
let requestSeq = 0
let editorBase = ''
let editorImage: HTMLImageElement | null = null
let crop: { x: number; y: number; w: number; h: number } | null = null
let dragging = false

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const statusOptions = [
  { label: '待扫描', value: 'pending' }, { label: '识别中', value: 'processing' },
  { label: '已完成', value: 'done' }, { label: '重复', value: 'duplicate' }, { label: '错误', value: 'error' },
]
const statusLabel: Record<string, string> = { pending: '待扫描', processing: '识别中', done: '已完成', duplicate: '重复', error: '错误' }
const statusType: Record<string, any> = { pending: 'warning', processing: 'info', done: 'success', duplicate: 'default', error: 'error' }

const columns: DataTableColumns<any> = [
  { title: '缩略图', key: 'thumb', width: 110, render: row => row.thumbnail_base64 ? h('img', { src: row.thumbnail_base64, style: 'max-width:82px;max-height:62px;border-radius:6px;cursor:pointer', onClick: () => openEditor(row) }) : '无' },
  { title: 'ID', key: 'id', width: 70 },
  { title: '文件名', key: 'file_name', minWidth: 220 },
  { title: '日期', key: 'record_date', width: 110, render: row => (row.record_date || row.created_at || '').slice(0, 10) },
  { title: '单号', key: 'order_no', width: 130 },
  { title: '状态', key: 'ocr_status', width: 105, render: row => h(NTag, { size: 'small', type: statusType[row.ocr_status] || 'default' }, () => statusLabel[row.ocr_status] || row.ocr_status || '-') },
  {
    title: 'OCR 原文',
    key: 'ocr_text',
    minWidth: 240,
    render: row => h(NTooltip, { trigger: 'hover', width: 420 }, {
      trigger: () => h('span', { style: 'color:#64748b' }, String(row.ocr_text || '').slice(0, 72)),
      default: () => row.ocr_text || '无 OCR 原文',
    }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 330,
    fixed: 'right',
    render: row => h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' }, [
      h(NButton, { size: 'small', onClick: () => openEditor(row) }, () => '查看'),
      row.record_id ? h(NButton, { size: 'small', type: 'primary', onClick: () => router.push({ path: '/review', query: { id: String(row.record_id) } }) }, () => '核对') : null,
      h(NButton, { size: 'small', onClick: () => reocr(row.id) }, () => '重新 OCR'),
      h(NButton, { size: 'small', onClick: () => rereview(row.id) }, () => '恢复未核对'),
      h(NPopconfirm, { onPositiveClick: () => del(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
        default: () => '确认删除图片及关联记录？',
      }),
    ]),
  },
]

function buildParams(includePaging = true) {
  const params: Record<string, string> = {}
  if (filters.month) params.month = filters.month
  if (filters.ocrStatus) params.ocr_status = filters.ocrStatus
  if (filters.fileName) params.file_name = filters.fileName
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
    const data = await imageApi.list(buildParams())
    if (seq === requestSeq) {
      rows.value = data.images || []
      total.value = data.total || 0
    }
  } catch (error: any) {
    if (seq === requestSeq) message.error(error?.message || '查询失败')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function scheduleSearch() {
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
  Object.assign(filters, { month: '', ocrStatus: '', fileName: '', orderNo: '' })
}

async function exportImages(format: 'zip' | 'xls' | 'csv') {
  const params = buildParams(false)
  params.format = format
  try {
    await download('/api/images/export?' + new URLSearchParams(params).toString(), `images.${format}`)
    message.success('导出已开始')
  } catch (error: any) {
    message.error(error?.message || '导出失败')
  }
}

async function openEditor(row: any) {
  const data = await imageApi.get(row.id)
  editorImg.value = row
  editorBase = data.image_base64 || ''
  editorImage = null
  crop = null
  editorOpen.value = true
  await nextTick()
  await loadEditorImage()
}

function loadEditorImage() {
  return new Promise<void>((resolve) => {
    if (!editorBase) return resolve()
    const img = new Image()
    img.onload = () => {
      editorImage = img
      const canvas = canvasEl.value
      if (canvas) {
        canvas.width = img.naturalWidth
        canvas.height = img.naturalHeight
      }
      redraw()
      resolve()
    }
    img.src = editorBase
  })
}

function redraw() {
  const canvas = canvasEl.value
  const img = editorImage
  if (!canvas || !img) return
  if (canvas.width !== img.naturalWidth || canvas.height !== img.naturalHeight) {
    canvas.width = img.naturalWidth
    canvas.height = img.naturalHeight
  }
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, 0, 0)
  if (crop) {
    const x = crop.w < 0 ? crop.x + crop.w : crop.x
    const y = crop.h < 0 ? crop.y + crop.h : crop.y
    const w = Math.abs(crop.w)
    const h = Math.abs(crop.h)
    ctx.save()
    ctx.strokeStyle = '#22c55e'
    ctx.lineWidth = Math.max(4, canvas.width / 500)
    ctx.setLineDash([16, 10])
    ctx.strokeRect(x, y, w, h)
    ctx.fillStyle = 'rgba(34,197,94,.16)'
    ctx.fillRect(x, y, w, h)
    ctx.restore()
  }
}

async function updateEditorBase(base64: string) {
  editorBase = base64
  editorImage = null
  crop = null
  await loadEditorImage()
}

function canvasToBase64(canvas: HTMLCanvasElement) {
  return canvas.toDataURL('image/jpeg', 0.95)
}

function point(e: MouseEvent) {
  const canvas = canvasEl.value!
  const rect = canvas.getBoundingClientRect()
  return {
    x: Math.max(0, Math.min(canvas.width, (e.clientX - rect.left) * canvas.width / rect.width)),
    y: Math.max(0, Math.min(canvas.height, (e.clientY - rect.top) * canvas.height / rect.height)),
  }
}

function startDrag(e: MouseEvent) {
  const p = point(e)
  dragging = true
  crop = { x: p.x, y: p.y, w: 0, h: 0 }
  redraw()
}
function drag(e: MouseEvent) {
  if (!dragging || !crop) return
  const p = point(e)
  crop.w = p.x - crop.x
  crop.h = p.y - crop.y
  redraw()
}
function endDrag() { dragging = false }

function applyCrop() {
  if (!crop || Math.abs(crop.w) < 10 || Math.abs(crop.h) < 10) return message.warning('请拖选裁剪区域')
  const source = editorImage
  if (!source) return
  const x = Math.round(Math.min(crop.x, crop.x + crop.w))
  const y = Math.round(Math.min(crop.y, crop.y + crop.h))
  const w = Math.round(Math.abs(crop.w))
  const h = Math.round(Math.abs(crop.h))
  const next = document.createElement('canvas')
  next.width = w
  next.height = h
  next.getContext('2d')!.drawImage(source, x, y, w, h, 0, 0, w, h)
  updateEditorBase(canvasToBase64(next))
}

function rotate(deg: number) {
  const img = editorImage
  if (!img) return
  const next = document.createElement('canvas')
  const rightAngle = Math.abs(deg) % 180 === 90
  next.width = rightAngle ? img.naturalHeight : img.naturalWidth
  next.height = rightAngle ? img.naturalWidth : img.naturalHeight
  const ctx = next.getContext('2d')!
  ctx.translate(next.width / 2, next.height / 2)
  ctx.rotate(deg * Math.PI / 180)
  ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2)
  updateEditorBase(canvasToBase64(next))
}

function resetEditor() {
  crop = null
  if (editorImg.value) openEditor(editorImg.value)
}

async function saveImage() {
  try {
    await imageApi.update(editorImg.value.id, { image_base64: editorBase })
    message.success('图片已保存')
    editorOpen.value = false
    search()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  }
}

async function reocr(id: number) {
  await imageApi.reocr(id)
  message.success('已加入 OCR 队列')
  search()
}

async function rereview(id: number) {
  const data = await imageApi.rereview(id)
  message.success(`已恢复未核对：${data.updated_records || 0} 条`)
  search()
}

async function del(id: number) {
  await imageApi.delete(id)
  message.success('已删除')
  search()
}

onMounted(search)

watch(filters, scheduleSearch)

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer)
})
</script>
