<template>
  <n-popover
    v-model:show="open"
    trigger="click"
    placement="bottom-start"
    :show-arrow="false"
    content-style="padding:8px"
  >
    <template #trigger>
      <n-input
        class="month-picker-input"
        :value="modelValue"
        :placeholder="placeholder"
        :clearable="clearable"
        readonly
        :style="{ width }"
        @clear.stop="clearValue"
      />
    </template>
    <n-date-picker
      panel
      type="month"
      value-format="yyyy-MM"
      :actions="null"
      :formatted-value="modelValue || null"
      @update:formatted-value="pickMonth"
    />
  </n-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  clearable?: boolean
  width?: string
}>(), {
  placeholder: '选择月份',
  clearable: true,
  width: '140px',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const open = ref(false)

function pickMonth(value: string | null) {
  emit('update:modelValue', value || '')
  open.value = false
}

function clearValue() {
  emit('update:modelValue', '')
  open.value = false
}
</script>

<style scoped>
.month-picker-input {
  cursor: pointer;
}

.month-picker-input :deep(.n-input__input-el) {
  cursor: pointer;
}
</style>
