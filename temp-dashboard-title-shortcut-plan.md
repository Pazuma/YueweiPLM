# 45-工作台标题去重_快捷操作右置_页面路线补充前端优化方案

## 1. 任务理解

本次仍然只做方案文档，不修改项目代码。

你这次补充的需求有三层：

1. 工作台页面去掉重复的 `Yuewei PLM 工作台`
2. 工作台快捷操作移动到头部右侧红框区域
3. 在你新截图红框位置增加“页面路线”

结合当前代码结构，我的理解是：

- 第 1、2 点主要作用在工作台页面本身
- 第 3 点不是单独某个页面的内容组件，而是顶部导航栏的“全局页面路线 / 面包屑能力”
- 这个能力不能只覆盖产品详情页，而要作为全系统统一规则，覆盖工作台、产品、工艺路线、文件中心、供应商、库存、订单、报表、系统管理等页面

所以这次文档不再只覆盖工作台，还需要把顶部导航栏一起纳入方案。

## 2. 所属 PLM 业务链路

本次属于首页与详情页的前端导航体验优化，目标是让用户更容易知道：

- 我现在在哪个页面
- 我是从哪里进入当前页面的
- 我下一步能快速去哪里

它不改变业务链路，不涉及状态机、审批流、接口设计和后端对象关系，只优化页面导航与入口组织方式。

## 3. 涉及前端页面 / 组件

本次方案涉及的前端文件应当包括：

- `plm-web/src/views/dashboard/DashboardView.vue`
- `plm-web/src/layout/Navbar.vue`
- `plm-web/src/views/product/ProductDetail.vue`

以及后续正式实施时需要一并检查的页面模块：

- `plm-web/src/views/product/*`
- `plm-web/src/views/processes/*` 或对应工艺路线页面
- `plm-web/src/views/files/*` 或对应文件中心页面
- `plm-web/src/views/suppliers/*`
- `plm-web/src/views/inventories/*`
- `plm-web/src/views/orders/*`
- `plm-web/src/views/reports/*`
- `plm-web/src/views/system/*`

其中职责分别是：

### 3.1 `DashboardView.vue`

负责：

- 去掉重复标题
- 快捷操作右置

### 3.2 `Navbar.vue`

负责：

- 在顶部加入页面路线
- 让红框位置展示当前页面路径，而不是只是一段孤立标题

### 3.3 `ProductDetail.vue`

负责：

- 补充适合页面路线展示的标题来源
- 让“产品详情”这一页在顶部导航中更清晰地表达层级关系

### 3.4 其他业务页面

负责：

- 按统一规则补齐页面路线所需的路由元信息
- 保证 Navbar 的面包屑在不同模块下都能正确显示
- 避免只在产品详情页有路径感，其他页面仍然是孤立标题

## 4. 涉及后端模块

无。

本次不涉及：

- 新增后端接口
- 调整返回字段
- 变更权限逻辑
- 修改状态流转

## 5. 涉及数据对象 / 状态 / API

### 5.1 涉及对象

仅是前端展示层承载这些对象的路径关系：

- `Product`
- `Order`
- `Process`
- `Inventory`

### 5.2 状态

不新增、不修改任何状态。

### 5.3 API

不新增 API，不改现有 API。

## 6. 当前问题判断

## 6.1 工作台标题重复

当前工作台页面存在：

- `PageContainer title="工作台"`
- 下面又出现 `Yuewei PLM 工作台`
- 并且还存在第二块类似说明区

这会造成第一屏信息重复。

## 6.2 快捷操作层级不对

快捷操作本质是高频入口，不是主内容区的一整块业务信息。

现在把它单独放成一整块面板，会和真正的列表内容抢视觉重点。

## 6.3 页面路线能力不完整

你新截图里红框标的位置，目前更像一个“页头标题占位”，但从使用体验看，它应该承担：

- 当前页面名称
- 页面层级路径
- 从列表到详情的上下文关系

而不是只显示一个孤立的“产品详情”。

## 6.4 当前顶部导航已经有基础面包屑，但表达不够完整

我查看了当前：

- `plm-web/src/layout/Navbar.vue`

它已经有：

- `el-breadcrumb`

但问题是：

1. 当前路由标题来源较弱，很多页面显示不够完整
2. 产品详情这类页面，如果只显示“产品详情”，用户不知道它来自哪个模块
3. 没有更直观地表达“系统 > 模块 > 页面 > 当前对象”

所以你的“页面路线”需求，本质上是要把顶部面包屑做得更完整、更可读。

## 6.5 当前代码文件存在前置风险

