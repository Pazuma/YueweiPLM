# PLM Web M3 Timeline Actions Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the existing backend M3 timeline action APIs to the PLM web project detail current-node area, then document code changes and browser-based acceptance tests.

**Architecture:** Backend M3 endpoints already exist under `/api/v1/projects/{projectId}/timeline/{nodeKey}`. The frontend will replace placeholder action functions in `project.ts`, render actions only in the product detail "当前节点" area, and refresh timeline data after every successful action. No new backend entity, database field, or root Project object is introduced; `projectId` remains `productId`.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, existing Axios `request` wrapper, Java Spring Boot M3 backend APIs, PostgreSQL-backed product timeline fields.

## Global Constraints

- 默认使用中文与用户沟通。
- 只使用 Customer、Product、Order、ProductionOrder、Process、Inventory、Workstation 七个核心对象。
- M3 时间轴动作属于 Product 承载的项目推进能力。
- `projectId = productId`，不新增 Project 根对象。
- 接口统一使用 `/api/v1/...`。
- 失败不能伪装成空对象成功，必须展示后端业务错误。
- 动作成功后必须重新拉取 timeline，不能只靠前端本地改状态。
- return 必须填写 reason。
- M3 动作会真实修改数据库，前端必须加 loading 防止重复提交。
- M3 操作按钮放在“当前节点”区域，不放到每个时间轴节点卡片里。

---

## File Structure

- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\project.ts`
  - Replace M3 placeholder functions with typed POST calls.
  - Export `TimelineActionResultVO` and `TimelineActionPayload`.
  - Keep freeze/publish/archive/abandon placeholders unchanged.
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue`
  - Import M3 API functions and Element Plus message utilities.
  - Add current-node action state, labels, validation, dialogs, and timeline refresh helper.
  - Render confirm/advance/return buttons only in the current-node section.
- Create: `D:\Yuewei\git\YUEWEI\plm-server\docs\backend-notes\2026-07-13-PLM前端M3时间轴动作接入代码实现沉淀.md`
  - Record modified files, logic, manual frontend test steps, pass criteria, and actual verification results.
- Verify: backend M3 tests and frontend `type-check` / `build` commands where available.

---

### Task 1: Connect Frontend M3 API Functions

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\project.ts`

**Interfaces:**
- Consumes: existing `request.post(url, body)` and `unwrapResponse<T>(response)`.
- Produces:
  - `TimelineActionResultVO`
  - `TimelineActionPayload`
  - `confirmTimelineNode(projectId: number, nodeKey: string, remark?: string): Promise<TimelineActionResultVO>`
  - `advanceTimelineNode(projectId: number, nodeKey: string, remark?: string): Promise<TimelineActionResultVO>`
  - `returnTimelineNode(projectId: number, nodeKey: string, reason: string, returnToPrevious?: boolean): Promise<TimelineActionResultVO>`

- [ ] **Step 1: Inspect existing action placeholders**

Run:

```powershell
Select-String -Path ..\plm-web\src\api\modules\project.ts -Pattern "rejectUntestedM3Action|confirmTimelineNode|advanceTimelineNode|returnTimelineNode" -Context 2,8
```

Expected: the three M3 action functions still call `rejectUntestedM3Action()`.

- [ ] **Step 2: Add exported action result and payload types**

Add below `TimelineDetailVO`:

```ts
export interface TimelineActionResultVO {
  projectId: number
  productId: number
  action: 'confirm' | 'advance' | 'return'
  nodeKey: string
  beforeStepNo: number
  currentStepNo: number
  currentNodeKey: string
  currentNodeName: string
  currentConfirmed: boolean
  productStatus: string
  logId: number
}

export interface TimelineActionPayload {
  remark?: string
  reason?: string
  returnToPrevious?: boolean
}
```

- [ ] **Step 3: Replace only the three M3 placeholder functions**

Use this implementation:

```ts
export async function confirmTimelineNode(
  projectId: number,
  nodeKey: string,
  remark?: string
): Promise<TimelineActionResultVO> {
  const payload: TimelineActionPayload = remark?.trim() ? { remark: remark.trim() } : {}
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/confirm`, payload)
  return unwrapResponse<TimelineActionResultVO>(response)
}

export async function advanceTimelineNode(
  projectId: number,
  nodeKey: string,
  remark?: string
): Promise<TimelineActionResultVO> {
  const payload: TimelineActionPayload = remark?.trim() ? { remark: remark.trim() } : {}
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/advance`, payload)
  return unwrapResponse<TimelineActionResultVO>(response)
}

