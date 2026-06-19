# 工艺路线详情页布局与交互优化实施记录

## 1. 本次任务理解

本次按 `temp-process-route-detail-layout-followup-plan.md` 执行，只涉及前端工艺路线模块，不涉及后端、数据库、接口联调或其他业务模块页面。

目标是继续收敛工艺路线详情页的布局和交互：

- 将详情页顶部的概览信息区下移到“概览”Tab 内容区域。
- 将资料挂接表格中的“预览 / 添加”操作移动到表格最右侧。
- 将“联动影响”改成左侧联动项列表 + 右侧简要信息预览。
- 删除“确认门禁”模块的导航和页面内容。

## 2. 引用文档

本次开发前已阅读：

- `D:\Yuewei\资料\PLM\docs\文件沉淀\开发提示词.md`
- `temp-process-route-detail-layout-followup-plan.md`
- `plm-web/src/views/process/ProcessCenterView.vue`

同时遵守：

- 只使用 `Process` 核心对象表达工艺路线、工序、资料和联动影响。
- 不新增根对象。
- 不做 ERP / MES / WMS / CRM 等外部系统正式集成。
- 不修改后端。

## 3. 修改文件

本次实际修改：

- `plm-web/src/views/process/ProcessCenterView.vue`

本次未修改：

- `plm-server`
- `plm-web/src/types/process.ts`
- `plm-web/src/mock/process.ts`
- 产品、文件中心、质量、BOM 等其他模块页面

## 4. 代码改动说明

## 4.1 删除确认门禁模块

调整内容：

- 将 `DetailSectionKey` 从：

```ts
type DetailSectionKey = 'overview' | 'operations' | 'gates' | 'attachments' | 'changes' | 'impacts'
```

调整为：

```ts
type DetailSectionKey = 'overview' | 'operations' | 'attachments' | 'changes' | 'impacts'
```

- 从 `sectionOptions` 中删除：

```ts
{ label: '确认门禁', value: 'gates' }
```

- 删除模板中 `activeSection === 'gates'` 对应的整段“角色确认 / 门禁检查”展示。

说明：

- 本次只是前端页面隐藏，不删除 mock 数据中的 `confirmations`、`gateChecks`。
- 这些数据仍可供概览或其他摘要计算使用。

## 4.2 概览信息区下移

调整前：

- `overview-panel` 位于详情页顶部，导航条上方。

调整后：

- 顶部只保留返回列表、路线名称、面包屑、状态和产品详情入口。
- `overview-panel` 移入 `activeSection === 'overview'` 的内容区。
- 原有“工序视图”和“当前对象”仍保留在概览 Tab 中。

预期效果：

- 详情页顶部更轻。
- 用户点击“概览”时，才看到完整概览信息。
- 页面层级更符合“导航先行，内容随 Tab 切换”的结构。

## 4.3 资料挂接操作列移到最右

调整前：

```vue
<el-table-column label="操作" width="150" fixed="left">
```

调整后：

```vue
<el-table-column label="操作" width="150" fixed="right">
```

并将该列移动到资料表格最后。

最终字段顺序：

1. 工序
2. SOP
3. SIP
4. 参数表
5. 检验标准
6. 状态
7. 最近更新
8. 操作

预期效果：

- 先看资料完整性，再在右侧操作。
- 更符合表格操作列的常见阅读路径。

## 4.4 联动影响改为右侧摘要预览

新增前端状态：

```ts
const activeImpactLabel = ref('')
```

新增计算：

- `activeImpact`
- `activeImpactSummaryItems`

交互调整：

- 左侧点击“产品详情 / 质量管理 / 文件中心”等联动项，不再直接跳转。
- 右侧展示当前联动项的简要信息。
- 右侧面板保留“查看完整页面”入口，用于需要时跳转。

当前摘要来源：

- 产品详情摘要来自 `activeDetail.productCode`、`productName`、`versionNo`、`status`、`currentGate`。
- 质量管理摘要来自 `passedGate`、质量确认记录、检验标准数量、差异工序数量。
- 文件中心摘要来自 SOP / SIP / 参数表 / 检验标准数量、缺失项和最近更新时间。

预期效果：

- 用户在工艺路线详情页内即可快速查看联动信息。
- 不会因为点击联动项立即离开当前工艺路线上下文。

## 5. 页面预期效果

### 5.1 概览页

顶部区域更简洁，概览信息下移到“概览”Tab 内容区。用户在概览页可以看到：

- 工序视图切换
- 当前对象摘要
- 工艺路线概览说明
- 工序数量、工艺成本、差异工序、当前门禁、路线类型、模板来源、负责人等信息栅格

### 5.2 资料挂接页

资料表格操作列位于最右侧，用户阅读路径为：

```text
工序 → 资料数量 → 齐套状态 → 最近更新 → 操作
```

### 5.3 联动影响页

页面形成左右结构：

```text
左侧：联动项列表
右侧：当前联动项简要信息
```

点击不同联动项时，右侧摘要随之切换。

### 5.4 确认门禁

页面导航不再显示“确认门禁”，对应内容区也不再出现。

## 6. 风险与后续建议

1. 本次只是前端隐藏确认门禁模块，未删除底层 mock 数据。
2. 联动影响右侧摘要目前由前端从现有数据计算，后续如果接真实后端，可考虑由接口返回摘要字段。
3. “查看完整页面”仍会跳转到对应模块，当前只是把直接跳转从左侧卡片移动到右侧摘要面板。
4. 如果后续需要更丰富的产品、质量、文件信息，建议只扩展 `ProcessImpactLink` 的可选摘要字段，不要改其他模块页面。

## 7. 本次交付结论

本次改造继续保持工艺路线模块内聚：

- 顶部更轻。
- 概览回到概览 Tab。
- 表格操作在最右。
- 联动影响改为页内预览。
- 删除确认门禁页面入口。

整体目标是让工艺路线详情页更像一个单页工作台，而不是多个模块入口的堆叠页。
