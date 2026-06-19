# 35-工序字段、时间轴操作、BOM 对比、时间筛选、订单筛选与报表联动前端改造方案

## 1. 这次要解决什么

这次先不改实际代码，先把前端改造方案沉淀清楚。  
要处理的内容一共 6 组：

1. 工艺路线里的工序表，新增 `工序编码` 字段；`工序类型` 只保留 `半成品` 和 `成品`
2. 产品详情里的项目时间轴，推进弹窗旁边补一个 `驳回` 按钮
3. 产品详情里的 `BOM 版本对比`，点击某个版本后，右边或下边同步显示该版本 BOM 明细，方便对比
4. 工艺路线页、库存页都加一个 `项目时间筛选组件`
5. 需求订单页里，`进行中的订单` 也要加“客户需求 / 市场需求”切换组件
6. 报表入口页里，点击每个报表中的数据板块，要能进入该状态对应的列表

本轮只出文档，不改代码、不改后端。

## 2. 按我的理解，应该怎么改

### 2.1 工艺路线页

当前工序明细表的问题不是缺表格，而是字段表达不够清楚。

你要的调整有两个重点：

1. 增加 `工序编码`
2. `工序类型` 不再显示一堆工艺类别，而是只显示：
   - `半成品`
   - `成品`

这里我的理解是：

- `工序名称` 继续表示“注塑成型、CNC 修边、贴合组装”这些具体动作
- `工序编码` 用来做唯一识别，比如 `PROC-INJ-010`
- `工序类型` 用来表示这个工序产出的阶段属性，是“半成品工序”还是“成品工序”

也就是说，后面页面上应该同时存在：

- 工序编码
- 工序名称
- 工序类型（仅半成品 / 成品）

而不是让“成型、修边、组装”继续放在 `工序类型` 里。

### 2.2 产品详情时间轴

现在时间轴里有：

- 查看节点
- 确认推进
- 强制推进

但没有 `驳回`

这会导致一个问题：

- 当前节点如果资料不全、测试不通过、审批意见不通过，只能“卡住”，不能明确做回退动作

所以建议补一个：

- `驳回` 按钮

并且驳回要配一个弹窗，至少说明：

- 驳回到哪个节点
- 驳回原因
- 是否通知责任人

前端这轮可以先做假数据交互，不接后端。

### 2.3 产品详情里的 BOM 版本对比

你提的这个点很对。  
现在是：

- 上面一张版本对比表
- 下面一个明细 tab

虽然逻辑通了，但“对比感”不够直观。

更好的前端方式是：

1. 点击版本行
2. 右侧或下方立即出现该版本的 BOM 明细
3. 当前被点中的版本要高亮
4. 最好再加一个“与当前默认版本差异提示”

我建议优先做成：

- 上：版本对比表
- 下：当前选中版本的 BOM 明细

原因很简单：

- 结构最稳
- 响应式最好处理
- 不会把右侧挤得太窄

如果以后还要更强的对比感，可以升级成左右双栏：

- 左边版本列表
- 右边版本明细

### 2.4 工艺路线页和库存页加时间筛选

你要的筛选项是：

- 全部
- 最近三天
- 最近一星期
- 最近一个月
- 半年

这类筛选适合提成一个通用前端组件，后面别的页面也能复用。

建议做一个通用组件：

- 组件名：`ProjectTimeRangeFilter.vue`

它只负责：

- 展示几种时间范围按钮
- 输出选中的值

页面自己去决定：

- 如何过滤数据

这样工艺路线页和库存页都能直接用。

### 2.5 需求订单页

现在历史订单已经有：

- 客户需求
- 市场需求 / 自主开发

但进行中的订单没有这个切换，所以查看逻辑不一致。

建议改成：

- `进行中`
  - 客户需求
  - 市场需求 / 自主开发
- `历史订单`
  - 客户需求
  - 市场需求 / 自主开发

这样用户不需要切到历史区，才能按需求来源筛选。

### 2.6 报表入口页

现在报表中心里虽然有指标和异常项，但“点击指标进入对应状态列表”的链路还不够完整。

建议把报表页里的每个数据板都变成真正可点击的入口：

- 例如“逾期 12 个”
  - 点击后跳到逾期项目列表
- 例如“待确认 8 个”
  - 点击后跳到待确认列表

这里前端上可以先做：

- 指标卡按钮化
- 给每个指标配置 `targetPath`
- 点击后带 query 参数跳转

比如：

- `/projects?report_status=delayed`
- `/products?report_status=pending_confirm`

## 3. 涉及哪些文件

这次如果正式开发，主要会动这些文件。

### 3.1 工艺路线相关

- `plm-web/src/views/process/ProcessCenterView.vue`
- `plm-web/src/types/process.ts`

