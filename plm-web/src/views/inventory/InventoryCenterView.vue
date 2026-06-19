<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getInventoryCenterSnapshot } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import type { InventoryItemCreatePayload, InventoryListRow } from '@/types/foundation'
import { getStatusLabel } from '@/utils/status'

type ItemGroupValue =
  | 'all'
  | 'finished'
  | 'finished-cd30'
  | 'finished-nhc01'
  | 'raw'
  | 'raw-tpu'
  | 'raw-pc'
  | 'raw-color'
  | 'component'
  | 'component-magnet'
  | 'component-deco'
  | 'component-functional'
  | 'package'
  | 'package-box'
  | 'package-inlay'
  | 'package-label'
  | 'tooling'
  | 'tooling-cd30'
  | 'tooling-cd30-mold'
  | 'tooling-cd30-hotpress'
  | 'tooling-cd30-edge'
  | 'tooling-lj30'
  | 'tooling-lj30-mold'
  | 'tooling-lj30-mirror'
type TimeRangeValue = 'all' | '7d' | '30d' | '90d' | 'this_year'
type InventoryActionMode = 'select' | 'delete'

type ItemGroupOption = {
  label: string
  value: ItemGroupValue
  children?: ItemGroupOption[]
}

const loading = ref(false)
const items = ref<InventoryListRow[]>([])
const keyword = ref('')
const itemGroup = ref<ItemGroupValue>('all')
const timeRange = ref<TimeRangeValue>('all')
const actionMode = ref<InventoryActionMode>('select')
const selectedItemIds = ref<string[]>([])
const deleteConfirmVisible = ref(false)
const deleteLoading = ref(false)

const itemGroupCascaderProps = {
  checkStrictly: true,
  emitPath: false
} as const

const itemGroupOptions: ItemGroupOption[] = [
  { label: '全部物料', value: 'all' },
  {
    label: '成品组',
    value: 'finished',
    children: [
      {
        label: 'NHC 超队 3.0',
        value: 'finished-cd30',
        children: [{ label: 'NHC01 超队 3.0 喷墨', value: 'finished-nhc01' }]
      }
    ]
  },
  {
    label: '原材料',
    value: 'raw',
    children: [
      { label: 'TPU', value: 'raw-tpu' },
      { label: 'PC', value: 'raw-pc' },
      { label: '色母', value: 'raw-color' }
    ]
  },
  {
    label: '功能件',
    value: 'component',
    children: [
      { label: '磁吸组件', value: 'component-magnet' },
      { label: '装饰件', value: 'component-deco' },
      { label: '结构辅件', value: 'component-functional' }
    ]
  },
  {
    label: '包材',
    value: 'package',
    children: [
      { label: '彩盒', value: 'package-box' },
      { label: '内托', value: 'package-inlay' },
      { label: '标签', value: 'package-label' }
    ]
  },
  {
    label: '模具治具',
    value: 'tooling',
    children: [
      {
        label: 'MHC 超队 3.0',
        value: 'tooling-cd30',
        children: [
          { label: 'MHC01 超队 3.0 注塑模', value: 'tooling-cd30-mold' },
          { label: 'MHC02 超队 3.0 热压治具', value: 'tooling-cd30-hotpress' },
          { label: 'MHC03 超队 3.0 包边治具', value: 'tooling-cd30-edge' }
        ]
      },
      {
        label: 'MHC 亮甲 3.0',
        value: 'tooling-lj30',
        children: [
          { label: 'MHC04 亮甲 3.0 注塑模', value: 'tooling-lj30-mold' },
          { label: 'MHC05 亮甲 3.0 镜面贴合治具', value: 'tooling-lj30-mirror' }
        ]
      }
    ]
  }
]

// ---- itemGroup → parent-includes-children map ----
const groupChildMap: Record<string, string[]> = {
  finished: ['finished-cd30', 'finished-nhc01', 'finished-cd30-inkjet-ip18'],
  'finished-cd30': ['finished-cd30', 'finished-nhc01', 'finished-cd30-inkjet-ip18'],
  'finished-nhc01': ['finished-nhc01', 'finished-cd30-inkjet-ip18'],
  raw: ['raw-tpu', 'raw-pc', 'raw-color'],
  component: ['component-magnet', 'component-deco', 'component-functional'],
  package: ['package-box', 'package-inlay', 'package-label'],
  tooling: [
    'tooling-cd30',
    'tooling-cd30-mold',
    'tooling-cd30-mold-ip18',
    'tooling-cd30-hotpress',
    'tooling-cd30-edge',
    'tooling-lj30',
    'tooling-lj30-mold',
    'tooling-lj30-mirror'
  ],
  'tooling-cd30': ['tooling-cd30', 'tooling-cd30-mold', 'tooling-cd30-mold-ip18', 'tooling-cd30-hotpress', 'tooling-cd30-edge'],
  'tooling-cd30-mold': ['tooling-cd30-mold', 'tooling-cd30-mold-ip18'],
  'tooling-cd30-hotpress': ['tooling-cd30-hotpress'],
  'tooling-cd30-edge': ['tooling-cd30-edge'],
  'tooling-lj30': ['tooling-lj30', 'tooling-lj30-mold', 'tooling-lj30-mirror'],
  'tooling-lj30-mold': ['tooling-lj30-mold'],
  'tooling-lj30-mirror': ['tooling-lj30-mirror']
}

