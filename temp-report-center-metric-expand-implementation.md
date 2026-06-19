# 39-报表中心指标点击展开对应项目列表前端优化实施记录

## 1. 本次实施目标

根据 [38-报表中心指标点击展开对应项目列表交互方案.md](D:/Yuewei/资料/PLM/docs/系统优化沉淀/38-报表中心指标点击展开对应项目列表交互方案.md)，本次已完成报表中心前端优化：

- 点击“立项中”，下方显示“立项中的项目列表”
- 点击“模具阶段”，下方显示“模具阶段项目列表”
- 点击“半成品阶段”，下方显示“半成品阶段项目列表”
- 模具状态报表、成本分析报表同步采用相同交互

本次修改只涉及前端，不涉及后端。

---

## 2. 本次实际改动文件

### 2.1 页面文件

- [ReportCenterView.vue](d:/Yuewei/git/YUEWEI/plm-web/src/views/report/ReportCenterView.vue)

### 2.2 类型文件

- [foundation.ts](d:/Yuewei/git/YUEWEI/plm-web/src/types/foundation.ts)

### 2.3 Mock 数据文件

- [report-center.ts](d:/Yuewei/git/YUEWEI/plm-web/src/mock/report-center.ts)

### 2.4 API 出口文件

- [foundation.ts](d:/Yuewei/git/YUEWEI/plm-web/src/api/modules/foundation.ts)

---

## 3. 改动内容说明

## 3.1 报表指标从“跳转按钮”改成“页内联动入口”

### 修改前

`ReportCenterView.vue` 里的指标卡点击后直接执行：

```ts
@click="openTarget(metric.targetPath)"
```

这意味着：

- 用户点了“立项中”
- 页面会直接跳转
- 当前报表页里看不到“立项中项目列表”

### 修改后

页面新增：

- `activeMetricKey`
- `activeMetric`
- `selectMetric()`

核心逻辑变成：

- 点击指标卡
- 设置当前激活指标 key
- 下方明细区自动切换成该指标的对应列表

这样报表页内部就形成了完整的分析闭环。

---

## 3.2 新增“当前指标对应明细列表区”

在 `ReportCenterView.vue` 中，已在指标区下方新增一个独立的明细区域：

- 标题：当前指标的 `detailTitle`
- 副标题：当前指标的 `detailSummary`
- 列表：当前指标的 `detailItems`

页面结构现在变成：

1. 报表入口卡片
2. 当前报表标题
3. 指标卡片区
4. 当前指标明细列表区
5. 异常区 + 分布区

这比之前只看数字更适合继续往下判断和操作。

---

## 3.3 给指标卡增加选中态

为了让用户知道“下面的列表到底对应哪张指标卡”，给指标卡增加了选中样式：

- 深一点的边框
- 浅蓝背景
- 投影加深
- 右上角显示“当前展开”

相关样式在：

- [ReportCenterView.vue](d:/Yuewei/git/YUEWEI/plm-web/src/views/report/ReportCenterView.vue)

新增类：

- `.metric-card--action.is-active`
- `.metric-card__state`

---

## 3.4 新增报表指标明细类型定义

为了承载“每张指标卡下面的列表”，在类型文件中新增了两个接口：

```ts
export interface ReportMetricDetailItem
export interface ReportMetricItem
```

位置：

- [foundation.ts](d:/Yuewei/git/YUEWEI/plm-web/src/types/foundation.ts)

### 作用

`ReportMetricDetailItem` 用来描述列表中的一条项目：

- 标题
- 副标题
- 负责人
- 当前节点
- 停留时长
- 风险提示
- 跳转地址

`ReportMetricItem` 用来描述一张可点击的报表指标卡：

- key
- label
- value
- hint
- detailTitle
- detailSummary
- detailItems

这样前端就不再只能展示“数字”，而是能完整承接“数字对应的业务对象列表”。

---

## 3.5 把报表中心 mock 数据单独拆出

### 为什么要拆

原来的 [foundation.ts](d:/Yuewei/git/YUEWEI/plm-web/src/mock/foundation.ts) 很大，而且包含大量其他模块 mock 数据。

如果直接在那里面继续大改：

- 容易影响别的模块
- 改动可读性差
- 后续维护不方便

