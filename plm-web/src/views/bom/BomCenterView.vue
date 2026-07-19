<script setup lang="ts">
import { ArrowRight, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'

import { getBomLedger, getBomSkus, getBomWorkbench } from '@/api/modules/bom'
import type { BomLedgerRow, BomSkuRow, BomWorkbench } from '@/types/bom'
import HistoricalBomImportDialog from './components/HistoricalBomImportDialog.vue'

const loading = ref(false)
const error = ref('')
const rows = ref<BomLedgerRow[]>([])
const keyword = ref('')
const detailVisible = ref(false)
const detail = ref<BomWorkbench | null>(null)
const activeTab = ref('basic')
const selectedRouteId = ref<number | null>(null)
const skuVisible = ref(false)
const skus = ref<BomSkuRow[]>([])
const skuPage = ref(1)
const skuPageSize = 8
const historicalImportVisible = ref(false)

const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return rows.value
  return rows.value.filter((row) => [row.bomCode, row.productCode, row.productName, row.model]
    .some((field) => String(field || '').toLowerCase().includes(value)))
})
const selectedRoute = computed(() => detail.value?.routes.find((route) => route.productBomRouteId === selectedRouteId.value) || detail.value?.routes[0] || null)
const pagedSkus = computed(() => skus.value.slice((skuPage.value - 1) * skuPageSize, skuPage.value * skuPageSize))

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = await getBomLedger() } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '正式 BOM 台账加载失败'
  } finally { loading.value = false }
}

async function openDetail(row: BomLedgerRow) {
  detail.value = await getBomWorkbench(row.productBomId)
  selectedRouteId.value = detail.value.routes[0]?.productBomRouteId || null
  activeTab.value = 'basic'
  detailVisible.value = true
}

async function openSkus(row: BomLedgerRow) {
  skus.value = await getBomSkus(row.productBomId)
  skuPage.value = 1
  skuVisible.value = true
}

function goWorkbench(row: BomLedgerRow) {
  window.location.hash = `#/projects?projectId=${row.productId}&section=bom`
}

onMounted(load)
</script>

