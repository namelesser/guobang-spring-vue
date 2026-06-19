<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  fetchUnreviewedRecords,
  fetchRecord,
  fetchImage,
  updateRecord,
  reviewRecord,
  deleteRecord,
  deleteImage,
  fetchRates,
  lookupRate,
  updateImage,
  reocrImage
} from '@/service/api/business';
import { useImageEditor } from '@/hooks/business/image-editor';
import { useCollections } from '@/hooks/business/use-collections';
import type { FreightRate } from '@/service/api/types';

type Field = {
  key: string;
  label: string;
  type: 'text' | 'select' | 'date' | 'number';
  collection?: string;
  readonly?: boolean;
};

defineOptions({ name: 'Review' });

const route = useRoute();
const message = useMessage();

const loading = ref(false);
const saving = ref(false);
const records = ref<any[]>([]);
const currentIndex = ref(0);
const imageId = ref('');
const imageBase64 = ref('');

const fields: Field[] = [
  { key: 'record_date', label: '日期', type: 'date' },
  { key: 'order_no', label: '单号', type: 'text' },
  { key: 'sender', label: '发货单位', type: 'select', collection: '__route_sender__' },
  { key: 'receiver', label: '收货单位', type: 'select', collection: '__route_receiver__' },
  { key: 'company', label: '开单公司', type: 'select', collection: 'company' },
  { key: 'plate_no', label: '车牌号', type: 'select', collection: 'plate_no' },
  { key: 'net_weight', label: '净重(吨)', type: 'number' },
  { key: 'freight_rate', label: '运费单价', type: 'number', readonly: true },
  { key: 'detour_surcharge', label: '绕路加价', type: 'number' },
  { key: 'total_cost', label: '总费用', type: 'number', readonly: true },
  { key: 'note', label: '备注', type: 'text' }
];

const form = reactive<Record<string, unknown>>({});
fields.forEach(field => {
  form[field.key] = field.type === 'number' ? null : field.type === 'date' ? null : '';
});
const { collectionCache, loadCollections: loadCollectionsBase } = useCollections();
const routeReceivers = ref<string[]>([]);
const routeSenders = ref<string[]>([]);
const allRates = ref<FreightRate[]>([]);

const receiverOptions = computed(() => routeReceivers.value.map(v => ({ label: v, value: v })));
const senderOptions = computed(() => routeSenders.value.map(v => ({ label: v, value: v })));

const {
  editorOpen,
  canvasEl,
  openEditor: openEditorRaw,
  startDrag,
  onDrag,
  endDrag,
  applyCrop,
  rotate,
  resetEditor,
  saveImage: saveImageRaw
} = useImageEditor({
  onSave: async base64 => {
    await updateImage(Number(imageId.value), { image_base64: base64 });
    imageBase64.value = base64;
    message.success('图片已保存');
  },
  getInitialBase64: () => imageBase64.value
});

const currentRecord = computed(() => records.value[currentIndex.value] || null);

function getOptions(field: Field) {
  if (field.collection === '__route_receiver__') return receiverOptions.value;
  if (field.collection === '__route_sender__') return senderOptions.value;
  return (collectionCache[field.collection || ''] || []).map(v => ({ label: v, value: v }));
}

async function loadCollections() {
  try {
    await loadCollectionsBase();
    const rateData = await fetchRates();
    allRates.value = rateData.items || rateData.rates || [];
    updateRouteReceivers();
    updateRouteSenders();
  } catch (e) {
    console.error('加载基础资料失败:', e);
  }
}

function updateRouteReceivers() {
  const company = String(form.company || '').trim();
  let rates = allRates.value;
  if (company) {
    rates = rates.filter((r: FreightRate) => String(r.origin || '').trim() === company);
  }
  const dests = [
    ...new Set(rates.map((r: FreightRate) => String(r.destination || '').trim()).filter(Boolean))
  ] as string[];
  routeReceivers.value = dests.sort((a, b) => a.localeCompare(b, 'zh-CN'));
}

