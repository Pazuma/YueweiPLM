<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getSupplierCenterSnapshot } from '@/api/modules/supplier'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { SearchField } from '@/types/common'
import type { SupplierCenterSnapshot, SupplierDetail, SupplierSupplyRecord } from '@/types/supplier'
import { formatAmount, formatDate } from '@/utils/format'

interface SupplierCreateForm {
  supplierName: string
  shortName: string
  contactPerson: string
  contactPhone: string
  contactEmail: string
  region: string
  supplyCategories: string[]
  paymentTerm: string
  cooperationLevel: string
  deliveryRisk: string
  status: 'draft' | 'active' | 'inactive'
}

const router = useRouter()
const loading = ref(false)
const snapshot = ref<SupplierCenterSnapshot | null>(null)
const activeSupplierId = ref<number | null>(null)
const rows = computed(() => snapshot.value?.suppliers || [])

// ---- create dialog ----
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()

function createEmptySupplierForm(): SupplierCreateForm {
  return {
    supplierName: '',
    shortName: '',
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    region: '',
    supplyCategories: [],
    paymentTerm: '月结 30 天',
    cooperationLevel: '待评估',
    deliveryRisk: '中',
    status: 'draft'
  }
}

const createForm = ref<SupplierCreateForm>(createEmptySupplierForm())
const editDialogVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = ref<SupplierCreateForm>(createEmptySupplierForm())

const createRules: FormRules<SupplierCreateForm> = {
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  region: [{ required: true, message: '请输入所在区域', trigger: 'blur' }],
  supplyCategories: [{ required: true, message: '请选择供应品类', trigger: 'change' }]
}

function openCreateDialog() {
  createDialogVisible.value = true
}

function resetCreateForm() {
  createForm.value = createEmptySupplierForm()
}

function normalizeSupplierStatus(status: SupplierDetail['status']): SupplierCreateForm['status'] {
  if (status === 'active' || status === 'inactive' || status === 'draft') return status
  return 'draft'
}

async function submitCreateForm() {
  const form = createFormRef.value
  if (!form || !snapshot.value) return

  await form.validate()

  const nextId = Math.max(...snapshot.value.suppliers.map((item) => item.supplierId), 0) + 1
  const now = new Date().toISOString()

  const newSupplier: SupplierDetail = {
    supplierId: nextId,
    supplierCode: `SUP-NEW-${String(nextId).padStart(4, '0')}`,
    supplierName: createForm.value.supplierName,
    shortName: createForm.value.shortName,
    contactPerson: createForm.value.contactPerson,
    contactPhone: createForm.value.contactPhone,
    contactEmail: createForm.value.contactEmail,
    supplyCategories: createForm.value.supplyCategories,
    region: createForm.value.region,
    status: createForm.value.status,
    updatedAt: now,
    cooperationLevel: createForm.value.cooperationLevel,
    paymentTerm: createForm.value.paymentTerm,
    deliveryRisk: createForm.value.deliveryRisk,
    supplyRecords: [],
    relatedProjects: [],
    qualificationFiles: []
  }

  snapshot.value = {
    ...snapshot.value,
    suppliers: [newSupplier, ...snapshot.value.suppliers]
  }

  activeSupplierId.value = newSupplier.supplierId
  createDialogVisible.value = false
  resetCreateForm()
  ElMessage.success('新增供应商成功')
}

// ---- search / table ----
const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '供应商编码 / 名称 / 联系人' },
  {
    prop: 'category',
    label: '供应品类',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '原材料', value: '原材料' },
      { label: '功能件', value: '功能件' },
      { label: '板材', value: '板材' },
      { label: '包材', value: '包材' },
      { label: '模具', value: '模具' },
      { label: '治具', value: '治具' }
    ]
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '启用', value: 'active' },
      { label: '草稿', value: 'draft' },
      { label: '停用', value: 'inactive' }
    ]
  }
]

const table = useTable(rows, ['supplierCode', 'supplierName', 'contactPerson', 'region'], (row, filters) => {
  const status = String(filters.status || '')
  const category = String(filters.category || '')
  if (status && row.status !== status) return false
  if (category && !row.supplyCategories.includes(category)) return false
  return true
})

const activeSupplier = computed(() => {
  const list = table.filteredRows.value
  if (!list.length) return null
  return list.find((item) => item.supplierId === activeSupplierId.value) || list[0]
})

const metrics = computed(() => {
  const suppliers = snapshot.value?.suppliers || []
  return [
    { label: '合作供应商', value: suppliers.length, hint: '材料、包材、模具统一维护' },
    {
      label: '交付风险',
      value: suppliers.filter((item) => item.deliveryRisk === '高').length,
      hint: '优先跟踪影响打样与发布的交付问题'
    },
    {
      label: '资质缺口',
      value: suppliers.reduce((sum, item) => sum + item.qualificationFiles.filter((file) => file.statusLabel !== '有效').length, 0),
      hint: '需要补齐合规文件与有效期管理'
    },
    {
      label: '参与项目',
      value: suppliers.reduce((sum, item) => sum + item.relatedProjects.length, 0),
      hint: '聚合查看历史合作项目与供应记录'
    }
  ]
})

