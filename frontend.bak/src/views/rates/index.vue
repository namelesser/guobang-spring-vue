<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">运价资料</div>
        <div class="page-subtitle">维护开单公司到收货单位的时效单价。基础资料已拆到独立页面。</div>
      </div>
      <n-space>
        <n-button @click="loadAll">刷新</n-button>
      </n-space>
    </div>

    <n-card class="soft-card" title="新增运价">
      <div class="rate-create-grid">
        <div class="filter-field"><span class="filter-label">开单公司</span><n-select v-model:value="createForm.origin" filterable tag :options="companyOpts" placeholder="选择或输入开单公司" style="width:230px" /></div>
        <div class="filter-field"><span class="filter-label">发货单位</span><n-select v-model:value="createForm.sender" filterable tag :options="senderOpts" placeholder="选择或输入发货单位" style="width:230px" /></div>
        <div class="filter-field"><span class="filter-label">收货单位</span><n-select v-model:value="createForm.destination" filterable tag :options="receiverOpts" placeholder="选择或输入收货单位" style="width:230px" /></div>
        <div class="filter-field"><span class="filter-label">单价(元/吨)</span><n-input-number v-model:value="createForm.price_per_ton" :min="0" :precision="2" placeholder="输入单价" style="width:150px" /></div>
        <div class="filter-field">
          <span class="filter-label">起始日期</span>
          <n-date-picker
            :formatted-value="createForm.effective_from || null"
            type="date"
            value-format="yyyy-MM-dd"
            :actions="null"
            placeholder="起始日期"
            style="width:150px"
            @update:formatted-value="setCreateFrom"
          />
        </div>
        <div class="filter-field">
          <span class="filter-label">截止日期</span>
          <n-date-picker
            :formatted-value="createForm.effective_to || null"
            type="date"
            value-format="yyyy-MM-dd"
            clearable
            :actions="null"
            placeholder="截止日期"
            style="width:150px"
            @update:formatted-value="setCreateTo"
          />
        </div>
        <div class="filter-field"><span class="filter-label">备注</span><n-input v-model:value="createForm.note" clearable placeholder="备注" style="width:180px" /></div>
        <div class="rate-create-actions">
          <n-button type="primary" :loading="saving" @click="createRate">添加运价</n-button>
          <n-button @click="resetCreateForm">清空</n-button>
        </div>
      </div>
    </n-card>

    <div class="toolbar">
      <div class="filter-field"><span class="filter-label">开单公司</span><n-select v-model:value="filter.company" clearable filterable :options="companyOpts" placeholder="全部开单公司" style="width:220px" /></div>
      <div class="filter-field"><span class="filter-label">发货单位</span><n-select v-model:value="filter.sender" clearable filterable :options="senderOpts" placeholder="全部发货单位" style="width:220px" /></div>
      <div class="filter-field"><span class="filter-label">收货单位</span><n-select v-model:value="filter.dest" clearable filterable :options="receiverOpts" placeholder="全部收货单位" style="width:220px" /></div>
      <div class="filter-field"><span class="filter-label">状态</span><n-select v-model:value="filter.status" clearable :options="statusOpts" placeholder="全部状态" style="width:140px" /></div>
      <n-button @click="resetFilters">重置</n-button>
    </div>

    <n-card class="soft-card" title="线路运价" content-style="padding:0">
      <n-data-table :columns="rateColumns" :data="filteredRates" :row-key="(row: any) => row.id" striped />
    </n-card>

    <n-modal v-model:show="rateOpen" preset="card" :title="rateForm.id ? '编辑运价' : '新增运价'" style="width:680px">
      <n-form label-placement="top" :show-feedback="false">
        <n-grid :cols="2" :x-gap="12">
          <n-gi><n-form-item label="开单公司"><n-select v-model:value="rateForm.origin" filterable tag :options="companyOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="发货单位"><n-select v-model:value="rateForm.sender" filterable tag :options="senderOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="收货单位"><n-select v-model:value="rateForm.destination" filterable tag :options="receiverOpts" /></n-form-item></n-gi>
          <n-gi><n-form-item label="单价(元/吨)"><n-input-number v-model:value="rateForm.price_per_ton" :min="0" :precision="2" style="width:100%" /></n-form-item></n-gi>
          <n-gi><n-form-item label="起始日期"><n-date-picker :formatted-value="rateForm.effective_from || null" type="date" value-format="yyyy-MM-dd" :actions="null" style="width:100%" @update:formatted-value="setEditFrom" /></n-form-item></n-gi>
          <n-gi><n-form-item label="截止日期"><n-date-picker :formatted-value="rateForm.effective_to || null" type="date" value-format="yyyy-MM-dd" clearable :actions="null" style="width:100%" @update:formatted-value="setEditTo" /></n-form-item></n-gi>
          <n-gi><n-form-item label="备注"><n-input v-model:value="rateForm.note" /></n-form-item></n-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="rateOpen=false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveRate">保存</n-button>
        </n-space>
      </template>
    </n-modal>

  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useMessage, NButton, NPopconfirm, NTag } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { collectionApi, rateApi } from '@/api'

