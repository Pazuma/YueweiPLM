<script setup lang="ts">
import { DocumentCopy, Lock, Plus, Promotion, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import {
  confirmTestBom,
  copyBomVersion,
  createProjectBom,
  freezeBom,
  getBomWorkbench,
  getProjectBoms,
  publishBom,
  recalculateBomCosts,
  saveBomRoutes,
  submitBomReview,
  type ProductBomVO
} from '@/api/modules/bom'
import type { BomRoute, BomWorkbench } from '@/types/bom'

import BomImportDialog from './BomImportDialog.vue'
import BomRouteEditor from './BomRouteEditor.vue'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ (event: 'changed'): void }>()

const mode = ref<'test' | 'formal'>('formal')
const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const boms = ref<ProductBomVO[]>([])
const selectedBomId = ref<number | null>(null)
const workbench = ref<BomWorkbench | null>(null)
const importVisible = ref(false)
const routeEditorVisible = ref(false)

const formalBoms = computed(() => boms.value.filter((bom) => bom.bomType !== 'test'))
const selectedBom = computed(() => boms.value.find((bom) => bom.productBomId === selectedBomId.value) || null)
const isReadOnly = computed(() => ['released', 'archived'].includes(workbench.value?.status || ''))
const isFrozen = computed(() =>
  Boolean((selectedBom.value as ProductBomVO & { frozenFlag?: number })?.frozenFlag)
  || selectedBom.value?.status === 'frozen'
  || isReadOnly.value
)

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'BOM 数据加载失败'
}

