<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useMessage, NButton, NSpace, NPopconfirm, NTag, NTooltip } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchImages, fetchImage, deleteImage, updateImage, reocrImage, rereviewImage } from '@/service/api/business';
import type { ImageAsset } from '@/service/api/types';
import { useImageEditor } from '@/hooks/business/image-editor';
import { monthOptions as getMonthOptions, downloadExport } from '@/utils/business';
import { useRouter } from 'vue-router';

defineOptions({ name: 'Images' });

const PAGE_SIZE = 20;
const router = useRouter();
const message = useMessage();
const loading = ref(false);
const total = ref(0);
const page = ref(1);
let searchTimer: number | null = null;
let abortController: AbortController | null = null;
const rows = ref<ImageAsset[]>([]);
const filters = reactive({
  month: '',
  plate: '',
  status: '' as string,
  orderNo: ''
});

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));

// 表格高度自适应窗口
const tableMaxHeight = ref(500);
function updateTableHeight() {
  tableMaxHeight.value = Math.max(300, window.innerHeight - 260);
}
onMounted(() => {
  updateTableHeight();
  window.addEventListener('resize', updateTableHeight);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableHeight);
});

const monthOptions = getMonthOptions();

const statusOptions = [
  { label: '待扫描', value: 'pending' },
  { label: '识别中', value: 'processing' },
  { label: '已完成', value: 'done' },
  { label: '重复', value: 'duplicate' },
  { label: '错误', value: 'error' }
];

const statusLabel: Record<string, string> = {
  pending: '待扫描',
  processing: '识别中',
  done: '已完成',
  duplicate: '重复',
  error: '错误'
};
const statusType: Record<string, 'primary' | 'info' | 'success' | 'warning' | 'error' | 'default'> = {
  pending: 'warning',
  processing: 'info',
  done: 'success',
  duplicate: 'default',
  error: 'error'
};

const editorImg = ref<ImageAsset | null>(null);
let editorBase = '';

const {
  editorOpen,
  canvasEl,
  openEditor: openEditorRaw,
  startDrag,
  onDrag,
  endDrag,
  applyCrop,
  rotate,
  resetEditor: resetEditorRaw,
  saveImage: saveImageRaw
} = useImageEditor({
  onSave: async (base64: string) => {
    if (!editorImg.value) return;
    await updateImage(editorImg.value.id, { image_base64: base64 });
    message.success('图片已保存');
    search();
  }
});

const columns: DataTableColumns<ImageAsset> = [
  {
    title: '缩略图',
    key: 'thumb',
    width: 110,
    render: row =>
      row.thumbnail_base64
        ? h('img', {
            src: row.thumbnail_base64,
            style: 'max-width:82px;max-height:62px;border-radius:6px;cursor:pointer',
            onClick: () => openEditor(row)
          })
        : h('span', { style: 'color:#94a3b8;font-size:12px' }, '无')
  },
  { title: 'ID', key: 'id', width: 70 },
  { title: '文件名', key: 'file_name', minWidth: 220 },
  {
    title: '日期',
    key: 'record_date',
    width: 110,
    render: row => (row.record_date || row.created_at || '').slice(0, 10)
  },
  { title: '单号', key: 'order_no', width: 130 },
  {
    title: '状态',
    key: 'ocr_status',
    width: 105,
    render: row =>
      h(
        NTag,
        { size: 'small', type: statusType[row.ocr_status || ''] || 'default' },
        () => statusLabel[row.ocr_status || ''] || row.ocr_status || '-'
      )
  },
  {
    title: 'OCR 原文',
    key: 'ocr_text',
    minWidth: 240,
    render: row =>
      h(
        NTooltip,
        { trigger: 'hover', width: 420 },
        {
          trigger: () => h('span', { style: 'color:#64748b' }, String(row.ocr_text || '').slice(0, 72)),
          default: () => row.ocr_text || '无 OCR 原文'
        }
      )
  },
  {
    title: '操作',
    key: 'actions',
    width: 330,
    fixed: 'right',
    render: row =>
      h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' }, [
        h(NButton, { size: 'small', onClick: () => openEditor(row) }, () => '查看'),
        row.record_id
          ? h(
              NButton,
              {
                size: 'small',
                type: 'primary',
                onClick: () => router.push({ path: '/review', query: { id: String(row.record_id) } })
              },
              () => '核对'
            )
          : null,
        h(NButton, { size: 'small', onClick: () => reocr(row.id) }, () => '重新 OCR'),
        h(NButton, { size: 'small', onClick: () => rereview(row.id) }, () => '恢复未核对'),
        h(
          NPopconfirm,
          { onPositiveClick: () => del(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
            default: () => '确认删除图片及关联记录？'
          }
        )
      ])
  }
];

