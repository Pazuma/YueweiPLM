<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getFoundationProducts, getProductPresentation } from '@/api/modules/foundation'
import PageContainer from '@/components/PageContainer/index.vue'
import type { FoundationProductRef, ProductDetailPresentation } from '@/types/foundation'
import { formatAmount } from '@/utils/format'
import { getStatusLabel } from '@/utils/status'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const product = ref<FoundationProductRef | null>(null)
const presentation = ref<ProductDetailPresentation | null>(null)
const expandedCost = ref<'estimated' | 'actual' | null>(null)

const productId = computed(() => Number(route.params.id))
const isVariant = computed(() => product.value?.productType === 'model_variant')

const topMetrics = computed(() => {
  if (!product.value || !presentation.value) return []

  return [
    { label: '流程类型', value: presentation.value.flowLabel, hint: product.value.currentStage },
    { label: '当前节点', value: presentation.value.currentNode, hint: `下一节点：${presentation.value.nextNode}` },
    { label: '当前版本', value: product.value.versionNo, hint: `状态：${getStatusLabel(product.value.status, 'product')}` },
    {
      label: isVariant.value ? '实际成本' : '总成本',
      value: formatAmount(isVariant.value ? presentation.value.costPanel.actualTotal : product.value.actualCost),
      hint: isVariant.value ? '新型号线只看增量实际成本' : '新产品线看完整成本结构'
    }
  ]
})

function toggleCostPanel(type: 'estimated' | 'actual') {
  expandedCost.value = expandedCost.value === type ? null : type
}

