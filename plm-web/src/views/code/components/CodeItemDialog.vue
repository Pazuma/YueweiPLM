<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { CodeItem, CodeItemPayload } from '@/api/modules/code'

const props = defineProps<{ modelValue: boolean; item?: CodeItem | null; loading?: boolean }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'save', payload: CodeItemPayload): void }>()
const form = reactive<CodeItemPayload>({ codeType: 'color', codeValue: '', codeName: '', sortOrder: 0 })

watch(() => [props.modelValue, props.item], () => {
  if (!props.modelValue) return
  form.codeType = props.item?.codeType || 'color'
  form.codeValue = props.item?.codeValue || ''
  form.codeName = props.item?.codeName || ''
  form.sortOrder = props.item?.sortOrder || 0
}, { immediate: true })
</script>

<template>
  <el-dialog :model-value="modelValue" :title="item ? '编辑编码' : '新增编码'" width="520px" @close="emit('update:modelValue', false)">
    <el-form label-position="top">
      <div class="code-form-grid">
        <el-form-item label="编码类型"><el-input v-model="form.codeType" disabled /></el-form-item>
        <el-form-item label="编码值"><el-input v-model="form.codeValue" :disabled="Boolean(item)" placeholder="例如 02" /></el-form-item>
      </div>
      <el-form-item label="编码名称"><el-input v-model="form.codeName" placeholder="例如 Negro" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!form.codeValue.trim() || !form.codeName.trim()" @click="emit('save', { ...form })">保存编码</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.code-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; } @media (max-width: 560px) { .code-form-grid { grid-template-columns: 1fr; } }</style>
