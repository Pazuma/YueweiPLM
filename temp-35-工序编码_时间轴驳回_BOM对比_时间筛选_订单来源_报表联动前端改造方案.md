# 35-工序编码、时间轴驳回、BOM 对比、时间筛选、订单来源、报表联动前端改造方案

## 1. 本次文档要解决什么

这次先不改实际代码，先把前端改造方案沉淀清楚，方便你先看方向对不对。

本轮只涉及前端，不涉及后端，重点覆盖 6 个改动点：

1. 工艺路线页面的工序表，新增 `工序编码` 字段，`类型` 只保留 `半成品 / 成品`
2. 产品详情页的项目时间轴，增加 `驳回` 按钮和对应弹窗交互
3. 产品详情页的 `BOM 版本对比`，点击版本后更直观展示该版本的 BOM 明细
4. 工艺路线页、库存管理页，增加统一的项目时间筛选组件
5. 需求订单页，进行中的订单也增加 `客户需求 / 市场需求` 切换
6. 报表入口板块中，点击对应数据板块后进入该状态的列表页

这份文档会按下面三个问题来展开：

- 改哪里
- 怎么改
- 改完后的页面效果是什么

同时也会把建议修改的代码片段写进去，并在代码里加上简短注释，方便后面真正开发时直接照着落。

---

## 2. 当前页面现状与我的理解

### 2.1 工艺路线页

当前工序表里已经有 `工序名称`、`工序类别` 等字段，但：

- 缺少便于追踪和定位的 `工序编码`
- `工序类别` 现在还是普通字符串，业务口径不够收敛

按你的要求，这里应该拆成两层信息：

- `工序编码`：用于唯一识别工序，例如 `PROC-INJ-010`
- `工序类型`：只表达产出阶段，只允许 `半成品 / 成品`

也就是说：

- “注塑成型 / 修边 / 贴合组装” 这些仍然属于 `工序名称`
- “半成品 / 成品” 才放在 `工序类型`

这样页面会更像正式工艺台账，而不是把不同维度的信息混在一个字段里。

### 2.2 产品详情时间轴

当前时间轴已经支持：

- 查看节点
- 确认推进
- 强制推进

但没有 `驳回`。  
这会导致一个现实问题：项目如果当前节点资料不全、样品未过、审批不通过，页面上没有明确的“退回处理”动作。

所以前端应该补上：

- 节点旁边增加 `驳回`
- 点击后弹出确认框
- 弹窗中说明驳回对象、驳回原因、责任提醒

这会让时间轴从“只能往前推”变成“可推进、可退回、可越权”，更贴近真实项目协同。

### 2.3 产品详情里的 BOM 版本对比

当前页面已经有：

- 版本对比表
- 选中版本的逻辑

说明底子其实已经有了，不需要推翻重做。

问题在于展示方式还不够直观。用户点击某个版本后，应该马上能在旁边或者下方看到该版本对应的 BOM 明细，这样才容易看出版本差异。

考虑到页面稳定性和响应式，我建议优先做成：

- 上方：版本对比表
- 下方：当前选中版本的 BOM 明细表

这是最稳妥的做法，后面如果还想强化对比感，再升级成左右双栏。

### 2.4 工艺路线页、库存页的时间筛选

你要求的筛选维度很明确：

- 全部
- 最近三天
- 最近一星期
- 最近一个月
- 半年

这类交互不适合每个页面各写一套，最好抽成一个通用组件，然后在工艺路线页、库存页复用。

这样有三个好处：

1. 交互统一
2. 后续别的页面也能复用
3. 维护成本低

### 2.5 需求订单页

当前需求订单页里：

- `历史订单` 已经有 `客户需求 / 市场 / 自主开发` 的来源筛选
- `进行中的订单` 还没有

这会造成一个体验问题：同一个页面里，用户只有切到历史区才有来源维度，进行中区反而不能按来源筛。

建议改成：

- 进行中和历史订单都共用同一套来源筛选

这样查询逻辑会更统一。

### 2.6 报表中心页

当前报表页其实已经有一些可复用基础：

- `targetPath`
- `openReport`
- `openTarget`

也就是说，这个页面不是完全没法做联动，而是已经铺了半层路。

这次要补的是：

- 把报表详情里的指标板块真正做成可点击入口
- 点击某个状态板后，跳到对应状态列表

这样报表中心就不只是“看数字”，而是“从数字直接进业务列表”。

---

## 3. 涉及文件清单

如果后续正式开发，这次主要会动这些前端文件。

### 3.1 工艺路线

- `plm-web/src/types/process.ts`
- `plm-web/src/views/process/ProcessCenterView.vue`