### 3.2 产品详情相关

- `plm-web/src/views/product/ProductDetail.vue`
- `plm-web/src/types/foundation.ts`

### 3.3 库存相关

- `plm-web/src/views/inventory/InventoryCenterView.vue`

### 3.4 订单相关

- `plm-web/src/views/order/OrderCenterView.vue`

### 3.5 报表相关

- `plm-web/src/views/report/ReportCenterView.vue`
- `plm-web/src/types/foundation.ts`

### 3.6 通用组件

建议新增：

- `plm-web/src/components/ProjectTimeRangeFilter/index.vue`

## 4. 每一项具体怎么改

---

## 4.1 工艺路线：工序编码 + 工序类型改造

### 修改位置 1：`plm-web/src/types/process.ts`

先改类型定义，把字段补进去。

```ts
export interface ProcessOperationRecord {
  operationId: number

  // 新增：工序编码，页面表格直接显示这一列
  operationCode: string

  sequenceNo: number
  operationName: string

  // 修改：工序类型不再随便传文本，统一限制为半成品/成品
  operationType: 'semi_finished' | 'finished'

  workstationName: string
  supplierName: string | null
  parameterSummary: string
  qualityRequirement: string
  unitCost: number
  leadDays: number
  attachmentStatus: ProcessAttachmentStatus
  isKeyProcess: boolean
  isExternalOperation: boolean
  isDifferenceOperation: boolean
  changedInCurrentVersion: boolean
}
```

### 修改位置 2：`plm-web/src/views/process/ProcessCenterView.vue`

工序明细表里新增“工序编码”列，并把工序类型显示成中文。

```vue
<!-- 放在工序名称前面，更符合用户阅读顺序 -->
<el-table-column prop="operationCode" label="工序编码" min-width="140" />

<el-table-column prop="operationName" label="工序名称" min-width="150" />

<el-table-column label="工序类型" min-width="120">
  <template #default="{ row }">
    {{ row.operationType === 'semi_finished' ? '半成品' : '成品' }}
  </template>
</el-table-column>
```

### 修改后的效果

- 工艺路线页的工序表更像正式台账
- 用户可以直接通过工序编码定位工序
- 工序类型不会再混乱，统一只看“半成品 / 成品”

---

## 4.2 产品时间轴：增加驳回按钮

### 修改位置：`plm-web/src/views/product/ProductDetail.vue`

在时间轴卡片操作区新增驳回按钮。

```vue
<div class="timeline-card__actions">
  <el-button size="small" @click="selectTimelineNode(node.nodeKey)">查看节点</el-button>

  <el-button
    v-if="node.status === 'current'"
    size="small"
    type="danger"
    plain
    @click="openActionDialog(node, 'reject')"
  >
    驳回
  </el-button>

  <el-button
    v-if="node.status === 'current'"
    size="small"
    type="primary"
    :disabled="!canSubmitNormalAdvance(node)"
    @click="openActionDialog(node, 'advance')"
  >
    确认推进
  </el-button>
</div>
```

同时脚本里要把动作类型扩展：

```ts
type TimelineActionMode = 'advance' | 'force' | 'reject'
```

弹窗里增加驳回专用说明：

```ts
const dialogTitle = computed(() => {
  if (dialogState.mode === 'force') return '强制推进确认'
  if (dialogState.mode === 'reject') return '节点驳回确认'
  return '节点推进确认'
})
```

### 修改后的效果

- 时间轴不只有“往前推”，也有“往回退”
- 节点处理更完整
- 项目推进状态更贴近真实业务

---

## 4.3 产品详情：BOM 版本对比更直观

### 修改位置：`plm-web/src/views/product/ProductDetail.vue`

现在就有：

- `presentation.bomCompareRows`
- `selectedBomVersion`
- `selectedBomItems`

所以不需要推翻重做，只要把明细展示区做得更明显。

建议改法：

```vue
<section class="bom-compare-layout">
  <!-- 上半部分：版本对比 -->
  <div class="bom-compare-layout__table">
    <el-table
      :data="presentation.bomCompareRows"
      border
      stripe
      highlight-current-row
      @row-click="(row) => selectBomVersion(row.versionNo)"
    >
      <!-- 版本对比列 -->
    </el-table>
  </div>

  <!-- 下半部分：当前选中版本的明细 -->
  <div class="bom-compare-layout__detail">
    <div class="toolbar-row">
      <div>
        <h4 class="section-title">{{ selectedBomTitle }}</h4>
        <p class="page-panel-desc">点击上方版本行后，这里同步展示对应版本的 BOM 明细。</p>
      </div>
    </div>

    <el-table :data="selectedBomItems" border stripe>
      <!-- BOM 明细列 -->
    </el-table>
  </div>
</section>
```