function buildParams(includePaging = true) {
  const params: Record<string, string> = {};
  if (filters.month) params.month = filters.month;
  if (filters.status) params.ocr_status = filters.status;
  if (filters.plate) params.plate_no = filters.plate;
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
    const data = await fetchImages(buildParams(), abortController.signal);
    rows.value = data.images || [];
    total.value = data.total || 0;
  } catch (error: unknown) {
    const err = error as Error;
    if (err.name !== 'AbortError') message.error(err?.message || '查询失败');
  } finally {
    loading.value = false;
  }
}

function scheduleSearch() {
  page.value = 1;
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => search(), 220);
}

function goPage(nextPage: number) {
  page.value = nextPage;
  search();
}

function resetFilters() {
  Object.assign(filters, { month: '', plate: '', status: '', orderNo: '' });
}

async function exportImages(format: 'zip' | 'xls' | 'csv') {
  const params = buildParams(false);
  params.format = format;
  try {
    const url = '/api/images/export?' + new URLSearchParams(params).toString();
    await downloadExport(url, `images.${format}`);
    message.success('导出已开始');
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '导出失败');
  }
}

async function openEditor(row: ImageAsset) {
  const data = await fetchImage(row.id);
  editorImg.value = row;
  editorBase = data.image_base64 || '';
  await openEditorRaw(editorBase);
}

async function resetEditor() {
  if (editorImg.value) {
    const data = await fetchImage(editorImg.value.id);
    editorBase = data.image_base64 || '';
    await openEditorRaw(editorBase);
  } else {
    resetEditorRaw();
  }
}

async function reocr(id: number) {
  await reocrImage(id);
  message.success('已加入 OCR 队列');
  search();
}

async function rereview(id: number) {
  const data = await rereviewImage(id);
  message.success(`已恢复未核对：${data.updated_records || 0} 条`);
  search();
}

async function del(id: number) {
  await deleteImage(id);
  message.success('已删除');
  search();
}

onMounted(search);

watch(filters, scheduleSearch);

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer);
  if (abortController) abortController.abort();
});
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-auto">
    <NCard title="图片资产" :bordered="false" size="small">
      <template #header-extra>
        <NSpace wrap>
          <NButton secondary @click="exportImages('zip')">导出图片</NButton>
          <NButton secondary @click="exportImages('xls')">导出 XLS</NButton>
          <NButton secondary @click="exportImages('csv')">导出 CSV</NButton>
        </NSpace>
      </template>

      <!-- 筛选栏 -->
      <NSpace class="mb-16px" wrap>
        <NForm label-placement="left" :show-feedback="false" :show-label="true">
          <NSpace wrap>
            <NFormItem label="月份">
              <NSelect
                v-model:value="filters.month"
                clearable
                placeholder="选择月份"
                :options="monthOptions"
                style="width: 140px"
              />
            </NFormItem>
            <NFormItem label="OCR">
              <NSelect
                v-model:value="filters.status"
                clearable
                placeholder="OCR 状态"
                :options="statusOptions"
                style="width: 140px"
              />
            </NFormItem>
            <NFormItem label="车牌">
              <NInput v-model:value="filters.plate" clearable placeholder="车牌号" style="width: 160px" />
            </NFormItem>
            <NFormItem label="单号">
              <NInput v-model:value="filters.orderNo" clearable placeholder="单号" style="width: 140px" />
            </NFormItem>
            <NButton @click="resetFilters">重置</NButton>
          </NSpace>
        </NForm>
      </NSpace>

      <NDataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :row-key="(row: ImageAsset) => row.id"
        :max-height="tableMaxHeight"
        :scroll-x="1320"
        remote
        striped
      />

      <NSpace justify="center" class="mt-16px">
        <NPagination v-model:page="page" :page-count="totalPages" :page-size="PAGE_SIZE" @update:page="goPage" />
      </NSpace>
    </NCard>

    <!-- 图片编辑器弹窗 -->
    <NModal
      v-if="editorOpen"
      v-model:show="editorOpen"
      preset="card"
      title="图片查看与编辑"
      style="width: 95vw; height: 92vh"
    >
      <template #header-extra>
        <NSpace align="center">
          <NButton size="small" @click="rotate(-90)">左转</NButton>
          <NButton size="small" @click="rotate(90)">右转</NButton>
          <NButton size="small" @click="resetEditor">重置</NButton>
          <NButton size="small" type="primary" @click="applyCrop">应用裁剪</NButton>
          <NButton size="small" type="success" @click="saveImageRaw">保存</NButton>
        </NSpace>
      </template>
      <div
        style="height: calc(92vh - 120px); overflow: auto; display: flex; justify-content: center; align-items: center"
      >
        <canvas
          ref="canvasEl"
          style="max-width: 100%; max-height: 100%; cursor: crosshair"
          @mousedown="startDrag"
          @mousemove="onDrag"
          @mouseup="endDrag"
          @mouseleave="endDrag"
        />
      </div>
    </NModal>
  </div>
</template>
