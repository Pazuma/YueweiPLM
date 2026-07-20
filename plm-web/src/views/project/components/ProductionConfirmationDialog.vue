<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { confirmProductionColors, confirmProductionRoutes, getBomWorkbench, getProductionConfirmation, getProjectBoms, type ProductionConfirmation } from '@/api/modules/bom'
import { getProjectProcessRoutes, type ProcessRouteVO } from '@/api/modules/process'
import type { BomRoute, BomWorkbench, ProductionRouteSelection } from '@/types/bom'

const props = defineProps<{ modelValue: boolean; projectId: number; mode: 'operations' | 'colors' }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'confirmed'): void }>()

const loading = ref(false)
const workbenches = ref<BomWorkbench[]>([])
const processRoutes = ref<ProcessRouteVO[]>([])
const confirmation = ref<ProductionConfirmation | null>(null)
const selectedRouteIds = ref<Record<number, number>>({})
const selectedOperationIds = ref<Record<number, number[]>>({})
const selectedColors = ref<string[]>([])

type RouteCandidate = BomRoute & {
  productBomId: number
  versionNo: string
  bomStatus: string
}

type RouteConfirmRow = {
  processId: number
  productBomId: number
  productBomRouteId: number
  operationProcessIds: number[]
}

const routes = computed<RouteCandidate[]>(() => workbenches.value.flatMap((bom) =>
  bom.routes.map((route) => ({
    ...route,
    productBomId: bom.productBomId,
    versionNo: bom.versionNo,
    bomStatus: bom.status
  }))
))

const routeGroups = computed(() => processRoutes.value.map((process) => ({
  process,
  candidates: routes.value.filter((route) => route.processId === process.processId)
})).filter((group) => group.candidates.length))

const formalRouteSelections = computed<ProductionRouteSelection[]>(() => confirmation.value?.routeSelections || [])

const formalRoutes = computed<RouteCandidate[]>(() => formalRouteSelections.value
  .map((selection) => routes.value.find((route) => route.productBomRouteId === selection.productBomRouteId))
  .filter((route): route is RouteCandidate => Boolean(route)))

const colorOptions = computed(() => {
  const values = new Map<number, { codeItemId: number; codeValue: string; codeName: string; route: RouteCandidate }>()
  formalRoutes.value.forEach((route) => (route.colorItems || []).forEach((color) => {
    if (!values.has(color.codeItemId)) values.set(color.codeItemId, { ...color, route })
  }))
  return [...values.values()]
})

async function load() {
  loading.value = true
  try {
    const [boms, processes, current] = await Promise.all([
      getProjectBoms(props.projectId),
      getProjectProcessRoutes(props.projectId),
      getProductionConfirmation(props.projectId)
    ])
    workbenches.value = await Promise.all(
      boms.filter((bom) => bom.bomType !== 'test' && bom.status !== 'archived')
        .map((bom) => getBomWorkbench(bom.productBomId))
    )
    processRoutes.value = processes
    confirmation.value = current
    initializeSelections(current)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '投产方案加载失败')
  } finally {
    loading.value = false
  }
}

function initializeSelections(current: ProductionConfirmation) {
  const routeByProcess = new Map((current.routeSelections || []).map((selection) => [selection.processId, selection]))
  const routeValues: Record<number, number> = {}
  const operationValues: Record<number, number[]> = {}
  for (const group of routeGroups.value) {
    const existing = routeByProcess.get(group.process.processId)
    const defaultRoute = existing?.productBomRouteId || group.candidates[0]?.productBomRouteId
    if (defaultRoute) routeValues[group.process.processId] = defaultRoute
    operationValues[group.process.processId] = existing?.operationProcessIds?.length
      ? [...existing.operationProcessIds]
      : group.process.operations.map((operation) => operation.processId)
  }
  selectedRouteIds.value = routeValues
  selectedOperationIds.value = operationValues
  const confirmedColorNames = new Set(current.colors || [])
  selectedColors.value = confirmedColorNames.size
    ? colorOptions.value.filter((value) => confirmedColorNames.has(value.codeName) || confirmedColorNames.has(value.codeValue)).map((value) => value.codeValue)
    : colorOptions.value.map((value) => value.codeValue)
}