### 工作台页风险

当前 `DashboardView.vue` 里已经存在：

- Git 合并冲突标记
- 两套实现混在一起
- 中文乱码残留

### 产品详情页风险

当前 `ProductDetail.vue`、`Sidebar.vue` 等文件也存在乱码。

所以这次方案必须把“先清理乱码与冲突”写成正式实施前步骤。

## 7. 优化目标

本次最终目标应当是：

### 7.1 工作台页

1. 顶部只保留一层核心工作台说明
2. 去掉重复的 `Yuewei PLM 工作台`
3. 快捷操作放到说明区右侧
4. 数据板仍在下面
5. 列表区保持联动展示逻辑

### 7.2 全局导航栏

1. 红框位置显示页面路线
2. 页面路线可以表达当前所在层级
3. 产品详情页进入后，用户能看出自己来自哪个模块

### 7.3 产品详情页

1. 顶部显示更清晰的路径
2. 不只是“产品详情”四个字
3. 可以体现：
   - 产品管理
   - 产品详情
   - 当前对象名称或编号

## 8. 页面路线的理解与建议

你新提的“页面路线”，我的理解不是单纯换个标题，而是做成“带层级的导航路径”。

而且这套规则必须是“全模块统一规则”，不能只给产品详情页单独补一条。

建议用法：

### 8.1 工作台页

顶部显示：

- `工作台`

或

- `工作台 / 首页总览`

### 8.2 产品列表页

顶部显示：

- `产品管理 / 进行中产品`

### 8.3 产品详情页

顶部显示：

- `产品管理 / 产品详情 / 超队 3.0 iPhone18 黑色`

或者更紧凑一点：

- `产品管理 / 产品详情`

标题下方或右侧再补当前产品名：

- `超队 3.0 iPhone18 黑色`

这样比只显示“产品详情”更有上下文。

### 8.4 其他模块页

同样逻辑应覆盖其他模块，建议至少包括：

- 工艺路线：`基础资料 / 工艺路线`
- 文件中心：`基础资料 / 文件中心`
- BOM 管理：`基础资料 / BOM 管理`
- 测试管理：`基础资料 / 测试管理`
- 供应商管理：`基础资料 / 供应商管理`
- 物料库存：`基础资料 / 物料库存`
- 需求订单：`基础资料 / 需求订单`
- SKU 视图：`SKU 管理 / SKU 视图`
- 产品列表：`产品管理 / 进行中产品`、`产品管理 / 冻结发布` 等
- 项目管理：`项目管理 / 进行中`、`项目管理 / 已完成`
- 报表中心：`报表中心 / 报表入口`
- 审批中心：`系统管理 / 审批中心`
- 用户管理：`系统管理 / 用户管理`
- 角色管理：`系统管理 / 角色管理`

这样用户无论从哪个模块进入，都能在顶部看到一致的页面路线。

## 9. 建议修改方案

## 9.1 工作台页面怎么改

文件：

- `plm-web/src/views/dashboard/DashboardView.vue`

建议：

1. 删除重复的第二层工作台标题块
2. 删除下方独立“快捷操作”大面板
3. 保留一块头部说明区
4. 在头部说明区右侧放快捷操作

建议结构：

```vue
<section class="page-panel dashboard-toolbar-panel">
  <div class="dashboard-toolbar">
    <div class="dashboard-toolbar__summary">
      <h2 class="page-panel-title">Yuewei PLM 工作台</h2>
      <p class="page-panel-desc">
        聚焦当前推进中的产品、我的待办、逾期风险和基础资料入口，让首页成为真实的行动面板。
      </p>
    </div>

    <div class="dashboard-toolbar__actions">
      ...
    </div>
  </div>
</section>
```

如果最终决定完全去掉 `Yuewei PLM 工作台` 这行标题，也可以只保留说明文字：

```vue
<div class="dashboard-toolbar__summary">
  <p class="page-panel-desc">
    聚焦当前推进中的产品、我的待办、逾期风险和基础资料入口，让首页成为真实的行动面板。
  </p>
</div>
```

这会更简洁，也更符合“避免重复标题”的目标。

## 9.2 快捷操作怎么放到右侧红框

右侧建议做成轻量入口，而不是下方大卡片。

推荐形式：

- 一行横向按钮
- 图标 + 标题
- 弱化说明

建议数据结构：

