<script setup lang="ts">
import { ArrowDown, ArrowUp, Delete, Edit, Lock, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  createProcessRoute,
  freezeProcessRoute,
  getProjectProcessRoutes,
  updateProcessRoute,
  type ProcessOperationSavePayload,
  type ProcessRouteSavePayload,
  type ProcessRouteVO
} from '@/api/modules/process'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ (event: 'changed'): void }>()

const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const routes = ref<ProcessRouteVO[]>([])
const selectedProcessId = ref<number | null>(null)
const dialogVisible = ref(false)
const editing = ref(false)

const routeForm = reactive<ProcessRouteSavePayload>({
  processName: '',
  versionNo: 'A',
  remark: '',
  operations: []
})

const selectedRoute = computed(() =>
  routes.value.find((item) => item.processId === selectedProcessId.value) || null
)
const isLocked = computed(() => selectedRoute.value?.status === 'locked')

function newOperation(sequenceNo: number): ProcessOperationSavePayload {
  return {
    sequenceNo,
    processName: '',
    processParamJson: '',
    standardTimeMins: 0,
    qualityRequirement: '',
    remark: ''
  }
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

async function loadRoutes(preferredProcessId?: number) {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getProjectProcessRoutes(props.projectId)
    routes.value = result
    const preferred = preferredProcessId ?? selectedProcessId.value
    selectedProcessId.value = result.some((item) => item.processId === preferred)
      ? preferred
      : result[0]?.processId ?? null
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function resetForm(route?: ProcessRouteVO) {
  routeForm.processName = route?.processName || ''
  routeForm.versionNo = route?.versionNo || 'A'
  routeForm.remark = route?.remark || ''
  routeForm.operations.splice(
    0,
    routeForm.operations.length,
    ...(route?.operations.map((item) => ({
      sequenceNo: item.sequenceNo,
      processName: item.processName,
      processParamJson: item.processParamJson || '',
      standardTimeMins: item.standardTimeMins ?? 0,
      qualityRequirement: item.qualityRequirement,
      remark: item.remark || ''
    })) || [newOperation(10)])
  )
}

function openCreate() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit() {
  if (!selectedRoute.value || isLocked.value) return
  editing.value = true
  resetForm(selectedRoute.value)
  dialogVisible.value = true
}

function addOperationRow() {
  const nextSequence = routeForm.operations.length
    ? Math.max(...routeForm.operations.map((item) => item.sequenceNo)) + 10
    : 10
  routeForm.operations.push(newOperation(nextSequence))
}

function removeOperationRow(index: number) {
  if (routeForm.operations.length === 1) {
    ElMessage.warning('工艺路线至少保留一道工序')
    return
  }
  routeForm.operations.splice(index, 1)
}

function moveOperation(index: number, offset: -1 | 1) {
  const targetIndex = index + offset
  if (targetIndex < 0 || targetIndex >= routeForm.operations.length) return
  const current = routeForm.operations[index]
  routeForm.operations[index] = routeForm.operations[targetIndex]
  routeForm.operations[targetIndex] = current
  routeForm.operations.forEach((item, itemIndex) => {
    item.sequenceNo = (itemIndex + 1) * 10
  })
}

function validateForm() {
  if (!routeForm.processName.trim() || !routeForm.versionNo.trim()) {
    return '请填写工艺路线名称和版本号'
  }
  if (!routeForm.operations.length) return '工艺路线至少需要一道工序'
  const sequenceSet = new Set<number>()
  for (const operation of routeForm.operations) {
    if (operation.sequenceNo <= 0) return '工序顺序必须大于 0'
    if (sequenceSet.has(operation.sequenceNo)) return `工序顺序 ${operation.sequenceNo} 重复`
    sequenceSet.add(operation.sequenceNo)
    if (!operation.processName.trim()) return `顺序 ${operation.sequenceNo} 的工序名称不能为空`
    if (!operation.qualityRequirement.trim()) return `顺序 ${operation.sequenceNo} 的质量要求不能为空`
    if (operation.standardTimeMins != null && operation.standardTimeMins < 0) return '标准工时不能小于 0'
    if (operation.processParamJson?.trim()) {
      try {
        JSON.parse(operation.processParamJson)
      } catch {
        return `顺序 ${operation.sequenceNo} 的参数 JSON 格式不正确`
      }
    }
  }
  return ''
}

async function saveRoute() {
  const validationError = validateForm()
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }
  actionLoading.value = true
  try {
    const payload: ProcessRouteSavePayload = {
      processName: routeForm.processName.trim(),
      versionNo: routeForm.versionNo.trim(),
      remark: routeForm.remark?.trim(),
      operations: routeForm.operations.map((item) => ({
        ...item,
        processName: item.processName.trim(),
        qualityRequirement: item.qualityRequirement.trim(),
        processParamJson: item.processParamJson?.trim() || undefined,
        remark: item.remark?.trim() || undefined
      }))
    }
    const result = editing.value && selectedRoute.value
      ? await updateProcessRoute(selectedRoute.value.processId, payload)
      : await createProcessRoute(props.projectId, payload)
    dialogVisible.value = false
    await loadRoutes(result.processId)
    emit('changed')
    ElMessage.success(editing.value ? '工艺路线已更新' : '工艺路线已创建')
  } finally {
    actionLoading.value = false
  }
}

async function lockRoute() {
  if (!selectedRoute.value || isLocked.value) return
  await ElMessageBox.confirm('锁定后工艺路线和工序都不能继续修改，是否继续？', '锁定工艺路线', {
    confirmButtonText: '锁定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  actionLoading.value = true
  try {
    const result = await freezeProcessRoute(selectedRoute.value.processId)
    await loadRoutes(result.processId)
    emit('changed')
    ElMessage.success('工艺路线已锁定')
  } finally {
    actionLoading.value = false
  }
}

watch(() => props.projectId, () => loadRoutes(), { immediate: true })
</script>

<template>
  <div class="m4-panel" v-loading="loading">
    <div class="m4-panel__toolbar">
      <div>
        <h4 class="section-title">工艺路线</h4>
        <p class="page-panel-desc">维护工序顺序、参数、标准工时和质量要求，锁定后进入只读状态。</p>
      </div>
      <div class="m4-panel__actions">
        <el-button :icon="Refresh" circle title="刷新工艺路线" @click="loadRoutes()" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新建路线</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <template v-else-if="selectedRoute">
      <div class="m4-panel__selector">
        <el-select v-model="selectedProcessId" class="m4-panel__select" aria-label="选择工艺路线">
          <el-option
            v-for="route in routes"
            :key="route.processId"
            :label="`${route.processName} / ${route.versionNo}`"
            :value="route.processId"
          />
        </el-select>
        <el-tag :type="isLocked ? 'success' : 'warning'" effect="light">{{ isLocked ? '已锁定' : '草稿' }}</el-tag>
        <span class="m4-panel__code">{{ selectedRoute.processCode }}</span>
        <div class="m4-panel__actions m4-panel__actions--right">
          <el-button data-test="process-edit" :icon="Edit" :disabled="isLocked" @click="openEdit">编辑路线</el-button>
          <el-button type="warning" plain :icon="Lock" :disabled="isLocked" @click="lockRoute">锁定</el-button>
        </div>
      </div>

      <el-table :data="selectedRoute.operations" border stripe size="small" class="m4-panel__table">
        <el-table-column prop="sequenceNo" label="顺序" width="72" />
        <el-table-column prop="processName" label="工序名称" min-width="150" />
        <el-table-column prop="standardTimeMins" label="标准工时(分钟)" width="130"><template #default="{ row }">{{ row.standardTimeMins ?? '--' }}</template></el-table-column>
        <el-table-column prop="processParamJson" label="工艺参数" min-width="220"><template #default="{ row }"><code>{{ row.processParamJson || '--' }}</code></template></el-table-column>
        <el-table-column prop="qualityRequirement" label="质量要求" min-width="220" />
        <el-table-column prop="remark" label="备注" min-width="140"><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!selectedRoute.operations.length" description="当前工艺路线还没有工序" />
    </template>

    <el-empty v-else description="当前项目还没有工艺路线">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建工艺路线</el-button>
    </el-empty>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑工艺路线' : '新建工艺路线'" width="min(960px, 94vw)" append-to-body>
      <el-form label-width="88px" class="route-form-head">
        <el-form-item label="路线名称" required><el-input v-model="routeForm.processName" maxlength="100" /></el-form-item>
        <el-form-item label="版本号" required><el-input v-model="routeForm.versionNo" maxlength="50" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="routeForm.remark" maxlength="500" /></el-form-item>
      </el-form>

      <div class="operation-editor__head">
        <strong>工序明细</strong>
        <el-button type="primary" plain :icon="Plus" @click="addOperationRow">添加工序</el-button>
      </div>
      <div class="operation-editor">
        <div v-for="(operation, index) in routeForm.operations" :key="index" class="operation-row">
          <el-input-number v-model="operation.sequenceNo" :min="1" :step="10" controls-position="right" aria-label="工序顺序" />
          <el-input v-model="operation.processName" placeholder="工序名称" maxlength="100" />
          <el-input-number v-model="operation.standardTimeMins" :min="0" :precision="2" controls-position="right" aria-label="标准工时" />
          <el-input v-model="operation.processParamJson" placeholder='参数 JSON，例如 {"temperature":82}' />
          <el-input v-model="operation.qualityRequirement" placeholder="质量要求" maxlength="500" />
          <div class="operation-row__actions">
            <el-button :icon="ArrowUp" circle :disabled="index === 0" title="上移" @click="moveOperation(index, -1)" />
            <el-button :icon="ArrowDown" circle :disabled="index === routeForm.operations.length - 1" title="下移" @click="moveOperation(index, 1)" />
            <el-button :icon="Delete" circle type="danger" plain title="删除工序" @click="removeOperationRow(index)" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="saveRoute">保存完整路线</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.m4-panel { min-width: 0; }
.m4-panel__toolbar,
.m4-panel__selector,
.m4-panel__actions { display: flex; align-items: center; gap: 10px; }
.m4-panel__toolbar { justify-content: space-between; margin-bottom: 16px; }
.m4-panel__toolbar h4,
.m4-panel__toolbar p { margin-top: 0; }
.m4-panel__selector { flex-wrap: wrap; margin-bottom: 14px; }
.m4-panel__select { width: min(100%, 320px); }
.m4-panel__code { color: var(--plm-color-text-secondary); font-size: 13px; }
.m4-panel__actions--right { margin-left: auto; }
.m4-panel__table { width: 100%; }
.route-form-head { display: grid; grid-template-columns: 2fr 1fr 2fr; gap: 12px; }
.operation-editor__head { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 12px; }
.operation-editor { max-height: 48vh; overflow: auto; border-top: 1px solid var(--plm-color-border-light); }
.operation-row { display: grid; grid-template-columns: 120px minmax(130px, 1fr) 140px minmax(180px, 1.3fr) minmax(180px, 1.3fr) 116px; gap: 8px; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--plm-color-border-light); }
.operation-row__actions { display: flex; gap: 4px; }
code { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .m4-panel__toolbar { align-items: flex-start; flex-direction: column; }
  .m4-panel__actions--right { width: 100%; margin-left: 0; }
  .route-form-head { grid-template-columns: 1fr; }
  .operation-row { grid-template-columns: 100px minmax(140px, 1fr); }
  .operation-row__actions { grid-column: 1 / -1; }
}
</style>
