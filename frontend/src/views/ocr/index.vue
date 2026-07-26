<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { ocrScan, fetchOcrStatus } from '@/service/api/business';
import type { OcrStatusResponse } from '@/service/api/types';

defineOptions({ name: 'Ocr' });

const message = useMessage();
const files = ref<File[]>([]);
const running = ref(false);
const total = ref(0);
const done = ref(0);
const logs = ref<string[]>([]);
const trackRecords = ref<{ record_id: number; file_name: string }[]>([]);
const folderInput = ref<HTMLInputElement | null>(null);
let timer: number | null = null;

type UploadFileLike = {
  file?: File | null;
};

type UploadChangePayload = {
  fileList: UploadFileLike[];
};

const statusLabel: Record<string, string> = {
  pending: '待扫描',
  processing: '识别中',
  done: '已完成',
  duplicate: '重复',
  error: '错误'
};

function addFiles(list: File[]) {
  const existed = new Set(files.value.map(f => `${f.name}-${f.size}-${f.lastModified}`));
  for (const file of list) {
    if (!file.type.startsWith('image/')) continue;
    const key = `${file.name}-${file.size}-${file.lastModified}`;
    if (!existed.has(key)) {
      files.value.push(file);
      existed.add(key);
    }
  }
}

function onUploadChange({ fileList }: UploadChangePayload) {
  addFiles(fileList.map(item => item.file).filter((file): file is File => file instanceof File));
}

function onFolderChange(event: Event) {
  const input = event.target as HTMLInputElement;
  addFiles(Array.from(input.files || []));
  input.value = '';
}

function clear() {
  files.value = [];
  logs.value = [];
  trackRecords.value = [];
  total.value = 0;
  done.value = 0;
  running.value = false;
  stopPoll();
}

function stopPoll() {
  if (timer) window.clearInterval(timer);
  timer = null;
}

async function startOCR() {
  if (!files.value.length) return;
  running.value = true;
  total.value = files.value.length;
  done.value = 0;
  logs.value = [];
  trackRecords.value = [];

  for (const file of files.value) {
    const form = new FormData();
    form.append('image', file);
    try {
      const data = await ocrScan(form);
      trackRecords.value.push({ record_id: data.id || data.record_id, file_name: file.name });
      logs.value.unshift(
        `${file.name} 已入库：记录 ${data.id || data.record_id}，图片 ${data.image_id}，状态 ${statusLabel[data.status] || data.status}`
      );
    } catch (error: unknown) {
      const err = error as Error;
      logs.value.unshift(`${file.name} 上传失败：${err?.message || '未知错误'}`);
    } finally {
      done.value += 1;
    }
  }

  running.value = false;
  files.value = [];
  message.success(`扫描入队完成：${done.value}/${total.value}`);
  startPoll();
}

function startPoll() {
  stopPoll();
  if (!trackRecords.value.length) return;
  timer = window.setInterval(async () => {
    const remain: { record_id: number; file_name: string }[] = [];
    for (const item of trackRecords.value) {
      try {
        const data: OcrStatusResponse = await fetchOcrStatus(item.record_id);
        const status = data.ocr_status || '';
        if (status === 'pending' || status === 'processing') {
          remain.push(item);
        } else {
          const extra = data.task?.error_message ? `，原因：${data.task.error_message}` : '';
          logs.value.unshift(`${item.file_name} 状态更新：${statusLabel[status] || status}${extra}`);
        }
      } catch {
        remain.push(item);
      }
    }
    trackRecords.value = remain;
    if (!remain.length) stopPoll();
  }, 1800);
}

onBeforeUnmount(stopPoll);
</script>

<template>
  <div class="flex-col-stretch gap-16px overflow-auto">
    <NCard title="OCR 扫描" :bordered="false" size="small">
      <template #header-extra>
        <NSpace wrap>
          <NButton @click="clear">清空</NButton>
          <NButton type="primary" :disabled="!files.length || running" :loading="running" @click="startOCR">
            开始扫描
          </NButton>
        </NSpace>
      </template>

      <!-- 上传区域 -->
      <NUpload multiple accept="image/*" :default-upload="false" :show-file-list="false" @change="onUploadChange">
        <NUploadDragger>
          <div style="margin-bottom: 6px; font-size: 18px; font-weight: 800">拖拽图片到这里，或点击选择图片</div>
          <NText depth="3">支持多张图片；也可以选择本地文件夹批量导入。</NText>
        </NUploadDragger>
      </NUpload>

      <NSpace wrap style="margin-top: 12px">
        <NButton @click="folderInput?.click()">选择文件夹</NButton>
        <NTag type="info">待上传 {{ files.length }} 张</NTag>
      </NSpace>
      <input ref="folderInput" type="file" webkitdirectory hidden @change="onFolderChange" />

      <!-- 文件列表 -->
      <NSpace v-if="files.length" wrap style="margin-top: 12px">
        <NTag v-for="(file, i) in files" :key="`${file.name}-${file.size}-${i}`" closable @close="files.splice(i, 1)">
          {{ file.name }}
        </NTag>
      </NSpace>
    </NCard>

    <!-- 扫描进度 -->
    <NCard title="扫描进度" :bordered="false" size="small">
      <NProgress
        v-if="total"
        type="line"
        :percentage="Math.round((done / total) * 100)"
        :status="running ? 'default' : 'success'"
      />
      <NEmpty v-else description="暂无扫描任务" />

      <div style="margin-top: 16px; max-height: 360px; overflow: auto">
        <div
          v-for="(item, i) in logs"
          :key="i"
          style="padding: 10px 12px; border-bottom: 1px solid #e2e8f0; font-size: 13px"
        >
          {{ item }}
        </div>
      </div>
    </NCard>
  </div>
</template>