```ts
interface QuickActionItem {
  label: string
  path: string
  icon: 'plus' | 'promotion' | 'document' | 'tickets'
}

const quickActions: QuickActionItem[] = [
  { label: '新建产品', path: '/products/create', icon: 'plus' },
  { label: '项目管理', path: '/projects?tab=in_progress', icon: 'promotion' },
  { label: '文件中心', path: '/files', icon: 'document' },
  { label: '需求订单', path: '/orders', icon: 'tickets' }
]
```

模板建议：

```vue
<div class="dashboard-toolbar__actions">
  <button
    v-for="action in quickActions"
    :key="action.label"
    class="quick-action-inline"
    type="button"
    @click="open(action.path)"
  >
    <span class="quick-action-inline__icon">
      <el-icon><Plus /></el-icon>
    </span>
    <span class="quick-action-inline__label">{{ action.label }}</span>
  </button>
</div>
```

## 9.3 页面路线应该怎么加

这个需求应当主要改：

- `plm-web/src/layout/Navbar.vue`

并且需要配合：

- 路由配置中的 `meta.title`
- 必要时补充 `meta.breadcrumb`
- 具体业务页面的标题来源

当前它已经有：

```vue
<el-breadcrumb separator="/">
  <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.path">
    {{ item.title }}
  </el-breadcrumb-item>
</el-breadcrumb>
```

所以不需要从零造一个新组件，更合适的做法是：

1. 保留 `el-breadcrumb`
2. 重新整理 `breadcrumbItems` 的显示内容
3. 让所有模块页面都能显示完整路径
4. 对详情页再额外支持“当前对象名”补充显示

### 建议表现形式

红框区域最终显示：

```vue
<div class="navbar__route">
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.path">
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
  <span v-if="route.meta?.subtitle" class="navbar__route-subtitle">
    {{ route.meta.subtitle }}
  </span>
</div>
```

如果不想做副标题，那就只保留强化后的面包屑。

## 9.4 产品详情页如何配合页面路线

文件：

- `plm-web/src/views/product/ProductDetail.vue`

当前：

- `PageContainer` 标题来自 `presentation?.title || '产品详情'`

这个标题本身没问题，但要配合顶部页面路线更清晰地表达当前对象。

建议两种方式：

### 方式 A：Navbar 直接根据路由元信息显示层级

例如：

- `产品管理 / 产品详情`

页面主体里继续显示产品名。

### 方式 B：Navbar 显示层级 + 当前对象名

例如：

- `产品管理 / 产品详情 / 超队 3.0 iPhone18 黑色`

这需要在页面里把当前对象标题同步给导航栏。

如果只讨论当前阶段、且不想牵动太多全局逻辑，我建议优先做方式 A。

## 9.5 其他模块如何配合页面路线

这是这次补充里最关键的一条：

页面路线不是只改 `Navbar.vue` 就自动完整，真正落地时还要检查所有主要模块的路由元信息。

建议统一规则：

1. 每个一级模块有明确 `meta.title`
2. 每个列表页有明确 `meta.title`
3. 每个详情页有明确 `meta.title`
4. 如果页面来自某个筛选态，也可以在页面内部补一个轻量副标题，但不要把筛选条件直接硬塞进面包屑

建议检查范围：

- 工作台
- 基础资料下所有页面
- SKU 模块
- 产品管理所有阶段页
- 项目管理页
- 报表中心页
- 系统管理页

如果后续路由层支持自定义面包屑字段，建议统一成：

```ts
meta: {
  title: '产品详情',
  breadcrumb: ['产品管理', '产品详情']
}
```

如果暂时不扩展字段，也至少要保证 `route.matched` 能拿到完整层级标题。

## 10. 建议修改代码位置

## 10.1 `DashboardView.vue`

需要处理：

1. 清理冲突标记
2. 清理乱码
3. 删除重复工作台标题块
4. 删除下方独立快捷操作面板
5. 把快捷操作放入右侧头部区域

## 10.2 `Navbar.vue`

需要处理：

1. 强化顶部面包屑的显示区域
2. 给页面路线预留更明确的布局容器
3. 必要时增加副标题或当前对象名区域
4. 面向所有模块统一消费路由元信息，而不是只为产品详情页特判

建议样式容器：

```css
.navbar__route {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.navbar__route-subtitle {
  color: var(--plm-color-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}
```

## 10.3 `ProductDetail.vue`

需要处理：

1. 补齐适合页面路线表达的标题来源
2. 如需显示当前对象名到顶部导航，则补一个可读字段来源

不建议在这个阶段直接把“页面路线”写死在产品详情页模板里，因为它本质是全局导航能力，不是某一页局部功能。

## 10.4 路由元信息

正式实施时，除了页面文件本身，还需要同步检查路由配置。

