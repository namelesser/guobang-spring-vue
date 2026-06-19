<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage, NButton, NPopconfirm, NTag, NCard, NDescriptions, NDescriptionsItem } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchRecords, createRecord, deleteRecord, fetchImage } from '@/service/api/business';
import { useCollections } from '@/hooks/business/use-collections';
import { today, monthOptions, fmtNum, firstImageId, downloadExport } from '@/utils/business';

defineOptions({ name: 'Records' });

const PAGE_SIZE = 50;
const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const saving = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const page = ref(1);
let searchTimer: number | null = null;
let abortController: AbortController | null = null;
let applyingRouteQuery = false;
const createOpen = ref(false);
const createForm = reactive({
  record_date: today(),
  order_no: '',
  sender: '',
  receiver: '',
  company: '',
  plate_no: '',
  net_weight: null as number | null,
  detour_surcharge: 0 as number,
  note: ''
});
const viewerOpen = ref(false);
const viewer = reactive({ record: null as Record<string, unknown> | null, index: 0, image: '' });
const { loadCollections, optionsFor } = useCollections();

const filters = reactive({
  month: '',
  reviewed: '',
  source: '',
  plate: '',
  sender: '',
  receiver: '',
  company: '',
  orderNo: ''
});

const reviewOptions = [
  { label: '已核对', value: '1' },
  { label: '未核对', value: '0' }
];

const sourceOptions = [
  { label: 'OCR', value: 'ocr' },
  { label: '手动', value: 'manual' }
];

const opts = (cat: string) => computed(() => optionsFor(cat));
const companyOptions = opts('company');
const senderOptions = opts('sender');
const receiverOptions = opts('receiver');
const plateOptions = opts('plate_no');
const monthOpts = monthOptions();

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));
const reviewedCount = computed(() => rows.value.filter(row => row.reviewed).length);
const unreviewedCount = computed(() => rows.value.filter(row => !row.reviewed).length);

const detailGroups = [
  {
    title: '基本信息',
    fields: [
      ['record_date', '日期'],
      ['order_no', '单号'],
      ['company', '开单公司'],
      ['sender', '发货单位'],
      ['receiver', '收货单位'],
      ['plate_no', '车牌号']
    ] as [string, string][]
  },
  {
    title: '费用信息',
    fields: [
      ['net_weight', '净重'],
      ['freight_rate', '运费单价'],
      ['detour_surcharge', '绕路加价'],
      ['total_cost', '总费用']
    ] as [string, string][]
  },
  {
    title: '状态信息',
    fields: [
      ['source', '来源'],
      ['ocr_status', 'OCR状态'],
      ['review_note', '审核备注'],
      ['note', '备注']
    ] as [string, string][]
  }
];

const columns: DataTableColumns<any> = [
  { title: 'ID', key: 'id', width: 65, fixed: 'left', ellipsis: { tooltip: true } },
  { title: '日期', key: 'record_date', width: 105, render: row => (row.record_date || '').slice(0, 10) },
  { title: '单号', key: 'order_no', minWidth: 120, ellipsis: { tooltip: true } },
  { title: '开单公司', key: 'company', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '收货单位', key: 'receiver', minWidth: 140, ellipsis: { tooltip: true } },
  { title: '车牌', key: 'plate_no', width: 100 },
  { title: '净重', key: 'net_weight', width: 85, align: 'right', render: row => fmtNum(row.net_weight) },
  { title: '单价', key: 'freight_rate', width: 85, align: 'right', render: row => fmtNum(row.freight_rate) },
  { title: '总费用', key: 'total_cost', width: 100, align: 'right', render: row => fmtNum(row.total_cost) },
  {
    title: '来源',
    key: 'source',
    width: 80,
    render: row =>
      h(NTag, { size: 'small', type: row.source === 'ocr' ? 'info' : 'default', round: true }, () =>
        row.source === 'ocr' ? 'OCR' : '手动'
      )
  },
  {
    title: '状态',
    key: 'reviewed',
    width: 85,
    render: row =>
      h(NTag, { size: 'small', type: row.reviewed ? 'success' : 'warning', round: true }, () => (row.reviewed ? '已核对' : '未核对'))
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right',
    render: row =>
      h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' }, [
        h(NButton, { size: 'small', onClick: () => openViewer(row) }, () => '详情'),
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            onClick: () => router.push({ path: '/review', query: { id: String(row.id) } })
          },
          () => '核对'
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => handleDelete(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
            default: () => '确认删除这条记录？'
          }
        )
      ])
  }
];

