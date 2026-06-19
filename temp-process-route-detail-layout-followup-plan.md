# 工艺路线详情页后续布局与交互调整方案

## 1. 任务理解

本次只停留在文档阶段，不修改实际前端代码。

本方案针对当前工艺路线详情页截图中的三个后续优化点进行沉淀：

1. 第一张图：把上方红框内的“概览信息区”下移到下方红框位置。
2. 第二张图：资料挂接表格中的“预览 / 添加”操作从表格最左侧调整到最右侧。
3. 第三张图：点击“产品详情”等联动项后，不直接跳转页面，而是在右侧红框区域展示简要信息；另外两个按钮同理。
4. 删除“确认门禁”这一模块，包括导航项和对应内容区。

边界要求：

- 只涉及前端。
- 只涉及工艺路线模块。
- 不修改后端、不新增接口、不改数据库。
- 不改产品、文件中心、质量、BOM 等其他模块页面。

## 2. 所属 PLM 业务链路

本次仍属于 `Process` 核心对象前端表达优化。

它服务的业务场景是：用户进入某条工艺路线详情后，在同一个页面内查看路线概要、工序明细、资料挂接、版本变更和联动影响，不需要频繁跳转到其他模块。

本次不新增任何根对象，不引入 `Routing`、`Operation`、`ECR`、`ECN` 等对象。

## 3. 涉及前端页面

计划后续只涉及：

- `plm-web/src/views/process/ProcessCenterView.vue`
- 如需要补 mock 展示内容，可涉及 `plm-web/src/mock/process.ts`
- 如需要补类型字段，可涉及 `plm-web/src/types/process.ts`

不涉及：

- `plm-server`
- 数据库
- 产品详情页
- 文件中心
- BOM 管理
- 质量管理
- 成本报价

## 4. 页面改造方案

## 4.1 概览信息区下移

### 当前问题

当前详情页顶部包含：

- 路线名称
- 面包屑路径
- 状态 / 产品详情
- 概览说明
- 工序数量、工艺成本、差异工序、当前门禁、路线类型等信息栅格

这些概览信息目前仍然出现在详情页上方。用户希望把第一张图上方红框里的概览信息移动到下方红框位置，也就是移动到导航条下方的内容区。

### 调整建议

详情页顶部只保留：

- 返回列表
- 工艺路线名称
- 面包屑路径
- 状态标签
- 顶部右侧操作入口

把原来顶部的 `overview-panel` 移到 `activeSection === 'overview'` 的内容区里。

调整后结构：

```text
详情页顶部
├─ 返回列表
├─ 路线名称
├─ 面包屑路径
└─ 状态 / 产品详情

导航条
├─ 概览
├─ 工序明细
├─ 资料挂接
├─ 版本变更
└─ 联动影响

概览 Tab 内容区
├─ 左侧：工序视图 / 说明
├─ 右侧：当前对象
└─ 下方或主区域：原概览信息栅格
```

### 代码调整示意

从顶部移除：

```vue
<section class="page-stack">
  <section class="overview-panel">
    ...
  </section>

  <section class="section-nav">
    <el-segmented v-model="activeSection" :options="sectionOptions" />
  </section>
</section>
```

改成顶部只保留导航：

```vue
<section class="section-nav">
  <el-segmented v-model="activeSection" :options="sectionOptions" class="section-switcher" />
</section>
```

再把概览信息移动到概览内容区：

```vue
<section v-if="activeSection === 'overview'" class="page-stack detail-section">
  <section class="split-grid overview-detail-grid">
    <article class="page-panel inner-panel">
      <div class="toolbar-row">
        <h3 class="section-title">工序视图</h3>
        <el-segmented ... />
      </div>
      <p class="page-panel-desc">概览区收纳原有数据板信息，具体工序在“工序明细”里查看和搜索。</p>
    </article>

    <article class="page-panel inner-panel">
      <h3 class="section-title">当前对象</h3>
      ...
    </article>
  </section>

  <section class="overview-panel">
    <div class="summary-box summary-box--note">
      <span class="subtle-text">概览</span>
      <strong>{{ activeDetail.overviewNote }}</strong>
    </div>

    <div class="overview-grid">
      <div v-for="item in overviewItems" :key="item.label" class="overview-item">
        <span class="subtle-text">{{ item.label }}</span>
        <strong :class="{ 'is-emphasis': item.emphasis }">{{ item.value }}</strong>
      </div>
    </div>
  </section>
</section>
```

## 4.2 资料挂接操作列移到最右

### 当前问题

第二张图中，“预览 / 添加”操作列在表格最左侧。用户要求它放到表格最右边。

### 调整建议

资料挂接表字段顺序调整为：

1. 工序
2. SOP
3. SIP
4. 参数表
5. 检验标准
6. 状态
7. 最近更新
8. 操作

操作列建议 `fixed="right"`，在横向滚动时保持可见。

### 代码调整示意

当前：

