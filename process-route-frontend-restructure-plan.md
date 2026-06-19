# 工艺路线模块前端改造正式方案

## 1. 任务理解

本次只停留在文档方案阶段，不修改 `plm-web` 实际业务代码。改造对象限定为工艺路线模块，也就是 `Process` 核心对象的前端表达；不顺手调整产品、BOM、文件中心、成本、质量、生产等其他模块。

当前用户明确要求是：

- 去掉工艺路线页面顶部红框内模块。
- 筛选栏保留，筛选思路不重做。
- 工艺路线主页面改成“工艺产品列表”。
- 主页面字段固定为：编号、所属产品、名字、状态、进程、版本、详情。
- 点击详情后进入工艺路线详情列表，详情页继续承载原来的工序、门禁、资料、版本、联动等内容。
- 每个工艺路线详情页去除顶部数据板卡片，把数据板信息收进“概览”。
- 把“查看板块”从一个独立说明字段改成面包屑/路径信息，放在名称下面。
- 工序明细列表增加“确认人”标签和区内搜索组件。
- 资料部分最左边增加“预览”和“添加”操作。
- 版本变更增加“敲定”和“变更申请”动作。
- 页面要工整、舒服、方便，优先服务工程、采购、品质日常查找和确认。

本方案基于现有 `ProcessCenterView.vue` 已经具备的路线列表、详情分区、工序表、资料表、版本变更等结构继续收敛，不再回到早期“占位页”方案。

## 2. 引用文档与代码

已阅读并对齐以下文档：

- `D:\Yuewei\资料\PLM\docs\文件沉淀\开发提示词.md`
- `D:\Yuewei\资料\PLM\docs\README.md`
- `D:\Yuewei\资料\PLM\docs\01-开发框架总纲.md`
- `D:\Yuewei\资料\PLM\docs\02-系统架构设计.md`
- `D:\Yuewei\资料\PLM\docs\04-AI开发规范.md`
- `D:\Yuewei\资料\PLM\docs\05-数据模型与编码规范.md`
- `D:\Yuewei\资料\PLM\docs\07-权限与审批流规范.md`
- `D:\Yuewei\资料\PLM\docs\08-测试验收规范.md`
- `D:\Yuewei\资料\PLM\docs\文件沉淀\手机壳制造业PLM系统需求规格说明书-完善版.md`
- `D:\Yuewei\资料\PLM\docs\modules\06-工艺路线管理.md`
- `D:\Yuewei\资料\PLM\docs\22-工艺路线模块前端改造实施前确认.md`
- `D:\Yuewei\资料\PLM\docs\23-工艺路线模块业务优化与效率提升方案.md`
- `D:\Yuewei\资料\PLM\docs\24-工艺路线模块前端页面信息架构与区块清单.md`
- `D:\Yuewei\资料\PLM\docs\代码现状-工艺路线模块.md`
- `D:\Yuewei\资料\PLM\docs\模块分析-工艺路线.md`
- `D:\Yuewei\资料\PLM\docs\系统优化方案-流程与模块重构.md`
- `temp-process-route-restructure-plan.md`

已阅读现有代码：

- `plm-web/src/views/process/ProcessCenterView.vue`
- `plm-web/src/types/process.ts`
- `plm-web/src/mock/process.ts`
- `plm-web/src/api/modules/process.ts`

## 3. 所属 PLM 业务链路

本次属于 `Process` 核心对象的前端表达优化。

它服务两条业务主线：

- 新产品线：从零建立完整工艺路线，确认工序、参数、供应商、质量要求、资料和版本。
- 新型号线：继承父产品工艺路线，只突出差异工序、差异测试、增量资料和版本变更。

本次不新增 `Routing`、`Operation`、`ECR`、`ECN` 等根对象。工艺路线、工序、资料、变更均作为 `Process` 视图下的列表、明细、状态和动作表达。

## 4. 当前代码现状判断

当前 `ProcessCenterView.vue` 已经不是占位页，具备以下结构：

- `PageContainer` 标题与右上角操作按钮。
- 顶部 `metric-grid` 指标卡片。
- `SearchBar` 筛选栏，附带 `ProjectTimeRangeFilter`。
- 左侧路线卡片列表。
- 右侧路线详情面板。
- 详情内包含：路线概览、工序明细、确认门禁、资料挂接、版本变更、联动影响。