重点不是“多写一个标题”，而是保证这些页面都能被 `Navbar.vue` 正确识别：

- 工作台
- 产品列表 / 产品详情
- 工艺路线
- 文件中心
- 供应商管理
- BOM 管理
- 测试管理
- 物料库存
- 需求订单
- 项目管理
- 报表中心
- 审批中心
- 用户管理
- 角色管理

也就是说，这次页面路线方案的实施范围，必须包含“补齐其他模块路由元信息”这一项。

## 11. 建议代码示例

## 11.1 Navbar 页面路线示例

```vue
<header class="navbar">
  <div class="navbar__left">
    <el-button circle :icon="appStore.sidebarCollapsed ? Expand : Fold" @click="appStore.toggleSidebar()" />

    <div class="navbar__route">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.path">
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
  </div>

  <div class="navbar__right">
    ...
  </div>
</header>
```

## 11.2 工作台头部 + 快捷操作示例

```vue
<section class="page-panel dashboard-toolbar-panel">
  <div class="dashboard-toolbar">
    <div class="dashboard-toolbar__summary">
      <p class="page-panel-desc">
        聚焦当前推进中的产品、我的待办、逾期风险和基础资料入口，让首页成为真实的行动面板。
      </p>
    </div>

    <div class="dashboard-toolbar__actions">
      <button
        v-for="action in quickActions"
        :key="action.label"
        class="quick-action-inline"
        type="button"
        @click="open(action.path)"
      >
        <span class="quick-action-inline__label">{{ action.label }}</span>
      </button>
    </div>
  </div>
</section>
```

## 12. 修改后的效果

## 12.1 工作台页面

第一屏会变成：

1. 顶部全局页头显示页面路线：`工作台 / 首页总览`
2. 工作台说明区只保留一份，不再重复
3. 快捷操作显示在右侧红框区域
4. 数据板继续在下面
5. 明细列表继续在更下面联动显示

## 12.2 产品详情页面

顶部会更清楚地告诉用户：

- 我现在在产品管理里
- 当前是产品详情页
- 必要时还能知道当前对象是谁

效果类似：

- `产品管理 / 产品详情`

或者：

- `产品管理 / 产品详情 / 超队 3.0 iPhone18 黑色`

## 12.3 其他模块页面

落地后，其他模块也要保持同样体验，例如：

- `基础资料 / 工艺路线`
- `基础资料 / 文件中心`
- `基础资料 / 供应商管理`
- `基础资料 / 物料库存`
- `项目管理 / 进行中`
- `系统管理 / 用户管理`

这样顶部导航会变成全系统统一语言，而不是只有产品页最完整。

## 13. 风险点

## 13.1 当前工作台页有冲突标记

这是实施前必须解决的问题。

## 13.2 当前多个文件有乱码

包括：

- `DashboardView.vue`
- `ProductDetail.vue`
- `Sidebar.vue`
- `Navbar.vue`

如果不先处理乱码，后续再做页面路线和标题优化，会越来越难维护。

## 13.3 页面路线依赖路由标题质量

`Navbar.vue` 当前面包屑依赖：

- `route.matched`
- `meta.title`

如果某些路由没有完整配置 `meta.title`，页面路线会不完整。

所以正式落地时，不能只改产品详情页，必须一起检查其他模块的路由元信息。

## 13.4 当前对象名是否进入面包屑要谨慎

如果把当前产品名直接放进页面路线：

- 好处是上下文更完整
- 风险是过长标题会挤压导航栏

建议先实现基础路线，再决定是否加入对象名。

## 14. 交付文档结构

本次文档已经覆盖：

1. 工作台标题去重
2. 快捷操作右置
3. 页面路线补充
4. 工作台 / Navbar / 产品详情页的职责划分
5. 建议修改文件
6. 建议代码示例
7. 修改后的效果
8. 风险点

## 15. 结论

我的结论是：

1. 工作台页面不应该继续保留重复标题块
2. 快捷操作应移动到工作台头部右侧红框位置
3. 你新提的“页面路线”应当落在全局 `Navbar.vue` 的面包屑区域
4. 页面路线不能只覆盖产品详情页，还应覆盖工艺路线、文件中心、供应商、库存、订单、报表、系统管理等页面
5. 正式实施时必须同步补齐其他模块的路由元信息
6. 产品详情页需要配合页面路线表达更清晰的层级信息
7. 真正实施前，必须先清掉当前页面文件中的乱码和冲突标记

如果按这个方案落地，整个系统的头部导航会更统一，工作台和产品详情页的路径感也会更强。
