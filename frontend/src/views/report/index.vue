<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, NButton } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchReport, fetchCollections } from '@/service/api/business';

defineOptions({ name: 'Report' });

const router = useRouter();
const message = useMessage();
const loading = ref(false);
const report = ref<any>(null);
let reportTimer: number | null = null;
let requestSeq = 0;
const collectionCache: Record<string, string[]> = reactive({});
const filters = reactive({ month: getCurrentMonth(), company: '', sender: '', receiver: '', plate: '' });

const gt = computed(() => report.value?.grand_total || {});
const avgRate = computed(() => {
  const weight = Number(gt.value.total_weight || 0);
  const freight = Number(gt.value.total_freight || 0);
  return weight ? (freight / weight).toFixed(2) : '0.00';
});

const opts = (cat: string) => computed(() => (collectionCache[cat] || []).map(value => ({ label: value, value })));
const companyOptions = opts('company');
const senderOptions = opts('sender');
const receiverOptions = opts('receiver');
const plateOptions = opts('plate_no');

const columns: DataTableColumns<any> = [
  { title: '线路', key: 'route', minWidth: 260, render: row => `${row.company || ''} -> ${row.receiver || ''}` },
  { title: '发货单位', key: 'sender', minWidth: 160 },
  { title: '收货单位', key: 'receiver', minWidth: 160 },
  { title: '车牌号', key: 'plate_no', width: 120 },
  { title: '车次', key: 'trips', width: 80, align: 'center' },
  { title: '总净重', key: 'total_weight', width: 120, align: 'right', render: row => formatNum(row.total_weight) },
  { title: '总运费', key: 'total_freight', width: 120, align: 'right', render: row => formatNum(row.total_freight) },
  { title: '均价', key: 'avg_rate', width: 100, align: 'right', render: row => formatNum(row.avg_rate) },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right',
    render: row => h(NButton, { size: 'small', type: 'primary', onClick: () => goRecords(row) }, () => '明细')
  }
];

function formatNum(value: any) {
  return Number(value || 0).toFixed(2);
}

function getCurrentMonth() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

async function loadCollections() {
  try {
    const data = await fetchCollections();
    for (const [cat, items] of Object.entries(data.collections || {}) as [string, any[]][]) {
      collectionCache[cat] = items
        .map(item => String(item.value || '').trim())
        .filter(v => v && v !== '未知')
        .sort((a, b) => a.localeCompare(b, 'zh-CN'));
    }
  } catch (e) {
    console.error('加载基础资料失败:', e);
  }
}

async function loadReport() {
  if (!filters.month) {
    report.value = null;
    return;
  }
  const seq = ++requestSeq;
  loading.value = true;
  try {
    const data = await fetchReport(buildReportParams());
    if (seq === requestSeq) report.value = data;
  } catch (error: any) {
    if (seq === requestSeq) message.error(error?.message || '查询失败');
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

function buildReportParams() {
  const params: Record<string, string> = { month: filters.month };
  if (filters.company) params.company = filters.company;
  if (filters.sender) params.sender = filters.sender;
  if (filters.receiver) params.receiver = filters.receiver;
  if (filters.plate) params.plate_no = filters.plate;
  return params;
}

function resetFilters() {
  Object.assign(filters, { company: '', sender: '', receiver: '', plate: '' });
}

function goRecords(group: any = null) {
  const query: Record<string, string> = {};
  if (filters.month) query.month = filters.month;
  const company = group?.company || filters.company;
  const sender = group?.sender || filters.sender;
  const receiver = group?.receiver || filters.receiver;
  const plate = group?.plate_no || filters.plate;
  if (company) query.company = company;
  if (sender) query.sender = sender;
  if (receiver) query.receiver = receiver;
  if (plate) query.plate = plate;
  router.push({ path: '/records', query });
}

onMounted(async () => {
  await loadCollections();
  loadReport();
});

onBeforeUnmount(() => {
  if (reportTimer) window.clearTimeout(reportTimer);
});

watch(filters, () => {
  if (reportTimer) window.clearTimeout(reportTimer);
  reportTimer = window.setTimeout(() => loadReport(), 220);
});
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <NCard title="经营报表" :bordered="false" size="small">
      <template #header-extra>
        <NSpace>
          <NButton :disabled="!filters.month" @click="goRecords()">查看明细</NButton>
        </NSpace>
      </template>

      <!-- 筛选栏 -->
      <NSpace class="mb-16px" wrap>
        <NForm label-placement="left" :show-feedback="false" :show-label="true">
          <NSpace wrap>
            <NFormItem label="月份">
              <NDatePicker
                v-model:formatted-value="filters.month"
                type="month"
                value-format="yyyy-MM"
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
            <NButton @click="resetFilters">重置筛选</NButton>
          </NSpace>
        </NForm>
      </NSpace>

      <!-- 统计卡片 -->
      <NGrid v-if="report" :cols="4" :x-gap="12" :y-gap="12" class="mb-16px">
        <NGi>
          <NCard embedded>
            <NStatistic label="总车次">{{ gt.trips || 0 }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="总净重(吨)">{{ formatNum(gt.total_weight) }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="总运费(元)">{{ formatNum(gt.total_freight) }}</NStatistic>
          </NCard>
        </NGi>
        <NGi>
          <NCard embedded>
            <NStatistic label="均价(元/吨)">{{ avgRate }}</NStatistic>
          </NCard>
        </NGi>
      </NGrid>

      <NSpin :show="loading">
        <NDataTable
          :columns="columns"
          :data="report?.groups || []"
          :loading="loading"
          :row-key="(row: any) => row.company + row.receiver"
          striped
        />
      </NSpin>

      <NEmpty v-if="!report && !loading" description="请选择月份" style="padding: 70px 0" />
    </NCard>
  </div>
</template>