export async function returnTimelineNode(
  projectId: number,
  nodeKey: string,
  reason: string,
  returnToPrevious = true
): Promise<TimelineActionResultVO> {
  const cleanReason = reason.trim()
  if (!cleanReason) throw new Error('请填写退回原因')
  const response = await request.post(`/projects/${projectId}/timeline/${nodeKey}/return`, {
    reason: cleanReason,
    returnToPrevious
  })
  return unwrapResponse<TimelineActionResultVO>(response)
}
```

- [ ] **Step 4: Keep non-M3 placeholders untouched**

Confirm `freezeProject`, `publishProject`, `archiveProject`, and `abandonProject` still call `rejectUntestedM3Action()`.

Run:

```powershell
Select-String -Path ..\plm-web\src\api\modules\project.ts -Pattern "freezeProject|publishProject|archiveProject|abandonProject" -Context 0,4
```

Expected: M5 and abandon actions are still blocked unless separately implemented.

---

### Task 2: Add Current-Node Action State and Refresh Logic

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue`

**Interfaces:**
- Consumes:
  - `confirmTimelineNode(projectId, nodeKey, remark?)`
  - `advanceTimelineNode(projectId, nodeKey, remark?)`
  - `returnTimelineNode(projectId, nodeKey, reason, returnToPrevious?)`
  - `getProjectTimeline(projectId)`
- Produces:
  - `timelineActionLoading: Ref<false | 'confirm' | 'advance' | 'return'>`
  - `refreshProjectTimeline(projectId: number): Promise<void>`
  - `handleConfirmCurrentNode(): Promise<void>`
  - `handleAdvanceCurrentNode(): Promise<void>`
  - `handleReturnCurrentNode(returnToPrevious: boolean): Promise<void>`

- [ ] **Step 1: Update imports**

Replace the project API import with:

```ts
import {
  advanceTimelineNode,
  confirmTimelineNode,
  getProjects,
  getProjectTimeline,
  returnTimelineNode
} from '@/api/modules/project'
```

Add Element Plus utilities:

```ts
import { ElMessage, ElMessageBox } from 'element-plus'
```

- [ ] **Step 2: Add action loading state**

Add near other detail refs:

```ts
const timelineActionLoading = ref<false | 'confirm' | 'advance' | 'return'>(false)
```

- [ ] **Step 3: Preserve timeline action metadata on mapped nodes**

When mapping `timeline.nodes` to `ProductTimelineNode[]` in `openDetail`, include:

```ts
confirmed: node.confirmed,
canAdvance: node.status === 'current' && Boolean(node.confirmed),
canReject: node.status === 'current'
```

The mapped object must continue to include existing fields such as `nodeKey`, `nodeName`, `status`, `ownerRole`, `summary`, `nextAction`, `documentCount`, and `phaseName`.

- [ ] **Step 4: Add current timeline state computed values**

Add below `activeProductFlowNode`:

```ts
const currentTimelineConfirmed = computed(() => Boolean(activeProductFlowNode.value?.confirmed))

const currentTimelineActionLabel = computed(() => {
  if (!activeProductFlowNode.value) return '暂无当前节点'
  return currentTimelineConfirmed.value ? '可推进到下一节点' : '等待确认当前节点'
})

const canAdvanceCurrentTimelineNode = computed(() => {
  if (!activeProductFlowNode.value) return false
  if (activeProductFlowNode.value.status !== 'current') return false
  const nodes = detailPresentation.value?.timeline || []
  const currentIndex = nodes.findIndex((node) => node.nodeKey === activeProductFlowNode.value?.nodeKey)
  return currentTimelineConfirmed.value && currentIndex >= 0 && currentIndex < nodes.length - 1
})
```

- [ ] **Step 5: Add timeline refresh helper**

Add a helper used by open-detail load and action handlers:

```ts
async function refreshProjectTimeline(projectId: number) {
  if (!detailPresentation.value) return
  const timeline = await getProjectTimeline(projectId)
  const nodes: ProductTimelineNode[] = timeline.nodes.map((node) => ({
    nodeKey: node.nodeKey,
    nodeName: node.nodeName,
    status: node.status,
    ownerRole: node.ownerRole,
    summary: node.summary,
    nextAction: node.nextAction,
    riskNote: node.riskNote,
    gateLabel: node.gateLabel,
    detailLines: node.detailLines,
    receiverRole: node.receiverRole,
    receiverUserName: node.receiverUserName,
    receivedAt: node.receivedAt,
    promoterRole: node.promoterRole,
    promoterUserName: node.promoterUserName,
    promotedAt: node.promotedAt,
    experienceSummary: node.experienceSummary,
    documentCount: node.documentCount,
    phaseName: node.phaseName,
    confirmed: node.confirmed,
    canAdvance: node.status === 'current' && Boolean(node.confirmed),
    canReject: node.status === 'current'
  }))
  const currentNode = nodes.find((node) => node.status === 'current')
  detailPresentation.value = {
    ...detailPresentation.value,
    currentNode: currentNode?.nodeName || timeline.currentNode || detailPresentation.value.currentNode,
    nextNode: currentNode?.nextAction || detailPresentation.value.nextNode,
    timeline: nodes.length ? nodes : detailPresentation.value.timeline,
    summary:
      timeline.lastReason ||
      (timeline.lastAction ? getTimelineActionLabel(timeline.lastAction) : detailPresentation.value.summary)
  }
  if (detailTarget.value) {
    detailTarget.value = {
      ...detailTarget.value,
      currentStage: currentNode?.nodeName || detailTarget.value.currentStage,
      currentStepNo: timeline.currentStepNo
    }
  }
}
```

- [ ] **Step 6: Add action and error labels**

Add helpers:

```ts
function getTimelineActionLabel(action?: string | null) {
  if (action === 'confirm') return '已确认当前节点'
  if (action === 'advance') return '已推进到下一节点'
  if (action === 'return') return '已退回处理'
  return '暂无动作'
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}
```

---

### Task 3: Implement Confirm, Advance, and Return Handlers

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue`

**Interfaces:**
- Consumes: state and helpers from Task 2.
- Produces: action handlers bound by the template buttons.

- [ ] **Step 1: Implement confirm handler**

Add:

```ts
async function handleConfirmCurrentNode() {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt('可填写确认备注，留空则只确认当前节点。', '确认当前节点', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：资料已检查，进入可推进状态'
    })
    timelineActionLoading.value = 'confirm'
    await confirmTimelineNode(detailTarget.value.productId, activeProductFlowNode.value.nodeKey, String(value || ''))
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('节点已确认')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}
```

- [ ] **Step 2: Implement advance handler**

Add:

```ts
async function handleAdvanceCurrentNode() {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  if (!canAdvanceCurrentTimelineNode.value) {
    ElMessage.warning(currentTimelineConfirmed.value ? '最后一个节点不能继续推进' : '请先确认当前节点')
    return
  }
  try {
    await ElMessageBox.confirm('推进后项目会进入下一节点，新节点会回到未确认状态。', '推进下一节点', {
      confirmButtonText: '推进',
      cancelButtonText: '取消',
      type: 'warning'
    })
    timelineActionLoading.value = 'advance'
    await advanceTimelineNode(detailTarget.value.productId, activeProductFlowNode.value.nodeKey)
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('已推进到下一节点')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}
```

- [ ] **Step 3: Implement return handler**

Add:

```ts
async function handleReturnCurrentNode(returnToPrevious: boolean) {
  if (!detailTarget.value || !activeProductFlowNode.value || timelineActionLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      returnToPrevious ? '请输入退回上一节点的原因。' : '请输入退回当前节点修改的原因。',
      returnToPrevious ? '退回上一节点' : '退回当前节点修改',
      {
        confirmButtonText: '退回',
        cancelButtonText: '取消',
        inputPlaceholder: '必须填写，例如：图纸资料需补充',
        inputValidator: (value) => Boolean(String(value || '').trim()),
        inputErrorMessage: '请填写退回原因'
      }
    )
    timelineActionLoading.value = 'return'
    await returnTimelineNode(
      detailTarget.value.productId,
      activeProductFlowNode.value.nodeKey,
      String(value || ''),
      returnToPrevious
    )
    await refreshProjectTimeline(detailTarget.value.productId)
    await loadData()
    ElMessage.success('已退回处理')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(getErrorMessage(error))
  } finally {
    timelineActionLoading.value = false
  }
}
```

- [ ] **Step 4: Check duplicate submission guard**

Run:

```powershell
Select-String -Path ..\plm-web\src\views\project\ProjectCenterView.vue -Pattern "timelineActionLoading" -Context 1,4
```

Expected: every action handler returns early when loading is active, sets a loading value before API call, and resets it in `finally`.

---

### Task 4: Render Buttons Only in the Current Node Area

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue`

