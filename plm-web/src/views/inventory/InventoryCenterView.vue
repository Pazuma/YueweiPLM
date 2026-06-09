<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getInventoryCenterSnapshot } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import type { SearchField } from '@/types/common'
import type { InventoryListRow, InventoryTreeNode } from '@/types/foundation'
import { getStatusLabel } from '@/utils/status'

const loading = ref(false)
const tree = ref<InventoryTreeNode[]>([])
const items = ref<InventoryListRow[]>([])
const selectedNodeId = ref('raw-tpu')
const keyword = ref('')

const searchFields: SearchField[] = [{ prop: 'keyword', label: '物料搜索', type: 'input', placeholder: '编码 / 名称 / 规格 / 供应商' }]

const filteredItems = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const categoryMatched = item.nodeId === selectedNodeId.value
    const keywordMatched =
      !search ||
      item.code.toLowerCase().includes(search) ||
      item.name.toLowerCase().includes(search) ||
      item.spec.toLowerCase().includes(search) ||
      item.supplierName.toLowerCase().includes(search)
    return categoryMatched && keywordMatched
  })
})

const metrics = computed(() => [
  { label: '叶子分类', value: collectLeafNodes(tree.value).length, hint: '分类下钻到具体物料叶子节点' },
  { label: '当前分类物料', value: filteredItems.value.length, hint: '右侧只显示所选分类物料' },
  { label: '预留物料', value: items.value.filter((item) => item.status === 'reserved').length, hint: '重点关注被 BOM 占用的物料' },
  { label: '模具治具', value: items.value.filter((item) => item.nodeId.startsWith('tooling')).length, hint: '统一纳入 Inventory 视角' }
])

function collectLeafNodes(nodes: InventoryTreeNode[]): InventoryTreeNode[] {
  return nodes.flatMap((node) => {
    if (!node.children?.length) return [node]
    return collectLeafNodes(node.children)
  })
}

const leafNodes = computed(() => collectLeafNodes(tree.value))

async function loadData() {
  loading.value = true
  try {
    const snapshot = await getInventoryCenterSnapshot()
    tree.value = snapshot.tree
    items.value = snapshot.items
    selectedNodeId.value = collectLeafNodes(snapshot.tree)[0]?.nodeId || ''
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="物料库存" description="页面改为左侧分类树、右侧列表。先找分类，再看具体物料，不再堆 KPI 和图表。">
    <section class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <SearchBar
      :fields="searchFields"
      :model-value="{ keyword }"
      @search="(value) => (keyword = String(value.keyword || ''))"
      @reset="keyword = ''"
    />

    <section class="split-grid inventory-layout" v-loading="loading">
      <article class="page-panel inventory-tree-panel">
        <h3 class="section-title">物料分类</h3>
        <div class="page-stack">
          <section v-for="root in tree" :key="root.nodeId" class="tree-root">
            <strong>{{ root.label }}</strong>
            <div class="tree-children">
              <button
                v-for="child in root.children || []"
                :key="child.nodeId"
                class="tree-node"
                :class="{ 'is-active': selectedNodeId === child.nodeId }"
                type="button"
                @click="selectedNodeId = child.nodeId"
              >
                {{ child.label }}
              </button>
            </div>
          </section>
        </div>
      </article>

      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">物料列表</h3>
            <p class="page-panel-desc">当前分类：{{ leafNodes.find((item) => item.nodeId === selectedNodeId)?.label || '--' }}</p>
          </div>
          <el-tag effect="light">{{ filteredItems.length }} 条</el-tag>
        </div>

        <el-table :data="filteredItems" border stripe>
          <el-table-column prop="code" label="编码" min-width="160" />
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="spec" label="规格" min-width="140" />
          <el-table-column prop="stock" label="库存" width="120" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              {{ getStatusLabel(row.status, 'inventory') }}
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商" min-width="160" />
          <el-table-column prop="updatedAt" label="更新时间" width="140" />
        </el-table>
      </article>
    </section>
  </PageContainer>
</template>

<style scoped>
.inventory-layout {
  grid-template-columns: 280px minmax(0, 1fr);
}

.inventory-tree-panel {
  align-self: start;
}

.tree-root {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tree-children {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tree-node {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.tree-node.is-active {
  border-color: var(--plm-color-primary);
  background: #f8fbff;
  color: var(--plm-color-primary);
}

@media (max-width: 1200px) {
  .inventory-layout {
    grid-template-columns: 1fr;
  }
}
</style>
