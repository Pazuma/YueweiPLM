<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'

import { createProduct, getProductDetail, updateProduct } from '@/api/modules/product'
import PageContainer from '@/components/PageContainer/index.vue'
import UserSelector from '@/components/UserSelector/index.vue'
import { mockUsers } from '@/mock/data'
import type { ProductFormPayload, ProductTestItem } from '@/types/product'
import { rules } from '@/utils/validate'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)

const productId = computed(() => Number(route.params.id || 0))
const isEdit = computed(() => Boolean(productId.value))

const defaultTestItems: ProductTestItem[] = [
  { name: '跌落测试', method: '1.2m 六面各两次', owner: '品质', frequency: '红样', result: '待执行', dueDate: '2026-06-20' },
  { name: '酒精测试', method: '95% 酒精 500 次', owner: '品质', frequency: '红样', result: '待执行', dueDate: '2026-06-21' },
  { name: '磁吸力测试', method: '标准磁吸治具', owner: '工程 / 品质', frequency: '试产前', result: '待执行', dueDate: '2026-06-22' }
]

const form = reactive<ProductFormPayload>({
  productCode: '',
  productName: '',
  seriesName: '',
  productType: 'product_line',
  ownerUserName: '',
  versionNo: 'A',
  material: '',
  packageType: '',
  surfaceProcess: '',
  coreProcess: '',
  composition: '',
  customerName: '',
  currentStage: 'Product 建档',
  expectedReleaseDate: '',
  model: '--',
  color: '--',
  estimatedCost: 0,
  estimatedCostCurrency: 'CNY',
  costBreakdown: [
    { category: '主材', amount: 0, ratio: 0, note: '' },
    { category: '功能件', amount: 0, ratio: 0, note: '' },
    { category: '包装', amount: 0, ratio: 0, note: '' },
    { category: '工艺', amount: 0, ratio: 0, note: '' }
  ],
  testItems: structuredClone(defaultTestItems)
})

const totalCost = computed(() => form.costBreakdown.reduce((sum, item) => sum + Number(item.amount || 0), 0))

const formRules = {
  productCode: [rules.required('产品编码')],
  productName: [rules.required('产品名称')],
  seriesName: [rules.required('系列名称')],
  productType: [rules.required('产品类型', 'change')],
  ownerUserName: [rules.required('责任人', 'change')],
  versionNo: [rules.required('版本号'), rules.versionNo()],
  estimatedCost: [rules.required('预估成本', 'change')]
}

function syncCostBreakdown() {
  form.estimatedCost = Number(totalCost.value.toFixed(2))
  const total = totalCost.value || 1
  form.costBreakdown.forEach((item) => {
    item.ratio = Number((Number(item.amount || 0) / total).toFixed(4))
  })
}

async function loadDetail() {
  if (!isEdit.value) {
    form.ownerUserName = mockUsers[0].userName
    return
  }

  loading.value = true
  try {
    const detail = await getProductDetail(productId.value)
    form.productCode = detail.basicInfo.productCode
    form.productName = detail.basicInfo.productName
    form.seriesName = detail.basicInfo.seriesName
    form.productType = detail.basicInfo.productTypeLabel.includes('SKU') ? 'model_variant' : 'product_line'
    form.ownerUserName = detail.basicInfo.ownerUserName
    form.versionNo = detail.basicInfo.versionNo
    form.material = detail.basicInfo.material
    form.packageType = detail.basicInfo.packageType
    form.surfaceProcess = detail.basicInfo.surfaceProcess
    form.coreProcess = detail.basicInfo.coreProcess
    form.composition = detail.basicInfo.composition
    form.customerName = detail.basicInfo.customerName
    form.currentStage = detail.basicInfo.currentStage
    form.expectedReleaseDate = detail.basicInfo.expectedReleaseDate || ''
    form.model = detail.basicInfo.model
    form.color = detail.basicInfo.color
    form.estimatedCost = detail.basicInfo.estimatedCost
    form.estimatedCostCurrency = detail.basicInfo.estimatedCostCurrency
    form.costBreakdown = structuredClone(detail.costBreakdown)
    form.testItems = structuredClone(detail.testItems)
  } finally {
    loading.value = false
  }
}

function addTestItem() {
  form.testItems.push({
    name: '',
    method: '',
    owner: '品质',
    frequency: '发布前',
    result: '待执行',
    dueDate: ''
  })
}

function removeTestItem(index: number) {
  form.testItems.splice(index, 1)
}

