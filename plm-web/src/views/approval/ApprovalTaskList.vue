<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getApprovalTasks, getApprovalTemplateOptions, getApprovalTemplates } from '@/api/modules/approval'
import PageContainer from '@/components/PageContainer/index.vue'
import SearchBar from '@/components/SearchBar/index.vue'
import StatusTag from '@/components/StatusTag/index.vue'
import { useTable } from '@/composables/useTable'
import type { ApprovalTask, ApprovalTemplate, ApprovalTemplateNode, SearchField } from '@/types/common'

type ApprovalView = 'tasks' | 'templates'

interface TemplateOptionState {
  objectTypes: Array<{ label: string; value: string }>
  flowTypes: string[]
  statuses: Array<{ label: string; value: string }>
  roleOptions: Array<{ label: string; value: string }>
  userOptions: Array<{ label: string; value: number; roleName: string }>
}

const route = useRoute()
const router = useRouter()

const taskRows = ref<ApprovalTask[]>([])
const templateRows = ref<ApprovalTemplate[]>([])
const loading = ref(false)
const activeView = ref<ApprovalView>('tasks')
const drawerVisible = ref(false)
const editingTemplateId = ref<number | null>(null)

const templateOptions = reactive<TemplateOptionState>({
  objectTypes: [],
  flowTypes: [],
  statuses: [],
  roleOptions: [],
  userOptions: []
})

const searchFields: SearchField[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '业务对象 / 审批节点 / 发起人 / 审批人' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      { label: '待处理', value: 'pending' },
      { label: '已通过', value: 'approved' },
      { label: '已驳回', value: 'rejected' }
    ]
  }
]

const table = useTable(taskRows, ['objectName', 'nodeName', 'initiator', 'approver'], (row, filters) => {
  const status = String(filters.status || '')
  return !status || row.status === status
})

const templateForm = reactive<ApprovalTemplate>({
  templateId: 0,
  templateName: '',
  objectType: 'product',
  flowType: '',
  status: 'draft',
  description: '',
  updatedAt: '',
  nodes: []
})

const taskSummary = computed(() => ({
  pending: taskRows.value.filter((item) => item.status === 'pending').length,
  approved: taskRows.value.filter((item) => item.status === 'approved').length,
  rejected: taskRows.value.filter((item) => item.status === 'rejected').length
}))

const templateSummary = computed(() => ({
  total: templateRows.value.length,
  active: templateRows.value.filter((item) => item.status === 'active').length,
  draft: templateRows.value.filter((item) => item.status === 'draft').length
}))

const filteredUserOptions = (roleName: string) =>
  templateOptions.userOptions.filter((item) => !roleName || item.roleName === roleName)

function cloneTemplate(template: ApprovalTemplate) {
  return structuredClone(template)
}

function createEmptyNode(stepNo: number): ApprovalTemplateNode {
  return {
    nodeId: Date.now() + stepNo,
    stepNo,
    nodeName: '',
    approverRole: '',
    approverUserName: '',
    isGate: false,
    note: ''
  }
}

function createEmptyTemplate() {
  templateForm.templateId = 0
  templateForm.templateName = ''
  templateForm.objectType = 'product'
  templateForm.flowType = templateOptions.flowTypes[0] || ''
  templateForm.status = 'draft'
  templateForm.description = ''
  templateForm.updatedAt = ''
  templateForm.nodes = [createEmptyNode(1)]
}

function openTask(task: ApprovalTask) {
  router.push(task.targetPath || '/approval-tasks')
}

function openCreateTemplate() {
  editingTemplateId.value = null
  createEmptyTemplate()
  drawerVisible.value = true
}

function openEditTemplate(template: ApprovalTemplate) {
  editingTemplateId.value = template.templateId
  Object.assign(templateForm, cloneTemplate(template))
  drawerVisible.value = true
}

function addNode() {
  templateForm.nodes.push(createEmptyNode(templateForm.nodes.length + 1))
}