// ---- filter helpers ----
function scopeKey(item: InventoryListRow): string {
  if (item.inventoryType === '半成品' || item.inventoryType === '成品') {
    if (item.phoneModel === 'iPhone18') return 'finished-cd30-inkjet-ip18'
    if (item.name.includes('喷墨')) return 'finished-nhc01'
    return 'finished-cd30'
  }
  if (item.inventoryType === '原材料') {
    if (item.name.includes('TPU')) return 'raw-tpu'
    if (item.name.includes('PC')) return 'raw-pc'
    if (item.name.includes('色母')) return 'raw-color'
    return 'raw-tpu'
  }
  if (item.inventoryType === '功能件') {
    if (item.name.includes('磁吸')) return 'component-magnet'
    if (item.name.includes('装饰')) return 'component-deco'
    return 'component-functional'
  }
  if (item.inventoryType === '包材') {
    if (item.name.includes('彩盒')) return 'package-box'
    if (item.name.includes('内托')) return 'package-inlay'
    return 'package-label'
  }
  if (item.inventoryType === '模具' || item.inventoryType === '治具') {
    if (item.code === 'INV-MOLD-218') return 'tooling-cd30-mold-ip18'
    if (item.code === 'INV-MOLD-201') return 'tooling-cd30-mold'
    if (item.code === 'INV-JIG-005') return 'tooling-cd30-hotpress'
    if (item.code === 'INV-JIG-008') return 'tooling-cd30-edge'
    if (item.code === 'INV-MOLD-233') return 'tooling-lj30-mold'
    if (item.code === 'INV-JIG-011') return 'tooling-lj30-mirror'
    if (item.productName === '亮甲 3.0') return 'tooling-lj30'
    return 'tooling-cd30'
  }
  return 'all'
}

function matchItemGroup(item: InventoryListRow, group: ItemGroupValue) {
  if (group === 'all') return true
  // parent-includes-children: check if item's scope belongs to group's child set
  const children = groupChildMap[group]
  if (children) return children.includes(scopeKey(item))
  return scopeKey(item) === group
}

function matchTimeRange(item: InventoryListRow, range: TimeRangeValue) {
  if (range === 'all') return true
  const updatedTime = new Date(item.updatedAt).getTime()
  const now = Date.now()
  const diffDays = (now - updatedTime) / (1000 * 60 * 60 * 24)
  if (range === '7d') return diffDays <= 7
  if (range === '30d') return diffDays <= 30
  if (range === '90d') return diffDays <= 90
  if (range === 'this_year') return new Date(item.updatedAt).getFullYear() === new Date().getFullYear()
  return true
}

// ---- current title ----
const groupLabels: Record<string, string> = {
  all: '全部物料',
  finished: '成品组',
  'finished-cd30': 'NHC 超队 3.0',
  'finished-nhc01': 'NHC01 超队 3.0 喷墨',
  raw: '原材料',
  'raw-tpu': 'TPU',
  'raw-pc': 'PC',
  'raw-color': '色母',
  component: '功能件',
  'component-magnet': '磁吸组件',
  'component-deco': '装饰件',
  'component-functional': '结构辅件',
  package: '包材',
  'package-box': '彩盒',
  'package-inlay': '内托',
  'package-label': '标签',
  tooling: '模具治具',
  'tooling-cd30': 'MHC 超队 3.0',
  'tooling-cd30-mold': 'MHC01 超队 3.0 注塑模',
  'tooling-cd30-hotpress': 'MHC02 超队 3.0 热压治具',
  'tooling-cd30-edge': 'MHC03 超队 3.0 包边治具',
  'tooling-lj30': 'MHC 亮甲 3.0',
  'tooling-lj30-mold': 'MHC04 亮甲 3.0 注塑模',
  'tooling-lj30-mirror': 'MHC05 亮甲 3.0 镜面贴合治具'
}