async function loadData() {
  loading.value = true
  try {
    const [products, detail] = await Promise.all([getFoundationProducts(), getProductPresentation(productId.value)])
    product.value = products.find((item) => item.productId === productId.value) || products[0] || null
    presentation.value = detail
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer
    :title="presentation?.title || '产品详情'"
    :description="presentation?.summary || '按基础资料优化方案收口后的产品详情视图。'"
  >
    <template #actions>
      <el-button @click="router.back()">返回</el-button>
      <el-button @click="router.push('/files')">文件管理</el-button>
      <el-button type="primary" @click="router.push(`/products/${productId}/edit`)">编辑</el-button>
    </template>

    <section class="metric-grid" v-loading="loading">
      <div v-for="metric in topMetrics" :key="metric.label" class="metric-card">
        <p class="metric-card__label">{{ metric.label }}</p>
        <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
        <span class="metric-card__trend">{{ metric.hint }}</span>
      </div>
    </section>

    <section class="split-grid detail-layout" v-if="product && presentation">
      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">基础信息</h3>
            <p class="page-panel-desc">产品和版本仍然挂在 Product 下，不拆出新的根对象。</p>
          </div>
          <el-tag effect="light">{{ getStatusLabel(product.status, 'product') }}</el-tag>
        </div>

        <div class="detail-grid">
          <div class="info-card">
            <span class="subtle-text">产品编码</span>
            <strong>{{ product.productCode }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">系列</span>
            <strong>{{ product.seriesName }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">机型 / 颜色</span>
            <strong>{{ product.model }} / {{ product.color }}</strong>
          </div>
          <div class="info-card">
            <span class="subtle-text">客户来源</span>
            <strong>{{ product.customerName }}</strong>
          </div>
        </div>
      </article>

      <article class="page-panel">
        <div class="toolbar-row">
          <div>
            <h3 class="section-title">成本结构</h3>
            <p class="page-panel-desc">默认只显示汇总，点击后展开明细；预计成本和实际成本不同时展开。</p>
          </div>
        </div>

        <div class="page-stack">
          <section v-if="presentation.costPanel.showEstimated" class="cost-card">
            <button class="cost-card__summary" type="button" @click="toggleCostPanel('estimated')">
              <div>
                <strong>预计成本</strong>
                <p class="subtle-text">立项和前期评估口径</p>
              </div>
              <div class="cost-card__summary-right">
                <strong>{{ formatAmount(presentation.costPanel.estimatedTotal) }}</strong>
                <span class="subtle-text">{{ expandedCost === 'estimated' ? '收起' : '点击展开' }}</span>
              </div>
            </button>
            <div v-if="expandedCost === 'estimated'" class="cost-card__detail">
              <div v-for="line in presentation.costPanel.estimatedLines || []" :key="line.label" class="cost-line">
                <div>
                  <strong>{{ line.label }}</strong>
                  <p class="subtle-text">{{ line.note }}</p>
                </div>
                <strong>{{ formatAmount(line.amount) }}</strong>
              </div>
            </div>
          </section>

          <section class="cost-card">
            <button class="cost-card__summary" type="button" @click="toggleCostPanel('actual')">
              <div>
                <strong>实际成本</strong>
                <p class="subtle-text">{{ isVariant ? '新型号线只看增量实际成本' : '当前版本真实执行口径' }}</p>
              </div>
              <div class="cost-card__summary-right">
                <strong>{{ formatAmount(presentation.costPanel.actualTotal) }}</strong>
                <span class="subtle-text">{{ expandedCost === 'actual' ? '收起' : '点击展开' }}</span>
              </div>
            </button>
            <div v-if="expandedCost === 'actual'" class="cost-card__detail">
              <div v-for="line in presentation.costPanel.actualLines" :key="line.label" class="cost-line">
                <div>
                  <strong>{{ line.label }}</strong>
                  <p class="subtle-text">{{ line.note }}</p>
                </div>
                <strong>{{ formatAmount(line.amount) }}</strong>
              </div>
            </div>
          </section>
        </div>
      </article>
    </section>

    <section class="page-panel" v-if="presentation">
      <el-tabs>
        <el-tab-pane label="BOM">
          <div class="page-stack">
            <div class="toolbar-row">
              <div>
                <h3 class="section-title">版本成本对比</h3>
                <p class="page-panel-desc">版本成本对比移回产品详情页 BOM Tab，只保留直接可比的表格。</p>
              </div>
            </div>

            <el-table :data="presentation.bomCompareRows" border stripe>
              <el-table-column prop="versionNo" label="版本" width="100" />
              <el-table-column prop="statusLabel" label="状态" width="120" />
              <el-table-column label="物料成本" width="140">
                <template #default="{ row }">{{ formatAmount(row.materialCost) }}</template>
              </el-table-column>
              <el-table-column label="工艺成本" width="140">
                <template #default="{ row }">{{ formatAmount(row.processCost) }}</template>
              </el-table-column>
              <el-table-column label="总成本" width="140">
                <template #default="{ row }">{{ formatAmount(row.totalCost) }}</template>
              </el-table-column>
              <el-table-column label="变化" width="140">
                <template #default="{ row }">
                  <span :class="row.delta > 0 ? 'text-danger' : row.delta < 0 ? 'text-success' : ''">
                    {{ row.delta > 0 ? '+' : '' }}{{ formatAmount(row.delta) }}
                  </span>
                </template>
              </el-table-column>
            </el-table>

            <div>
              <h3 class="section-title">当前 BOM 明细</h3>
              <el-table :data="presentation.bomItems" border stripe>
                <el-table-column prop="inventoryCode" label="物料编码" min-width="160" />
                <el-table-column prop="inventoryName" label="物料名称" min-width="180" />
                <el-table-column prop="quantity" label="用量" width="100" />
                <el-table-column prop="stockUom" label="单位" width="90" />
                <el-table-column prop="supplierName" label="供应商" min-width="160" />
                <el-table-column label="单价" width="140">
                  <template #default="{ row }">{{ formatAmount(row.unitCost) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="关联入口">
          <div class="quick-links">
            <button class="quick-link" type="button" @click="router.push('/files')">
              <strong>文件管理</strong>
              <span class="subtle-text">按产品文件 / 新型号文件查看当前版本资料</span>
            </button>
            <button class="quick-link" type="button" @click="router.push('/production-orders')">
              <strong>测试管理</strong>
              <span class="subtle-text">查看当前产品的测试种类和测试记录</span>
            </button>
            <button class="quick-link" type="button" @click="router.push('/inventories')">
              <strong>物料库存</strong>
              <span class="subtle-text">从分类树中查看当前 BOM 相关物料</span>
            </button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </PageContainer>
</template>

<style scoped>
.detail-layout {
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.95fr);
}

.metric-card__value--small {
  font-size: 20px;
}

.info-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.cost-card {
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  overflow: hidden;
}

.cost-card__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 14px 16px;
  border: 0;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.cost-card__summary-right {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.cost-card__detail {
  padding: 0 16px 16px;
}

.cost-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--plm-color-border-light);
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.quick-link {
  display: flex;
  min-height: 112px;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.quick-link:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
}

.text-danger {
  color: var(--el-color-danger);
}

.text-success {
  color: var(--el-color-success);
}

@media (max-width: 1200px) {
  .detail-layout,
  .quick-links {
    grid-template-columns: 1fr;
  }
}
</style>
