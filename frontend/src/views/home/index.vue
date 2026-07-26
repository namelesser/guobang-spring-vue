<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, NButton } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import type { TransportRecord } from '@/service/api/types';
import { fetchRecords, fetchDataQuality, fetchOcrSummary } from '@/service/api/business';

defineOptions({ name: 'HomeDashboard' });

const router = useRouter();
const message = useMessage();

const stats = ref({
  totalRecords: 0,
  unreviewedCount: 0,
  unreviewedRatio: 0,
  missingNetWeight: 0,
  suspiciousWeight: 0,
  ocrRemainingQuota: null as string | null
});

const recentRecords = ref<TransportRecord[]>([]);
const loading = ref(false);

const recentColumns: DataTableColumns<TransportRecord> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '日期', key: 'record_date', width: 100, render: row => formatDate(row.record_date || '') },
  { title: '单号', key: 'order_no', width: 120, ellipsis: { tooltip: true } },
  { title: '开单公司', key: 'company', width: 150, ellipsis: { tooltip: true } },
  { title: '发货单位', key: 'sender', width: 150, ellipsis: { tooltip: true } },
  { title: '收货单位', key: 'receiver', width: 150, ellipsis: { tooltip: true } },
  { title: '车牌号', key: 'plate_no', width: 100 },
  {
    title: '净重',
    key: 'net_weight',
    width: 80,
    render: row => (row.net_weight != null ? row.net_weight.toFixed(2) : '-')
  },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: row =>
      h(
        NButton,
        { size: 'small', type: 'primary', onClick: () => goTo(`/review?id=${row.id}`) },
        { default: () => '核对' }
      )
  }
];

async function loadDashboardData() {
  loading.value = true;
  try {
    const [qualityData, summaryData, pendingData, ocrSummary] = await Promise.all([
      fetchDataQuality(),
      fetchRecords({ limit: '1' }),
      fetchRecords({ limit: '5', reviewed: '0' }),
      fetchOcrSummary()
    ]);

    const r = qualityData.report;
    const totalRecords = summaryData.summary?.total_trips || summaryData.total || 0;
    const unreviewedCount = summaryData.summary?.unreviewed_count || 0;
    stats.value = {
      totalRecords,
      unreviewedCount,
      unreviewedRatio: totalRecords ? unreviewedCount / totalRecords : 0,
      missingNetWeight: r?.missing_weights?.length || 0,
      suspiciousWeight: r?.missing_rates?.length || 0,
      ocrRemainingQuota: ocrSummary.remaining_quota
    };

    recentRecords.value = pendingData.records || [];
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '加载数据失败');
  } finally {
    loading.value = false;
  }
}

function goTo(path: string) {
  router.push(path);
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-';
  return dateStr.substring(0, 10);
}

function formatOcrQuota() {
  return stats.value.ocrRemainingQuota?.trim() || '未设置';
}

onMounted(loadDashboardData);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-auto h-full">
    <NCard title="过磅运输管理系统" :bordered="false" class="flex-1-hidden overflow-auto">
      <template #header-extra>
        <NButton type="primary" @click="loadDashboardData">刷新数据</NButton>
      </template>

      <!-- 统计卡片 -->
      <NGrid :cols="6" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="总记录数">
              <template #default>{{ stats.totalRecords }}</template>
              <template #suffix>
                <NButton text type="primary" @click="goTo('/records')">查看</NButton>
              </template>
            </NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="待核对">
              <template #default>{{ stats.unreviewedCount }}</template>
              <template #suffix>
                <NButton text type="warning" @click="goTo('/review')">去核对</NButton>
              </template>
            </NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="核对率">
              <template #default>{{ (stats.unreviewedRatio * 100).toFixed(1) }}%</template>
            </NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="缺净重">
              <template #default>{{ stats.missingNetWeight }}</template>
              <template #suffix>
                <NButton text type="error" @click="goTo('/data-quality')">检查</NButton>
              </template>
            </NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="异常重量">
              <template #default>{{ stats.suspiciousWeight }}</template>
            </NStatistic>
          </NCard>
        </NGi>
        <NGi span="6 m:1">
          <NCard embedded>
            <NStatistic label="OCR 剩余额度">
              <template #default>{{ formatOcrQuota() }}</template>
              <template #suffix>
                <NButton text type="primary" @click="goTo('/ocr')">OCR</NButton>
              </template>
            </NStatistic>
          </NCard>
        </NGi>
      </NGrid>
    </NCard>

    <!-- 快捷操作 -->
    <NCard title="快捷操作" :bordered="false" size="small">
      <NSpace wrap>
        <NButton type="primary" @click="goTo('/ocr')">OCR 扫描</NButton>
        <NButton type="info" @click="goTo('/review')">人工核对</NButton>
        <NButton @click="goTo('/records')">运输记录</NButton>
        <NButton @click="goTo('/images')">图片资产</NButton>
        <NButton @click="goTo('/rates')">运费费率</NButton>
        <NButton @click="goTo('/report')">经营报表</NButton>
        <NButton @click="goTo('/data-quality')">数据质量</NButton>
        <NButton @click="goTo('/collections')">基础资料</NButton>
      </NSpace>
    </NCard>

    <!-- 待核对记录 -->
    <NCard title="待核对记录" :bordered="false" size="small">
      <template #header-extra>
        <NButton text type="primary" @click="goTo('/review')">查看全部</NButton>
      </template>
      <NDataTable
        :columns="recentColumns"
        :data="recentRecords"
        :loading="loading"
        :bordered="false"
        :scroll-x="990"
        size="small"
      />
    </NCard>
  </div>
</template>

<style scoped></style>