function removeNode(index: number) {
  if (templateForm.nodes.length === 1) {
    ElMessage.warning('审批模板至少保留一个节点。')
    return
  }
  templateForm.nodes.splice(index, 1)
  resequenceNodes()
}

function moveNode(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= templateForm.nodes.length) return
  const current = templateForm.nodes[index]
  templateForm.nodes[index] = templateForm.nodes[target]
  templateForm.nodes[target] = current
  resequenceNodes()
}

function resequenceNodes() {
  templateForm.nodes.forEach((node, index) => {
    node.stepNo = index + 1
  })
}

function handleRoleChange(node: ApprovalTemplateNode) {
  const firstMatch = filteredUserOptions(node.approverRole)[0]
  node.approverUserName = firstMatch ? firstMatch.label.split(' / ')[0] : ''
}

function handleUserChange(node: ApprovalTemplateNode, userId: number) {
  const user = templateOptions.userOptions.find((item) => item.value === userId)
  node.approverUserName = user ? user.label.split(' / ')[0] : ''
}

function saveTemplate() {
  if (!templateForm.templateName.trim()) {
    ElMessage.warning('请先填写审批模板名称。')
    return
  }

  const incompleteNode = templateForm.nodes.find(
    (node) => !node.nodeName.trim() || !node.approverRole.trim() || !node.approverUserName.trim()
  )

  if (incompleteNode) {
    ElMessage.warning('请完成所有审批节点的名称、角色和审批人配置。')
    return
  }

  const payload = cloneTemplate({
    ...templateForm,
    templateId: editingTemplateId.value || Date.now(),
    updatedAt: '2026-06-10'
  })

  const index = templateRows.value.findIndex((item) => item.templateId === payload.templateId)
  if (index >= 0) {
    templateRows.value.splice(index, 1, payload)
    ElMessage.success('审批模板已更新。')
  } else {
    templateRows.value.unshift(payload)
    ElMessage.success('审批模板已新增。')
  }

  drawerVisible.value = false
}

async function loadData() {
  loading.value = true
  try {
    const [tasks, templates, options] = await Promise.all([
      getApprovalTasks(),
      getApprovalTemplates(),
      getApprovalTemplateOptions()
    ])
    taskRows.value = tasks
    templateRows.value = templates
    templateOptions.objectTypes = options.objectTypes
    templateOptions.flowTypes = options.flowTypes
    templateOptions.statuses = options.statuses
    templateOptions.roleOptions = options.roleOptions
    templateOptions.userOptions = options.userOptions
    createEmptyTemplate()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  table.setQuery({
    keyword: String(route.query.keyword || ''),
    status: String(route.query.status || '')
  })
  await loadData()
})
</script>

