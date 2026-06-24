<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, NButton } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchDataQuality } from '@/service/api/business';
import type {
  DataQualityCollectionItem,
  DataQualityDuplicateOrder,
  DataQualityReport,
  DataQualitySenderCompanyMismatch,
  DataQualityStaleOcrTask
} from '@/service/api/types';

defineOptions({ name: 'DataQuality' });

const message = useMessage();
const router = useRouter();
const loading = ref(false);
const report = ref<DataQualityReport | null>(null);

const pageSize = 20;
type DetailRow = Record<string, string | number | null | undefined>;
const pages = reactive<Record<string, number>>({
  future: 1,
  missingImages: 1,
  noNetWeight: 1,
  noRate: 1,
  stuck: 1,
  duplicateOrders: 1,
  senderCompanyMismatch: 1,
  receiverNotInCollection: 1,
  plateNoNotInCollection: 1
});

const collectionRows = computed(() => {
  const rows: Array<{ field: string; label: string } & DataQualityCollectionItem> = [];
  for (const check of report.value?.collection_checks || []) {
    for (const item of check.items || []) {
      rows.push({ field: check.field, label: check.label, ...item });
    }
  }
  return rows;
});

const collectionCols: DataTableColumns<{ field: string; label: string } & DataQualityCollectionItem> = [
  { title: '字段', key: 'label', width: 140 },
  { title: '异常值', key: 'value', minWidth: 240 },
  { title: '出现次数', key: 'cnt', width: 110, align: 'center' }
];

function simpleColumns<T extends DetailRow>(cols: [keyof T & string, string][]): DataTableColumns<T> {
  const base = cols.map(([key, title]) => ({ key, title, minWidth: key === 'id' ? 70 : 130 }));
  return [
    ...base,
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: row => {
        const id = row.first_id || row.record_id || row.id;
        return id
          ? h(
              NButton,
              {
                size: 'small',
                type: 'primary',
                onClick: () => router.push({ path: '/review', query: { id: String(id) } })
              },
              () => '核对'
            )
          : null;
      }
    }
  ] as DataTableColumns<T>;
}

function paginateRows<T>(rows: T[], key: string): T[] {
  const page = pages[key] || 1;
  return rows.slice((page - 1) * pageSize, page * pageSize);
}

const sections = computed(() => {
  const allSections = [
    {
      key: 'future',
      title: '未来日期',
      allRows: report.value?.future_dates || [],
      columns: simpleColumns([
        ['id', 'ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['company', '开单公司']
      ])
    },
    {
      key: 'missingImages',
      title: 'OCR 缺图片记录',
      allRows: report.value?.missing_images || [],
      columns: simpleColumns([
        ['id', 'ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['company', '开单公司'],
        ['ocr_status', 'OCR状态']
      ])
    },
    {
      key: 'noNetWeight',
      title: '缺净重记录',
      allRows: report.value?.missing_weights || [],
      columns: simpleColumns([
        ['id', 'ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['company', '开单公司'],
        ['receiver', '收货单位'],
        ['ocr_status', 'OCR状态'],
        ['net_weight', '净重']
      ])
    },
    {
      key: 'noRate',
      title: '缺运价记录',
      allRows: report.value?.missing_rates || [],
      columns: simpleColumns([
        ['id', 'ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['company', '开单公司'],
        ['receiver', '收货单位'],
        ['freight_rate', '运价']
      ])
    },
    {
      key: 'stuck',
      title: 'OCR 卡住任务',
      allRows: report.value?.stale_ocr_tasks || ([] as DataQualityStaleOcrTask[]),
      columns: simpleColumns([
        ['record_id', '记录ID'],
        ['image_id', '图片ID'],
        ['file_name', '文件名'],
        ['retry_count', '重试次数'],
        ['started_at', '开始时间']
      ])
    },
    {
      key: 'duplicateOrders',
      title: '重复单号',
      allRows: report.value?.duplicate_orders || ([] as DataQualityDuplicateOrder[]),
      columns: simpleColumns([
        ['order_no', '单号'],
        ['dup_count', '出现次数'],
        ['first_id', '首条ID']
      ])
    },
    {
      key: 'senderCompanyMismatch',
      title: '开单公司与基础资料不一致',
      allRows: report.value?.sender_company_mismatch || ([] as DataQualitySenderCompanyMismatch[]),
      columns: simpleColumns([
        ['record_id', '记录ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['company', '记录中开单公司'],
        ['collection_value', '基础资料值']
      ])
    },
    {
      key: 'receiverNotInCollection',
      title: '收货单位不在基础资料',
      allRows: report.value?.receiver_not_in_collection || [],
      columns: simpleColumns([
        ['record_id', '记录ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['receiver', '收货单位']
      ])
    },
    {
      key: 'plateNoNotInCollection',
      title: '车牌号不在基础资料',
      allRows: report.value?.plate_no_not_in_collection || [],
      columns: simpleColumns([
        ['record_id', '记录ID'],
        ['record_date', '日期'],
        ['order_no', '单号'],
        ['plate_no', '车牌号']
      ])
    }
  ];

  return allSections.map(s => ({
    ...s,
    rows: paginateRows(s.allRows as DetailRow[], s.key),
    total: s.allRows.length
  }));
});

async function loadData() {
  loading.value = true;
  try {
    const data = await fetchDataQuality();
    report.value = data.report;
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '检查失败');
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-auto h-full">
    <NCard title="数据质量" :bordered="false" size="small" class="flex-1-hidden">
      <template #header-extra>
        <NButton type="primary" :loading="loading" @click="loadData">刷新检查</NButton>
      </template>

      <!-- 统计卡片 -->
      <NGrid :cols="6" :x-gap="12" :y-gap="12" responsive="screen" item-responsive class="mb-16px">
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="基础资料异常">{{ collectionRows.length }}</NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="未来日期">{{ report?.future_dates?.length || 0 }}</NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="OCR 缺图片">{{ report?.missing_images?.length || 0 }}</NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="缺净重">{{ report?.missing_weights?.length || 0 }}</NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="缺运价">{{ report?.missing_rates?.length || 0 }}</NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 s:3 m:2 l:1">
          <NCard embedded>
            <NStatistic label="OCR 卡住">{{ report?.stale_ocr_tasks?.length || 0 }}</NStatistic>
          </NCard>
        </NGi>
      </NGrid>

      <NSpin :show="loading" class="flex-1-hidden overflow-auto">
        <!-- 基础资料异常 -->
        <NCard title="基础资料异常" class="mb-16px" content-style="padding: 0">
          <NDataTable
            :columns="collectionCols"
            :data="collectionRows"
            :row-key="row => row.value || row.id"
            :scroll-x="520"
            size="small"
            striped
          />
        </NCard>

        <!-- 各类问题表格 -->
        <NCard
          v-for="section in sections"
          :key="section.title"
          :title="section.title"
          class="mb-16px"
          content-style="padding: 0"
        >
          <NDataTable
            :columns="section.columns"
            :data="section.rows"
            :row-key="row => row.id || row.record_id || row.order_no"
            :scroll-x="960"
            size="small"
            striped
          />
          <div v-if="section.total > pageSize" class="flex justify-end p-12px">
            <NPagination v-model:page="pages[section.key]" :page-size="pageSize" :item-count="section.total" />
          </div>
        </NCard>
      </NSpin>
    </NCard>
  </div>
</template>
