<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">人工核对</div>
        <div class="page-subtitle">核验图片、OCR 字段和运价，保存后可直接标记已核对。</div>
      </div>
      <n-space>
        <n-button :disabled="index <= 1" @click="move(-1)">上一条</n-button>
        <n-button :disabled="index >= total" @click="move(1)">下一条</n-button>
        <n-button :loading="loading" @click="loadCurrent">刷新</n-button>
      </n-space>
    </div>

    <n-empty v-if="!loading && !record" description="当前没有待核对记录" style="padding:80px 0" />
    <n-spin v-else :show="loading">
      <div class="review-grid">
        <section class="soft-card review-image">
          <div class="review-toolbar">
            <n-space align="center">
              <n-tag type="info" round>{{ index }} / {{ total }}</n-tag>
              <n-text depth="3">记录 ID {{ record?.id || '-' }} · 图片 ID {{ imageId || '-' }}</n-text>
            </n-space>
            <n-space>
              <n-button size="small" @click="openEditor">裁剪/旋转</n-button>
              <n-button size="small" type="primary" @click="reocr">重新 OCR</n-button>
            </n-space>
          </div>
          <div class="image-stage">
            <img v-if="imageBase64" :src="imageBase64" />
            <n-empty v-else description="暂无图片" />
          </div>
        </section>

        <section class="soft-card review-form">
          <n-form label-placement="top" :show-feedback="false">
            <n-grid :cols="2" :x-gap="12">
              <n-gi v-for="field in fields" :key="field.key">
                <n-form-item :label="field.label">
                  <n-select
                    v-if="field.type === 'select'"
                    v-model:value="form[field.key]"
                    :options="options(field.collection)"
                    clearable
                    filterable
                    @update:value="onFieldChange(field.key)"
                  />
                  <n-date-picker
                    v-else-if="field.type === 'date'"
                    v-model:formatted-value="form[field.key]"
                    type="date"
                    value-format="yyyy-MM-dd"
                    style="width:100%"
                    :is-date-disabled="(ts: number) => ts > Date.now()"
                    @update:formatted-value="onFieldChange(field.key)"
                  />
                  <n-input-number
                    v-else-if="field.type === 'number'"
                    v-model:value="form[field.key]"
                    :precision="2"
                    :step="0.01"
                    :readonly="field.readonly"
                    :show-button="!field.readonly"
                    style="width:100%"
                    @update:value="onFieldChange(field.key)"
                  />
                  <n-input v-else v-model:value="form[field.key]" :readonly="field.readonly" @change="onFieldChange(field.key)" />
                </n-form-item>
              </n-gi>
            </n-grid>
          </n-form>
          <n-alert v-if="record?.review_note" type="warning" style="margin-bottom:12px">
            {{ record.review_note }}
          </n-alert>
          <n-space justify="end">
            <n-popconfirm @positive-click="deleteCurrent">
              <template #trigger><n-button type="error">删除图片和记录</n-button></template>
              确认删除当前图片及关联记录？
            </n-popconfirm>
            <n-button type="primary" :loading="saving" @click="save">保存修改</n-button>
            <n-button type="success" :loading="saving" @click="markReviewed">已核对</n-button>
          </n-space>
        </section>
      </div>
    </n-spin>

    <n-modal v-model:show="editorOpen" preset="card" title="图片编辑" style="width:95vw;height:92vh">
      <template #header-extra>
        <n-space align="center">
          <n-button size="small" @click="rotate(-90)">左转</n-button>
          <n-button size="small" @click="rotate(90)">右转</n-button>
          <n-button size="small" @click="resetEditor">重置</n-button>
          <n-button size="small" type="primary" @click="applyCrop">应用裁剪</n-button>
          <n-button size="small" type="success" @click="saveEditedImage">保存图片</n-button>
        </n-space>
      </template>
      <div class="editor-stage">
        <canvas ref="canvasEl" class="editor-canvas" @mousedown="startDrag" @mousemove="drag" @mouseup="endDrag" @mouseleave="endDrag" />
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { collectionApi, imageApi, rateApi, recordApi } from '@/api'

type Field = { key: string; label: string; type: 'text' | 'select' | 'date' | 'number'; collection?: string; readonly?: boolean }

const route = useRoute()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const record = ref<any>(null)
const allRecords = ref<any[]>([])
const total = ref(0)
const index = ref(0)
const imageId = ref('')
const imageBase64 = ref('')
const editorOpen = ref(false)
const canvasEl = ref<HTMLCanvasElement | null>(null)
const form = reactive<Record<string, any>>({})
const collectionCache: Record<string, string[]> = reactive({})

let editorBase = ''
let editorImage: HTMLImageElement | null = null
let crop: { x: number; y: number; w: number; h: number } | null = null
let dragging = false

const fields: Field[] = [
  { key: 'record_date', label: '日期', type: 'date' },
  { key: 'order_no', label: '单号', type: 'text' },
  { key: 'sender', label: '发货单位', type: 'select', collection: 'sender' },
  { key: 'receiver', label: '收货单位', type: 'select', collection: 'receiver' },
  { key: 'company', label: '开单公司', type: 'select', collection: 'company' },
  { key: 'plate_no', label: '车牌号', type: 'select', collection: 'plate' },
  { key: 'net_weight', label: '净重(吨)', type: 'number' },
  { key: 'freight_rate', label: '运费单价', type: 'number', readonly: true },
  { key: 'detour_surcharge', label: '绕路加价', type: 'number' },
  { key: 'total_cost', label: '总费用', type: 'number', readonly: true },
  { key: 'note', label: '备注', type: 'text' },
]