<template>
  <PageContainer title="审批中心" description="统一处理审批任务，并配置审批模板、关键门禁节点和节点审批人。">
    <template #actions>
      <el-segmented
        v-model="activeView"
        :options="[
          { label: '审批任务', value: 'tasks' },
          { label: '审批模板', value: 'templates' }
        ]"
      />
      <el-button v-if="activeView === 'templates'" type="primary" @click="openCreateTemplate">新增审批模板</el-button>
    </template>

    <template v-if="activeView === 'tasks'">
      <section class="metric-grid">
        <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'pending' })">
          <p class="metric-card__label">待处理</p>
          <p class="metric-card__value">{{ taskSummary.pending }}</p>
          <span class="metric-card__trend">优先处理当前待审节点</span>
        </button>
        <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'approved' })">
          <p class="metric-card__label">已通过</p>
          <p class="metric-card__value">{{ taskSummary.approved }}</p>
          <span class="metric-card__trend">查看最近已完成的审批链路</span>
        </button>
        <button class="metric-card summary-button" type="button" @click="table.setQuery({ ...table.query, status: 'rejected' })">
          <p class="metric-card__label">已驳回</p>
          <p class="metric-card__value">{{ taskSummary.rejected }}</p>
          <span class="metric-card__trend">定位异常节点和退回原因</span>
        </button>
        <button class="metric-card summary-button" type="button" @click="table.resetQuery({ keyword: '', status: '' })">
          <p class="metric-card__label">全部任务</p>
          <p class="metric-card__value">{{ taskRows.length }}</p>
          <span class="metric-card__trend">恢复完整审批任务列表</span>
        </button>
      </section>

      <SearchBar
        :fields="searchFields"
        :model-value="table.query"
        @search="table.setQuery"
        @reset="table.resetQuery({ keyword: '', status: '' })"
      />

      <section class="page-panel" v-loading="loading">
        <el-table :data="table.pagedRows.value" border stripe @row-click="openTask">
          <el-table-column prop="objectName" label="业务对象" min-width="260" />
          <el-table-column prop="nodeName" label="审批节点" min-width="180" />
          <el-table-column prop="initiator" label="发起人" width="120" />
          <el-table-column prop="approver" label="审批人" width="120" />
          <el-table-column prop="dueDate" label="截止日期" width="140" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" object-type="product" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openTask(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="toolbar-row pager-row">
          <span class="subtle-text">共 {{ table.filteredRows.value.length }} 条</span>
          <el-pagination
            v-model:current-page="table.currentPage.value"
            v-model:page-size="table.pageSize.value"
            layout="prev, pager, next"
            :total="table.filteredRows.value.length"
          />
        </div>
      </section>
    </template>

    <template v-else>
      <section class="metric-grid">
        <div class="metric-card">
          <p class="metric-card__label">模板总数</p>
          <p class="metric-card__value">{{ templateSummary.total }}</p>
          <span class="metric-card__trend">统一管理新产品线、新型号线与冻结发布流程</span>
        </div>
        <div class="metric-card">
          <p class="metric-card__label">启用中</p>
          <p class="metric-card__value">{{ templateSummary.active }}</p>
          <span class="metric-card__trend">当前可直接被业务对象调用</span>
        </div>
        <div class="metric-card">
          <p class="metric-card__label">草稿中</p>
          <p class="metric-card__value">{{ templateSummary.draft }}</p>
          <span class="metric-card__trend">尚未正式启用的流程模板</span>
        </div>
      </section>

      <section class="split-grid template-layout" v-loading="loading">
        <article class="page-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">审批模板列表</h3>
              <p class="page-panel-desc">模板负责定义“走哪些节点、谁来审批、哪些是关键门禁”。</p>
            </div>
          </div>

          <div class="page-stack">
            <button
              v-for="template in templateRows"
              :key="template.templateId"
              class="template-card"
              type="button"
              @click="openEditTemplate(template)"
            >
              <div class="toolbar-row">
                <div class="cell-stack">
                  <strong>{{ template.templateName }}</strong>
                  <span class="subtle-text">{{ template.flowType }} / {{ template.objectType }}</span>
                </div>
                <el-tag :type="template.status === 'active' ? 'success' : template.status === 'draft' ? 'warning' : 'info'" effect="light">
                  {{ template.status === 'active' ? '启用' : template.status === 'draft' ? '草稿' : '停用' }}
                </el-tag>
              </div>
              <p class="page-panel-desc">{{ template.description }}</p>
              <div class="detail-row">
                <span>节点 {{ template.nodes.length }} 个</span>
                <span>{{ template.updatedAt }}</span>
              </div>
            </button>
          </div>
        </article>

        <article class="page-panel rules-panel">
          <div class="toolbar-row">
            <div>
              <h3 class="section-title">配置规则提醒</h3>
              <p class="page-panel-desc">当前只做前端模板配置，但规则语义要和审批规范保持一致。</p>
            </div>
          </div>
          <div class="page-stack">
            <div class="rule-card">
              <strong>发起人与确认人分离</strong>
              <p class="page-panel-desc">关键门禁节点不建议由同一人同时发起和审批，避免流程失真。</p>
            </div>
            <div class="rule-card">
              <strong>关键门禁节点必须指定明确审批人</strong>
              <p class="page-panel-desc">立项、开模、版本冻结、正式发布这类节点必须落到角色和具体人员。</p>
            </div>
            <div class="rule-card">
              <strong>先配角色，再配人员</strong>
              <p class="page-panel-desc">先明确岗位职责，再匹配具体人，便于后续衔接权限体系和审批流引擎。</p>
            </div>
          </div>
        </article>
      </section>
    </template>

    <el-drawer v-model="drawerVisible" title="审批模板配置" size="760px">
      <div class="page-stack">
        <section class="template-form-grid">
          <el-input v-model="templateForm.templateName" placeholder="模板名称，例如：新型号线差异发布审批" />
          <el-select v-model="templateForm.objectType" placeholder="适用对象">
            <el-option v-for="item in templateOptions.objectTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="templateForm.flowType" placeholder="适用流程">
            <el-option v-for="item in templateOptions.flowTypes" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="templateForm.status" placeholder="模板状态">
            <el-option v-for="item in templateOptions.statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </section>

        <el-input
          v-model="templateForm.description"
          type="textarea"
          :rows="3"
          placeholder="描述这个模板用于哪个业务场景、控制哪些关键门禁。"
        />

        <div class="toolbar-row">
          <div>
            <h3 class="section-title">审批节点配置</h3>
            <p class="page-panel-desc">每个节点都可以指定审批角色、审批人和是否为关键门禁。</p>
          </div>
          <el-button type="primary" plain @click="addNode">新增节点</el-button>
        </div>

        <div class="page-stack">
          <article v-for="(node, index) in templateForm.nodes" :key="node.nodeId" class="node-card">
            <div class="toolbar-row">
              <strong>第 {{ node.stepNo }} 节点</strong>
              <div class="node-actions">
                <el-button text @click="moveNode(index, -1)">上移</el-button>
                <el-button text @click="moveNode(index, 1)">下移</el-button>
                <el-button text type="danger" @click="removeNode(index)">删除</el-button>
              </div>
            </div>

            <div class="node-grid">
              <el-input v-model="node.nodeName" placeholder="节点名称，例如：版本冻结" />
              <el-select v-model="node.approverRole" placeholder="审批角色" @change="handleRoleChange(node)">
                <el-option v-for="item in templateOptions.roleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select
                :model-value="templateOptions.userOptions.find((item) => item.label.startsWith(node.approverUserName))?.value"
                placeholder="审批人"
                @update:model-value="handleUserChange(node, Number($event))"
              >
                <el-option
                  v-for="item in filteredUserOptions(node.approverRole)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>

            <div class="node-grid node-grid--secondary">
              <el-switch v-model="node.isGate" active-text="关键门禁" inactive-text="普通节点" />
              <el-input v-model="node.note" placeholder="节点说明，例如：确认 BOM / 工艺 / 图纸齐套后方可冻结。" />
            </div>
          </article>
        </div>

        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveTemplate">保存模板</el-button>
        </div>
      </div>
    </el-drawer>
  </PageContainer>
</template>

<style scoped>
.summary-button,
.template-card {
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.summary-button:hover,
.template-card:hover {
  border-color: var(--plm-color-primary);
  box-shadow: var(--plm-shadow-md);
  transform: translateY(-1px);
}

.pager-row {
  margin-top: 16px;
}

.template-layout {
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
}

.template-card,
.rule-card,
.node-card {
  padding: 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
}

.rules-panel {
  align-self: start;
}

.template-form-grid,
.node-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.node-grid--secondary {
  grid-template-columns: 180px minmax(0, 1fr);
  margin-top: 12px;
}

.node-actions,
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--plm-color-text-secondary);
  font-size: var(--plm-font-size-sm);
}

@media (max-width: 1200px) {
  .template-layout,
  .template-form-grid,
  .node-grid,
  .node-grid--secondary {
    grid-template-columns: 1fr;
  }
}
</style>
