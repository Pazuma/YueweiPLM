<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { confirmProductionColors, confirmProductionOperations, getBomWorkbench, getProductionConfirmation, getProjectBoms } from '@/api/modules/bom'
import { getProjectProcessRoutes, type ProcessRouteVO } from '@/api/modules/process'
import type { BomRoute, BomWorkbench } from '@/types/bom'

const props = defineProps<{ modelValue: boolean; projectId: number; mode: 'operations' | 'colors' }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'confirmed'): void }>()
const loading = ref(false)
const workbenches = ref<BomWorkbench[]>([])
const processRoutes = ref<ProcessRouteVO[]>([])
const selectedRouteId = ref<number | null>(null)
const selectedOperationIds = ref<number[]>([])
const selectedColors = ref<string[]>([])

type RouteCandidate = BomRoute & { productBomId: number; versionNo: string }
const routes = computed<RouteCandidate[]>(() => workbenches.value.flatMap(bom => bom.routes.map(route => ({ ...route, productBomId: bom.productBomId, versionNo: bom.versionNo }))))
const selectedRoute = computed(() => routes.value.find(route => route.productBomRouteId === selectedRouteId.value) || null)
const selectedProcessRoute = computed(() => processRoutes.value.find(route => route.processId === selectedRoute.value?.processId) || null)
const colorOptions = computed(() => {
  const values = new Map<string, RouteCandidate>()
  routes.value.forEach(route => route.colors.forEach(color => { if (!values.has(color)) values.set(color, route) }))
  return [...values.entries()].map(([colorName, route]) => ({ colorName, route }))
})

async function load() {
  loading.value = true
  try {
    const [boms, processes, confirmation] = await Promise.all([
      getProjectBoms(props.projectId), getProjectProcessRoutes(props.projectId), getProductionConfirmation(props.projectId)
    ])
    workbenches.value = await Promise.all(boms.filter(bom => bom.bomType !== 'test' && bom.status !== 'archived').map(bom => getBomWorkbench(bom.productBomId)))
    processRoutes.value = processes
    selectedRouteId.value = routes.value[0]?.productBomRouteId || null
    selectedOperationIds.value = confirmation.operationProcessIds || []
    selectedColors.value = confirmation.colors?.length ? [...confirmation.colors] : colorOptions.value.map(value => value.colorName)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '投产方案加载失败') }
  finally { loading.value = false }
}

watch(() => props.modelValue, value => { if (value) void load() }, { immediate: true })
watch(selectedRouteId, () => {
  const operationIds = selectedProcessRoute.value?.operations.map(operation => operation.processId) || []
  if (!selectedOperationIds.value.some(id => operationIds.includes(id))) selectedOperationIds.value = [...operationIds]
})

async function confirm() {
  loading.value = true
  try {
    if (props.mode === 'operations') {
      if (!selectedRoute.value || !selectedOperationIds.value.length) return ElMessage.warning('请至少选择一道投产工序')
      await confirmProductionOperations(props.projectId, { productBomRouteId: selectedRoute.value.productBomRouteId!, operationProcessIds: selectedOperationIds.value })
      ElMessage.success('投产工序已确认')
    } else {
      const colors = colorOptions.value.filter(value => selectedColors.value.includes(value.colorName)).map(value => ({
        colorName: value.colorName, productBomId: value.route.productBomId, productBomRouteId: value.route.productBomRouteId!
      }))
      if (!colors.length) return ElMessage.warning('请至少选择一个批量投产颜色')
      const result = await confirmProductionColors(props.projectId, { colors })
      ElMessage.success(result.createdSkuCount ? `已创建 ${result.createdSkuCount} 个 SKU` : '正式投产颜色已确认')
    }
    emit('confirmed')
    emit('update:modelValue', false)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '投产方案确认失败') }
  finally { loading.value = false }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="mode === 'operations' ? '敲定投产工序' : '确认批量投产颜色'" width="820px" destroy-on-close @close="emit('update:modelValue', false)">
    <div v-loading="loading" class="production-confirmation">
      <template v-if="mode === 'operations'">
        <el-select v-model="selectedRouteId" class="route-select" placeholder="选择工艺路线">
          <el-option v-for="route in routes" :key="route.productBomRouteId" :value="route.productBomRouteId" :label="`${route.routeName} · ${route.versionNo}`" />
        </el-select>
        <el-checkbox-group v-model="selectedOperationIds" class="operation-list">
          <label v-for="operation in selectedProcessRoute?.operations || []" :key="operation.processId" class="operation-row">
            <el-checkbox :value="operation.processId" />
            <span>{{ operation.sequenceNo }}</span><strong>{{ operation.processName }}</strong><code>{{ operation.businessOperationCode || operation.processCode }}</code>
          </label>
        </el-checkbox-group>
      </template>
      <template v-else>
        <el-alert title="默认选择全部已验证颜色。取消勾选的颜色不会创建 SKU。" type="info" show-icon :closable="false" />
        <el-checkbox-group v-model="selectedColors" class="color-list">
          <label v-for="item in colorOptions" :key="item.colorName" class="color-row">
            <el-checkbox :value="item.colorName" />
            <strong>{{ item.colorName }}</strong><span>{{ item.route.routeName }}</span><code>{{ item.route.routeCode }}</code><span>{{ item.route.costSnapshot?.currencyCode || 'CNY' }} {{ item.route.costSnapshot?.totalCost ?? '--' }}</span>
          </label>
        </el-checkbox-group>
        <div class="selection-count">已选择 {{ selectedColors.length }} 个颜色</div>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :data-test="mode === 'colors' ? 'confirm-production-colors' : 'confirm-production-operations'" type="primary" :loading="loading" @click="confirm">{{ mode === 'colors' ? '确认批量投产并创建 SKU' : '确认投产工序' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.production-confirmation { min-height: 240px; }.route-select { width: min(420px, 100%); margin-bottom: 14px; }
.operation-list, .color-list { display: grid; border-top: 1px solid var(--el-border-color-lighter); }
.operation-row, .color-row { display: grid; grid-template-columns: 32px 56px minmax(140px, 1fr) minmax(120px, .8fr); align-items: center; gap: 10px; min-height: 48px; border-bottom: 1px solid var(--el-border-color-lighter); }
.color-row { grid-template-columns: 32px minmax(90px, .6fr) minmax(140px, 1fr) 110px 110px; }.selection-count { margin-top: 12px; font-weight: 600; }
code { color: var(--plm-color-text-secondary); font-size: 12px; }
@media (max-width: 680px) { .operation-row, .color-row { grid-template-columns: 32px 1fr; }.operation-row > :not(:first-child), .color-row > :not(:first-child) { grid-column: 2; } }
</style>
