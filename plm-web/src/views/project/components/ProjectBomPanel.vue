<script setup lang="ts">
import { DocumentCopy, Lock, Plus, Promotion, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
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
import { getProjectProcessRoutes, type ProcessRouteVO } from '@/api/modules/process'
import type { BomRoute, BomWorkbench } from '@/types/bom'

import BomImportDialog from './BomImportDialog.vue'
import BomRouteEditor from './BomRouteEditor.vue'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ (event: 'changed'): void }>()

const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const boms = ref<ProductBomVO[]>([])
const processRoutes = ref<ProcessRouteVO[]>([])
const selectedBomId = ref<number | null>(null)
const workbench = ref<BomWorkbench | null>(null)
const importVisible = ref(false)
const routeEditorVisible = ref(false)
const createVisible = ref(false)
const createForm = reactive({
  bomName: '',
  bomType: 'mbom',
  versionNo: '',
  processId: null as number | null,
  remark: ''
})

const candidateBoms = computed(() => boms.value.filter((bom) => bom.bomType !== 'test'))
const selectedBom = computed(() => boms.value.find((bom) => bom.productBomId === selectedBomId.value) || null)
const isReadOnly = computed(() => ['released', 'archived'].includes(workbench.value?.status || ''))
const isFrozen = computed(() =>
  Boolean(selectedBom.value?.frozenFlag)
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
    const [projectBoms, routes] = await Promise.all([
      getProjectBoms(props.projectId),
      getProjectProcessRoutes(props.projectId)
    ])
    boms.value = projectBoms
    processRoutes.value = routes
    const candidates = projectBoms.filter((bom) => bom.bomType !== 'test')
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

function selectBom(row: ProductBomVO) {
  selectedBomId.value = row.productBomId
  void changeVersion()
}

function openCreateBom() {
  const next = candidateBoms.value.length + 1
  createForm.bomName = `候选 BOM V${next}`
  createForm.versionNo = `V${next}`
  createForm.bomType = 'mbom'
  createForm.processId = null
  createForm.remark = '工作台新建候选 BOM'
  createVisible.value = true
}

async function submitCreateBom() {
  if (!createForm.processId) {
    ElMessage.warning('请选择关联工艺路线')
    return
  }
  actionLoading.value = true
  try {
    const result = await createProjectBom(props.projectId, {
      bomName: createForm.bomName,
      bomType: createForm.bomType,
      versionNo: createForm.versionNo,
      processId: createForm.processId,
      remark: createForm.remark
    })
    createVisible.value = false
    await load(result.productBomId)
    emit('changed')
    ElMessage.success('候选 BOM 已创建')
  } finally {
    actionLoading.value = false
  }
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

watch(() => props.projectId, () => load(), { immediate: true })
</script>

<template>
  <section class="bom-workbench" v-loading="loading">
    <header class="bom-workbench__header">
      <div>
        <h4>BOM 工作台</h4>
        <p>在项目流程中维护候选 BOM、关联工艺路线、路线成本和后续正式确认资料。</p>
      </div>
      <div class="command-row">
        <el-tooltip content="刷新"><el-button :icon="Refresh" circle @click="load(selectedBomId || undefined)" /></el-tooltip>
        <el-button data-test="bom-create" type="primary" :icon="Plus" @click="openCreateBom">新建 BOM</el-button>
      </div>
    </header>

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />

    <div v-if="candidateBoms.length" class="mode-panel">
      <div class="formal-toolbar">
        <el-select v-model="selectedBomId" aria-label="选择候选 BOM 版本" @change="changeVersion">
          <el-option v-for="bom in candidateBoms" :key="bom.productBomId" :label="`${bom.versionNo} · ${bom.bomName}`" :value="bom.productBomId" />
        </el-select>
        <el-tag v-if="selectedBom?.currentFormal" type="success" effect="light">当前正式</el-tag>
        <el-tag v-else effect="light">候选</el-tag>
        <el-tag v-if="isFrozen" type="warning" effect="light">已冻结</el-tag>
        <el-tag v-if="selectedBom?.routeName" effect="plain">{{ selectedBom.routeName }}</el-tag>
        <div class="command-row command-row--push">
          <el-button :icon="Upload" :disabled="!selectedBomId || isFrozen" @click="importVisible = true">导入 XLSX</el-button>
          <el-button data-test="bom-edit" :disabled="!selectedBomId || isFrozen" @click="routeEditorVisible = true">维护路线与 BOM</el-button>
          <el-button data-test="bom-item-add" :disabled="!selectedBomId || isFrozen" @click="routeEditorVisible = true">添加明细</el-button>
        </div>
      </div>

      <el-table :data="candidateBoms" class="candidate-table" size="small" @row-click="selectBom">
        <el-table-column prop="versionNo" label="版本" width="90" />
        <el-table-column prop="bomName" label="BOM 名称" min-width="150" />
        <el-table-column prop="routeName" label="关联工艺路线" min-width="150" />
        <el-table-column label="候选状态" width="110">
          <template #default="{ row }">{{ row.candidateStatus || row.status }}</template>
        </el-table-column>
        <el-table-column label="物料数" width="90">
          <template #default="{ row }">{{ row.materialCount ?? row.items?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="试算成本" width="120">
          <template #default="{ row }">{{ row.totalCost ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="正式关系" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.currentFormal" type="success" size="small">当前正式</el-tag>
            <span v-else>候选</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="workbench?.routes.length" class="route-list">
        <article v-for="route in workbench.routes" :key="route.routeCode" class="route-row">
          <div class="route-row__identity"><strong>{{ route.routeName }}</strong><code>{{ route.routeCode }}</code></div>
          <div class="color-list"><el-tag v-for="color in route.colors" :key="color" size="small">{{ color }}</el-tag></div>
          <span>{{ route.items.length }} 项物料</span>
          <strong>{{ route.costSnapshot ? `${route.costSnapshot.currencyCode} ${route.costSnapshot.totalCost}` : '待计算' }}</strong>
        </article>
      </div>
      <el-empty v-else description="当前候选 BOM 尚未维护工艺路线明细" />

      <footer class="lifecycle-bar">
        <el-button :loading="actionLoading" :disabled="isReadOnly" @click="lifecycle('cost')">刷新成本</el-button>
        <el-button :icon="Promotion" :disabled="isReadOnly" @click="lifecycle('review')">提交审核</el-button>
        <el-button :icon="Lock" :disabled="isFrozen" @click="lifecycle('freeze')">冻结</el-button>
        <el-button type="primary" :disabled="isReadOnly" @click="lifecycle('publish')">发布</el-button>
        <el-button :icon="DocumentCopy" @click="lifecycle('copy')">复制版本</el-button>
      </footer>
    </div>
    <el-empty v-else description="当前项目还没有 BOM" />

    <el-dialog v-model="createVisible" title="新建候选 BOM" width="520px" destroy-on-close>
      <p class="create-dialog__hint">关联工艺路线决定这份候选 BOM 后续在哪条路线下参与成本试算和正式敲定。</p>
      <el-form label-width="110px">
        <el-form-item label="BOM 名称">
          <el-input v-model="createForm.bomName" placeholder="例如：候选 BOM V1" />
        </el-form-item>
        <el-form-item label="BOM 类型">
          <el-select v-model="createForm.bomType">
            <el-option label="MBOM" value="mbom" />
            <el-option label="EBOM" value="ebom" />
            <el-option label="包装 BOM" value="pack" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="createForm.versionNo" placeholder="例如：V1" />
        </el-form-item>
        <el-form-item label="关联工艺路线" required>
          <el-select data-test="bom-route-select" v-model="createForm.processId" placeholder="请选择关联工艺路线" filterable>
            <el-option v-for="route in processRoutes" :key="route.processId" :label="route.processName" :value="route.processId" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button data-test="bom-create-submit" type="primary" :loading="actionLoading" @click="submitCreateBom">保存候选 BOM</el-button>
      </template>
    </el-dialog>

    <BomImportDialog v-model="importVisible" :product-id="projectId" :bom-id="selectedBomId" @committed="load(selectedBomId || undefined)" />
    <BomRouteEditor v-if="routeEditorVisible && workbench" v-model="routeEditorVisible" :routes="workbench.routes" :loading="actionLoading" @save="saveRoutes" />
  </section>
</template>

<style scoped>
.bom-workbench { min-width: 0; }
.bom-workbench__header, .formal-toolbar, .command-row, .lifecycle-bar { display: flex; align-items: center; gap: 10px; }
.bom-workbench__header { justify-content: space-between; margin-bottom: 14px; }
.bom-workbench__header h4, .bom-workbench__header p { margin: 0; }
.bom-workbench__header p { color: var(--plm-color-text-secondary); font-size: 13px; }
.mode-panel { margin-top: 14px; }
.formal-toolbar { flex-wrap: wrap; margin-bottom: 12px; }
.formal-toolbar :deep(.el-select) { width: 260px; }
.command-row--push { margin-left: auto; }
.candidate-table { margin-bottom: 14px; }
.create-dialog__hint { margin: 0 0 12px; color: var(--plm-color-text-secondary); font-size: 13px; }
.route-list { border-top: 1px solid var(--el-border-color-lighter); }
.route-row { display: grid; grid-template-columns: minmax(170px, 1.2fr) minmax(180px, 1fr) 100px 110px; align-items: center; gap: 12px; min-height: 58px; border-bottom: 1px solid var(--el-border-color-lighter); }
.route-row__identity { display: grid; gap: 3px; }
.route-row code { color: var(--plm-color-text-secondary); font-size: 12px; }
.color-list { display: flex; flex-wrap: wrap; gap: 5px; }
.lifecycle-bar { justify-content: flex-end; flex-wrap: wrap; margin-top: 14px; }
@media (max-width: 760px) {
  .bom-workbench__header { align-items: flex-start; flex-direction: column; }
  .route-row { grid-template-columns: 1fr; padding: 10px 0; }
  .command-row--push { width: 100%; margin-left: 0; flex-wrap: wrap; }
}
</style>