function buildParams(includePaging = true) {
  const params: Record<string, string> = {};
  if (filters.month) params.month = filters.month;
  if (filters.reviewed) params.reviewed = filters.reviewed;
  if (filters.source) params.source = filters.source;
  if (filters.plate) params.plate = filters.plate;
  if (filters.sender) params.sender = filters.sender;
  if (filters.receiver) params.receiver = filters.receiver;
  if (filters.company) params.company = filters.company;
  if (filters.orderNo) params.order_no = filters.orderNo;
  if (includePaging) {
    params.limit = String(PAGE_SIZE);
    params.offset = String((page.value - 1) * PAGE_SIZE);
  }
  return params;
}

async function search() {
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = null;
  if (abortController) abortController.abort();
  abortController = new AbortController();
  loading.value = true;
  try {
    const data = await fetchRecords(buildParams(), abortController.signal);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } catch (error: unknown) {
    const err = error as Error;
    if (err.name !== 'AbortError') message.error(err?.message || '查询失败');
  } finally {
    loading.value = false;
  }
}

function scheduleSearch() {
  if (applyingRouteQuery) return;
  page.value = 1;
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => search(), 220);
}

function goPage(nextPage: number) {
  page.value = nextPage;
  search();
}

function resetFilters() {
  Object.assign(filters, {
    month: '',
    reviewed: '',
    source: '',
    plate: '',
    sender: '',
    receiver: '',
    company: '',
    orderNo: ''
  });
}

async function exportRecords(format: 'xls' | 'csv') {
  const params = buildParams(false);
  params.format = format;
  try {
    const url = '/api/records/export?' + new URLSearchParams(params).toString();
    await downloadExport(url, `records.${format}`);
    message.success('导出已开始');
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '导出失败');
  }
}

async function openViewer(row: Record<string, unknown>) {
  viewer.record = row;
  viewer.index = rows.value.indexOf(row);
  viewer.image = '';
  viewerOpen.value = true;
  const id = firstImageId(row);
  if (!id) return;
  try {
    const data = await fetchImage(Number(id));
    viewer.image = data.image_base64 || '';
  } catch (e) {
    console.error('加载图片失败:', e);
  }
}

function viewerMove(step: number) {
  const index = viewer.index + step;
  if (index < 0 || index >= rows.value.length) return;
  openViewer(rows.value[index]);
}

function openCreate() {
  Object.assign(createForm, {
    record_date: today(),
    order_no: '',
    sender: '',
    receiver: '',
    company: '',
    plate_no: '',
    net_weight: null,
    detour_surcharge: 0,
    note: ''
  });
  createOpen.value = true;
}

async function handleCreate() {
  if (
    !createForm.sender ||
    !createForm.receiver ||
    !createForm.company ||
    !createForm.plate_no ||
    createForm.net_weight == null
  ) {
    message.warning('请补全基础资料和净重');
    return;
  }
  saving.value = true;
  try {
    await createRecord({
      ...createForm,
      net_weight: Number(createForm.net_weight),
      detour_surcharge: Number(createForm.detour_surcharge || 0)
    });
    message.success('记录已创建');
    createOpen.value = false;
    search();
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '创建失败');
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id: number) {
  try {
    await deleteRecord(id);
    message.success('记录已删除');
    search();
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '删除失败');
  }
}

function applyRouteQuery() {
  const q = route.query as Record<string, string>;
  applyingRouteQuery = true;
  Object.assign(filters, {
    month: String(q.month || ''),
    reviewed: String(q.reviewed || ''),
    source: String(q.source || ''),
    plate: String(q.plate || ''),
    sender: String(q.sender || ''),
    receiver: String(q.receiver || ''),
    company: String(q.company || ''),
    orderNo: String(q.order_no || '')
  });
  page.value = 1;
  applyingRouteQuery = false;
}