async function loadSnapshot() {
  loading.value = true
  try {
    snapshot.value = await getSupplierCenterSnapshot()
    activeSupplierId.value = snapshot.value.suppliers[0]?.supplierId ?? null
  } finally {
    loading.value = false
  }
}

function openTarget(path: string) {
  router.push(path)
}

function selectSupplier(row: SupplierDetail) {
  activeSupplierId.value = row.supplierId
}

function openEditSupplier(row: SupplierDetail) {
  editForm.value = {
    supplierName: row.supplierName,
    shortName: row.shortName,
    contactPerson: row.contactPerson,
    contactPhone: row.contactPhone,
    contactEmail: row.contactEmail,
    region: row.region,
    supplyCategories: [...row.supplyCategories],
    paymentTerm: row.paymentTerm,
    cooperationLevel: row.cooperationLevel,
    deliveryRisk: row.deliveryRisk,
    status: normalizeSupplierStatus(row.status)
  }
  editDialogVisible.value = true
}

async function submitEditSupplier() {
  const form = editFormRef.value
  const current = activeSupplier.value
  if (!form || !snapshot.value || !current) return

  await form.validate()

  const updatedAt = new Date().toISOString()
  snapshot.value = {
    ...snapshot.value,
    suppliers: snapshot.value.suppliers.map((item) =>
      item.supplierId === current.supplierId
        ? {
            ...item,
            ...editForm.value,
            updatedAt
          }
        : item
    )
  }
  activeSupplierId.value = current.supplierId
  editDialogVisible.value = false
  ElMessage.success('供应商信息已更新')
}

function supplyTypeLabel(type: SupplierSupplyRecord['supplyType']) {
  if (type === 'material') return '物料'
  if (type === 'tooling') return '模具/治具'
  return '包材'
}

onMounted(loadSnapshot)
</script>