```vue
<el-table-column label="操作" width="150" fixed="left">
  ...
</el-table-column>
<el-table-column prop="operationName" label="工序" min-width="150" />
```

调整为：

```vue
<el-table-column prop="operationName" label="工序" min-width="150" />
<el-table-column prop="sopCount" label="SOP" width="70" />
<el-table-column prop="sipCount" label="SIP" width="70" />
<el-table-column prop="parameterSheetCount" label="参数表" width="90" />
<el-table-column prop="qualitySpecCount" label="检验标准" width="90" />
<el-table-column label="状态" width="100">
  ...
</el-table-column>
<el-table-column prop="updatedAt" label="最近更新" width="140" />
<el-table-column label="操作" width="150" fixed="right">
  <template #default="{ row }">
    <div class="table-actions">
      <el-button link type="primary" @click="previewAttachment(row)">预览</el-button>
      <el-button link type="success" :disabled="row.canAdd === false" @click="addAttachment(row)">添加</el-button>
    </div>
  </template>
</el-table-column>
```

## 4.3 联动影响改为右侧简要信息预览

### 当前问题

第三张图中，“联动影响”左侧有三个按钮：

- 产品详情
- 质量管理
- 文件中心

当前点击后会跳转到对应业务页面，用户希望点击后在右侧红框区域出现简要信息。其他两个按钮同理。

### 调整建议

将“联动影响”改成左右分栏：

```text
联动影响
├─ 左侧：联动项列表
│  ├─ 产品详情
│  ├─ 质量管理
│  └─ 文件中心
└─ 右侧：简要信息面板
   ├─ 标题
   ├─ 摘要
   ├─ 关键字段
   └─ 可选：查看完整页面按钮
```

交互方式：

- 默认选中第一个联动项。
- 点击“产品详情”，右侧展示当前产品简要信息。
- 点击“质量管理”，右侧展示质量/测试简要信息。
- 点击“文件中心”，右侧展示资料冻结/缺失简要信息。
- 如果仍需保留跳转，可在右侧信息面板底部放一个“查看完整页面”按钮，而不是点击左侧卡片直接跳转。

### 需要新增的前端状态

```ts
const activeImpactLabel = ref('')
```

加载详情后默认选中第一项：

```ts
watch(
  () => activeDetail.value?.routeId,
  () => {
    activeImpactLabel.value = activeDetail.value?.impacts[0]?.label || ''
  },
  { immediate: true }
)
```

计算当前联动项：

```ts
const activeImpact = computed(() => {
  const impacts = activeDetail.value?.impacts || []
  return impacts.find((item) => item.label === activeImpactLabel.value) || impacts[0] || null
})
```

### 简要信息内容建议

由于本次只做前端、不接后端，右侧简要信息可以先基于现有 `activeDetail` 和 `impact.summary` 组织。

产品详情：

- 产品编码：`activeDetail.productCode`
- 产品名称：`activeDetail.productName`
- 工艺版本：`activeDetail.versionNo`
- 当前状态：`activeDetail.status`
- 当前门禁：`activeDetail.currentGate`

质量管理：

- 门禁结果：`passedGate`
- 质量确认状态：从 `confirmations` 中取 `质量确认`
- 检验标准齐套情况：从 `attachments` 统计 `qualitySpecCount`

文件中心：

- 资料总数：SOP / SIP / 参数表 / 检验标准合计
- 缺失项：`attachments.status === 'missing'`
- 最近更新时间：取附件记录中的 `updatedAt`

### 代码结构示意

```vue
<section v-else class="page-stack detail-section">
  <div class="toolbar-row">
    <div>
      <h3 class="section-title">联动影响</h3>
      <p class="page-panel-desc">点击左侧联动项，在右侧查看当前路线相关摘要。</p>
    </div>
  </div>

  <div class="impact-preview-layout">
    <div class="impact-list">
      <button
        v-for="item in activeDetail.impacts"
        :key="item.label"
        class="impact-card"
        :class="{ 'is-active': activeImpact?.label === item.label }"
        type="button"
        @click="activeImpactLabel = item.label"
      >
        <div class="toolbar-row">
          <strong>{{ item.label }}</strong>
          <el-icon><ArrowRight /></el-icon>
        </div>
        <p class="page-panel-desc">{{ item.summary }}</p>
      </button>
    </div>

    <article v-if="activeImpact" class="impact-preview-panel">
      <div class="toolbar-row">
        <h3 class="section-title">{{ activeImpact.label }}</h3>
        <el-button link type="primary" @click="openTarget(activeImpact.targetPath)">查看完整页面</el-button>
      </div>
      <p class="page-panel-desc">{{ activeImpact.summary }}</p>

      <div class="overview-grid overview-grid--compact">
        <div v-for="item in activeImpactSummaryItems" :key="item.label" class="overview-item">
          <span class="subtle-text">{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </article>
  </div>
</section>
```

## 4.4 删除“确认门禁”模块

### 当前问题

当前详情页导航包含：

- 概览
- 工序明细
- 确认门禁
- 资料挂接
- 版本变更
- 联动影响