const message = useMessage()
const saving = ref(false)
const rates = ref<any[]>([])
const collectionCache: Record<string, string[]> = reactive({})
const rateOpen = ref(false)
const filter = reactive({ company: '', sender: '', dest: '', status: '' })
const rateForm = reactive({ id: 0, origin: '', sender: '', destination: '', price_per_ton: null as number | null, effective_from: '', effective_to: '', note: '' })
const createForm = reactive({ origin: '', sender: '', destination: '', price_per_ton: null as number | null, effective_from: today(), effective_to: '', note: '' })

const statusOpts = [{ label: '当前有效', value: 'active' }, { label: '未来生效', value: 'future' }, { label: '已失效', value: 'expired' }]
const opts = (cat: string) => computed(() => (collectionCache[cat] || []).map(value => ({ label: value, value })))
const companyOpts = opts('company')
const senderOpts = opts('sender')
const receiverOpts = opts('receiver')

const filteredRates = computed(() => rates.value.filter(rate => {
  if (filter.company && rate.origin !== filter.company) return false
  if (filter.sender && rate.sender !== filter.sender) return false
  if (filter.dest && rate.destination !== filter.dest) return false
  if (filter.status && rateStatus(rate).key !== filter.status) return false
  return true
}))

const rateColumns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '开单公司', key: 'origin', minWidth: 200 },
  { title: '发货单位', key: 'sender', minWidth: 180 },
  { title: '收货单位', key: 'destination', minWidth: 180 },
  { title: '单价', key: 'price_per_ton', width: 110, align: 'right', render: row => Number(row.price_per_ton || 0).toFixed(2) },
  { title: '生效日期', key: 'effective_from', width: 120, render: row => fmtDate(rateFrom(row)) },
  { title: '截止日期', key: 'effective_to', width: 120, render: row => fmtDate(rateTo(row)) || '永久' },
  { title: '状态', key: 'status', width: 110, render: row => { const s = rateStatus(row); return h(NTag, { size: 'small', type: s.type }, () => s.text) } },
  { title: '备注', key: 'note', minWidth: 160 },
  {
    title: '操作', key: 'actions', width: 160, fixed: 'right',
    render: row => h('div', { style: 'display:flex;gap:6px' }, [
      h(NButton, { size: 'small', onClick: () => openRate(row) }, () => '编辑'),
      h(NPopconfirm, { onPositiveClick: () => deleteRate(row.id) }, {
        trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
        default: () => '确认删除该运价？',
      }),
    ]),
  },
]

function fmtDate(value: string) {
  return value ? String(value).slice(0, 10) : ''
}

function rateFrom(rate: any) {
  return rate.effective_from || rate.start_date || rate.date_from || rate.valid_from || ''
}

function rateTo(rate: any) {
  return rate.effective_to || rate.end_date || rate.date_to || rate.valid_to || ''
}

function today() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function rateStatus(rate: any) {
  const current = today()
  const from = fmtDate(rateFrom(rate))
  const to = fmtDate(rateTo(rate))
  if (from && from > current) return { key: 'future', text: '未来生效', type: 'warning' as const }
  if (to && to < current) return { key: 'expired', text: '已失效', type: 'default' as const }
  return { key: 'active', text: '当前有效', type: 'success' as const }
}