async function load(preferredBomId?: number) {
  loading.value = true
  loadError.value = ''
  try {
    boms.value = await getProjectBoms(props.projectId)
    const candidates = boms.value.filter((bom) => bom.bomType !== 'test')
    selectedBomId.value = candidates.some((bom) => bom.productBomId === preferredBomId)
      ? preferredBomId || null
      : candidates[0]?.productBomId || null
    workbench.value = selectedBomId.value ? await getBomWorkbench(selectedBomId.value) : null
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function changeVersion() {
  workbench.value = selectedBomId.value ? await getBomWorkbench(selectedBomId.value) : null
}

async function createFormalBom() {
  const next = formalBoms.value.length + 1
  const result = await createProjectBom(props.projectId, {
    bomName: `正式 BOM V${next}`,
    bomType: 'mbom',
    versionNo: `V${next}`,
    remark: '工作台创建'
  })
  await load(result.productBomId)
  emit('changed')
}

async function saveRoutes(routes: BomRoute[]) {
  if (!selectedBomId.value) return
  actionLoading.value = true
  try {
    await saveBomRoutes(selectedBomId.value, routes)
    routeEditorVisible.value = false
    await changeVersion()
    emit('changed')
    ElMessage.success('工艺路线与 BOM 已保存')
  } finally {
    actionLoading.value = false
  }
}

async function lifecycle(action: 'cost' | 'review' | 'freeze' | 'publish' | 'copy') {
  if (!selectedBomId.value || !workbench.value) return
  actionLoading.value = true
  try {
    if (action === 'cost') await recalculateBomCosts(selectedBomId.value, workbench.value.routes)
    if (action === 'review') await submitBomReview(selectedBomId.value)
    if (action === 'freeze') await freezeBom(selectedBomId.value)
    if (action === 'publish') await publishBom(selectedBomId.value)
    if (action === 'copy') {
      const colors = workbench.value.routes.flatMap((route) => route.colors)
      await copyBomVersion(selectedBomId.value, { versionNo: `${workbench.value.versionNo}-COPY`, selectedColors: colors })
    }
    await load(selectedBomId.value)
    emit('changed')
    ElMessage.success('BOM 状态已更新')
  } finally {
    actionLoading.value = false
  }
}

async function confirmTest() {
  await confirmTestBom(props.projectId)
  ElMessage.success('测试 BOM 成本已确认')
  emit('changed')
}

watch(() => props.projectId, () => load(), { immediate: true })
</script>

<template>
  <section class="bom-workbench" v-loading="loading">
    <header class="bom-workbench__header">
      <div>
        <h4>BOM 工作台</h4>
        <p>在项目流程中维护测试成本、正式版本、路线颜色和路线 BOM。</p>
      </div>
      <div class="command-row">
        <el-tooltip content="刷新"><el-button :icon="Refresh" circle @click="load(selectedBomId || undefined)" /></el-tooltip>
        <el-button data-test="bom-create" type="primary" :icon="Plus" @click="createFormalBom">新建正式 BOM</el-button>
      </div>
    </header>

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />

    <div class="mode-switch" role="tablist" aria-label="BOM 类型">
      <button :class="{ active: mode === 'test' }" role="tab" @click="mode = 'test'">测试 BOM</button>
      <button :class="{ active: mode === 'formal' }" role="tab" @click="mode = 'formal'">正式 BOM</button>
    </div>

    <div v-if="mode === 'test'" class="mode-panel">
      <div class="mode-panel__title">
        <div><strong>测试 BOM</strong><span>确认后只保存单一测试总成本</span></div>
        <div class="command-row">
          <el-button :icon="Upload" @click="importVisible = true">导入 XLSX</el-button>
          <el-button type="primary" :icon="Promotion" @click="confirmTest">确认测试成本</el-button>
        </div>
      </div>
      <el-empty description="测试 BOM 明细通过导入或人工维护后在此显示" />
    </div>

    <div v-else-if="formalBoms.length" class="mode-panel">
      <div class="formal-toolbar">
        <el-select v-model="selectedBomId" aria-label="选择正式 BOM 版本" @change="changeVersion">
          <el-option v-for="bom in formalBoms" :key="bom.productBomId" :label="`${bom.versionNo} · ${bom.bomName}`" :value="bom.productBomId" />
        </el-select>
        <el-tag v-if="isFrozen" type="warning" effect="light">已冻结</el-tag>
        <el-tag v-else-if="workbench" effect="light">{{ workbench.status }}</el-tag>
        <div class="command-row command-row--push">
          <el-button :icon="Upload" :disabled="isFrozen" @click="importVisible = true">导入 XLSX</el-button>
          <el-button data-test="bom-edit" :disabled="isFrozen" @click="routeEditorVisible = true">维护路线与 BOM</el-button>
          <el-button data-test="bom-item-add" :disabled="isFrozen" @click="routeEditorVisible = true">添加明细</el-button>
        </div>
      </div>

      <div v-if="workbench?.routes.length" class="route-list">
        <article v-for="route in workbench.routes" :key="route.routeCode" class="route-row">
          <div class="route-row__identity"><strong>{{ route.routeName }}</strong><code>{{ route.routeCode }}</code></div>
          <div class="color-list"><el-tag v-for="color in route.colors" :key="color" size="small">{{ color }}</el-tag></div>
          <span>{{ route.items.length }} 项物料</span>
          <strong>{{ route.costSnapshot ? `${route.costSnapshot.currencyCode} ${route.costSnapshot.totalCost}` : '待计算' }}</strong>
        </article>
      </div>
      <el-empty v-else description="当前正式版本尚未维护工艺路线" />

      <footer class="lifecycle-bar">
        <el-button :loading="actionLoading" :disabled="isReadOnly" @click="lifecycle('cost')">刷新成本</el-button>
        <el-button :icon="Promotion" :disabled="isReadOnly" @click="lifecycle('review')">提交审核</el-button>
        <el-button :icon="Lock" :disabled="isFrozen" @click="lifecycle('freeze')">冻结</el-button>
        <el-button type="primary" :disabled="isReadOnly" @click="lifecycle('publish')">发布</el-button>
        <el-button :icon="DocumentCopy" @click="lifecycle('copy')">复制版本</el-button>
      </footer>
    </div>
    <el-empty v-else description="当前项目还没有 BOM" />

    <BomImportDialog v-model="importVisible" :product-id="projectId" :bom-id="selectedBomId" @committed="load(selectedBomId || undefined)" />
    <BomRouteEditor v-if="workbench" v-model="routeEditorVisible" :routes="workbench.routes" :loading="actionLoading" @save="saveRoutes" />
  </section>
</template>

<style scoped>
.bom-workbench { min-width: 0; }
.bom-workbench__header, .mode-panel__title, .formal-toolbar, .command-row, .lifecycle-bar { display: flex; align-items: center; gap: 10px; }
.bom-workbench__header { justify-content: space-between; margin-bottom: 14px; }
.bom-workbench__header h4, .bom-workbench__header p { margin: 0; }
.bom-workbench__header p, .mode-panel__title span { color: var(--plm-color-text-secondary); font-size: 13px; }
.mode-panel { margin-top: 14px; }
.mode-switch { display: inline-grid; grid-template-columns: 1fr 1fr; padding: 3px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-fill-color-light); }
.mode-switch button { min-width: 108px; padding: 6px 12px; border: 0; border-radius: 4px; color: var(--plm-color-text-secondary); background: transparent; cursor: pointer; }
.mode-switch button.active { color: var(--el-color-primary); background: var(--el-bg-color); box-shadow: 0 1px 3px rgb(0 0 0 / 10%); }
.mode-panel__title { justify-content: space-between; margin-bottom: 12px; }
.mode-panel__title div:first-child { display: grid; gap: 3px; }
.formal-toolbar { flex-wrap: wrap; margin-bottom: 12px; }
.formal-toolbar :deep(.el-select) { width: 260px; }
.command-row--push { margin-left: auto; }
.route-list { border-top: 1px solid var(--el-border-color-lighter); }
.route-row { display: grid; grid-template-columns: minmax(170px, 1.2fr) minmax(180px, 1fr) 100px 110px; align-items: center; gap: 12px; min-height: 58px; border-bottom: 1px solid var(--el-border-color-lighter); }
.route-row__identity { display: grid; gap: 3px; }
.route-row code { color: var(--plm-color-text-secondary); font-size: 12px; }
.color-list { display: flex; flex-wrap: wrap; gap: 5px; }
.lifecycle-bar { justify-content: flex-end; flex-wrap: wrap; margin-top: 14px; }
@media (max-width: 760px) {
  .bom-workbench__header, .mode-panel__title { align-items: flex-start; flex-direction: column; }
  .route-row { grid-template-columns: 1fr; padding: 10px 0; }
  .command-row--push { width: 100%; margin-left: 0; flex-wrap: wrap; }
}
</style>