onMounted(async () => {
  await loadCollections();
  applyRouteQuery();
  search();
});

watch(
  () => route.query,
  () => {
    applyRouteQuery();
    search();
  }
);

watch(filters, scheduleSearch);

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer);
  if (abortController) abortController.abort();
});
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto h-full">
    <NCard title="运输记录" :bordered="false" size="small" class="flex-1-hidden">
      <template #header-extra>
        <NSpace>
          <NButton type="primary" @click="openCreate">手动录入</NButton>
        </NSpace>
      </template>

      <!-- 统计卡片 -->
      <NGrid :cols="4" :x-gap="12" :y-gap="12" class="mb-16px">
        <NGi>
          <NCard embedded>
            <NStatistic label="当前结果">{{ total }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="本页记录">{{ rows.length }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="已核对">{{ reviewedCount }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="未核对">{{ unreviewedCount }}</NStatistic>
          </NCard>
        </NGi>
      </NGrid>

      <!-- 筛选栏 -->
      <NSpace class="mb-16px" wrap>
        <NForm label-placement="left" :show-feedback="false" :show-label="true">
          <NSpace wrap>
            <NFormItem label="月份">
              <NSelect
                v-model:value="filters.month"
                clearable
                placeholder="选择月份"
                :options="monthOpts"
                style="width: 140px"
              />
            </NFormItem>
            <NFormItem label="核对">
              <NSelect
                v-model:value="filters.reviewed"
                clearable
                placeholder="核对状态"
                :options="reviewOptions"
                style="width: 120px"
              />
            </NFormItem>
            <NFormItem label="来源">
              <NSelect
                v-model:value="filters.source"
                clearable
                placeholder="记录来源"
                :options="sourceOptions"
                style="width: 120px"
              />
            </NFormItem>
            <NFormItem label="车牌">
              <NSelect
                v-model:value="filters.plate"
                clearable
                filterable
                placeholder="车牌号"
                :options="plateOptions"
                style="width: 130px"
              />
            </NFormItem>
            <NFormItem label="发货">
              <NSelect
                v-model:value="filters.sender"
                clearable
                filterable
                placeholder="发货单位"
                :options="senderOptions"
                style="width: 160px"
              />
            </NFormItem>
            <NFormItem label="收货">
              <NSelect
                v-model:value="filters.receiver"
                clearable
                filterable
                placeholder="收货单位"
                :options="receiverOptions"
                style="width: 160px"
              />
            </NFormItem>
            <NFormItem label="公司">
              <NSelect
                v-model:value="filters.company"
                clearable
                filterable
                placeholder="开单公司"
                :options="companyOptions"
                style="width: 180px"
              />
            </NFormItem>
            <NFormItem label="单号">
              <NInput v-model:value="filters.orderNo" clearable placeholder="单号" style="width: 140px" />
            </NFormItem>
            <NButton @click="resetFilters">重置</NButton>
            <NButton @click="exportRecords('xls')">导出 XLS</NButton>
            <NButton @click="exportRecords('csv')">导出 CSV</NButton>
          </NSpace>
        </NForm>
      </NSpace>

      <!-- 表格容器 -->
      <div class="flex-1-hidden">
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="loading"
          :row-key="(row: Record<string, unknown>) => (row.id as string | number)"
          :row-class-name="() => 'record-row'"
          remote
          striped
          size="medium"
          flex-height
        />
      </div>

      <NSpace justify="center" class="mt-16px">
        <NPagination v-model:page="page" :page-count="totalPages" :page-size="PAGE_SIZE" @update:page="goPage" />
      </NSpace>
    </NCard>

    <!-- 详情弹窗 -->
    <NModal v-model:show="viewerOpen" preset="card" title="记录详情" style="width: 92vw">
      <template v-if="viewer.record">
        <!-- 翻页导航 -->
        <NSpace justify="space-between" align="center" style="margin-bottom: 16px">
          <NButton :disabled="viewer.index <= 0" @click="viewerMove(-1)">上一条</NButton>
          <NText depth="3">ID {{ viewer.record.id }} · {{ viewer.index + 1 }} / {{ rows.length }}</NText>
          <NButton :disabled="viewer.index >= rows.length - 1" @click="viewerMove(1)">下一条</NButton>
        </NSpace>

        <!-- 主内容区：左图右表 -->
        <div style="display: grid; grid-template-columns: minmax(360px, 1.1fr) minmax(320px, 0.9fr); gap: 20px">
          <!-- 左侧：图片 -->
          <div style="text-align: center; background: #f8f9fa; border-radius: 8px; padding: 12px; display: flex; align-items: center; justify-content: center; min-height: 300px">
            <img v-if="viewer.image" :src="viewer.image" style="max-width: 100%; max-height: 60vh; border-radius: 4px" />
            <NEmpty v-else description="暂无图片" />
          </div>

          <!-- 右侧：分组详情 -->
          <div style="display: flex; flex-direction: column; gap: 12px; overflow-y: auto; max-height: 60vh">
            <NCard v-for="group in detailGroups" :key="group.title" :title="group.title" size="small" :bordered="true">
              <NDescriptions :column="2" label-placement="left" size="small">
                <NDescriptionsItem v-for="[key, label] in group.fields" :key="key" :label="label" :span="key === 'note' || key === 'review_note' ? 2 : 1">
                  <template v-if="key === 'net_weight' || key === 'freight_rate' || key === 'detour_surcharge' || key === 'total_cost'">
                    {{ fmtNum(viewer.record[key]) }}
                  </template>
                  <template v-else-if="key === 'source'">
                    <NTag size="small" :type="viewer.record[key] === 'ocr' ? 'info' : 'default'">
                      {{ viewer.record[key] === 'ocr' ? 'OCR' : '手动' }}
                    </NTag>
                  </template>
                  <template v-else-if="key === 'ocr_status'">
                    <NTag size="small" :type="viewer.record[key] === 'success' ? 'success' : viewer.record[key] === 'failed' ? 'error' : 'warning'">
                      {{ viewer.record[key] === 'success' ? '成功' : viewer.record[key] === 'failed' ? '失败' : viewer.record[key] || '-' }}
                    </NTag>
                  </template>
                  <template v-else>
                    {{ viewer.record[key] ?? '-' }}
                  </template>
                </NDescriptionsItem>
              </NDescriptions>
            </NCard>
          </div>
        </div>
      </template>
    </NModal>

    <!-- 手动录入弹窗 -->
    <NModal v-model:show="createOpen" preset="card" title="手动录入记录" style="width: 760px">
      <NForm label-placement="top">
        <NGrid :cols="2" :x-gap="12">
          <NGi>
            <NFormItem label="日期">
              <NDatePicker
                v-model:formatted-value="createForm.record_date"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 100%"
              />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="单号"><NInput v-model:value="createForm.order_no" /></NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="发货单位">
              <NSelect v-model:value="createForm.sender" filterable :options="senderOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="收货单位">
              <NSelect v-model:value="createForm.receiver" filterable :options="receiverOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="开单公司">
              <NSelect v-model:value="createForm.company" filterable :options="companyOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="车牌号">
              <NSelect v-model:value="createForm.plate_no" filterable :options="plateOptions" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="净重(吨)">
              <NInputNumber v-model:value="createForm.net_weight" :min="0" :precision="2" style="width: 100%" />
            </NFormItem>
          </NGi>
          <NGi>
            <NFormItem label="绕路加价">
              <NInputNumber v-model:value="createForm.detour_surcharge" :min="0" :precision="2" style="width: 100%" />
            </NFormItem>
          </NGi>
          <NGi :span="2">
            <NFormItem label="备注"><NInput v-model:value="createForm.note" type="textarea" /></NFormItem>
          </NGi>
        </NGrid>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="createOpen = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="handleCreate">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
:deep(.record-row td) {
  padding: 12px 10px !important;
}
</style>