<template>
  <PageContainer
    title="供应商管理"
    description="按优化文档收敛为统一入口：列表看基础信息，详情只看供应记录、参与项目与资质文件，不展示内部报价审批内容。"
  >
    <template #actions>
      <el-button type="primary" @click="openCreateDialog">新增供应商</el-button>
      <el-button @click="router.push('/inventories')">物料 / 模具</el-button>
      <el-button @click="router.push('/products')">产品管理</el-button>
    </template>

    <section class="metric-grid" v-loading="loading">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="table.query"
      @search="table.setQuery"
      @reset="table.resetQuery({ keyword: '', category: '', status: '' })"
    />

    <section class="split-grid supplier-split-grid">
      <article class="page-panel supplier-list-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">供应商列表</h3>
            <p class="page-panel-desc">点击卡片切换右侧详情。</p>
          </div>
        </div>
        <div class="supplier-card-list" v-loading="loading">
          <button
            v-for="row in table.pagedRows.value"
            :key="row.supplierId"
            class="supplier-card"
            :class="{ 'is-active': activeSupplierId === row.supplierId }"
            type="button"
            @click="selectSupplier(row)"
          >
            <strong>{{ row.supplierName }}</strong>
            <div class="tag-wrap">
              <el-tag v-for="item in row.supplyCategories" :key="item" effect="light" size="small">{{ item }}</el-tag>
            </div>
          </button>
        </div>
      </article>

      <article v-if="activeSupplier" class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">{{ activeSupplier.supplierName }}</h3>
            <p class="page-panel-desc">{{ activeSupplier.supplierCode }} / {{ activeSupplier.cooperationLevel }}</p>
          </div>
          <StatusTag :status="activeSupplier.status" object-type="customer" />
          <el-button size="small" type="primary" plain @click="openEditSupplier(activeSupplier)">编辑</el-button>
        </div>

        <section class="detail-grid">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="联系人">{{ activeSupplier.contactPerson }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ activeSupplier.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ activeSupplier.contactEmail }}</el-descriptions-item>
            <el-descriptions-item label="付款条件">{{ activeSupplier.paymentTerm }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="区域">{{ activeSupplier.region }}</el-descriptions-item>
            <el-descriptions-item label="交付风险">{{ activeSupplier.deliveryRisk }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(activeSupplier.updatedAt, 'YYYY-MM-DD HH:mm') }}</el-descriptions-item>
            <el-descriptions-item label="供应范围">
              {{ activeSupplier.supplyCategories.join(' / ') }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <el-tabs class="section-gap">
          <el-tab-pane label="供应记录">
            <el-table :data="activeSupplier.supplyRecords" border stripe>
              <el-table-column label="供应类型" width="120">
                <template #default="{ row }">
                  {{ supplyTypeLabel(row.supplyType) }}
                </template>
              </el-table-column>
              <el-table-column prop="itemCode" label="编码" min-width="150" />
              <el-table-column prop="itemName" label="名称" min-width="180" />
              <el-table-column prop="relatedProduct" label="关联产品" min-width="180" />
              <el-table-column label="单价" width="130">
                <template #default="{ row }">
                  {{ formatAmount(row.unitPrice, row.currency) }}
                </template>
              </el-table-column>
              <el-table-column prop="lastDeliveryDate" label="最近交付" width="120" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <StatusTag :status="row.status" object-type="inventory" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openTarget(row.targetPath)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="参与项目">
            <div class="page-stack">
              <button
                v-for="item in activeSupplier.relatedProjects"
                :key="item.projectCode"
                class="risk-card summary-button"
                type="button"
                @click="openTarget(item.targetPath)"
              >
                <div class="toolbar-row">
                  <strong>{{ item.projectName }}</strong>
                  <el-tag effect="light">{{ item.stage }}</el-tag>
                </div>
                <p class="page-panel-desc">{{ item.projectCode }}</p>
                <div class="metric-card__footer">
                  <span class="subtle-text">{{ item.roleSummary }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </div>
              </button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="资质文件">
            <el-table :data="activeSupplier.qualificationFiles" border stripe>
              <el-table-column prop="fileName" label="文件名" min-width="220" />
              <el-table-column prop="fileType" label="类型" width="130" />
              <el-table-column prop="validUntil" label="有效期" width="140" />
              <el-table-column prop="statusLabel" label="状态" width="120" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </article>
    </section>

    <section class="page-panel">
      <h3 class="section-title">风险提醒</h3>
      <div class="page-stack">
        <button
          v-for="risk in snapshot?.risks || []"
          :key="risk.title"
          class="risk-card summary-button"
          type="button"
          @click="openTarget(risk.targetPath)"
        >
          <div class="toolbar-row">
            <strong>{{ risk.title }}</strong>
            <el-tag :type="risk.level === 'high' ? 'danger' : risk.level === 'medium' ? 'warning' : 'info'" effect="light">
              {{ risk.level === 'high' ? '高' : risk.level === 'medium' ? '中' : '低' }}
            </el-tag>
          </div>
          <p class="page-panel-desc">责任人：{{ risk.owner }}</p>
          <div class="metric-card__footer">
            <span class="subtle-text">{{ risk.action }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </button>
      </div>
    </section>

    <!-- create dialog -->
    <el-dialog v-model="createDialogVisible" title="新增供应商" width="720px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="createForm.supplierName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简称" prop="shortName">
              <el-input v-model="createForm.shortName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="createForm.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="createForm.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱" prop="contactEmail">
              <el-input v-model="createForm.contactEmail" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域" prop="region">
              <el-input v-model="createForm.region" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="供应品类" prop="supplyCategories">
              <el-select v-model="createForm.supplyCategories" multiple style="width: 100%">
                <el-option label="原材料" value="原材料" />
                <el-option label="功能件" value="功能件" />
                <el-option label="板材" value="板材" />
                <el-option label="包材" value="包材" />
                <el-option label="模具" value="模具" />
                <el-option label="治具" value="治具" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateForm">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- edit dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑供应商" width="720px">
      <el-form ref="editFormRef" :model="editForm" :rules="createRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="editForm.supplierName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简称" prop="shortName">
              <el-input v-model="editForm.shortName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="editForm.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="editForm.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱" prop="contactEmail">
              <el-input v-model="editForm.contactEmail" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域" prop="region">
              <el-input v-model="editForm.region" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="供应品类" prop="supplyCategories">
              <el-select v-model="editForm.supplyCategories" multiple style="width: 100%">
                <el-option label="原材料" value="原材料" />
                <el-option label="功能件" value="功能件" />
                <el-option label="板材" value="板材" />
                <el-option label="包材" value="包材" />
                <el-option label="模具" value="模具" />
                <el-option label="治具" value="治具" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditSupplier">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.summary-button {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.summary-button:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.metric-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.risk-card {
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.supplier-split-grid {
  grid-template-columns: minmax(260px, 0.72fr) minmax(620px, 1.28fr);
}

.supplier-list-panel { min-width: 0; }

.supplier-card-list { display: flex; flex-direction: column; gap: 8px; }

.supplier-card { display: flex; flex-direction: column; gap: 6px; width: 100%; padding: 14px; border: 1px solid var(--plm-color-border-light); border-radius: var(--plm-radius-base); background: #fff; text-align: left; cursor: pointer; transition: border-color 0.16s; }

.supplier-card:hover { border-color: var(--plm-color-primary); }

.supplier-card.is-active { border-color: var(--plm-color-primary); background: rgba(37,99,235,0.05); }

.section-gap {
  margin-top: var(--plm-space-4);
}
</style>