**Interfaces:**
- Consumes action state and handlers from Tasks 2-3.
- Produces visible Element Plus buttons inside the `activeDetailSection === 'current_node'` section only.

- [ ] **Step 1: Add current-node status details**

Inside the current node section, after the hero block and before the existing detail grid, add:

```vue
<div class="timeline-action-panel">
  <div class="timeline-action-panel__status">
    <el-tag :type="currentTimelineConfirmed ? 'success' : 'warning'" effect="light">
      {{ currentTimelineConfirmed ? '已确认' : '未确认' }}
    </el-tag>
    <span>{{ currentTimelineActionLabel }}</span>
  </div>
  <div class="timeline-action-panel__buttons">
    <el-button
      v-if="activeProductFlowNode && !currentTimelineConfirmed"
      type="primary"
      :loading="timelineActionLoading === 'confirm'"
      :disabled="Boolean(timelineActionLoading)"
      @click="handleConfirmCurrentNode"
    >
      确认当前节点
    </el-button>
    <el-button
      v-if="activeProductFlowNode && currentTimelineConfirmed"
      type="primary"
      :loading="timelineActionLoading === 'advance'"
      :disabled="Boolean(timelineActionLoading) || !canAdvanceCurrentTimelineNode"
      @click="handleAdvanceCurrentNode"
    >
      推进下一节点
    </el-button>
    <el-dropdown
      v-if="activeProductFlowNode"
      :disabled="Boolean(timelineActionLoading)"
      @command="(command: 'current' | 'previous') => handleReturnCurrentNode(command === 'previous')"
    >
      <el-button :loading="timelineActionLoading === 'return'">
        退回
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="current">退回当前节点修改</el-dropdown-item>
          <el-dropdown-item command="previous">退回上一节点</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</div>
```

- [ ] **Step 2: Add recent action info cards**

Add cards to the existing `.detail-grid`:

```vue
<div class="info-card">
  <span class="subtle-text">确认状态</span>
  <strong>{{ currentTimelineConfirmed ? '已确认' : '未确认' }}</strong>
</div>
<div class="info-card">
  <span class="subtle-text">当前节点操作</span>
  <strong>{{ currentTimelineActionLabel }}</strong>
</div>
```

Do not add confirm/advance/return buttons to the project-flow card list.

- [ ] **Step 3: Add minimal styles**

Add near existing current-node styles:

```css
.timeline-action-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.timeline-action-panel__status,
.timeline-action-panel__buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
```

- [ ] **Step 4: Mobile layout check**

Confirm existing media query stacks flex layouts at narrow widths. If needed, add:

```css
@media (max-width: 768px) {
  .timeline-action-panel {
    align-items: stretch;
    flex-direction: column;
  }
}
```

---

### Task 5: Write Implementation Deposition Document

**Files:**
- Create: `D:\Yuewei\git\YUEWEI\plm-server\docs\backend-notes\2026-07-13-PLM前端M3时间轴动作接入代码实现沉淀.md`

**Interfaces:**
- Consumes actual modified file list and verification results.
- Produces the user-requested deposition document.

- [ ] **Step 1: Create the document with required sections**

Use this structure:

```markdown
# PLM 前端 M3 时间轴动作接入代码实现沉淀

日期：2026-07-13

## 1. 本次目标

把后端 M3 时间轴动作接口接入前端项目详情页当前节点区域，支持确认当前节点、推进下一节点、退回当前节点修改、退回上一节点。

## 2. 引用文档

- D:\Yuewei\资料\PLM\docs\文件沉淀\开发提示词.md
- D:\Yuewei\资料\PLM\docs\README.md
- D:\Yuewei\资料\PLM\docs\01-开发框架总纲.md
- D:\Yuewei\资料\PLM\docs\02-系统架构设计.md
- D:\Yuewei\资料\PLM\docs\04-AI开发规范.md
- D:\Yuewei\资料\PLM\docs\05-数据模型与编码规范.md
- D:\Yuewei\资料\PLM\docs\07-权限与审批流规范.md
- D:\Yuewei\资料\PLM\docs\08-测试验收规范.md
- D:\Yuewei\资料\PLM\docs\文件沉淀\手机壳制造业PLM系统需求规格说明书-完善版.md
- D:\Yuewei\资料\PLM\docs\modules\01-产品主数据与SKU.md
- docs/backend-notes/2026-07-13-PLM前端M3时间轴动作接入实施前操作文档.md

## 3. 修改的代码文件

- D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\project.ts
- D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue

## 4. 修改的文档文件

- docs/backend-notes/2026-07-13-PLM前端M3时间轴动作接入代码实现沉淀.md

## 5. 代码逻辑

记录真实实现。

## 6. 作用功能

记录用户可执行的功能。

## 7. 前端测试步骤

记录浏览器直接测试步骤。

## 8. 每一步合格标准

记录 Network、页面状态、错误提示和刷新标准。

## 9. 实际验证结果

记录本次实际运行的命令和结果。

## 10. 后续维护建议

记录后续并发控制、权限细化、日志前端入口建议。
```