const currentViewTitle = computed(() => {
  return groupLabels[itemGroup.value] || '全部物料'
})

// ---- filtered rows ----
const currentRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const groupMatched = matchItemGroup(item, itemGroup.value)
    const timeMatched = matchTimeRange(item, timeRange.value)
    const keywordMatched =
      !search ||
      item.code.toLowerCase().includes(search) ||
      item.name.toLowerCase().includes(search) ||
      item.spec.toLowerCase().includes(search) ||
      item.supplierName.toLowerCase().includes(search) ||
      (item.productName || '').toLowerCase().includes(search)
    return groupMatched && timeMatched && keywordMatched
  })
})

// ---- create item dialog ----
const createItemVisible = ref(false)
const createItemForm = ref<InventoryItemCreatePayload>({
  nodeId: '',
  productName: '',
  phoneModel: '',
  item_code: '',
  item_name: '',
  item_group: '',
  stock_uom: '',
  custom_specifications: '',
  custom_external_code: '',
  custom_short_name: '',
  custom_mnemonic_code: '',
  custom_dpci: '',
  is_stock_item: 1,
  is_sales_item: 1,
  is_purchase_item: 1
})

function openCreateItemDialog() {
  createItemForm.value = {
    nodeId: '',
    productName: '',
    phoneModel: '',
    item_code: '',
    item_name: '',
    item_group: groupLabels[itemGroup.value] || '全部物料',
    stock_uom: '',
    custom_specifications: '',
    custom_external_code: '',
    custom_short_name: '',
    custom_mnemonic_code: '',
    custom_dpci: '',
    is_stock_item: 1,
    is_sales_item: 1,
    is_purchase_item: 1
  }
  createItemVisible.value = true
}

function submitCreateItem() {
  if (!createItemForm.value.item_code || !createItemForm.value.item_name || !createItemForm.value.stock_uom) return

  const newItem: InventoryListRow = {
    itemId: `inv-${Date.now()}`,
    nodeId: '',
    code: createItemForm.value.item_code,
    name: createItemForm.value.item_name,
    spec: createItemForm.value.custom_specifications || '--',
    stock: '0',
    inventoryType: createItemForm.value.item_group,
    productName: createItemForm.value.productName || undefined,
    phoneModel: createItemForm.value.phoneModel || undefined,
    status: 'available',
    supplierName: '--',
    updatedAt: new Date().toISOString().split('T')[0]
  }

  items.value.unshift(newItem)
  createItemVisible.value = false
}

// ---- create group dialog ----
const createGroupVisible = ref(false)
const createGroupForm = ref({ parentGroup: '', groupCode: '', groupName: '' })

function openCreateGroupDialog() {
  createGroupForm.value = {
    parentGroup: groupLabels[itemGroup.value] || '全部物料',
    groupCode: '',
    groupName: ''
  }
  createGroupVisible.value = true
}

// ---- delete logic ----
const selectedRows = computed(() =>
  currentRows.value.filter((row) => selectedItemIds.value.includes(row.itemId))
)

function handleSelectionChange(rows: InventoryListRow[]) {
  selectedItemIds.value = rows.map((row) => row.itemId)
}

function submitDeleteItems() {
  deleteLoading.value = true
  try {
    items.value = items.value.filter((row) => !selectedItemIds.value.includes(row.itemId))
    selectedItemIds.value = []
    actionMode.value = 'select'
    deleteConfirmVisible.value = false
  } finally {
    deleteLoading.value = false
  }
}