function options(cat?: string) {
  return (collectionCache[cat || ''] || []).map(value => ({ label: value, value }))
}

async function loadCollections() {
  const data = await collectionApi.all()
  for (const [cat, items] of Object.entries(data.collections || {}) as [string, any[]][]) {
    collectionCache[cat] = items.map(item => String(item.value || '').trim()).filter(v => v && v !== '未知').sort((a, b) => a.localeCompare(b, 'zh-CN'))
  }
}

function syncForm(row: any) {
  fields.forEach(field => {
    form[field.key] = field.type === 'number'
      ? (row?.[field.key] == null ? null : Number(row[field.key]))
      : String(row?.[field.key] ?? '')
  })
}

async function lookupRate() {
  const company = String(form.company || '').trim()
  const receiver = String(form.receiver || '').trim()
  const date = String(form.record_date || '').trim()
  if (!company || !receiver || !date) return
  try {
    const data = await rateApi.lookup(company, receiver, date)
    if (!data.found || !data.rate) {
      form.freight_rate = null
      form.total_cost = null
      return
    }
    const rate = Number(data.rate.price_per_ton)
    const net = Number(form.net_weight || 0)
    const detour = Number(form.detour_surcharge || 0)
    form.freight_rate = rate
    form.total_cost = Number((net * (rate + detour)).toFixed(2))
  } catch {}
}

function onFieldChange(key: string) {
  if (['company', 'receiver', 'record_date', 'net_weight', 'detour_surcharge'].includes(key)) lookupRate()
}

function buildBody() {
  const body: Record<string, any> = {}
  fields.forEach(field => {
    if (field.readonly) return
    const value = form[field.key]
    body[field.key] = field.type === 'number'
      ? (value == null || value === '' ? null : Number(value))
      : (String(value ?? '').trim() || null)
  })
  return body
}

async function loadImage(id: string) {
  imageBase64.value = ''
  if (!id) return
  try {
    const data = await imageApi.get(Number(id))
    imageBase64.value = data.image_base64 || ''
  } catch {}
}

async function refreshList() {
  const data = await recordApi.unreviewedList(500)
  allRecords.value = data.records || []
  total.value = data.total || 0
}

async function loadRecord(row: any) {
  record.value = row
  imageId.value = row.first_image_id || String(row.image_id || '').split(',')[0].trim()
  index.value = allRecords.value.findIndex(item => item.id === row.id) + 1 || 1
  syncForm(row)
  await lookupRate()
  await loadImage(imageId.value)
}

async function loadCurrent() {
  loading.value = true
  try {
    if (!Object.keys(collectionCache).length) await loadCollections()
    const queryId = route.query.id ? Number(route.query.id) : 0
    if (queryId) {
      const data = await recordApi.get(queryId)
      await refreshList()
      await loadRecord(data.record)
    } else {
      const data = await recordApi.unreviewed()
      await refreshList()
      if (!data.record) {
        record.value = null
        imageId.value = ''
        imageBase64.value = ''
        index.value = 0
        return
      }
      await loadRecord(data.record)
    }
  } catch (error: any) {
    message.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function move(step: number) {
  const currentIdx = allRecords.value.findIndex(item => item.id === record.value?.id)
  const next = allRecords.value[currentIdx + step]
  if (next) loadRecord(next)
}

async function save() {
  if (!record.value) return
  saving.value = true
  try {
    await recordApi.update(record.value.id, buildBody())
    message.success('已保存')
    await loadCurrent()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function markReviewed() {
  const net = Number(form.net_weight || 0)
  const rate = Number(form.freight_rate || 0)
  if (net <= 0) return message.error('净重必须大于 0')
  if (rate <= 0) return message.error('运费单价必须大于 0')
  saving.value = true
  try {
    await recordApi.update(record.value.id, buildBody())
    await recordApi.review(record.value.id, '')
    message.success('已核对')
    await loadCurrent()
  } catch (error: any) {
    message.error(error?.message || '核对失败')
  } finally {
    saving.value = false
  }
}

async function deleteCurrent() {
  if (!imageId.value) return
  try {
    await imageApi.delete(Number(imageId.value))
    message.success('已删除')
    await loadCurrent()
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  }
}

async function reocr() {
  if (!imageId.value) return
  try {
    await imageApi.reocr(Number(imageId.value))
    message.success('已加入 OCR 队列')
  } catch (error: any) {
    message.error(error?.message || '重新 OCR 失败')
  }
}

async function openEditor() {
  if (!imageId.value) return message.warning('暂无可编辑图片')
  const data = await imageApi.get(Number(imageId.value))
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

function endDrag() {
  dragging = false
}

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
  const rightAngle = Math.abs(deg) % 180 === 90
  const next = document.createElement('canvas')
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
  editorBase = imageBase64.value
  redraw()
}

async function saveEditedImage() {
  await imageApi.update(Number(imageId.value), { image_base64: editorBase })
  imageBase64.value = editorBase
  editorOpen.value = false
  message.success('图片已保存')
}

onMounted(loadCurrent)
watch(() => route.query, loadCurrent)
</script>

<style scoped>
.review-grid {
  display: grid;
  grid-template-columns: minmax(460px, 1.05fr) minmax(440px, .95fr);
  gap: 16px;
  align-items: start;
}
.review-image,
.review-form {
  padding: 16px;
}
.review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
</style>