当前主要问题不在“没有内容”，而在入口层级和信息密度：

1. 顶部操作按钮与指标卡片占用第一屏，弱化了用户最需要的“先找产品/路线”。
2. 左侧路线卡片更像“选中器”，不适合作为主页面清单。
3. 详情页顶部还有一组数据板卡，和概览内容重复。
4. “查看板块”独立成块，像额外模块，不像自然导航路径。
5. 工序表缺少确认人字段，责任边界不够直观。
6. 工序明细没有区内搜索，长工序列表定位效率不高。
7. 资料挂接区只能看数量和状态，缺少直接“预览 / 添加”入口。
8. 版本变更区只展示记录，缺少“变更申请 / 敲定”动作感。

## 5. 页面改造方案

### 5.1 主页面：改为工艺产品列表

主页面结构建议固定为：

1. 页面标题与简短说明。
2. 筛选栏：保留现有 `SearchBar` 与时间范围组件，不改变整体筛选思路。
3. 工艺产品列表表格。

去掉：

- `PageContainer` 的右上角 `#actions` 操作区。
- 顶部 `metric-grid` 指标卡片区。
- 左侧路线卡片列表。
- 同屏右侧详情面板。

保留：

- 当前筛选字段：关键词、路线类型、状态、来源。
- 当前时间范围组件。
- `useTable` 的筛选逻辑。

主页面表格字段固定为：

| 字段 | 数据来源 | 展示建议 |
| --- | --- | --- |
| 编号 | `routeCode` | 工艺路线编号，如 `PROC-SC30-A` |
| 所属产品 | `productName` + `productCode` | 主行显示产品名，副行显示产品编码 |
| 名字 | `routeName` | 工艺路线名称 |
| 状态 | `status` | 使用 `StatusTag`，对象类型为 `process` |
| 进程 | `currentGate` | 当前门禁或推进阶段 |
| 版本 | `versionNo` | 如 `A`、`B` |
| 详情 | `routeId` | 按钮进入详情视图 |

交互建议：

- 点击“详情”后，通过同一路由 query 切换到详情态，例如 `/processes?routeId=501`。
- 详情页顶部提供返回列表按钮，返回 `/processes` 或清空 `routeId`。
- 表格行可以支持点击进入详情，但“详情”按钮必须保留，避免用户不知道入口。

### 5.2 详情页：从数据卡片页改成详情列表页

详情页建议仍在 `ProcessCenterView.vue` 内通过 `routeId` query 控制显示，不新增真实业务路由。

详情页结构：

1. 返回列表入口。
2. 工艺路线名称。
3. 名称下方的面包屑路径。
4. 概览信息区。
5. 分区导航。
6. 分区内容：工序明细、确认门禁、资料挂接、版本变更、联动影响。

名称下方的面包屑示例：

```text
工艺路线中心 / 超队 3.0 / PROC-SC30-A / 版本 A
```

或者：

```text
PROC-SC30-A / 超队 3.0 / 标准工艺路线 / 版本 A
```

这里取代现有“查看板块”说明字段。分区切换仍可保留 `el-segmented`，但不再用“查看板块”作为标题，不再写大段解释。

### 5.3 概览区：承接原数据板信息

现有详情页顶部 `routeMetrics` 包含：

- 工序数量
- 工艺成本
- 差异工序
- 当前门禁

本次不再用四张横向数据卡片展示，而是放入“概览”区的信息栅格。概览建议包含：

- 工序数量：`operations.length`
- 工艺成本：`totalCost`
- 差异工序：`differenceOperationCount`
- 当前门禁：`currentGate`
- 路线类型：`routeType`
- 模板来源：`templateSource`
- 负责人：`owner`
- 继承来源：`inheritedFrom`
- 当前状态：`status`
- 是否锁定：`isLocked`
- 门禁是否通过：`passedGate`

表现方式：

- 用紧凑两列或三列信息栅格。
- 每项固定为“标签 + 值”。
- 状态、门禁、锁定状态使用标签强调。
- 不再使用大号数字卡片，避免又变成另一组数据板。

### 5.4 工序明细：增加确认人和搜索

现有工序表字段保留：

- 顺序
- 工序编码
- 工序名称
- 工序类型
- 执行位置
- 供应商
- 核心参数摘要
- 质量要求
- 单工序成本
- 标记

