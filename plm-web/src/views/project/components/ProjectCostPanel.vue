<script setup lang="ts">
import { Check, Edit, Plus, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import {
  confirmProjectCostItem,
  createProjectCostItem,
  getProjectCostItems,
  getProjectCostSummary,
  updateProjectCostItem,
  voidProjectCostItem,
  type ProjectCostItemPayload,
  type ProjectCostItemVO,
  type ProjectCostSummaryVO
} from '@/api/modules/project'
import { formatAmount, formatDateTime } from '@/utils/format'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{
  changed: []
}>()

const loading = ref(false)
const actionLoading = ref(false)
const summary = ref<ProjectCostSummaryVO | null>(null)
const items = ref<ProjectCostItemVO[]>([])
const dialogVisible = ref(false)
const editingItemId = ref<number | null>(null)
const form = reactive<ProjectCostItemPayload>({
  costCategory: 'mold',
  costName: '',
  amount: 0,
  currencyCode: 'CNY',
  supplierName: '',
  occurredAt: '',
  remark: ''
})

const isEditing = computed(() => editingItemId.value !== null)
const confirmedItems = computed(() => items.value.filter(item => item.status === 'confirmed'))
const draftItems = computed(() => items.value.filter(item => item.status === 'draft'))

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function resetForm() {
  editingItemId.value = null
  form.costCategory = 'mold'
  form.costName = ''
  form.amount = 0
  form.currencyCode = 'CNY'
  form.supplierName = ''
  form.occurredAt = ''
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(item: ProjectCostItemVO) {
  editingItemId.value = item.projectCostItemId
  form.costCategory = item.costCategory
  form.costName = item.costName
  form.amount = Number(item.amount || 0)
  form.currencyCode = item.currencyCode || 'CNY'
  form.supplierName = item.supplierName || ''
  form.occurredAt = item.occurredAt || ''
  form.remark = item.remark || ''
  dialogVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const [nextSummary, nextItems] = await Promise.all([
      getProjectCostSummary(props.projectId),
      getProjectCostItems(props.projectId)
    ])
    summary.value = nextSummary
    items.value = nextItems
  } catch (error) {
    ElMessage.error(errorMessage(error, '成本数据加载失败'))
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.costName.trim()) {
    ElMessage.warning('请填写成本名称')
    return
  }
  if (Number(form.amount) < 0) {
    ElMessage.warning('金额不能为负数')
    return
  }
  actionLoading.value = true
  try {
    const payload: ProjectCostItemPayload = {
      ...form,
      costName: form.costName.trim(),
      amount: Number(form.amount || 0),
      currencyCode: form.currencyCode?.trim() || 'CNY',
      supplierName: form.supplierName?.trim() || undefined,
      occurredAt: form.occurredAt || undefined,
      remark: form.remark?.trim() || undefined
    }
    if (editingItemId.value) {
      await updateProjectCostItem(props.projectId, editingItemId.value, payload)
      ElMessage.success('成本项已更新')
    } else {
      await createProjectCostItem(props.projectId, payload)
      ElMessage.success('成本项已添加')
    }
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    ElMessage.error(errorMessage(error, '成本项保存失败'))
  } finally {
    actionLoading.value = false
  }
}

async function confirmItem(item: ProjectCostItemVO) {
  try {
    await ElMessageBox.confirm(`确认将“${item.costName}”计入总成本吗？`, '确认成本项', {
      confirmButtonText: '确认计入',
      cancelButtonText: '取消',
      type: 'warning'
    })
    actionLoading.value = true
    await confirmProjectCostItem(props.projectId, item.projectCostItemId)
    await load()
    emit('changed')
    ElMessage.success('成本项已确认')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '成本项确认失败'))
  } finally {
    actionLoading.value = false
  }
}

async function voidItem(item: ProjectCostItemVO) {
  try {
    await ElMessageBox.confirm(`确认作废“${item.costName}”吗？`, '作废成本项', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      type: 'warning'
    })
    actionLoading.value = true
    await voidProjectCostItem(props.projectId, item.projectCostItemId)
    await load()
    emit('changed')
    ElMessage.success('成本项已作废')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '成本项作废失败'))
  } finally {
    actionLoading.value = false
  }
}

onMounted(load)

watch(
  () => props.projectId,
  () => {
    summary.value = null
    items.value = []
    load()
  }
)
</script>