### 本次做法

新增：

- [report-center.ts](d:/Yuewei/git/YUEWEI/plm-web/src/mock/report-center.ts)

这个文件专门只负责报表中心 mock 数据。

然后在：

- [foundation.ts](d:/Yuewei/git/YUEWEI/plm-web/src/api/modules/foundation.ts)

里把 `getReportCenterSnapshot` 改成从新文件导出。

### 这样做的好处

- 报表中心数据单独维护
- 后续继续优化报表不需要反复进入大文件
- 也避免把其他模块旧编码文本一起卷进来

---

## 4. 本次补充的 mock 数据内容

在 [report-center.ts](d:/Yuewei/git/YUEWEI/plm-web/src/mock/report-center.ts) 中，已经为三类报表补充了“指标 -> 明细列表”数据：

### 4.1 开发进度报表

已补：

- `立项中`
- `模具阶段`
- `半成品阶段`

每个指标下都有自己的项目列表。

### 4.2 模具状态报表

已补：

- `开模中`
- `试模中`
- `已验收`

每个指标下都有对应模具或项目列表。

### 4.3 成本分析报表

已补：

- `超队 3.0`
- `亮甲 3.0`
- `单品均摊`

每个指标下都有对应的成本明细列表。

---

## 5. 关键实现逻辑

## 5.1 默认激活第一张指标卡

页面通过 `watch(currentDetail)` 做了处理：

- 当切换报表时
- 如果当前激活指标不在新报表里
- 自动激活第一张指标卡

这样用户进入页面后，下方列表不会是空的。

---

## 5.2 报表切换后自动刷新明细

因为 `activeMetric` 是从 `currentDetail.metrics` 计算出来的：

- 切开发进度报表
- 明细区显示开发进度的第一组
- 切到模具状态报表
- 明细区自动切换到模具状态的第一组

不需要额外手工刷新。

---

## 5.3 明细列表项继续支持跳转

每条明细卡仍然保留跳转能力：

- 点击整张卡可跳转
- 右上角“查看”按钮也可跳转

这样报表仍然保留“从分析结果继续进入业务页面”的能力。

---

## 6. 修改后的页面效果

改完之后，页面使用方式会变成：

1. 先选择一个报表
2. 点击某个指标卡，例如“立项中”
3. 下方立即展开“立项中的项目列表”
4. 点击列表中的项目，再进入产品详情或相关页面

具体体感变化是：

- 页面不再只是“数字展示页”
- 用户不需要立刻跳走
- 可以先在报表页内部完成一轮定位和筛选

这会让报表中心更像一个工作页面，而不是单纯入口页。

---

## 7. 本次使用到的前端技术方式

本次主要使用的是：

- Vue `ref`
- Vue `computed`
- Vue `watch`
- 条件渲染 `v-if`
- 列表渲染 `v-for`
- 局部状态驱动的页内联动
- 独立 mock 文件拆分

没有引入新依赖，也没有改动后端接口。

---

## 8. 构建验证结果

已执行前端构建验证：

```powershell
C:\Program Files\nodejs\node.exe .\node_modules\vite\bin\vite.js build
```

结果：

- 构建通过
- 无新增编译错误

---

## 9. 后续可继续优化的方向

如果下一步继续细化，这个报表中心还可以继续增强：

### 9.1 增加列表筛选

例如在“立项中项目列表”上方再加：

- 负责人筛选
- 风险等级筛选
- 客户来源筛选

### 9.2 增加列表字段差异化

当前三类报表都复用了统一字段结构。

后续可以按报表类型进一步细化：

- 开发进度报表偏项目节点
- 模具状态报表偏模具编号与供应商
- 成本分析报表偏版本与金额结构

### 9.3 支持记忆上次点击的指标

以后可以把 `activeMetricKey` 同步到路由 query，让刷新页面后仍停留在之前选中的指标上。

---

## 10. 本次结论

本次前端优化已经把报表中心从：

- “点击指标就跳走”

改成了：

- “点击指标，先在当前页展开对应项目列表，再继续判断是否跳转”

这正好符合你提出的使用方式，也和整个系统现在强调的“先选类型，再看明细”的交互方向保持一致。