### 3.2 产品详情

- `plm-web/src/views/product/ProductDetail.vue`
- `plm-web/src/types/foundation.ts`

### 3.3 库存管理

- `plm-web/src/views/inventory/InventoryCenterView.vue`

### 3.4 需求订单

- `plm-web/src/views/order/OrderCenterView.vue`

### 3.5 报表中心

- `plm-web/src/views/report/ReportCenterView.vue`
- `plm-web/src/types/foundation.ts`

### 3.6 通用组件

建议新增：

- `plm-web/src/components/ProjectTimeRangeFilter/index.vue`

---

## 4. 具体怎么改

## 4.1 工艺路线页：新增工序编码，收敛工序类型

### 要改什么

把工艺路线页里的工序表调整成更标准的结构：

- 新增 `工序编码`
- `工序类别` 调整为 `工序类型`
- 类型值只允许：
  - `半成品`
  - `成品`

### 为什么这样改

因为页面里现在“名称”和“类型”的边界不够清楚。

后续改完后，用户会这样理解：

- `工序名称`：注塑、修边、组装
- `工序编码`：唯一编号
- `工序类型`：半成品 / 成品

这会更贴合工艺路线的业务表达。

### 需要修改的文件

#### 文件 1：`plm-web/src/types/process.ts`

把工序类型定义收窄，并新增工序编码字段。

建议代码如下：

```ts
export interface ProcessOperationRecord {
  operationId: number

  // 新增：工序编码，用于唯一识别工序，也方便表格检索和追踪
  operationCode: string

  sequenceNo: number
  operationName: string

  // 修改：工序类型不再使用自由文本，只保留半成品 / 成品两种业务口径
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

#### 文件 2：`plm-web/src/views/process/ProcessCenterView.vue`

在工序表中新增 `工序编码` 列，同时把 `工序类别` 显示改成中文业务值。

建议代码如下：

```vue
<!-- 放在工序名称前面，先看编码再看名称，更符合工艺台账阅读顺序 -->
<el-table-column prop="operationCode" label="工序编码" min-width="140" />

<el-table-column prop="operationName" label="工序名称" min-width="150" />

<el-table-column label="工序类型" min-width="120">
  <template #default="{ row }">
    <!-- 统一把内部枚举映射成业务可读中文 -->
    {{ row.operationType === 'semi_finished' ? '半成品' : '成品' }}
  </template>
</el-table-column>
```

### 改完后的页面效果

用户进入工艺路线页后，会直接看到一张更规整的工序表：

- 第一眼先看工序编码
- 再看工序名称
- 再看这个工序属于半成品还是成品阶段

页面会更清楚，也更适合后续做筛选、导出、对照。

---

## 4.2 产品详情页：时间轴增加驳回按钮

### 要改什么

在产品详情页的项目时间轴节点操作区，增加一个 `驳回` 按钮。

### 为什么这样改

现在时间轴只有推进，没有退回。  
但真实业务里，节点常常会出现：

- 样品不通过
- 图纸待补
- 测试不达标
- 审批退回

所以前端必须给出一个明确的驳回动作。

### 需要修改的文件

#### 文件：`plm-web/src/views/product/ProductDetail.vue`

当前代码里已经有：

- `TimelineActionMode = 'advance' | 'force'`
- `openActionDialog(node, mode)`
- 推进弹窗

说明这里很适合直接扩展，不需要新起一套逻辑。

建议代码如下：

```ts
// 修改：把时间轴动作扩展为推进、强制推进、驳回三种
type TimelineActionMode = 'advance' | 'force' | 'reject'
```

```ts
const dialogTitle = computed(() => {
  if (dialogState.mode === 'force') return '强制推进确认'
  if (dialogState.mode === 'reject') return '节点驳回确认'
  return '节点推进确认'
})
```

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

```vue
<el-input
  v-model="dialogState.note"
  type="textarea"
  :rows="dialogState.mode === 'reject' ? 4 : 3"
  :placeholder="
    dialogState.mode === 'reject'
      ? '请填写驳回原因、需补充内容和建议回退动作。'
      : '可填写推进备注、交接说明或提醒事项。'
  "
