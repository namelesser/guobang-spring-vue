<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import { useMessage, NButton, NPopconfirm, NTag } from 'naive-ui';
import type { DataTableColumns, FormRules, FormInst } from 'naive-ui';
import {
  fetchRates,
  createRate as createRateApi,
  updateRate as updateRateApi,
  deleteRate as deleteRateApi
} from '@/service/api/business';
import type { FreightRate } from '@/service/api/types';
import { useCollections } from '@/hooks/business/use-collections';
import { today } from '@/utils/business';

defineOptions({ name: 'Rates' });

const message = useMessage();
const saving = ref(false);
const rates = ref<FreightRate[]>([]);
const { loadCollections, optionsFor } = useCollections();
const rateOpen = ref(false);
const filter = reactive({ company: '', sender: '', dest: '', status: '' });

const createFormRef = ref<FormInst | null>(null);
const rateFormRef = ref<FormInst | null>(null);

const rateFormRules: FormRules = {
  origin: { required: true, message: '请选择开单公司', trigger: 'change' },
  destination: { required: true, message: '请选择收货单位', trigger: 'change' },
  price_per_ton: { required: true, type: 'number', message: '请输入单价', trigger: 'change' },
  effective_from: { required: true, message: '请选择起始日期', trigger: 'change' }
};

const page = ref(1);
const pageSize = 20;
const total = ref(0);

const rateForm = reactive({
  id: null as number | null,
  origin: '',
  sender: '',
  destination: '',
  price_per_ton: null as number | null,
  surcharge: null as number | null,
  effective_from: null as string | null,
  effective_to: null as string | null,
  note: ''
});

const createForm = reactive({
  origin: '',
  sender: '',
  destination: '',
  price_per_ton: null as number | null,
  surcharge: null as number | null,
  effective_from: null as string | null,
  effective_to: null as string | null,
  note: ''
});

const statusOptions = [
  { label: '当前有效', value: 'active' },
  { label: '未来生效', value: 'future' },
  { label: '已失效', value: 'expired' }
];

const opts = (cat: string) => computed(() => optionsFor(cat));
const companyOptions = opts('company');
const senderOptions = opts('sender');
const receiverOptions = opts('receiver');

const filteredRates = computed(() =>
  rates.value.filter(rate => {
    if (filter.company && rate.origin !== filter.company) return false;
    if (filter.sender && rate.sender !== filter.sender) return false;
    if (filter.dest && rate.destination !== filter.dest) return false;
    if (filter.status && rateStatus(rate).key !== filter.status) return false;
    return true;
  })
);

const rateColumns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '开单公司', key: 'origin', minWidth: 200 },
  { title: '发货单位', key: 'sender', minWidth: 180 },
  { title: '收货单位', key: 'destination', minWidth: 180 },
  {
    title: '单价',
    key: 'price_per_ton',
    width: 110,
    align: 'right',
    render: row => Number(row.price_per_ton || 0).toFixed(2)
  },
  { title: '生效日期', key: 'effective_from', width: 120, render: row => fmtDate(rateFrom(row)) },
  { title: '截止日期', key: 'effective_to', width: 120, render: row => fmtDate(rateTo(row)) || '永久' },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: row => {
      const s = rateStatus(row);
      return h(NTag, { size: 'small', type: s.type }, () => s.text);
    }
  },
  { title: '备注', key: 'note', minWidth: 160 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    fixed: 'right',
    render: row =>
      h('div', { style: 'display:flex;gap:6px' }, [
        h(NButton, { size: 'small', onClick: () => openRate(row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => deleteRateItem(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
            default: () => '确认删除该运价？'
          }
        )
      ])
  }
];

function fmtDate(value: string) {
  return value ? String(value).slice(0, 10) : '';
}

function rateFrom(rate: FreightRate) {
  return rate.effective_from || '';
}

function rateTo(rate: FreightRate) {
  return rate.effective_to || '';
}

function rateStatus(rate: FreightRate) {
  const current = today();
  const from = fmtDate(rateFrom(rate));
  const to = fmtDate(rateTo(rate));
  if (from && from > current) return { key: 'future', text: '未来生效', type: 'warning' as const };
  if (to && to < current) return { key: 'expired', text: '已失效', type: 'default' as const };
  return { key: 'active', text: '当前有效', type: 'success' as const };
}

async function loadRates() {
  const data = await fetchRates({ offset: (page.value - 1) * pageSize, limit: pageSize });
  rates.value = data.items || [];
  total.value = data.total || 0;
}

async function loadAll() {
  try {
    await Promise.all([loadCollections(), loadRates()]);
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '加载失败');
  }
}

