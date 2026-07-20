<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, ref, watch } from 'vue'

import type { BomRoute } from '@/types/bom'
import { getEnabledColorCodes, type CodeItem } from '@/api/modules/code'

const props = defineProps<{ modelValue: boolean; routes: BomRoute[]; loading?: boolean }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'save', value: BomRoute[]): void }>()
type EditableBomRoute = BomRoute & { selectedColorIds: number[] }
const draft = ref<EditableBomRoute[]>([])
const colorCodes = ref<CodeItem[]>([])

function cloneRoutes(routes: BomRoute[]) {
  return (JSON.parse(JSON.stringify(routes)) as BomRoute[]).map(route => ({
    ...route, selectedColorIds: (route.colorItems || []).map(color => color.codeItemId)
  }))
}

watch(() => props.modelValue, (visible) => {
  if (visible) draft.value = cloneRoutes(props.routes)
}, { immediate: true })

function addRoute() {
  draft.value.push({ processId: 0, routeCode: '', routeName: '', colors: [], colorItems: [], selectedColorIds: [], items: [] })
}

function addItem(route: BomRoute) {
  route.items.push({
    lineNo: route.items.length + 1,
    itemCode: '',
    itemName: '',
    quantity: 1,
    unit: 'PCS',
    lossRate: 0,
    unitCost: 0
  })
}

function updateColors(route: BomRoute, ids: number[]) {
  route.colorItems = colorCodes.value.filter(color => ids.includes(color.codeItemId)).map(color => ({
    codeItemId: color.codeItemId, codeValue: color.codeValue, codeName: color.codeName
  }))
  route.colors = route.colorItems.map(color => color.codeName)
}

function save() {
  const colors = new Set<string>()
  for (const route of draft.value) {
    if (!route.processId || !route.routeCode.trim() || !route.routeName.trim() || !route.colors.length) {
      ElMessage.warning('请完整填写路线、工艺 ID 和适用颜色')
      return
    }
    for (const color of route.colors) {
      if (colors.has(color)) {
        ElMessage.warning(`颜色“${color}”只能归属一条有效路线`)
        return
      }
      colors.add(color)
    }
    if (route.items.some((item) => !item.itemName.trim() || item.quantity <= 0 || (item.lossRate || 0) < 0 || (item.lossRate || 0) > 1 || (item.unitCost || 0) < 0)) {
      ElMessage.warning('请检查物料名称、数量、损耗率和单价')
      return
    }
  }
  emit('save', draft.value.map(({ selectedColorIds, ...route }) => JSON.parse(JSON.stringify(route)) as BomRoute))
}

onMounted(async () => { colorCodes.value = await getEnabledColorCodes() })
</script>

<template>
  <el-drawer :model-value="modelValue" title="维护工艺路线与路线 BOM" size="min(920px, 94vw)" @close="emit('update:modelValue', false)">
    <div class="editor-tools"><el-button :icon="Plus" @click="addRoute">新增路线</el-button></div>
    <div class="route-editor-list">
      <section v-for="(route, index) in draft" :key="index" class="route-editor">
        <div class="route-editor__header">
          <el-input-number v-model="route.processId" :min="1" controls-position="right" placeholder="工艺 ID" />
          <el-input v-model="route.routeCode" placeholder="路线编码" />
          <el-input v-model="route.routeName" placeholder="路线名称" />
          <el-checkbox-group v-model="route.selectedColorIds" class="route-color-codes" @change="updateColors(route, route.selectedColorIds)">
            <el-checkbox v-for="color in colorCodes" :key="color.codeItemId" :value="color.codeItemId" border>
              {{ color.codeValue }} · {{ color.codeName }}
            </el-checkbox>
          </el-checkbox-group>
          <el-button data-test="route-item-add" :icon="Plus" @click="addItem(route)">添加物料</el-button>
          <el-button :icon="Delete" circle title="移除路线" @click="draft.splice(index, 1)" />
        </div>
        <div class="item-table-wrap">
          <table class="item-table">
            <thead><tr><th>物料编码</th><th>物料名称</th><th>规格</th><th>数量</th><th>单位</th><th>损耗率</th><th>单价</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="!route.items.length"><td colspan="8" class="item-table__empty">暂无物料明细</td></tr>
              <tr v-for="(item, itemIndex) in route.items" :key="item.productBomItemId || itemIndex">
                <td><el-input v-model="item.itemCode" data-test="route-item-code" /></td>
                <td><el-input v-model="item.itemName" data-test="route-item-name" /></td>
                <td><el-input v-model="item.specification" /></td>
                <td><el-input-number v-model="item.quantity" data-test="route-item-quantity" :min="0.000001" :precision="6" /></td>
                <td><el-input v-model="item.unit" /></td>
                <td><el-input-number v-model="item.lossRate" data-test="route-item-loss-rate" :min="0" :max="1" :precision="4" /></td>
                <td><el-input-number v-model="item.unitCost" data-test="route-item-unit-cost" :min="0" :precision="4" /></td>
                <td><el-button :icon="Delete" circle title="移除物料" @click="route.items.splice(itemIndex, 1)" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button data-test="route-editor-save" type="primary" :loading="loading" @click="save">保存路线与 BOM</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.editor-tools { margin-bottom: 12px; }
.route-editor-list { display: grid; gap: 8px; }
.route-editor { display: grid; gap: 10px; padding: 10px 0 16px; border-bottom: 1px solid var(--el-border-color-lighter); }
.route-editor__header { display: grid; grid-template-columns: 120px 140px minmax(150px, 1fr) minmax(180px, 1fr) auto 34px; align-items: center; gap: 8px; }
.route-color-codes { display: flex; flex-wrap: wrap; gap: 6px; max-height: 84px; overflow-y: auto; padding: 5px; border: 1px solid var(--el-border-color); border-radius: 5px; }
.route-color-codes :deep(.el-checkbox) { margin: 0; }
.item-table-wrap { overflow-x: auto; }
.item-table { width: 100%; min-width: 930px; border-collapse: collapse; table-layout: fixed; }
.item-table th, .item-table td { padding: 6px; border: 1px solid var(--el-border-color-lighter); text-align: left; }
.item-table th { color: var(--plm-color-text-secondary); background: var(--el-fill-color-light); font-size: 12px; font-weight: 600; }
.item-table th:nth-child(1), .item-table th:nth-child(3) { width: 130px; }
.item-table th:nth-child(4), .item-table th:nth-child(6), .item-table th:nth-child(7) { width: 120px; }
.item-table th:nth-child(5) { width: 80px; }
.item-table th:last-child { width: 52px; }
.item-table__empty { color: var(--plm-color-text-secondary); text-align: center !important; }
@media (max-width: 900px) { .route-editor__header { grid-template-columns: 1fr 1fr; } }
</style>