async function loadCollections() {
  const data = await collectionApi.all()
  for (const [cat, items] of Object.entries(data.collections || {}) as [string, any[]][]) {
    collectionCache[cat] = items.map(item => String(item.value || '').trim()).filter(v => v && v !== '未知').sort((a, b) => a.localeCompare(b, 'zh-CN'))
  }
}

async function loadRates() {
  const data = await rateApi.list()
  rates.value = data.rates || []
}

async function loadAll() {
  try {
    await Promise.all([loadCollections(), loadRates()])
  } catch (error: any) {
    message.error(error?.message || '加载失败')
  }
}

function resetFilters() {
  Object.assign(filter, { company: '', sender: '', dest: '', status: '' })
}

function openRate(row: any = null) {
  Object.assign(rateForm, row ? {
    id: row.id,
    origin: row.origin || '',
    sender: row.sender || '',
    destination: row.destination || '',
    price_per_ton: Number(row.price_per_ton || 0),
    effective_from: fmtDate(rateFrom(row)),
    effective_to: fmtDate(rateTo(row)),
    note: row.note || '',
  } : { id: 0, origin: '', sender: '', destination: '', price_per_ton: null, effective_from: '', effective_to: '', note: '' })
  rateOpen.value = true
}

function resetCreateForm() {
  Object.assign(createForm, { origin: '', sender: '', destination: '', price_per_ton: null, effective_from: today(), effective_to: '', note: '' })
}

function invalidPeriod(from: string, to: string | null | undefined) {
  return Boolean(from && to && to < from)
}

function setCreateFrom(value: string | null) {
  createForm.effective_from = value || ''
}

function setCreateTo(value: string | null) {
  createForm.effective_to = value || ''
}

function setEditFrom(value: string | null) {
  rateForm.effective_from = value || ''
}

function setEditTo(value: string | null) {
  rateForm.effective_to = value || ''
}

async function createRate() {
  if (!createForm.origin || !createForm.destination || createForm.price_per_ton == null || createForm.price_per_ton <= 0 || !createForm.effective_from) {
    message.warning('请填写开单公司、收货单位、单价和生效日期')
    return
  }
  if (invalidPeriod(createForm.effective_from, createForm.effective_to)) {
    message.warning('截止日期不能早于起始日期')
    return
  }
  saving.value = true
  try {
    await rateApi.create({
      origin: createForm.origin,
      sender: createForm.sender || '',
      destination: createForm.destination,
      price_per_ton: Number(createForm.price_per_ton),
      effective_from: createForm.effective_from,
      effective_to: createForm.effective_to || null,
      note: createForm.note || '',
    })
    message.success('运价已添加')
    resetCreateForm()
    await loadRates()
  } catch (error: any) {
    message.error(error?.message || '添加失败')
  } finally {
    saving.value = false
  }
}

async function saveRate() {
  if (!rateForm.origin || !rateForm.destination || rateForm.price_per_ton == null || rateForm.price_per_ton <= 0 || !rateForm.effective_from) {
    message.warning('请补全运价必填项')
    return
  }
  if (invalidPeriod(rateForm.effective_from, rateForm.effective_to)) {
    message.warning('截止日期不能早于起始日期')
    return
  }
  saving.value = true
  const body = {
    origin: rateForm.origin,
    sender: rateForm.sender || '',
    destination: rateForm.destination,
    price_per_ton: Number(rateForm.price_per_ton),
    effective_from: rateForm.effective_from,
    effective_to: rateForm.effective_to || null,
    note: rateForm.note || '',
  }
  try {
    if (rateForm.id) await rateApi.update(rateForm.id, body)
    else await rateApi.create(body)
    message.success('运价已保存')
    rateOpen.value = false
    await loadRates()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteRate(id: number) {
  await rateApi.delete(id)
  message.success('运价已删除')
  loadRates()
}

onMounted(loadAll)
</script>

<style scoped>
.rate-create-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, max-content));
  align-items: end;
  gap: 12px;
}

.rate-create-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