function resetFilters() {
  Object.assign(filter, { company: '', sender: '', dest: '', status: '' });
}

function openRate(row: FreightRate | null = null) {
  Object.assign(
    rateForm,
    row
      ? {
          id: row.id,
          origin: row.origin || '',
          sender: row.sender || '',
          destination: row.destination || '',
          price_per_ton: Number(row.price_per_ton || 0),
          effective_from: fmtDate(rateFrom(row)),
          effective_to: fmtDate(rateTo(row)),
          note: row.note || ''
        }
      : {
          id: 0,
          origin: '',
          sender: '',
          destination: '',
          price_per_ton: null,
          effective_from: null,
          effective_to: null,
          note: ''
        }
  );
  rateOpen.value = true;
}

function resetCreateForm() {
  Object.assign(createForm, {
    origin: '',
    sender: '',
    destination: '',
    price_per_ton: null,
    effective_from: today(),
    effective_to: null,
    note: ''
  });
}

function invalidPeriod(from: string | null, to: string | null | undefined) {
  return Boolean(from && to && to < from);
}

async function createRateItem() {
  try {
    await createFormRef.value?.validate();
  } catch {
    return;
  }
  if (invalidPeriod(createForm.effective_from, createForm.effective_to)) {
    message.warning('截止日期不能早于起始日期');
    return;
  }
  saving.value = true;
  try {
    await createRateApi({
      origin: createForm.origin,
      sender: createForm.sender || '',
      destination: createForm.destination,
      price_per_ton: Number(createForm.price_per_ton),
      effective_from: createForm.effective_from || '',
      effective_to: createForm.effective_to || undefined,
      note: createForm.note || ''
    });
    message.success('运价已添加');
    resetCreateForm();
    await loadRates();
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '添加失败');
  } finally {
    saving.value = false;
  }
}