新增：

- 确认人

确认人展示建议：

- 单独一列，列名为“确认人”。
- 主行显示姓名，如 `刘浩`。
- 副行显示角色或责任标签，如 `工程确认`、`采购确认`、`品质确认`。

工序搜索组件建议：

- 放在“工序明细”标题行右侧。
- 宽度约 240px 到 280px。
- 只搜索当前详情页内的工序，不影响主页面筛选栏。
- 搜索范围：`operationCode`、`operationName`、`workstationName`、`supplierName`、`confirmerName`、`confirmerRole`。

### 5.5 资料挂接：左侧增加预览和添加

资料区现有字段保留：

- 工序
- SOP
- SIP
- 参数表
- 检验标准
- 状态
- 最近更新

新增最左侧操作列：

- 预览
- 添加

推荐合并为一列“操作”，固定在最左侧，里面放两个小按钮：

- `预览`：查看该工序已挂接资料。
- `添加`：为该工序补充 SOP、SIP、参数表或检验标准。

本轮前端方案阶段只定义入口，不做真实文件上传、下载、权限校验或文件预览实现。

### 5.6 版本变更：增加敲定和变更申请

版本变更区现有变更记录保留：

- 版本
- 变更类型
- 变更原因
- 涉及工序
- 成本变化
- 交期变化
- 变更人
- 变更时间

新增区块级动作：

- `变更申请`
- `敲定`

位置建议：

- 放在“版本变更”区标题栏右侧。
- `变更申请` 使用普通按钮或描边按钮。
- `敲定` 使用主按钮。

含义建议：

- `变更申请`：发起新的工艺变更，后续应进入 `Process` 变更流程。
- `敲定`：表示当前变更内容已确认，可进入锁定或发布前状态。

本轮只做前端入口与 mock 状态字段规划，不实现真实审批、状态流转或后端动作接口。

## 6. 涉及修改的代码文件

后续进入代码阶段时，建议只涉及以下文件：

### 6.1 `plm-web/src/views/process/ProcessCenterView.vue`

主要改动：

1. 移除 `PageContainer` 的 `#actions`。
2. 移除主页面顶部 `metric-grid`。
3. 将 `split-grid process-grid` 改成列表态 / 详情态二选一。
4. 主页面新增工艺产品列表表格。
5. 用 `route.query.routeId` 判断进入详情态。
6. 详情页移除顶部 `nested-metrics`。
7. 将 `routeMetrics` 内容合并进概览区。
8. 将“查看板块”改成名称下方的面包屑路径。
9. 工序区新增 `operationKeyword` 与 `filteredOperationRows` 搜索逻辑。
10. 工序表新增确认人列。
11. 资料表最左侧新增操作列。
12. 版本变更标题栏新增 `变更申请` 和 `敲定`。

### 6.2 `plm-web/src/types/process.ts`

补充类型字段，见第 7 节。

### 6.3 `plm-web/src/mock/process.ts`

补充 mock 字段，见第 8 节。

### 6.4 `plm-web/src/api/modules/process.ts`

当前仍可保持 `getProcessCenterSnapshot()` 不变。只要 mock 数据结构与类型一致，API 层不需要拆接口。

如果后续要更像真实接口，可以再拆成：

- `getProcessRouteList()`
- `getProcessRouteDetail(routeId)`

但本轮不建议拆，以免扩大范围。

## 7. 需要补的类型字段

### 7.1 `ProcessRouteListItem`

当前主列表需要的字段基本已经齐全：

```ts
export interface ProcessRouteListItem {
  routeId: number
  routeCode: string
  routeName: string
  productId: number
  productCode: string
  productName: string
  versionNo: string
  status: CommonStatus
  currentGate: string
  targetPath: string
}
```

现有扩展字段如 `routeType`、`templateSource`、`owner`、`operationCount`、`totalCost`、`riskLevel` 可继续保留，但主页面不展示，避免主列表过重。

### 7.2 `ProcessOperationRecord`

新增：

```ts
confirmerName?: string
confirmerRole?: string
```

用途：

- 工序明细表“确认人”列。
- 工序区内搜索。
- 工程、采购、品质协同责任可见。

### 7.3 `ProcessAttachmentSummary`

新增：

```ts
previewPath?: string
canPreview?: boolean
canAdd?: boolean
```

用途：

