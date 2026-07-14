<script setup lang="ts">
import { Delete, Edit, Lock, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  addBomItem,
  createProjectBom,
  deleteBomItem,
  freezeBom,
  getProjectBoms,
  updateBom,
  updateBomItem,
  type ProductBomItemSavePayload,
  type ProductBomItemVO,
  type ProductBomSavePayload,
  type ProductBomVO
} from '@/api/modules/bom'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ (event: 'changed'): void }>()

const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const boms = ref<ProductBomVO[]>([])
const selectedBomId = ref<number | null>(null)
const bomDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const editingBom = ref(false)
const editingItemId = ref<number | null>(null)

const bomForm = reactive<ProductBomSavePayload>({
  bomName: '',
  bomType: 'ebom',
  versionNo: 'A',
  remark: ''
})

const itemForm = reactive<ProductBomItemSavePayload>({
  itemCode: '',
  itemName: '',
  specification: '',
  lineNo: 10,
  quantity: 1,
  unit: 'pcs',
  lossRate: 0,
  substituteFlag: 0,
  remark: ''
})

const selectedBom = computed(() =>
  boms.value.find((item) => item.productBomId === selectedBomId.value) || null
)
const isFrozen = computed(() => selectedBom.value?.status === 'frozen')

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

async function loadBoms(preferredBomId?: number) {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getProjectBoms(props.projectId)
    boms.value = result
    const preferred = preferredBomId ?? selectedBomId.value
    selectedBomId.value = result.some((item) => item.productBomId === preferred)
      ? preferred
      : result[0]?.productBomId ?? null
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function resetBomForm(bom?: ProductBomVO) {
  bomForm.bomName = bom?.bomName || ''
  bomForm.bomType = bom?.bomType || 'ebom'
  bomForm.versionNo = bom?.versionNo || 'A'
  bomForm.remark = bom?.remark || ''
}

function openCreateBom() {
  editingBom.value = false
  resetBomForm()
  bomDialogVisible.value = true
}

function openEditBom() {
  if (!selectedBom.value || isFrozen.value) return
  editingBom.value = true
  resetBomForm(selectedBom.value)
  bomDialogVisible.value = true
}

async function saveBom() {
  if (!bomForm.bomName.trim() || !bomForm.bomType.trim() || !bomForm.versionNo.trim()) {
    ElMessage.warning('请填写 BOM 名称、类型和版本号')
    return
  }
  actionLoading.value = true
  try {
    const payload = { ...bomForm }
    const result = editingBom.value && selectedBom.value
      ? await updateBom(selectedBom.value.productBomId, payload)
      : await createProjectBom(props.projectId, payload)
    bomDialogVisible.value = false
    await loadBoms(result.productBomId)
    emit('changed')
    ElMessage.success(editingBom.value ? 'BOM 已更新' : 'BOM 已创建')
  } finally {
    actionLoading.value = false
  }
}

function resetItemForm(item?: ProductBomItemVO) {
  itemForm.inventoryId = item?.inventoryId ?? null
  itemForm.itemCode = item?.itemCode || ''
  itemForm.itemName = item?.itemName || ''
  itemForm.specification = item?.specification || ''
  itemForm.lineNo = item?.lineNo ?? ((selectedBom.value?.items.length || 0) + 1) * 10
  itemForm.quantity = item?.quantity ?? 1
  itemForm.unit = item?.unit || 'pcs'
  itemForm.lossRate = item?.lossRate ?? 0
  itemForm.substituteFlag = item?.substituteFlag ?? 0
  itemForm.remark = item?.remark || ''
}

function openCreateItem() {
  if (!selectedBom.value || isFrozen.value) return
  editingItemId.value = null
  resetItemForm()
  itemDialogVisible.value = true
}

function openEditItem(item: ProductBomItemVO) {
  if (isFrozen.value) return
  editingItemId.value = item.productBomItemId
  resetItemForm(item)
  itemDialogVisible.value = true
}

async function saveItem() {
  if (!selectedBom.value) return
  if (!itemForm.itemName.trim() || !itemForm.unit.trim() || itemForm.quantity <= 0 || itemForm.lineNo <= 0) {
    ElMessage.warning('请填写有效的行号、物料名称、用量和单位')
    return
  }
  actionLoading.value = true
  try {
    const payload = { ...itemForm }
    const result = editingItemId.value
      ? await updateBomItem(selectedBom.value.productBomId, editingItemId.value, payload)
      : await addBomItem(selectedBom.value.productBomId, payload)
    itemDialogVisible.value = false
    await loadBoms(result.productBomId)
    emit('changed')
    ElMessage.success(editingItemId.value ? 'BOM 明细已更新' : 'BOM 明细已添加')
  } finally {
    actionLoading.value = false
  }
}

async function removeItem(item: ProductBomItemVO) {
  if (!selectedBom.value || isFrozen.value) return
  await ElMessageBox.confirm(`确认删除第 ${item.lineNo} 行“${item.itemName}”吗？`, '删除 BOM 明细', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  actionLoading.value = true
  try {
    const result = await deleteBomItem(selectedBom.value.productBomId, item.productBomItemId)
    await loadBoms(result.productBomId)
    emit('changed')
    ElMessage.success('BOM 明细已删除')
  } finally {
    actionLoading.value = false
  }
}

async function lockBom() {
  if (!selectedBom.value || isFrozen.value) return
  await ElMessageBox.confirm('冻结后 BOM 头和全部明细都不能继续修改，是否继续？', '冻结 BOM', {
    confirmButtonText: '冻结',
    cancelButtonText: '取消',
    type: 'warning'
  })
  actionLoading.value = true
  try {
    const result = await freezeBom(selectedBom.value.productBomId)
    await loadBoms(result.productBomId)
    emit('changed')
    ElMessage.success('BOM 已冻结')
  } finally {
    actionLoading.value = false
  }
}

watch(() => props.projectId, () => loadBoms(), { immediate: true })
</script>

<template>
  <div class="m4-panel" v-loading="loading">
    <div class="m4-panel__toolbar">
      <div>
        <h4 class="section-title">BOM 管理</h4>
        <p class="page-panel-desc">维护当前 Product 的 BOM 版本和物料明细，冻结后进入只读状态。</p>
      </div>
      <div class="m4-panel__actions">
        <el-button :icon="Refresh" circle title="刷新 BOM" @click="loadBoms()" />
        <el-button data-test="bom-create" type="primary" :icon="Plus" @click="openCreateBom">新建 BOM</el-button>
      </div>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <template v-else-if="selectedBom">
      <div class="m4-panel__selector">
        <el-select v-model="selectedBomId" class="m4-panel__select" aria-label="选择 BOM 版本">
          <el-option
            v-for="bom in boms"
            :key="bom.productBomId"
            :label="`${bom.bomName} / ${bom.versionNo}`"
            :value="bom.productBomId"
          />
        </el-select>
        <el-tag :type="isFrozen ? 'success' : 'warning'" effect="light">
          {{ isFrozen ? '已冻结' : '草稿' }}
        </el-tag>
        <span class="m4-panel__code">{{ selectedBom.bomCode }} / {{ selectedBom.bomType.toUpperCase() }}</span>
        <div class="m4-panel__actions m4-panel__actions--right">
          <el-button data-test="bom-edit" :icon="Edit" :disabled="isFrozen" @click="openEditBom">编辑</el-button>
          <el-button data-test="bom-item-add" type="primary" plain :icon="Plus" :disabled="isFrozen" @click="openCreateItem">添加明细</el-button>
          <el-button type="warning" plain :icon="Lock" :disabled="isFrozen" @click="lockBom">冻结</el-button>
        </div>
      </div>

      <el-table :data="selectedBom.items" border stripe size="small" class="m4-panel__table">
        <el-table-column prop="lineNo" label="行号" width="72" />
        <el-table-column prop="itemCode" label="物料编码" min-width="130"><template #default="{ row }">{{ row.itemCode || '--' }}</template></el-table-column>
        <el-table-column prop="itemName" label="物料名称" min-width="160" />
        <el-table-column prop="specification" label="规格" min-width="140"><template #default="{ row }">{{ row.specification || '--' }}</template></el-table-column>
        <el-table-column prop="quantity" label="用量" width="100" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="lossRate" label="损耗率" width="90"><template #default="{ row }">{{ row.lossRate ?? 0 }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140"><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" :disabled="isFrozen" title="编辑明细" @click="openEditItem(row)" />
            <el-button link type="danger" :icon="Delete" :disabled="isFrozen" title="删除明细" @click="removeItem(row)" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!selectedBom.items.length" description="当前 BOM 还没有明细" />
    </template>

    <el-empty v-else description="当前项目还没有 BOM">
      <el-button type="primary" :icon="Plus" @click="openCreateBom">新建 BOM</el-button>
    </el-empty>

    <el-dialog v-model="bomDialogVisible" :title="editingBom ? '编辑 BOM' : '新建 BOM'" width="520px" append-to-body>
      <el-form label-width="88px">
        <el-form-item label="BOM 名称" required><el-input v-model="bomForm.bomName" maxlength="100" /></el-form-item>
        <el-form-item label="BOM 类型" required>
          <el-select v-model="bomForm.bomType" style="width: 100%">
            <el-option label="EBOM" value="ebom" />
            <el-option label="MBOM" value="mbom" />
            <el-option label="包装 BOM" value="pack" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" required><el-input v-model="bomForm.versionNo" maxlength="50" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="bomForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="saveBom">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="editingItemId ? '编辑 BOM 明细' : '添加 BOM 明细'" width="680px" append-to-body>
      <el-form label-width="88px" class="m4-form-grid">
        <el-form-item label="行号" required><el-input-number v-model="itemForm.lineNo" :min="1" :step="10" controls-position="right" /></el-form-item>
        <el-form-item label="物料编码"><el-input v-model="itemForm.itemCode" maxlength="100" /></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="itemForm.itemName" maxlength="200" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="itemForm.specification" maxlength="200" /></el-form-item>
        <el-form-item label="用量" required><el-input-number v-model="itemForm.quantity" :min="0.000001" :precision="6" controls-position="right" /></el-form-item>
        <el-form-item label="单位" required><el-input v-model="itemForm.unit" maxlength="30" /></el-form-item>
        <el-form-item label="损耗率"><el-input-number v-model="itemForm.lossRate" :min="0" :precision="4" controls-position="right" /></el-form-item>
        <el-form-item label="替代料"><el-switch v-model="itemForm.substituteFlag" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注" class="m4-form-grid__wide"><el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="saveItem">保存</el-button>
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
.m4-form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
.m4-form-grid__wide { grid-column: 1 / -1; }
@media (max-width: 760px) {
  .m4-panel__toolbar { align-items: flex-start; flex-direction: column; }
  .m4-panel__actions--right { width: 100%; margin-left: 0; flex-wrap: wrap; }
  .m4-form-grid { grid-template-columns: 1fr; }
  .m4-form-grid__wide { grid-column: auto; }
}
</style>
