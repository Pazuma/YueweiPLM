# 36-工序编码_时间轴驳回_BOM联动_时间筛选_订单来源_报表联动前端优化实施记录

## 1. 本次实施范围

本次只涉及前端，不涉及后端。

落地内容包括：

1. 工艺路线页增加工序编码展示，并把工序类型收口为更清晰的业务表达
2. 产品详情页时间轴增加驳回动作
3. 产品详情页 BOM 版本点击后联动展示对应明细
4. 工艺路线页、库存页增加统一时间筛选组件
5. 需求订单页进行中区域增加来源切换
6. 报表中心页指标卡支持点击跳转

---

## 2. 本次实际修改的文件

### 新增文件

- `plm-web/src/components/ProjectTimeRangeFilter/index.vue`
- `plm-web/src/views/order/OrderCenterView.vue`
- `plm-web/src/views/process/ProcessCenterView.vue`
- `plm-web/src/views/product/ProductDetail.vue`
- `plm-web/src/views/inventory/InventoryCenterView.vue`
- `plm-web/src/views/report/ReportCenterView.vue`
- `temp-36-工序编码_时间轴驳回_BOM联动_时间筛选_订单来源_报表联动前端优化实施记录.md`

### 修改文件

- `plm-web/src/types/process.ts`
- `plm-web/src/types/foundation.ts`
- `plm-web/src/mock/process.ts`
- `plm-web/src/mock/foundation.ts`

---

## 3. 每一项是怎么改的

## 3.1 工艺路线页

### 改动目标

- 增加 `工序编码`
- 工序表支持顶部时间筛选
- 工序类型显示更清楚
- 顺手清理原页面中用户可见的乱码和阅读噪音

### 代码落点

#### `plm-web/src/components/ProjectTimeRangeFilter/index.vue`

新增通用时间筛选组件，供多个页面复用。

使用技术：

- Vue 3 `defineModel`
- Element Plus `el-segmented`

#### `plm-web/src/views/process/ProcessCenterView.vue`

本次直接重构为干净版页面，主要做了：

- 引入 `ProjectTimeRangeFilter`
- 增加 `timeRange` 状态
- 用 `isWithinTimeRange()` 过滤路线数据
- 在工序表新增 `工序编码` 列
- 增加 `getOperationTypeLabel()`，把工序类型转成可读文案

### 为什么这样改

原来的问题不是只有“少一个字段”，而是页面表达本身有点混：

- 工序名称和工序类型边界不清
- 没有统一时间筛选入口
- 用户要看工序时，表头结构不够像正式业务台账

这次改完后：

- 工艺路线列表可以按时间范围先收窄
- 工序表能直接看编码
- 工序类型展示更稳定

---

## 3.2 产品详情页

### 改动目标

- 时间轴支持驳回
- BOM 版本点击后，下面直接展示对应 BOM 明细
- 保持时间轴、资料区、商务区、质量区结构更清楚

### 代码落点

#### `plm-web/src/views/product/ProductDetail.vue`

这次也是直接重构成干净版页面，核心点有：

- `type TimelineActionMode = 'advance' | 'force' | 'reject'`
- 增加 `canRejectNode()`
- 时间轴卡片操作区增加 `驳回` 按钮
- 弹窗里增加驳回目标节点 `rejectTo`
- BOM 版本区拆成：
  - 上方版本对比表
  - 下方当前版本物料明细

### 为什么这样改

#### 时间轴

原来只有推进，没有退回，实际业务会很别扭。  
现在一个节点可以：

- 正常推进
- 驳回
- 管理员强制推进

动作链条完整了。

#### BOM 对比

原来版本和明细的联动感不够强。  
现在点击版本，下面马上跟着变，查看差异更直接。

---

## 3.3 库存页

### 改动目标

- 增加统一时间筛选
- 保持左树右表结构稳定
- 页面文案更干净

### 代码落点

#### `plm-web/src/views/inventory/InventoryCenterView.vue`

主要做了：

- 引入 `ProjectTimeRangeFilter`
- 增加 `timeRange`
- 通过 `projectDate || updatedAt` 做时间过滤
- 保留“左侧分类树 + 右侧物料表格”的结构

### 为什么这样改

库存页本来就适合这种结构，这次不是推翻，而是补上统一筛选能力。  
改完后用户可以先按：

- 全部
- 最近三天
- 最近一星期
- 最近一个月
- 半年

过滤，再看当前分类下的物料。

---