<template>
  <section class="project-cost-panel">
    <div class="project-cost-panel__head">
      <div>
        <h4 class="section-title">成本管理</h4>
        <p class="page-panel-desc">BOM 成本作为参考，研发成本由确认/未确认 BOM 自动汇总；模具和其他成本确认后计入总成本。</p>
      </div>
      <div class="project-cost-panel__actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">添加成本</el-button>
      </div>
    </div>

    <div v-loading="loading" class="project-cost-metrics">
      <div class="cost-metric cost-metric--total">
        <span>总成本</span>
        <strong>{{ formatAmount(summary?.totalCost, summary?.currencyCode || 'CNY') }}</strong>
        <small>研发 + 模具 + 其他</small>
      </div>
      <div class="cost-metric">
        <span>BOM 成本参考</span>
        <strong>{{ formatAmount(summary?.bomCost, summary?.currencyCode || 'CNY') }}</strong>
        <small>当前 BOM SKU 单位成本，不重复计入</small>
      </div>
      <div class="cost-metric">
        <span>研发成本</span>
        <strong>{{ formatAmount(summary?.rdCost, summary?.currencyCode || 'CNY') }}</strong>
        <small>确认及未确认 BOM 自动汇总</small>
      </div>
      <div class="cost-metric">
        <span>模具成本</span>
        <strong>{{ formatAmount(summary?.moldCost, summary?.currencyCode || 'CNY') }}</strong>
        <small>仅统计已确认项</small>
      </div>
      <div class="cost-metric">
        <span>其他成本</span>
        <strong>{{ formatAmount(summary?.otherCost, summary?.currencyCode || 'CNY') }}</strong>
        <small>仅统计已确认项</small>
      </div>
    </div>

    <div class="project-cost-panel__subhead">
      <div>
        <h5>手工成本明细</h5>
        <span>当前只维护模具成本和其他成本</span>
      </div>
      <el-tag size="small" type="info">已确认 {{ confirmedItems.length }} 项 / 草稿 {{ draftItems.length }} 项</el-tag>
    </div>

    <el-table :data="items" border stripe size="small" empty-text="暂无手工成本项">
      <el-table-column prop="costCategoryName" label="分类" width="110" />
      <el-table-column prop="costName" label="成本名称" min-width="170" />
      <el-table-column label="金额" width="140">
        <template #default="{ row }">{{ formatAmount(row.amount, row.currencyCode) }}</template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商/对象" min-width="150" />
      <el-table-column label="发生时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'confirmed' ? 'success' : row.status === 'void' ? 'info' : 'warning'">
            {{ row.statusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'draft'" link type="primary" :icon="Edit" :disabled="actionLoading" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'draft'" link type="success" :icon="Check" :disabled="actionLoading" @click="confirmItem(row)">确认</el-button>
          <el-button v-if="row.status !== 'void'" link type="danger" :icon="SwitchButton" :disabled="actionLoading" @click="voidItem(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑成本项' : '添加成本项'" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="成本分类" required>
          <el-select v-model="form.costCategory" style="width: 100%">
            <el-option label="模具成本" value="mold" />
            <el-option label="其他成本" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="成本名称" required>
          <el-input v-model="form.costName" maxlength="128" placeholder="例如：开模费、快递费、样品费" />
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="币种">
          <el-input v-model="form.currencyCode" maxlength="16" />
        </el-form-item>
        <el-form-item label="供应商/对象">
          <el-input v-model="form.supplierName" maxlength="128" />
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" maxlength="500" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.project-cost-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.project-cost-panel__head,
.project-cost-panel__subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.project-cost-panel__actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.project-cost-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.cost-metric {
  min-height: 106px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: 8px;
  background: #fff;
}

.cost-metric--total {
  border-color: rgba(37, 99, 235, 0.28);
  background: #f5f8ff;
}

.cost-metric span,
.cost-metric small {
  display: block;
  color: var(--plm-color-text-secondary);
}

.cost-metric strong {
  display: block;
  margin: 8px 0 5px;
  color: var(--plm-color-text-primary);
  font-size: 20px;
}

.cost-metric small {
  font-size: 12px;
  line-height: 1.4;
}

.project-cost-panel__subhead {
  padding-top: 4px;
}

.project-cost-panel__subhead h5 {
  margin: 0 0 4px;
  font-size: 15px;
}

.project-cost-panel__subhead span {
  color: var(--plm-color-text-secondary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .project-cost-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .project-cost-panel__head,
  .project-cost-panel__subhead {
    align-items: flex-start;
    flex-direction: column;
  }

  .project-cost-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