// ---- init ----
async function loadData() {
  loading.value = true
  try {
    const snapshot = await getInventoryCenterSnapshot()
    items.value = snapshot.items
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    title="物料库存"
    description="按物料组和查看范围筛选库存物料记录，支持关键字搜索与就地新建物料。"
  >
    <section class="page-panel inventory-filter-bar">
      <el-cascader
        v-model="itemGroup"
        class="inventory-filter-bar__group"
        :options="itemGroupOptions"
        :props="itemGroupCascaderProps"
        placeholder="选择物料组"
      />

      <el-select v-model="timeRange" class="inventory-filter-bar__time" placeholder="选择时间范围">
        <el-option label="全部时间" value="all" />
        <el-option label="最近 7 天" value="7d" />
        <el-option label="最近 1 个月" value="30d" />
        <el-option label="最近 6 个月" value="90d" />
      </el-select>

      <el-input
        v-model="keyword"
        clearable
        class="inventory-filter-bar__search"
        placeholder="搜索编码 / 名称 / 规格 / 供应商 / 产品"
      />

      <div class="inventory-filter-bar__actions">
        <el-button
          v-if="actionMode === 'select'"
          @click="actionMode = 'delete'"
        >
          选择
        </el-button>
        <el-button
          v-if="actionMode === 'delete' && selectedItemIds.length"
          type="danger"
          @click="deleteConfirmVisible = true"
        >
          删除
        </el-button>
        <el-button
          v-if="actionMode === 'delete'"
          @click="actionMode = 'select'; selectedItemIds = []"
        >
          取消
        </el-button>
        <el-button type="primary" @click="openCreateItemDialog">新增物料</el-button>
        <el-button @click="openCreateGroupDialog">新增物料组</el-button>
      </div>
    </section>

    <section class="page-panel inventory-list-panel" v-loading="loading">
      <div class="toolbar-row inventory-list-panel__header">
        <div>
          <h3 class="section-title">{{ currentViewTitle }}</h3>
          <p class="page-panel-desc">按当前物料组和查看范围筛选库存物料记录。</p>
        </div>
        <el-tag effect="light">{{ currentRows.length }} 条记录</el-tag>
      </div>

      <el-table :data="currentRows" border stripe @selection-change="handleSelectionChange">
        <el-table-column v-if="actionMode === 'delete'" type="selection" width="48" />
        <el-table-column prop="code" label="物料编码" min-width="160" />
        <el-table-column prop="name" label="物料名称" min-width="200" />
        <el-table-column prop="inventoryType" label="类型" width="110" />
        <el-table-column prop="productName" label="所属产品" min-width="180" />
        <el-table-column prop="phoneModel" label="手机型号" min-width="130" />
        <el-table-column prop="spec" label="规格 / 型号" min-width="160" />
        <el-table-column prop="stock" label="库存" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            {{ getStatusLabel(row.status, 'inventory') }}
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="updatedAt" label="更新时间" width="140" />
      </el-table>
    </section>

    <!-- create item dialog -->
    <el-dialog v-model="createItemVisible" title="新建物料" width="680px">
      <el-form :model="createItemForm" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料编码" required>
              <el-input v-model="createItemForm.item_code" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" required>
              <el-input v-model="createItemForm.item_name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属分类">
              <el-input :model-value="createItemForm.item_group" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存单位" required>
              <el-input v-model="createItemForm.stock_uom" placeholder="pcs / kg / set" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="createItemForm.custom_specifications" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="外部编码">
              <el-input v-model="createItemForm.custom_external_code" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="简称">
              <el-input v-model="createItemForm.custom_short_name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="助记码">
              <el-input v-model="createItemForm.custom_mnemonic_code" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="DPCI 编码">
              <el-input v-model="createItemForm.custom_dpci" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createItemVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateItem">确认新建</el-button>
      </template>
    </el-dialog>

    <!-- create group dialog -->
    <el-dialog v-model="createGroupVisible" title="新增物料组" width="480px">
      <el-form :model="createGroupForm" label-width="100px">
        <el-form-item label="父级物料组">
          <el-input :model-value="createGroupForm.parentGroup" disabled />
        </el-form-item>
        <el-form-item label="分组编码">
          <el-input v-model="createGroupForm.groupCode" placeholder="如 MHC06 / NHC02" />
        </el-form-item>
        <el-form-item label="分组名称" required>
          <el-input v-model="createGroupForm.groupName" placeholder="如 超队 3.0 新工艺组" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createGroupVisible = false">取消</el-button>
        <el-button type="primary" @click="createGroupVisible = false">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- delete confirm dialog -->
    <el-dialog v-model="deleteConfirmVisible" title="删除物料确认" width="520px">
      <p>确认删除已选中的 {{ selectedItemIds.length }} 条物料记录吗？</p>
      <ul>
        <li v-for="row in selectedRows.slice(0, 5)" :key="row.itemId">
          {{ row.code }} / {{ row.name }}
        </li>
      </ul>
      <p v-if="selectedRows.length > 5" class="subtle-text">...及其他 {{ selectedRows.length - 5 }} 条</p>
      <template #footer>
        <el-button @click="deleteConfirmVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="submitDeleteItems">确认删除</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.inventory-filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.inventory-filter-bar__group {
  width: 240px;
}

.inventory-filter-bar__time {
  width: 180px;
}

.inventory-filter-bar__search {
  width: 320px;
}

.inventory-filter-bar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.inventory-list-panel__header {
  margin-bottom: 12px;
}
</style>