用户明确要求删除“确认门禁”这一模块。

### 调整建议

删除内容：

1. `DetailSectionKey` 中的 `'gates'`
2. `sectionOptions` 中的 `{ label: '确认门禁', value: 'gates' }`
3. 模板中 `activeSection === 'gates'` 的整段内容

是否删除类型和 mock：

- 本次建议只从页面上删除，不删除 `types/process.ts` 中的 `ProcessConfirmationRecord`、`ProcessGateCheck` 和 mock 中的 `confirmations`、`gateChecks`。
- 原因是这些数据未来可能仍被概览、状态判断或其他位置复用；删除页面模块即可满足当前 UI 要求，避免扩大影响。

### 代码调整示意

当前：

```ts
type DetailSectionKey = 'overview' | 'operations' | 'gates' | 'attachments' | 'changes' | 'impacts'
```

调整为：

```ts
type DetailSectionKey = 'overview' | 'operations' | 'attachments' | 'changes' | 'impacts'
```

当前：

```ts
const sectionOptions = [
  { label: '概览', value: 'overview' },
  { label: '工序明细', value: 'operations' },
  { label: '确认门禁', value: 'gates' },
  { label: '资料挂接', value: 'attachments' },
  { label: '版本变更', value: 'changes' },
  { label: '联动影响', value: 'impacts' }
] as const
```

调整为：

```ts
const sectionOptions = [
  { label: '概览', value: 'overview' },
  { label: '工序明细', value: 'operations' },
  { label: '资料挂接', value: 'attachments' },
  { label: '版本变更', value: 'changes' },
  { label: '联动影响', value: 'impacts' }
] as const
```

删除模板：

```vue
<section v-else-if="activeSection === 'gates'" class="page-stack detail-section">
  ...
</section>
```

## 5. 预计代码改动文件

### 5.1 `plm-web/src/views/process/ProcessCenterView.vue`

预计改动：

- 调整 `DetailSectionKey`。
- 调整 `sectionOptions`。
- 移动 `overview-panel` 到概览 Tab 内容区。
- 删除确认门禁 Tab 内容区。
- 将资料挂接操作列从 `fixed="left"` 改为 `fixed="right"` 并移动到最后。
- 新增 `activeImpactLabel`、`activeImpact`、`activeImpactSummaryItems`。
- 将联动影响区改为左侧列表 + 右侧摘要面板。

### 5.2 `plm-web/src/types/process.ts`

原则上不需要改。

如果后续希望 mock 数据更清晰，可以给 `ProcessImpactLink` 扩展可选字段：

```ts
export interface ProcessImpactLink {
  label: string
  summary: string
  targetPath: string
  detailItems?: Array<{
    label: string
    value: string | number
  }>
}
```

但本轮建议不新增字段，直接通过 `activeDetail` 计算右侧摘要，减少类型改动。

### 5.3 `plm-web/src/mock/process.ts`

原则上不需要改。

如果希望右侧摘要更丰富，可在 `impacts` 中补充 `detailItems`，但这不是必须项。

## 6. 预期页面效果

### 6.1 概览区

调整后详情页顶部更轻，只保留路线标题和路径。原本在上方的大块概览信息会进入“概览”Tab 的内容区，页面视觉重心下移，和截图中下方红框位置一致。

### 6.2 资料挂接

“预览 / 添加”出现在表格最右侧，符合常见表格操作习惯。用户先看资料状态，再在最右边操作，扫描路径更自然。

### 6.3 联动影响

用户点击“产品详情 / 质量管理 / 文件中心”后，右侧立即展示摘要，不再被迫跳离当前工艺路线详情页。这样能保留上下文，也方便快速比对当前路线对其他业务的影响。

### 6.4 确认门禁

导航条减少一项，详情页信息密度降低。用户主要围绕概览、工序明细、资料挂接、版本变更、联动影响五个区块操作。

## 7. 风险点与待确认

1. 删除“确认门禁”只是前端隐藏，还是要彻底删除相关数据展示口径，需要业务确认。本方案建议先只隐藏页面模块。
2. 联动影响右侧摘要目前可由现有 mock 计算，不需要新增接口；后续接后端时再考虑是否由接口返回摘要字段。
3. “产品详情”点击后展示摘要还是仍保留“查看完整页面”入口，需要确认。本方案建议保留右侧面板内的二级跳转按钮。
4. 概览信息下移后，详情页顶部会更空，需要保证导航和内容区间距舒服，不要形成大片空白。

## 8. 交付结论

这次后续调整的核心是让详情页更像一个单页工作台：

- 顶部只负责说明当前对象。
- 概览信息放回概览内容区。
- 表格操作放到最右，符合操作习惯。
- 联动影响不立即跳转，先在右侧给用户看摘要。
- 删除确认门禁，减少当前页面层级。

后续进入代码阶段时，建议优先修改 `ProcessCenterView.vue`，并尽量不动类型和 mock，除非右侧摘要信息需要更丰富的数据支撑。