- 控制“预览”按钮是否可用。
- 控制“添加”按钮是否可用。
- 为后续文件中心联动保留路径，不在本轮实现真实上传。

### 7.4 `ProcessChangeRecord`

新增：

```ts
canFinalize?: boolean
canApplyChange?: boolean
```

也可以放在 `ProcessRouteDetail` 详情级别：

```ts
canFinalizeChange?: boolean
canApplyChange?: boolean
```

更推荐放在 `ProcessRouteDetail`，因为 `敲定` 和 `变更申请` 是对当前路线/当前版本变更集合的动作，不是对单条变更记录的动作。

### 7.5 `ProcessRouteDetail`

建议新增：

```ts
canFinalizeChange?: boolean
canApplyChange?: boolean
```

## 8. 需要补的 mock 字段

### 8.1 工序 mock

每条 `operations` 增加：

```ts
confirmerName: '刘浩',
confirmerRole: '工程确认'
```

示例：

```ts
{
  operationId: 1,
  sequenceNo: 10,
  operationName: '注塑成型',
  confirmerName: '刘浩',
  confirmerRole: '工程确认'
}
```

建议按业务角色分配：

- 工程类工序：`刘浩 / 工程确认`
- 外协与供应商类工序：`李采 / 采购确认`
- 检验与测试类工序：`王质 / 品质确认`
- 资料冻结类工序：`赵越 / 工程确认`

### 8.2 资料 mock

每条 `attachments` 增加：

```ts
previewPath: '/files?operationId=1',
canPreview: true,
canAdd: true
```

对 `missing` 状态可设置：

```ts
canPreview: false,
canAdd: true
```

### 8.3 详情 mock

每个 `routeDetails[routeId]` 增加：

```ts
canFinalizeChange: true,
canApplyChange: true
```

对于已经 `released` 或 `locked` 的记录，是否允许 `canApplyChange` 需要后续业务确认；本轮可先用 mock 表达按钮状态。

## 9. 预计页面效果

### 9.1 主页面效果

用户进入 `/processes` 后，第一屏只看到：

- 页面标题。
- 筛选栏。
- 工艺产品列表。

页面会从“摘要卡片 + 左右分栏详情”变成“清单入口”。工程、采购、品质可以快速按产品、路线编号、状态、当前进程查找目标路线。

### 9.2 详情页效果

进入详情后，顶部更轻：

- 返回列表。
- 路线名称。
- 面包屑路径。
- 状态标签。
- 概览信息栅格。
- 分区导航。

原来占空间的数据卡片不再挤压内容，用户能更快进入工序、资料或版本区。

### 9.3 工序明细效果

工序表更适合协同：

- 可以搜索工序。
- 可以看到确认人。
- 可以按当前工序定位责任角色。
- 长列表不需要反复翻找。

### 9.4 资料区效果

资料区从“被动统计表”变成“资料工作入口”：

- 左侧直接预览。
- 左侧直接添加。
- 仍保留 SOP、SIP、参数表、检验标准的齐套状态。

### 9.5 版本区效果

版本变更区从“历史记录展示”变成“可推进区块”：

- 可发起变更申请。
- 可对当前变更内容执行敲定。
- 仍保持当前变更记录、成本变化、交期变化和影响工序可追溯。

## 10. 可参考的代码结构示意

以下仅为后续实施参考，不是本轮落地代码。

### 10.1 列表态 / 详情态切换

```ts
const activeRouteId = computed(() => Number(route.query.routeId || 0) || null)
const isDetailMode = computed(() => Boolean(activeRouteId.value))

function openRouteDetail(row: ProcessRouteListItem) {
  router.push({ path: '/processes', query: { ...route.query, routeId: row.routeId } })
}

function backToList() {
  const nextQuery = { ...route.query }
  delete nextQuery.routeId
  router.push({ path: '/processes', query: nextQuery })
}
```

### 10.2 主页面表格