### 修改后的效果

- 用户点一下版本，下面马上看到对应明细
- 不用再切来切去
- 更容易直观看出不同版本的 BOM 差异

---

## 4.4 通用时间筛选组件

### 建议新增文件：`plm-web/src/components/ProjectTimeRangeFilter/index.vue`

```vue
<script setup lang="ts">
const model = defineModel<string>({ default: 'all' })

const options = [
  { label: '全部', value: 'all' },
  { label: '最近三天', value: '3d' },
  { label: '最近一星期', value: '7d' },
  { label: '最近一个月', value: '30d' },
  { label: '半年', value: '180d' }
]
</script>

<template>
  <el-segmented v-model="model" :options="options" />
</template>
```

### 使用位置 1：`ProcessCenterView.vue`

```vue
<!-- 放在 SearchBar 下方或右侧工具区 -->
<ProjectTimeRangeFilter v-model="timeRange" />
```

```ts
// 新增：工艺路线页面时间筛选状态
const timeRange = ref<'all' | '3d' | '7d' | '30d' | '180d'>('all')
```

### 使用位置 2：`InventoryCenterView.vue`

```vue
<!-- 放在搜索框旁边 -->
<ProjectTimeRangeFilter v-model="timeRange" />
```

### 修改后的效果

- 工艺路线和库存页可以快速按时间范围看项目
- 前端交互统一，后面别的页面也能复用

---

## 4.5 需求订单：进行中也加来源切换

### 修改位置：`plm-web/src/views/order/OrderCenterView.vue`

现在只有历史订单才显示来源筛选，建议改成：

```vue
<el-segmented
  v-model="sourceFilter"
  :options="[
    { label: '客户需求', value: 'customer' },
    { label: '市场需求', value: 'market_internal' }
  ]"
/>
```

脚本里新增统一筛选状态：

```ts
const sourceFilter = ref<OrderSource>('customer')
```

过滤逻辑改成不分 tab，都可以按来源筛选：

```ts
const filteredRows = computed(() => {
  return rows.filter((row) => {
    const tabMatched =
      activeTab.value === 'in_progress'
        ? ['draft', 'confirmed', 'in_production'].includes(row.status)
        : ['completed', 'closed'].includes(row.status)

    const sourceMatched = row.source === sourceFilter.value

    return tabMatched && sourceMatched
  })
})
```

### 修改后的效果

- 进行中和历史订单的筛选方式一致
- 用户更容易按“来源”维度排查订单

---

## 4.6 报表入口：指标卡点击进入状态列表

### 修改位置 1：`plm-web/src/types/foundation.ts`

先把报表指标补充跳转字段：

```ts
export interface ReportDetailSection {
  key: string
  title: string
  summary: string
  metrics: Array<{
    label: string
    value: string
    hint: string

    // 新增：点击指标后跳转到哪里
    targetPath: string
  }>
  alerts: ReportAlertItem[]
  distribution: ReportDistributionItem[]
}
```

### 修改位置 2：`plm-web/src/views/report/ReportCenterView.vue`

把指标卡改成可点击：

```vue
<button
  v-for="metric in currentDetail.metrics"
  :key="metric.label"
  class="metric-card metric-card--action"
  type="button"
  @click="openTarget(metric.targetPath)"
>
  <p class="metric-card__label">{{ metric.label }}</p>
  <p class="metric-card__value metric-card__value--small">{{ metric.value }}</p>
  <span class="metric-card__trend">{{ metric.hint }}</span>
</button>
```

### 修改后的效果

- 报表页不只是看数字
- 点数字就能进对应状态列表
- 报表入口真正变成“看板 + 跳转入口”

## 5. 这轮如果正式开发，建议顺序怎么排

建议顺序：

1. 先做 `工序编码 + 工序类型`
2. 再做 `时间轴驳回`
3. 再做 `BOM 版本对比联动`
4. 再抽 `时间筛选组件`
5. 再接 `订单来源筛选`
6. 最后做 `报表指标跳转`

原因：

- 前 3 项是你当前最直观能看到的业务优化
- 中间 2 项是通用筛选增强
- 最后一项是报表交互增强，适合放后面收口

## 6. 最后总结

按你的需求，这轮前端真正要改的核心不是“多加几个字段”，而是把几个页面从“能看”进一步升级成“更适合业务操作”：

1. 工艺路线页更像正式工艺台账
2. 时间轴具备推进和驳回两种动作
3. BOM 版本对比更直观
4. 工艺路线和库存页有统一时间筛选
5. 订单页按来源筛选更一致
6. 报表页从看数字升级成点数字进列表

---

本文件是实施前沉淀文档。  
当前没有修改任何实际代码。