/>
```

### 改完后的页面效果

时间轴节点右侧会形成三类动作：

- 正常推进
- 强制推进
- 驳回

用户点击 `驳回` 后会弹窗说明原因。  
这样项目进度就不再只有“往前走”，也能“打回重做”，业务表达会完整很多。

---

## 4.3 产品详情页：BOM 版本对比点击后直出明细

### 要改什么

让用户在 BOM 版本对比区域点击某个版本后，页面下方或旁边立刻出现该版本的 BOM 明细。

### 为什么这样改

当前虽然有版本切换基础，但“看完版本，再去找明细”的感觉还不够顺。

用户真正想要的是：

1. 点一下版本
2. 马上看到这个版本的明细
3. 直观看出版本差异

### 需要修改的文件

#### 文件：`plm-web/src/views/product/ProductDetail.vue`

当前文件里已经有：

- `selectedBomVersion`
- `presentation.bomCompareRows`
- `selectedBomItems`

这说明数据选择逻辑已经存在，主要是把布局做得更直观。

建议代码如下：

```vue
<section class="bom-compare-layout">
  <!-- 上半部分：版本对比表 -->
  <div class="bom-compare-layout__table">
    <el-table
      :data="presentation.bomCompareRows"
      border
      stripe
      highlight-current-row
      @row-click="(row: { versionNo: string }) => selectBomVersion(row.versionNo)"
    >
      <!-- 这里保留当前版本对比列 -->
    </el-table>
  </div>

  <!-- 下半部分：当前选中版本的 BOM 明细 -->
  <div class="bom-compare-layout__detail">
    <div class="toolbar-row">
      <div>
        <h4 class="section-title">{{ selectedBomTitle }}</h4>
        <p class="page-panel-desc">
          点击上方版本行后，这里同步展示对应版本的 BOM 明细，方便直接查看差异。
        </p>
      </div>
    </div>

    <el-table :data="selectedBomItems" border stripe>
      <!-- 这里展示选中版本的 BOM 行项目 -->
    </el-table>
  </div>
</section>
```

### 改完后的页面效果

用户点某个 BOM 版本后：

- 上方版本表高亮当前选中版本
- 下方同步展示这个版本的 BOM 明细

这样就不需要来回切区域找内容，差异查看会直观很多。

---

## 4.4 工艺路线页、库存页：增加统一时间筛选组件

### 要改什么

抽一个统一的时间筛选组件，并在以下页面复用：

- 工艺路线页
- 库存管理页

筛选项固定为：

- 全部
- 最近三天
- 最近一星期
- 最近一个月
- 半年

### 为什么这样改

这类控件如果每个页面单独写，很快就会：

- 样式不统一
- 命名不一致
- 后面不好维护

最适合抽成一个通用组件。

### 需要新增的文件

#### 新增文件：`plm-web/src/components/ProjectTimeRangeFilter/index.vue`

建议代码如下：

```vue
<script setup lang="ts">
// 统一时间筛选值，后续别的页面也能直接复用
const model = defineModel<'all' | '3d' | '7d' | '30d' | '180d'>({ default: 'all' })

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

### 页面接入位置

#### 文件 1：`plm-web/src/views/process/ProcessCenterView.vue`

```ts
// 新增：工艺路线页的时间范围状态
const timeRange = ref<'all' | '3d' | '7d' | '30d' | '180d'>('all')
```

```vue
<!-- 放在搜索栏下方或页面工具条右侧 -->
<ProjectTimeRangeFilter v-model="timeRange" />
```

#### 文件 2：`plm-web/src/views/inventory/InventoryCenterView.vue`

```ts
// 新增：库存页的时间范围状态
const timeRange = ref<'all' | '3d' | '7d' | '30d' | '180d'>('all')
```

```vue
<!-- 放在库存搜索区域旁边，作为额外筛选条件 -->
<ProjectTimeRangeFilter v-model="timeRange" />
```

### 改完后的页面效果

工艺路线和库存页顶部都会多一个统一的时间筛选条。

用户想看：

- 最近三天
- 最近一周
- 最近一个月

直接点一下就能切。  
而且两个页面的操作方式完全一样，不用重新适应。

---

## 4.5 需求订单页：进行中订单也增加来源切换

### 要改什么

把订单来源筛选从“历史订单专属”改成“整个订单页共用”。

### 为什么这样改

当前页面里：

- 历史订单可以按来源筛
- 进行中的订单不能按来源筛

这在使用上是不连续的。

更合理的方式是：

- 不管是进行中还是历史，都能按 `客户需求 / 市场需求` 切换

### 需要修改的文件

#### 文件：`plm-web/src/views/order/OrderCenterView.vue`

目前文件里已经有：

- `activeTab`
- `historySource`

说明可以在现有结构上改，不必推翻页面。

建议调整为一个统一的 `sourceFilter`：

```ts
type OrderSource = 'customer' | 'market_internal'

// 修改：来源筛选不再只给历史订单使用，而是整个页面共用
const sourceFilter = ref<OrderSource>('customer')
```