async function handleSubmit() {
  syncCostBreakdown()
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateProduct(productId.value, structuredClone(form))
      ElMessage.success('产品已更新')
      router.push(`/products/${productId.value}`)
      return
    }

    const created = await createProduct(structuredClone(form))
    ElMessage.success('产品已创建')
    router.replace(`/products/${created.productId}`)
  } finally {
    submitting.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <PageContainer
    :title="isEdit ? '编辑产品' : '新建产品'"
    description="围绕 Product 主档录入产品类型、测试项、预估成本和核心工艺信息，作为后续 BOM 与资料冻结的统一入口。"
  >
    <section class="page-panel" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <div class="detail-grid">
          <div>
            <el-form-item label="产品编码" prop="productCode">
              <el-input v-model="form.productCode" placeholder="例如 PRD-SC30-0001" />
            </el-form-item>
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" />
            </el-form-item>
            <el-form-item label="系列名称" prop="seriesName">
              <el-input v-model="form.seriesName" />
            </el-form-item>
            <el-form-item label="产品类型" prop="productType">
              <el-segmented
                v-model="form.productType"
                :options="[
                  { label: '新产品产品线', value: 'product_line' },
                  { label: '型号扩展 / SKU 视图', value: 'model_variant' }
                ]"
              />
            </el-form-item>
            <el-form-item label="责任人" prop="ownerUserName">
              <UserSelector
                :model-value="mockUsers.find((item) => item.userName === form.ownerUserName)?.userId || null"
                @update:model-value="
                  form.ownerUserName = mockUsers.find((item) => item.userId === Number($event))?.userName || ''
                "
              />
            </el-form-item>
            <el-form-item label="版本号" prop="versionNo">
              <el-input v-model="form.versionNo" />
            </el-form-item>
            <el-form-item label="客户来源">
              <el-input v-model="form.customerName" />
            </el-form-item>
          </div>

          <div>
            <el-form-item label="机型">
              <el-input v-model="form.model" :disabled="form.productType === 'product_line'" />
            </el-form-item>
            <el-form-item label="颜色">
              <el-input v-model="form.color" :disabled="form.productType === 'product_line'" />
            </el-form-item>
            <el-form-item label="材料">
              <el-input v-model="form.material" />
            </el-form-item>
            <el-form-item label="包装方式">
              <el-input v-model="form.packageType" />
            </el-form-item>
            <el-form-item label="表面工艺">
              <el-input v-model="form.surfaceProcess" />
            </el-form-item>
            <el-form-item label="核心工艺">
              <el-input v-model="form.coreProcess" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="组成说明">
              <el-input v-model="form.composition" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="预计发布时间">
              <el-date-picker v-model="form.expectedReleaseDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
          </div>
        </div>

        <section class="form-block">
          <div class="toolbar-row">
            <h3 class="section-title">预估成本拆分</h3>
            <el-tag effect="light">{{ form.estimatedCostCurrency }}</el-tag>
          </div>
          <div class="cost-grid">
            <div v-for="item in form.costBreakdown" :key="item.category" class="cost-card">
              <strong>{{ item.category }}</strong>
              <el-input-number v-model="item.amount" :min="0" :precision="2" :step="0.5" style="width: 100%" @change="syncCostBreakdown" />
              <el-input v-model="item.note" placeholder="补充说明" />
              <div class="subtle-text">占比 {{ Math.round(item.ratio * 100) }}%</div>
            </div>
          </div>
          <div class="cost-total">总预估成本：{{ totalCost.toFixed(2) }} {{ form.estimatedCostCurrency }}</div>
        </section>

        <section class="form-block">
          <div class="toolbar-row">
            <h3 class="section-title">测试项配置</h3>
            <el-button type="primary" plain @click="addTestItem">新增测试项</el-button>
          </div>
          <div class="page-stack">
            <div v-for="(item, index) in form.testItems" :key="`${item.name}-${index}`" class="test-card">
              <div class="test-card__grid">
                <el-input v-model="item.name" placeholder="测试项名称" />
                <el-input v-model="item.method" placeholder="测试方法" />
                <el-input v-model="item.owner" placeholder="责任人" />
                <el-input v-model="item.frequency" placeholder="阶段" />
                <el-input v-model="item.result" placeholder="结果状态" />
                <el-date-picker v-model="item.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="计划完成日" />
              </div>
              <div class="test-card__actions">
                <el-button text type="danger" @click="removeTestItem(index)">删除</el-button>
              </div>
            </div>
          </div>
        </section>

        <div class="toolbar-actions">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
        </div>
      </el-form>
    </section>
  </PageContainer>
</template>

<style scoped>
.form-block {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--plm-color-border-light);
}

.cost-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.cost-card,
.test-card {
  padding: 12px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
}

.cost-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cost-total {
  margin-top: 12px;
  font-weight: 600;
}

.test-card__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.test-card__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 1200px) {
  .cost-grid,
  .test-card__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .cost-grid,
  .test-card__grid {
    grid-template-columns: 1fr;
  }
}
</style>