- [ ] **Step 2: Fill actual verification results after Task 6**

The actual result section must distinguish:

```text
已通过：命令退出码 0 的检查。
未执行：需要后端/前端服务或浏览器手工环境但本次未启动的检查。
失败：命令退出码非 0，并记录失败原因。
```

---

### Task 6: Verification

**Files:**
- Verify frontend and backend; update document from Task 5.

**Interfaces:**
- Consumes code changes and implementation document.
- Produces evidence before claiming completion.

- [ ] **Step 1: Run frontend type-check**

Run:

```powershell
cd D:\Yuewei\git\YUEWEI\plm-web
& "D:\Yuewei\program\node-v20.15.0-win-x64\npm.cmd" run type-check
```

Expected: exit code `0`, no TypeScript errors.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
cd D:\Yuewei\git\YUEWEI\plm-web
$env:VITE_API_BASE_URL="http://localhost:8080"
& "D:\Yuewei\program\node-v20.15.0-win-x64\npm.cmd" run build
```

Expected: exit code `0`, `dist` generated. Chunk size warnings can be recorded as non-blocking warnings.

- [ ] **Step 3: Run backend M3 regression tests**

Run:

```powershell
cd D:\Yuewei\git\YUEWEI\plm-server
& "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd" "-Dtest=TimelineActionServiceImplTest,TimelineControllerActionTest,TimelineServiceImplTest" test
```

Expected: `BUILD SUCCESS`, `Failures=0`, `Errors=0`.

- [ ] **Step 4: Manual browser test checklist**

Run through these frontend browser checks when services are available:

```text
1. Login as engineer01 / plm123456.
2. Open Project Center.
3. Open a non-production in-progress project.
4. Confirm current node:
   - Network has POST /api/v1/projects/{projectId}/timeline/{nodeKey}/confirm.
   - Response code=0 and data.action=confirm.
   - Page refreshes timeline and shows 已确认.
5. Advance current node:
   - Button is only available after confirmation.
   - Network has POST /advance.
   - Response code=0 and data.currentConfirmed=false.
   - Current node moves to the next node.
6. Return with empty reason:
   - Frontend blocks submit or backend returns explicit error.
   - Page does not show success.
7. Return current node:
   - Request body has returnToPrevious=false and reason.
   - Page refreshes and shows 未确认.
8. Return previous node:
   - Request body has returnToPrevious=true and reason.
   - Page current node moves back when backend allows it.
9. Non-current timeline cards:
   - No confirm/advance/return buttons are rendered on each card.
```

Expected: every successful action refreshes `GET /api/v1/projects/{projectId}/timeline`, and every failed action shows an error instead of success.

- [ ] **Step 5: Update implementation document with actual verification evidence**

Record command output summaries and manual test status in:

```text
docs/backend-notes/2026-07-13-PLM前端M3时间轴动作接入代码实现沉淀.md
```

---

## Self-Review

- Spec coverage: The plan covers M3 API wiring, current-node-only buttons, confirm/advance/return behavior, reason validation, loading guard, timeline refresh, frontend browser test criteria, command verification, and deposition documentation.
- Placeholder scan: No implementation step uses TBD/TODO/fill later. The document template has explicit sections and requires concrete results after verification.
- Type consistency: `TimelineActionResultVO`, `TimelineActionPayload`, `confirmTimelineNode`, `advanceTimelineNode`, and `returnTimelineNode` signatures match the backend DTO/VO and the Vue handlers.
