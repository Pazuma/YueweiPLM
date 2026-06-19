# 44-工作台数据板联动展示与快捷操作上移前端优化实施记录

## 1. 本次改动范围

本次只改动前端工作台页面，未涉及后端，也未修改其他业务页面。

修改文件：

- `plm-web/src/views/dashboard/DashboardView.vue`

## 2. 改动目标

根据已确认的方案文档《43-工作台数据板联动展示与快捷操作上移前端优化方案》，把工作台首页从“所有模块一起堆叠展示”改成“上方数据板切换、下方只展示当前选中内容”的结构，减少首页信息拥挤感。

本次重点解决：

1. 顶部数据板点击后，下方只展示对应分类内容
2. 快捷操作上移到顶部，避免和下方业务列表混排
3. 新增“待冻结资料”独立视图
4. 工作台保留前端假数据演示，不依赖后端接口

## 3. 具体修改说明

### 3.1 数据获取方式调整

原页面依赖：

- `getDashboardSnapshot()`
- `getProductList()`
- `getApprovalTasks()`

本次改为页面内前端假数据，直接在 `DashboardView.vue` 中维护四组展示数据：

- `inProgressProducts`
- `myPendingTasks`
- `overdueRisks`
- `pendingFreezeItems`

这样做的原因：

- 当前阶段只做前端，不涉及后端
- 可以稳定演示交互结构，不受接口状态影响
- 更符合本轮“只改工作台页”的范围控制

### 3.2 顶部数据板改成联动切换

新增状态：

```ts
type DashboardViewKey = 'products' | 'tasks' | 'risks' | 'freeze'

const activeMetricView = ref<DashboardViewKey>('products')
```

新增切换方法：

```ts
function selectMetricView(view: DashboardViewKey) {
  activeMetricView.value = view
}
```

处理方式：

- 点击“进行中的产品”，下方只显示产品列表
- 点击“我的待办”，下方只显示待办列表
- 点击“逾期预警”，下方只显示风险列表
- 点击“待冻结资料”，下方只显示冻结缺口列表

这样改完之后，工作台不再把所有内容同时铺开，用户在首页会更容易聚焦。

### 3.3 快捷操作上移

把“快捷操作”从原来的下方业务区中拆出来，独立放到顶部数据板下方。

保留的快捷入口：

- 新建产品
- 项目管理
- 文件中心
- 需求订单

交互效果：

- 上方先看指标
- 中间快速进入高频入口
- 下方再看当前选中分类的详细列表

页面层次会比之前更清晰。

### 3.4 增加当前选中视图的标题、说明和跳转按钮

新增三个计算属性：

- `activeSectionTitle`
- `activeSectionDesc`
- `activeSectionActionLabel`
- `activeSectionActionPath`

作用：

- 根据当前激活的数据板，动态变化下方内容区标题
- 给出当前列表的说明文字
- 右上角按钮跳到对应业务页

例如：

- 待办视图右上角显示“审批中心”
- 风险视图右上角显示“风险详情”
- 冻结视图右上角显示“冻结缺口”

### 3.5 新增待冻结资料列表

新增数据结构：

```ts
interface DashboardFreezeItem {
  productId: number
  productName: string
  versionNo: string
  missingItems: string[]
  ownerUserName: string
  dueDate: string
  targetPath: string
}
```

展示内容包括：

- 产品名
- 版本号
- 缺失资料
- 负责人
- 截止日期

这样首页可以直接看到“哪些产品还不能冻结”，比以前只展示抽象数字更直观。

### 3.6 样式调整

本次在 `DashboardView.vue` 中补充了以下样式：

- 数据板激活态 `.dashboard-button.is-active`
- 顶部快捷操作网格 `.dashboard-actions`
- 快捷操作按钮 `.dashboard-action-chip`
- 下方列表项按钮 `.list-button`
- 风险项按钮 `.risk-button`

核心视觉变化：

- 当前激活的数据板会高亮
- 快捷操作作为单独区域展示
- 内容区列表统一成可点击卡片

## 4. 修改代码位置

### 4.1 脚本区

文件：

- `plm-web/src/views/dashboard/DashboardView.vue`

主要修改位置：

1. 删除接口依赖和 `onMounted(loadData)` 逻辑
2. 新增页面内 mock 数据
3. 新增 `activeMetricView` 和切换逻辑
4. 新增当前视图说明相关的计算属性

### 4.2 模板区

主要修改位置：

1. 顶部四个数据板改为点击切换，而不是直接跳页
2. 新增快捷操作独立板块
3. 下方内容区改成 `v-if / v-else-if / v-else` 单视图展示

### 4.3 样式区

主要修改位置：

1. 补充数据板激活态样式
2. 重构快捷操作区布局
3. 调整列表卡片的 hover 和层级表现

## 5. 修改后的页面效果

改完后的工作台效果可以理解为：

1. 第一层：顶部四个数据板
2. 第二层：快捷操作按钮区
3. 第三层：当前选中数据板对应的列表详情

用户点击不同数据板时，下方内容会直接切换：

- 点击“进行中的产品” -> 出现产品推进列表
- 点击“我的待办” -> 出现当前用户待办列表
- 点击“逾期预警” -> 出现逾期项目列表
- 点击“待冻结资料” -> 出现冻结缺口列表

这样首页更像“行动入口”，而不是“所有信息同时展示的总览页”。

## 6. 技术说明

本次使用到的前端技术点：

- Vue 3 `script setup`
- `ref`
- `computed`
- 条件渲染 `v-if / v-else-if / v-else`
- 列表渲染 `v-for`
- `vue-router` 页面跳转
- Element Plus 组件：
  - `el-tag`
  - `el-button`
  - `el-progress`
  - `el-icon`

## 7. 校验结果

已完成单文件改动后的构建校验。

执行命令：

```powershell
& 'C:\Program Files\nodejs\node.exe' '.\node_modules\vite\bin\vite.js' build --outDir dist-dashboard-check
```

执行目录：

```powershell
d:\Yuewei\git\YUEWEI\plm-web
```

结果：

- 构建成功
- 未修改正式 `dist` 目录
- 输出到单独校验目录 `dist-dashboard-check`

## 8. 结论

这次工作台优化已经按方案落地完成，并且控制在单文件范围内：

- 只改了 `DashboardView.vue`
- 只涉及前端
- 不影响其他业务页面
- 已通过构建校验

后续如果继续优化工作台，可以在这个结构上继续加：

- 数据板对应的筛选条件
- 按角色显示不同快捷操作
- 列表分页或“查看更多”