function updateRouteSenders() {
  const company = String(form.company || '').trim();
  let rates = allRates.value;
  if (company) {
    rates = rates.filter((r: FreightRate) => String(r.origin || '').trim() === company);
  }
  const senders = [
    ...new Set(rates.map((r: FreightRate) => String(r.sender || '').trim()).filter(Boolean))
  ] as string[];
  routeSenders.value = senders.sort((a, b) => a.localeCompare(b, 'zh-CN'));
}

function onFieldChange(key: string) {
  if (key === 'company') {
    updateRouteReceivers();
    updateRouteSenders();
    const receiver = String(form.receiver || '').trim();
    if (receiver && routeReceivers.value.length && !routeReceivers.value.includes(receiver)) {
      form.receiver = '';
    }
    const sender = String(form.sender || '').trim();
    if (sender && routeSenders.value.length && !routeSenders.value.includes(sender)) {
      form.sender = '';
    }
  }
  if (['company', 'receiver', 'record_date', 'net_weight', 'detour_surcharge'].includes(key)) {
    lookupRateForForm();
  }
}

function syncForm(row: Record<string, unknown>) {
  fields.forEach(field => {
    if (field.type === 'number') {
      form[field.key] = row?.[field.key] == null ? null : Number(row[field.key]);
    } else if (field.type === 'date') {
      const val = row?.[field.key];
      form[field.key] = val ? String(val) : null;
    } else {
      form[field.key] = String(row?.[field.key] ?? '');
    }
  });
}

async function lookupRateForForm() {
  const company = String(form.company || '').trim();
  const receiver = String(form.receiver || '').trim();
  const date = form.record_date ? new Date(String(form.record_date)).toISOString().split('T')[0] : '';
  if (!company || !receiver || !date) return;
  try {
    const data = await lookupRate({ origin: company, destination: receiver, date });
    if (!data.found || !data.rate) {
      form.freight_rate = null;
      form.total_cost = null;
      return;
    }
    const rate = Number(data.rate.price_per_ton);
    const net = Number(form.net_weight || 0);
    const detour = Number(form.detour_surcharge || 0);
    form.freight_rate = rate;
    form.total_cost = Number((net * (rate + detour)).toFixed(2));
  } catch (e) {
    console.error('查询运价失败:', e);
  }
}

async function loadImage(id: string) {
  imageBase64.value = '';
  if (!id) return;
  try {
    const data = await fetchImage(Number(id));
    imageBase64.value = data.image_base64 || '';
  } catch (e) {
    console.error('加载图片失败:', e);
  }
}

async function refreshList() {
  const data = await fetchUnreviewedRecords();
  records.value = data.records || [];
}

async function loadRecord(row: Record<string, unknown>) {
  imageId.value =
    String(row.first_image_id || '') ||
    String(row.image_id || '')
      .split(',')[0]
      .trim();
  currentIndex.value = records.value.findIndex(item => item.id === row.id);
  if (currentIndex.value < 0) currentIndex.value = 0;
  syncForm(row);
  updateRouteReceivers();
  updateRouteSenders();
  await lookupRateForForm();
  await loadImage(imageId.value);
}