```vue
<el-segmented
  v-model="sourceFilter"
  :options="[
    { label: '客户需求', value: 'customer' },
    { label: '市场需求', value: 'market_internal' }
  ]"
/>
```

```ts
const filteredRows = computed(() => {
  return rows.filter((row) => {
    const tabMatched =
      activeTab.value === 'in_progress'
        ? ['draft', 'confirmed', 'in_production'].includes(row.status)
        : ['completed', 'closed'].includes(row.status)

    // 统一来源筛选，进行中和历史都生效
    const sourceMatched = row.source === sourceFilter.value

    return tabMatched && sourceMatched
  })
})
```

### 改完后的页面效果

订单页顶部会出现统一来源切换：

- 客户需求
- 市场需求

用户切到 `进行中` 时也能按来源筛；切到 `历史订单` 时还是同一套操作逻辑。  
整个页面会更顺手。

---

## 4.6 报表中心页：点击数据板块进入对应状态列表

### 要改什么

让报表详情中的指标板块可以直接点击，并跳转到对应状态列表页。

### 为什么这样改

报表页如果只是显示：

- 待确认 8 个
- 逾期 12 个
- 风险 5 个

但点不进去，用户还是要自己回别的页面找列表，效率会比较差。

所以这次建议把每个数据板块都变成真正的业务入口。

### 需要修改的文件

#### 文件 1：`plm-web/src/types/foundation.ts`

这个文件里已经存在 `targetPath` 相关定义，但如果要统一所有指标卡跳转，建议确保详情指标结构明确带上该字段。

建议代码如下：

```ts
export interface ReportDetailSection {
  key: string
  title: string
  summary: string
  metrics: Array<{
    label: string
    value: string
    hint: string

    // 点击指标卡后跳转到的目标页面
    targetPath: string
  }>
  alerts: ReportAlertItem[]
  distribution: ReportDistributionItem[]
}
```

#### 文件 2：`plm-web/src/views/report/ReportCenterView.vue`

当前文件中已经有 `openTarget(targetPath)`，所以主要是把指标卡从普通展示改成可点击。

建议代码如下：

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

如果要进一步强调“这是可以点的”，样式上还可以补：

```css
.metric-card--action {
  cursor: pointer;
}

.metric-card--action:hover {
  transform: translateY(-1px);
}
```

### 改完后的页面效果

报表入口不再只是展示数字，而会变成真正可点击的入口。

例如：

- 点击“逾期 12 个” -> 进入逾期项目列表
- 点击“待确认 8 个” -> 进入待确认列表

这会让报表页真正承担“从概览到明细入口”的角色。

---

## 5. 推荐实施顺序

如果后面你确认要正式开发，我建议按这个顺序来：

1. 工艺路线页：工序编码 + 工序类型收敛
2. 产品详情页：时间轴驳回
3. 产品详情页：BOM 版本点击联动明细
4. 通用时间筛选组件
5. 订单页来源筛选统一
6. 报表指标卡跳转联动

### 为什么这样排

因为前 3 项是最直接影响业务操作的：

- 工艺路线看得更清楚
- 时间轴动作更完整
- BOM 对比更直观

后 3 项则更偏向统一交互和入口效率，放后面推进会更顺。

---

## 6. 涉及技术与实现方式

这次方案里主要用到的前端技术点是：

- `Vue 3 <script setup>`
- `TypeScript` 类型收敛
- `Element Plus`
  - `el-table`
  - `el-button`
  - `el-input`
  - `el-segmented`
- 已有 `router.push` 路由跳转
- 现有页面状态管理和 `computed / ref`

整体上不需要引入新框架，也不需要改后端接口，属于基于现有页面结构的增强式改造。

---

## 7. 改完后的整体收益

如果这 6 项都落下去，页面会有几个很明显的变化：

1. 工艺路线页更像正式业务台账，不再字段混杂
2. 产品时间轴既能推进，也能驳回，项目动作完整
3. BOM 版本差异查看更直接，不用来回切区域
4. 工艺路线页和库存页有统一时间维度筛选
5. 订单页筛选逻辑统一，进行中和历史都好用
6. 报表页真正具备“点指标直达列表”的业务入口能力

---

## 8. 本次沉淀结论

这份文档是实施前沉淀文档，当前结论是：

- 已明确本轮只做前端，不涉及后端
- 已明确每个需求该改哪个文件
- 已明确建议代码写法
- 已明确改完后的页面效果

当前还没有修改任何实际业务代码。  
如果你确认这份方案没问题，下一步就可以按这份文档逐项落前端。