async function confirm() {
  loading.value = true
  try {
    if (props.mode === 'operations') {
      const payloadRoutes = routeGroups.value.map((group) => {
        const productBomRouteId = selectedRouteIds.value[group.process.processId]
        const route = group.candidates.find((candidate) => candidate.productBomRouteId === productBomRouteId)
        const operationProcessIds = selectedOperationIds.value[group.process.processId] || []
        return route ? {
          processId: group.process.processId,
          productBomId: route.productBomId,
          productBomRouteId: route.productBomRouteId!,
          operationProcessIds
        } : null
      }).filter((route): route is RouteConfirmRow => Boolean(route))
      if (!payloadRoutes.length || payloadRoutes.some((route) => !route.operationProcessIds.length)) {
        ElMessage.warning('请先为每条路线选择正式 BOM，并至少选择一道投产工序')
        return
      }
      await confirmProductionRoutes(props.projectId, { routes: payloadRoutes, remark: '敲定工序：确认正式 BOM 与投产工序' })
      ElMessage.success('正式 BOM 与投产工序已确认')
    } else {
      const colors = colorOptions.value.filter((value) => selectedColors.value.includes(value.codeValue)).map((value) => ({
        codeItemId: value.codeItemId,
        colorCode: value.codeValue,
        colorName: value.codeName,
        productBomId: value.route.productBomId,
        productBomRouteId: value.route.productBomRouteId!
      }))
      if (!colors.length) {
        ElMessage.warning('请至少选择一个批量投产颜色')
        return
      }
      const result = await confirmProductionColors(props.projectId, { colors })
      ElMessage.success(result.createdSkuCount ? `已创建 ${result.createdSkuCount} 个 SKU` : '正式投产颜色已确认')
    }
    emit('confirmed')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '投产方案确认失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (value) => { if (value) void load() }, { immediate: true })
</script>

<template>
  <el-dialog :model-value="modelValue" :title="mode === 'operations' ? '敲定工序：确认正式 BOM 与投产工序' : '确认批量投产颜色'" width="860px" destroy-on-close @close="emit('update:modelValue', false)">
    <div v-loading="loading" class="production-confirmation">
      <template v-if="mode === 'operations'">
        <article v-for="group in routeGroups" :key="group.process.processId" class="route-card">
          <header>
            <strong>{{ group.process.processName }}</strong>
            <span>{{ group.candidates.length }} 份候选 BOM</span>
          </header>
          <section>
            <p class="section-title">正式 BOM</p>
            <el-radio-group v-model="selectedRouteIds[group.process.processId]" class="candidate-list">
              <label v-for="route in group.candidates" :key="route.productBomRouteId" class="candidate-row">
                <el-radio :value="route.productBomRouteId" />
                <strong>候选 BOM {{ route.versionNo }}</strong>
                <span>{{ route.items.length }} 项物料</span>
                <span>{{ route.costSnapshot?.currencyCode || 'CNY' }} {{ route.costSnapshot?.totalCost ?? '--' }}</span>
              </label>
            </el-radio-group>
          </section>
          <section>
            <p class="section-title">正式投产工序</p>
            <el-checkbox-group v-model="selectedOperationIds[group.process.processId]" class="operation-list">
              <label v-for="operation in group.process.operations" :key="operation.processId" class="operation-row">
                <el-checkbox :value="operation.processId" />
                <span>{{ operation.sequenceNo }}</span>
                <strong>{{ operation.processName }}</strong>
                <code>{{ operation.businessOperationCode || operation.processCode }}</code>
              </label>
            </el-checkbox-group>
          </section>
        </article>
        <el-empty v-if="!routeGroups.length" description="当前项目还没有可敲定的候选 BOM 路线" />
      </template>
      <template v-else>
        <el-alert title="只展示已在“敲定工序”中确认正式 BOM 和投产工序的路线颜色。" type="info" show-icon :closable="false" />
        <el-checkbox-group v-model="selectedColors" class="color-list">
          <label v-for="item in colorOptions" :key="item.codeItemId" class="color-row">
            <el-checkbox :value="item.codeValue" />
            <strong>{{ item.codeValue }} · {{ item.codeName }}</strong>
            <span>{{ item.route.routeName }}</span>
            <code>{{ item.route.routeCode }}</code>
            <span>{{ item.route.costSnapshot?.currencyCode || 'CNY' }} {{ item.route.costSnapshot?.totalCost ?? '--' }}</span>
          </label>
        </el-checkbox-group>
        <el-empty v-if="!colorOptions.length" description="请先在敲定工序中确认正式 BOM 与投产工序" />
        <div class="selection-count">已选择 {{ selectedColors.length }} 个颜色</div>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :data-test="mode === 'colors' ? 'confirm-production-colors' : 'confirm-production-operations'" type="primary" :loading="loading" @click="confirm">{{ mode === 'colors' ? '确认批量投产并创建 SKU' : '确认正式 BOM 与投产工序' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.production-confirmation { min-height: 240px; }
.route-card { padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-bg-color); }
.route-card + .route-card { margin-top: 12px; }
.route-card header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.route-card header span, .section-title { color: var(--plm-color-text-secondary); font-size: 13px; }
.section-title { margin: 10px 0 6px; }
.candidate-list, .operation-list, .color-list { display: grid; border-top: 1px solid var(--el-border-color-lighter); }
.candidate-row, .operation-row, .color-row { display: grid; align-items: center; gap: 10px; min-height: 48px; border-bottom: 1px solid var(--el-border-color-lighter); }
.candidate-row { grid-template-columns: 32px minmax(150px, 1fr) 100px 120px; }
.operation-row { grid-template-columns: 32px 56px minmax(140px, 1fr) minmax(120px, .8fr); }
.color-row { grid-template-columns: 32px minmax(100px, .7fr) minmax(140px, 1fr) 110px 110px; }
.selection-count { margin-top: 12px; font-weight: 600; }
code { color: var(--plm-color-text-secondary); font-size: 12px; }
@media (max-width: 680px) {
  .candidate-row, .operation-row, .color-row { grid-template-columns: 32px 1fr; padding: 8px 0; }
  .candidate-row > :not(:first-child), .operation-row > :not(:first-child), .color-row > :not(:first-child) { grid-column: 2; }
}
</style>