<template>
  <main class="bom-ledger" v-loading="loading">
    <header class="page-head">
      <div><h2>BOM 管理</h2><p>查询已进入正式台账的产品与型号 BOM。</p></div>
      <div class="page-actions"><el-button data-test="historical-bom-import" type="primary" :icon="Upload" @click="historicalImportVisible = true">批量导入历史 BOM</el-button><el-button :icon="Refresh" circle title="刷新" @click="load" /></div>
    </header>

    <div class="filter-bar">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="BOM、产品或型号编码 / 名称" />
      <span>共 {{ filteredRows.length }} 条正式 BOM</span>
    </div>

    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
    <div v-else class="table-shell">
      <table>
        <thead><tr><th>BOM 编码</th><th>产品 / 型号</th><th>版本</th><th>路线数</th><th>关联 SKU</th><th>状态</th><th>来源</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in filteredRows" :key="row.productBomId">
            <td><code>{{ row.bomCode }}</code></td>
            <td><strong>{{ row.productName }}</strong><small>{{ row.productCode }}{{ row.model ? ` · ${row.model}` : '' }}</small></td>
            <td>{{ row.versionNo }}</td><td>{{ row.routeCount }}</td>
            <td><el-button data-test="bom-skus" link type="primary" @click="openSkus(row)">{{ row.skuCount }} 个关联 SKU</el-button></td>
            <td><el-tag size="small" effect="light">{{ row.status }}</el-tag></td><td>{{ row.sourceType }}</td>
            <td>{{ row.updatedAt ? row.updatedAt.slice(0, 16).replace('T', ' ') : '--' }}</td>
            <td class="row-actions"><el-button data-test="bom-detail" link type="primary" @click="openDetail(row)">详情</el-button><el-button link :icon="ArrowRight" @click="goWorkbench(row)">工作台</el-button></td>
          </tr>
        </tbody>
      </table>
      <el-empty v-if="!filteredRows.length" description="暂无正式 BOM" />
    </div>

    <el-drawer v-model="detailVisible" :title="`BOM 详情：${detail?.bomCode || ''}`" size="min(980px, 96vw)">
      <el-tabs v-if="detail" v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic"><dl class="facts"><div><dt>名称</dt><dd>{{ detail.bomName }}</dd></div><div><dt>版本</dt><dd>{{ detail.versionNo }}</dd></div><div><dt>状态</dt><dd>{{ detail.status }}</dd></div><div><dt>路线数</dt><dd>{{ detail.routes.length }}</dd></div></dl></el-tab-pane>
        <el-tab-pane label="BOM 明细" name="items"><div class="route-selector"><el-select v-model="selectedRouteId"><el-option v-for="route in detail.routes" :key="route.productBomRouteId" :label="route.routeName" :value="route.productBomRouteId" /></el-select></div><table class="inner-table"><thead><tr><th>行号</th><th>物料编码</th><th>物料名称</th><th>用量</th><th>单位</th><th>损耗率</th></tr></thead><tbody><tr v-for="item in selectedRoute?.items || []" :key="item.productBomItemId || item.lineNo"><td>{{ item.lineNo }}</td><td>{{ item.itemCode || '--' }}</td><td>{{ item.itemName }}</td><td>{{ item.quantity }}</td><td>{{ item.unit }}</td><td>{{ item.lossRate || 0 }}</td></tr></tbody></table></el-tab-pane>
        <el-tab-pane label="工艺路线" name="routes"><div class="route-detail" v-for="route in detail.routes" :key="route.routeCode"><div><strong>{{ route.routeName }}</strong><code>{{ route.routeCode }}</code></div><div class="tags"><el-tag v-for="color in route.colors" :key="color" size="small">{{ color }}</el-tag></div><span>{{ route.items.length }} 项物料</span></div></el-tab-pane>
        <el-tab-pane label="路线成本" name="cost"><div v-if="selectedRoute?.costSnapshot" class="cost-grid"><div><span>物料</span><strong>{{ selectedRoute.costSnapshot.materialCost }}</strong></div><div><span>损耗</span><strong>{{ selectedRoute.costSnapshot.lossCost }}</strong></div><div><span>工艺</span><strong>{{ selectedRoute.costSnapshot.processCost }}</strong></div><div><span>路线总成本</span><strong>{{ selectedRoute.costSnapshot.currencyCode }} {{ selectedRoute.costSnapshot.totalCost }}</strong></div></div><el-empty v-else description="当前路线尚无成本快照" /></el-tab-pane>
        <el-tab-pane label="版本记录" name="versions"><el-empty description="当前显示所选正式版本，其他版本请从主列表查看" /></el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog v-model="skuVisible" title="关联 SKU" width="min(860px, 94vw)">
      <table class="inner-table"><thead><tr><th>SKU 编码</th><th>产品</th><th>手机型号</th><th>颜色</th><th>状态</th><th>路线</th></tr></thead><tbody><tr v-for="sku in pagedSkus" :key="sku.productId"><td><code>{{ sku.skuCode }}</code></td><td>{{ sku.productName }}</td><td>{{ sku.phoneModel }}</td><td>{{ sku.color }}</td><td>{{ sku.status }}</td><td>{{ sku.routeCode || '暂无适用路线' }}</td></tr></tbody></table>
      <el-pagination v-if="skus.length > skuPageSize" v-model:current-page="skuPage" :page-size="skuPageSize" :total="skus.length" layout="prev, pager, next" />
    </el-dialog>
    <HistoricalBomImportDialog v-model="historicalImportVisible" @committed="load" />
  </main>
</template>

<style scoped>
.bom-ledger { padding: 20px; min-width: 0; }
.page-head, .filter-bar, .page-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.page-head h2, .page-head p { margin: 0; }.page-head p, small, .filter-bar span { color: var(--plm-color-text-secondary); font-size: 12px; }
.filter-bar { margin: 16px 0 10px; }.filter-bar :deep(.el-input) { max-width: 420px; }
.table-shell { overflow-x: auto; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; } th { background: var(--el-fill-color-lighter); text-align: left; font-weight: 600; } th, td { padding: 11px 12px; border-bottom: 1px solid var(--el-border-color-lighter); white-space: nowrap; } td strong, td small { display: block; } code { font-size: 12px; }.row-actions { min-width: 150px; }
.facts, .cost-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }.facts div, .cost-grid div { padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }.facts dt, .cost-grid span { color: var(--plm-color-text-secondary); font-size: 12px; }.facts dd, .cost-grid strong { display: block; margin: 5px 0 0; }
.route-selector { margin-bottom: 10px; }.route-detail { display: grid; grid-template-columns: 1.2fr 1fr 100px; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }.route-detail div:first-child { display: grid; }.tags { display: flex; flex-wrap: wrap; gap: 4px; }.inner-table { margin-bottom: 12px; }
@media (max-width: 760px) { .bom-ledger { padding: 12px; }.page-head, .filter-bar { align-items: flex-start; flex-direction: column; }.facts, .cost-grid { grid-template-columns: 1fr 1fr; }.route-detail { grid-template-columns: 1fr; } }
</style>