```vue
<el-table :data="table.filteredRows.value" border stripe>
  <el-table-column prop="routeCode" label="编号" min-width="150" />
  <el-table-column label="所属产品" min-width="220">
    <template #default="{ row }">
      <div class="cell-stack">
        <strong>{{ row.productName }}</strong>
        <span class="subtle-text">{{ row.productCode }}</span>
      </div>
    </template>
  </el-table-column>
  <el-table-column prop="routeName" label="名字" min-width="240" />
  <el-table-column label="状态" width="120">
    <template #default="{ row }">
      <StatusTag :status="row.status" object-type="process" />
    </template>
  </el-table-column>
  <el-table-column prop="currentGate" label="进程" min-width="160" />
  <el-table-column prop="versionNo" label="版本" width="90" />
  <el-table-column label="详情" width="110" fixed="right">
    <template #default="{ row }">
      <el-button link type="primary" @click="openRouteDetail(row)">详情</el-button>
    </template>
  </el-table-column>
</el-table>
```

### 10.3 详情页面包屑

```vue
<div class="detail-heading">
  <el-button link @click="backToList">返回列表</el-button>
  <h3 class="section-title">{{ activeDetail.routeName }}</h3>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item>工艺路线中心</el-breadcrumb-item>
    <el-breadcrumb-item>{{ activeDetail.productName }}</el-breadcrumb-item>
    <el-breadcrumb-item>{{ activeDetail.routeCode }}</el-breadcrumb-item>
    <el-breadcrumb-item>版本 {{ activeDetail.versionNo }}</el-breadcrumb-item>
  </el-breadcrumb>
</div>
```

### 10.4 概览信息栅格

```vue
<section class="overview-grid">
  <div v-for="item in overviewItems" :key="item.label" class="overview-item">
    <span class="subtle-text">{{ item.label }}</span>
    <strong>{{ item.value }}</strong>
  </div>
</section>
```

### 10.5 工序搜索与确认人列

```vue
<div class="toolbar-row">
  <h3 class="section-title">工序明细</h3>
  <el-input
    v-model="operationKeyword"
    clearable
    placeholder="搜索工序 / 位置 / 供应商 / 确认人"
    class="operation-search"
  />
</div>

<el-table-column label="确认人" min-width="140">
  <template #default="{ row }">
    <div class="cell-stack">
      <strong>{{ row.confirmerName || '--' }}</strong>
      <span class="subtle-text">{{ row.confirmerRole || '未指定' }}</span>
    </div>
  </template>
</el-table-column>
```

### 10.6 资料区操作列

```vue
<el-table-column label="操作" width="150" fixed="left">
  <template #default="{ row }">
    <div class="table-actions">
      <el-button link type="primary" :disabled="row.canPreview === false">预览</el-button>
      <el-button link type="success" :disabled="row.canAdd === false">添加</el-button>
    </div>
  </template>
</el-table-column>
```

### 10.7 版本区动作

```vue
<div class="toolbar-row">
  <h3 class="section-title">版本变更</h3>
  <div class="header-actions">
    <el-button :disabled="activeDetail.canApplyChange === false">变更申请</el-button>
    <el-button type="primary" :disabled="activeDetail.canFinalizeChange === false">敲定</el-button>
  </div>
</div>
```

## 11. 风险点与待确认

1. `Process` 标准状态机是 `draft -> confirmed -> locked -> changed -> archived`，但当前 mock 中有 `released`。后续代码实施前建议确认：工艺路线是否继续展示 `released`，还是统一收敛到 `locked / archived`。
2. “敲定”按钮的业务含义需要确认：是进入 `confirmed`，还是从 `confirmed` 进入 `locked`，或只是前端文案表达。
3. “变更申请”是否任何状态都可点击需要确认。按规范，已锁定后应走变更流程，草稿态通常不需要发起变更。
4. 资料区“预览 / 添加”本轮只做入口，不做文件中心真实上传和权限校验。
5. 主页面从左右分栏改成列表 + 详情态后，需要保证返回列表和 query 状态清晰，否则用户会觉得多了一层跳转。
6. 本次只动工艺路线模块；早期文档提到的产品详情页/产品编辑页工艺摘要，本轮不纳入实施范围。

## 12. 交付结论

本次工艺路线模块前端改造的核心不是增加更多信息，而是重排信息优先级：

1. 主页面先让用户找到要处理的工艺产品和路线。
2. 详情页再承接工序、门禁、资料、版本、联动这些具体内容。
3. 原数据板信息不删除，收进概览。
4. 原板块切换不删除，改成更自然的详情导航。
5. 工序、资料、版本三个高频区块补上责任人、操作入口和推进动作。

按这个方案落地后，页面会更像一个清晰的工艺工作台：入口干净，详情稳，责任和动作都能被看见。