## 3.4 需求订单页

### 改动目标

- 进行中订单也能按来源筛选
- 进行中和历史订单共用一套来源切换逻辑

### 代码落点

#### `plm-web/src/views/order/OrderCenterView.vue`

主要做了：

- 使用统一的 `sourceFilter`
- 不再只给历史订单保留来源切换
- 让 `activeTab + sourceFilter + keyword` 一起参与过滤

### 为什么这样改

原来只有历史订单可以切来源，进行中反而不行，体验不连贯。  
现在两个区域都统一了，用户切来源时不会再有“为什么这里不能筛”的落差。

---

## 3.5 报表中心页

### 改动目标

- 报表详情里的指标卡可点击
- 点击后直接跳到对应状态列表或对象页面

### 代码落点

#### `plm-web/src/views/report/ReportCenterView.vue`

主要做了：

- 指标卡改成 `button`
- 点击触发 `openTarget(metric.targetPath)`

#### `plm-web/src/mock/foundation.ts`

补齐了报表详情 `metrics` 的 `targetPath`

#### `plm-web/src/types/foundation.ts`

把 `ReportDetailSection.metrics` 增加了可选的 `targetPath`

### 为什么这样改

报表页不能只停留在“展示数字”，更重要的是：

- 点数字
- 进列表
- 去处理

这次改完后，报表页才更像真正的业务入口。

---

## 4. 类型和 mock 数据做了什么配合

### `plm-web/src/types/process.ts`

本次主要补了两个能力：

- 路线对象支持 `updatedAt`
- 工序对象支持 `operationCode`

同时保留一定兼容性，避免旧 mock 先把页面卡死。

### `plm-web/src/types/foundation.ts`

本次补了：

- `ProductTimelineNode.canReject`
- `InventoryListRow.projectDate`
- `ReportDetailSection.metrics[].targetPath`

### `plm-web/src/mock/process.ts`

补充了路线的 `updatedAt`，让工艺路线页的时间筛选真正有数据可筛。

### `plm-web/src/mock/foundation.ts`

补充了报表详情指标的 `targetPath`，让报表指标卡点击跳转能真正工作。

---

## 5. 本次使用到的前端技术

这次没有引入新框架，全部基于现有前端栈完成：

- Vue 3 `script setup`
- TypeScript
- Element Plus
  - `el-table`
  - `el-dialog`
  - `el-segmented`
  - `el-input`
  - `el-tree`
  - `el-tag`
  - `el-select`
- Vue Router `router.push`
- `computed / ref / reactive`

实现方式属于：

- 基于现有页面结构的增强式改造
- 局部组件抽象复用
- mock 数据联动验证

---

## 6. 修改后的页面效果

### 工艺路线页

- 顶部多了统一时间筛选
- 工序表能直接看工序编码
- 工序类型更清楚

### 产品详情页

- 时间轴节点旁边能直接点驳回
- BOM 版本点击后，下方马上出对应版本明细

### 库存页

- 顶部多了时间范围筛选
- 仍保持左树右表，阅读更稳定

### 订单页

- 进行中和历史都能按来源切

### 报表页

- 指标卡不只是数字，已经能点进去

---

## 7. 构建验证结果

本次已执行前端构建验证：

命令：

```powershell
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -Command "& 'C:\Program Files\nodejs\node.exe' '.\node_modules\vite\bin\vite.js' build"
```

结果：

- 构建成功
- Vite 生产构建通过

说明：

- 你机器上的 `npm.cmd run build` 直接执行时仍会因为环境变量里的 `node` 未识别而失败
- 但通过显式调用 `C:\Program Files\nodejs\node.exe` 已完成构建验证

另外还有两个非阻塞提示：

1. `@vueuse/core` 的 pure 注释提示
2. chunk 体积超过 500kb 的打包警告

这两个都不是本次功能改动引起的阻塞错误，属于后续可继续优化项。

---

## 8. 本次结论

这轮前端改动已经完成并通过构建验证。

核心结果是：

1. 工艺路线页更像正式业务台账
2. 产品详情页时间轴支持驳回
3. BOM 版本对比更直观
4. 工艺路线页和库存页有统一时间筛选
5. 订单页来源筛选更一致
6. 报表页指标卡真正具备业务跳转能力

如果下一步继续迭代，建议优先看两个方向：

1. 把剩余页面的可见乱码也统一清理
2. 对报表、库存、工艺路线进一步补充更细粒度的假数据场景