async function loadCurrent() {
  loading.value = true;
  try {
    if (!Object.keys(collectionCache).length) await loadCollections();
    const queryId = route.query.id ? Number(route.query.id) : 0;
    if (queryId) {
      const data = await fetchRecord(queryId);
      await refreshList();
      await loadRecord((data.record || data) as unknown as Record<string, unknown>);
    } else {
      await refreshList();
      if (records.value.length === 0) {
        imageId.value = '';
        imageBase64.value = '';
        return;
      }
      await loadRecord(records.value[0]);
    }
  } catch (error: any) {
    message.error(error?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function move(step: number) {
  const newIndex = currentIndex.value + step;
  if (newIndex >= 0 && newIndex < records.value.length) {
    currentIndex.value = newIndex;
    const row = records.value[newIndex];
    loadRecord(row);
  }
}

function validateForm(): boolean {
  const company = String(form.company || '').trim();
  const receiver = String(form.receiver || '').trim();
  const sender = String(form.sender || '').trim();
  if (company && !collectionCache['company']?.includes(company)) {
    message.error(`开单公司"${company}"不在集合中，请先在运价资料中添加`);
    return false;
  }
  if (company && receiver && routeReceivers.value.length && !routeReceivers.value.includes(receiver)) {
    message.error(`收货单位"${receiver}"不是"${company}"的路线，请先在运价资料中添加路线`);
    return false;
  }
  if (company && sender && routeSenders.value.length && !routeSenders.value.includes(sender)) {
    message.error(`发货单位"${sender}"不是"${company}"的路线，请先在运价资料中添加路线`);
    return false;
  }
  return true;
}

function buildBody() {
  const body: Record<string, any> = {};
  fields.forEach(field => {
    if (field.readonly) return;
    const value = form[field.key];
    body[field.key] =
      field.type === 'number'
        ? value == null || value === ''
          ? null
          : Number(value)
        : String(value ?? '').trim() || null;
  });
  return body;
}

async function save() {
  const record = currentRecord.value;
  if (!record) return;
  if (!validateForm()) return;
  saving.value = true;
  try {
    await updateRecord(record.id, buildBody());
    message.success('已保存');
    await loadCurrent();
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function markReviewed() {
  const record = currentRecord.value;
  if (!record) return;
  const net = Number(form.net_weight || 0);
  const rate = Number(form.freight_rate || 0);
  if (net <= 0) return message.error('净重必须大于 0');
  if (rate <= 0) return message.error('运费单价必须大于 0');
  if (!validateForm()) return;
  saving.value = true;
  try {
    await updateRecord(record.id, buildBody());
    await reviewRecord(record.id, '');
    message.success('已核对');
    records.value.splice(currentIndex.value, 1);
    if (currentIndex.value >= records.value.length) {
      currentIndex.value = Math.max(0, records.value.length - 1);
    }
    if (records.value.length > 0) {
      await loadRecord(records.value[currentIndex.value]);
    } else {
      imageId.value = '';
      imageBase64.value = '';
    }
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '核对失败');
  } finally {
    saving.value = false;
  }
}

async function deleteCurrent() {
  const record = currentRecord.value;
  if (!record) return;
  try {
    if (imageId.value) {
      await deleteImage(Number(imageId.value));
    }
    await deleteRecord(record.id);
    message.success('已删除');
    records.value.splice(currentIndex.value, 1);
    if (currentIndex.value >= records.value.length) {
      currentIndex.value = Math.max(0, records.value.length - 1);
    }
    if (records.value.length > 0) {
      await loadRecord(records.value[currentIndex.value]);
    } else {
      imageId.value = '';
      imageBase64.value = '';
    }
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '删除失败');
  }
}

async function reocr() {
  if (!imageId.value) return;
  try {
    await reocrImage(Number(imageId.value));
    message.success('已加入 OCR 队列');
  } catch (error: unknown) {
    const err = error as Error;
    message.error(err?.message || '重新 OCR 失败');
  }
}

async function openEditor() {
  if (!imageId.value) return message.warning('暂无可编辑图片');
  const data = await fetchImage(Number(imageId.value));
  const base64 = data.image_base64 || '';
  await openEditorRaw(base64);
}

onMounted(async () => {
  await loadCollections();
  await loadCurrent();
});

watch(
  () => route.query,
  () => {
    loadCurrent();
  }
);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <NCard title="人工核对" :bordered="false" size="small">
      <template #header-extra>
        <NSpace>
          <NButton :disabled="currentIndex <= 0" @click="move(-1)">上一条</NButton>
          <NButton :disabled="currentIndex >= records.length - 1" @click="move(1)">下一条</NButton>
          <NButton :loading="loading" @click="loadCurrent">刷新</NButton>
        </NSpace>
      </template>

      <NEmpty v-if="!loading && records.length === 0" key="empty" description="当前没有待核对记录" style="padding: 80px 0" />

      <NSpin v-else key="content" :show="loading">
        <div style="display: grid; grid-template-columns: minmax(460px, 1.05fr) minmax(440px, 0.95fr); gap: 16px">
          <!-- 图片区 -->
          <NCard>
            <template #header>
              <NSpace align="center">
                <NTag type="info" round>{{ currentIndex + 1 }} / {{ records.length }}</NTag>
                <NText depth="3">记录 ID {{ currentRecord?.id || '-' }} · 图片 ID {{ imageId || '-' }}</NText>
              </NSpace>
            </template>
            <template #header-extra>
              <NSpace>
                <NButton size="small" @click="openEditor">裁剪/旋转</NButton>
                <NButton size="small" type="primary" @click="reocr">重新 OCR</NButton>
              </NSpace>
            </template>
            <div style="text-align: center; min-height: 300px">
              <img v-if="imageBase64" key="image" :src="imageBase64" style="max-width: 100%; max-height: 60vh" />
              <NEmpty v-else key="empty" description="暂无图片" />
            </div>
          </NCard>

          <!-- 表单区 -->
          <NCard title="记录信息">
            <NForm label-placement="top" :show-feedback="false">
              <NGrid :cols="2" :x-gap="12">
                <NGi v-for="field in fields" :key="field.key">
                  <NFormItem :label="field.label">
                    <NSelect
                      v-if="field.type === 'select'"
                      :value="(form[field.key] as string | number | null)"
                      :options="getOptions(field)"
                      clearable
                      filterable
                      @update:value="
                        (v: string | number | null) => {
                          form[field.key] = v;
                          onFieldChange(field.key);
                        }
                      "
                    />
                    <NDatePicker
                      v-else-if="field.type === 'date'"
                      :formatted-value="(form[field.key] as string | null)"
                      type="date"
                      value-format="yyyy-MM-dd"
                      style="width: 100%"
                      :is-date-disabled="(ts: number) => ts > Date.now()"
                      @update:formatted-value="
                        (v: string | null) => {
                          form[field.key] = v;
                          onFieldChange(field.key);
                        }
                      "
                    />
                    <NInputNumber
                      v-else-if="field.type === 'number'"
                      :value="(form[field.key] as number | null)"
                      :precision="2"
                      :step="0.01"
                      :readonly="field.readonly"
                      :show-button="!field.readonly"
                      style="width: 100%"
                      @update:value="
                        (v: number | null) => {
                          form[field.key] = v;
                          onFieldChange(field.key);
                        }
                      "
                    />
                    <NInput
                      v-else
                      :value="String(form[field.key] || '')"
                      :readonly="field.readonly"
                      @change="onFieldChange(field.key)"
                    />
                  </NFormItem>
                </NGi>
              </NGrid>
            </NForm>

            <NAlert v-if="currentRecord?.review_note" type="warning" style="margin: 12px 0">
              {{ currentRecord.review_note }}
            </NAlert>

            <NSpace justify="end" style="margin-top: 12px">
              <NPopconfirm @positive-click="deleteCurrent">
                <template #trigger><NButton type="error">删除图片和记录</NButton></template>
                确认删除当前图片及关联记录？
              </NPopconfirm>
              <NButton type="primary" :loading="saving" @click="save">保存修改</NButton>
              <NButton type="success" :loading="saving" @click="markReviewed">已核对</NButton>
            </NSpace>
          </NCard>
        </div>
      </NSpin>
    </NCard>

    <!-- 图片编辑器弹窗 -->
    <NModal v-model:show="editorOpen" preset="card" title="图片编辑" style="width: 95vw; height: 92vh">
      <template #header-extra>
        <NSpace align="center">
          <NButton size="small" @click="rotate(-90)">左转</NButton>
          <NButton size="small" @click="rotate(90)">右转</NButton>
          <NButton size="small" @click="resetEditor">重置</NButton>
          <NButton size="small" type="primary" @click="applyCrop">应用裁剪</NButton>
          <NButton size="small" type="success" @click="saveImageRaw">保存图片</NButton>
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