async function saveRate() {
  try {
    await rateFormRef.value?.validate();
  } catch {
    return;
  }
  if (invalidPeriod(rateForm.effective_from, rateForm.effective_to)) {
    message.warning('截止日期不能早于起始日期');
    return;
  }
  saving.value = true;
  const body = {
    origin: rateForm.origin,
    sender: rateForm.sender || '',
    destination: rateForm.destination,
    price_per_ton: Number(rateForm.price_per_ton),
    effective_from: rateForm.effective_from || '',
    effective_to: rateForm.effective_to || undefined,
    note: rateForm.note || ''
  };
  try {
    if (rateForm.id) await updateRateApi(rateForm.id, body);
    else await createRateApi(body);
    message.success('运价已保存');
    rateOpen.value = false;
    await loadRates();
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function deleteRateItem(id: number) {
  await deleteRateApi(id);
  message.success('运价已删除');
  loadRates();
}

onMounted(loadAll);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto h-full">
    <NCard title="运价资料" :bordered="false" size="small" class="flex-1-hidden">
      <template #header-extra>
        <NSpace>
          <NButton @click="loadAll">刷新</NButton>
        </NSpace>
      </template>

      <!-- 新增运价表单 -->
      <NCard title="新增运价" class="mb-16px">
        <NForm
          ref="createFormRef"
          :model="createForm"
          :rules="rateFormRules"
          label-placement="top"
          :show-feedback="false"
        >
          <NGrid :cols="4" :x-gap="12">
            <NGi>
              <NFormItem label="开单公司">
                <NSelect
                  v-model:value="createForm.origin"
                  filterable
                  tag
                  :options="companyOptions"
                  placeholder="选择或输入"
                  style="width: 100%"
                />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="发货单位">
                <NSelect
                  v-model:value="createForm.sender"
                  filterable
                  tag
                  :options="senderOptions"
                  placeholder="选择或输入"
                  style="width: 100%"
                />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="收货单位">
                <NSelect
                  v-model:value="createForm.destination"
                  filterable
                  tag
                  :options="receiverOptions"
                  placeholder="选择或输入"
                  style="width: 100%"
                />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="单价(元/吨)">
                <NInputNumber v-model:value="createForm.price_per_ton" :min="0" :precision="2" style="width: 100%" />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="起始日期">
                <NDatePicker
                  v-model:formatted-value="createForm.effective_from"
                  type="date"
                  value-format="yyyy-MM-dd"
                  style="width: 100%"
                />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="截止日期">
                <NDatePicker
                  v-model:formatted-value="createForm.effective_to"
                  type="date"
                  value-format="yyyy-MM-dd"
                  clearable
                  style="width: 100%"
                />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label="备注">
                <NInput v-model:value="createForm.note" placeholder="备注" />
              </NFormItem>
            </NGi>
            <NGi>
              <NFormItem label=" ">
                <NSpace>
                  <NButton type="primary" :loading="saving" @click="createRateItem">添加运价</NButton>
                  <NButton @click="resetCreateForm">清空</NButton>
                </NSpace>
              </NFormItem>
            </NGi>
          </NGrid>
        </NForm>
      </NCard>

      <!-- 筛选栏 -->
      <NSpace class="mb-16px" wrap>
        <NForm label-placement="left" :show-feedback="false" :show-label="true">
          <NSpace wrap>
            <NFormItem label="公司">
              <NSelect
                v-model:value="filter.company"
                clearable
                filterable
                placeholder="开单公司"
                :options="companyOptions"
                style="width: 200px"
              />
            </NFormItem>
            <NFormItem label="发货">
              <NSelect
                v-model:value="filter.sender"
                clearable
                filterable
                placeholder="发货单位"
                :options="senderOptions"
                style="width: 180px"
              />
            </NFormItem>
            <NFormItem label="收货">
              <NSelect
                v-model:value="filter.dest"
                clearable
                filterable
                placeholder="收货单位"
                :options="receiverOptions"
                style="width: 180px"
              />
            </NFormItem>
            <NFormItem label="状态">
              <NSelect
                v-model:value="filter.status"
                clearable
                placeholder="状态"
                :options="statusOptions"
                style="width: 140px"
              />
            </NFormItem>
            <NButton @click="resetFilters">重置</NButton>
          </NSpace>
        </NForm>
      </NSpace>

      <!-- 运价列表 -->
      <NCard title="线路运价" content-style="padding: 0" class="flex-1-hidden">
        <div class="flex-1-hidden">
          <NDataTable :columns="rateColumns" :data="filteredRates" :row-key="(row: FreightRate) => row.id" striped flex-height />
        </div>
        <div class="flex justify-end p-12px">
          <NPagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadRates" />
        </div>
      </NCard>
    </NCard>

    <!-- 编辑弹窗 -->
    <NModal v-model:show="rateOpen" preset="card" :title="rateForm.id ? '编辑运价' : '新增运价'" style="width: 680px">
      <NForm ref="rateFormRef" :model="rateForm" :rules="rateFormRules" label-placement="top" :show-feedback="false">
        <NGrid :cols="2" :x-gap="12">
          <NGi>
            <NFormItem label="开单公司">
              <NSelect v-model:value="rateForm.origin" filterable tag :options="companyOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="发货单位">
              <NSelect v-model:value="rateForm.sender" filterable tag :options="senderOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="收货单位">
              <NSelect v-model:value="rateForm.destination" filterable tag :options="receiverOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="单价(元/吨)">
              <NInputNumber v-model:value="rateForm.price_per_ton" :min="0" :precision="2" style="width: 100%" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="起始日期">
              <NDatePicker
                v-model:formatted-value="rateForm.effective_from"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 100%"
              />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="截止日期">
              <NDatePicker
                v-model:formatted-value="rateForm.effective_to"
                type="date"
                value-format="yyyy-MM-dd"
                clearable
                style="width: 100%"
              />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="备注">
              <NInput v-model:value="rateForm.note" />
            </NFormItem>
          </NGi>
        </NGrid>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="rateOpen = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="saveRate">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
