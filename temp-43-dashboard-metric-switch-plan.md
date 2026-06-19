# 43-工作台数据板联动展示与快捷操作上移前端优化方案

## 1. 本次需求理解

这次你要优化的是工作台页面的信息组织方式，不是新增业务功能。

你的要求可以拆成 3 个点：

1. 点击上面的数据板后，下面区域只显示这个数据板对应的内容
2. 上面的数据板既然已经有分类，下面就不要把所有板块都一起挤着展示
3. 快捷操作可以上移到顶部的数据板区域，一起做成上方操作入口

本次只输出文档方案，不修改项目代码。

---

## 2. 当前页面问题判断

当前工作台页面文件：

- `plm-web/src/views/dashboard/DashboardView.vue`

当前页面结构大致是：

1. 顶部说明卡
2. 一排数据板
3. 下方同时展示：
   - 进行中的产品
   - 我的待办
   - 逾期预警
   - 快捷操作

### 2.1 当前主要问题

虽然上方数据板已经做成了分类入口，但下方内容区还是把多个模块同时堆出来，导致两个问题：

1. 信息重复
2. 页面视觉重点不够集中

举例来说：

- 上面已经有“进行中的产品”
- 下面又常驻展示“进行中的产品”

这样用户点击上方数据板和不点击，上下页面差异不够大，数据板像“会跳转的统计块”，而不像“当前内容视图切换器”。

### 2.2 快捷操作的位置不够高频

当前快捷操作在页面下半区，属于和列表内容平级的一个模块。

但从实际使用习惯看，快捷操作更像：

- 页面级入口
- 高频按钮区
- 用户打开工作台后想立刻点的操作

所以它更适合放在上面的操作层，而不是埋在下方内容区。

---

## 3. 优化目标

工作台优化后，应该更像一个“可切换视图的行动面板”，而不是“多个模块一起堆出来的首页”。

建议改成下面这种结构：

### 3.1 顶部区

顶部保留：

- 页面标题和说明
- 当前用户标识
- 一排数据板
- 一排快捷操作按钮

### 3.2 内容区

内容区只显示“当前选中的数据板对应内容”。

也就是说：

- 点击“进行中的产品” -> 下方只展示进行中的产品列表
- 点击“我的待办” -> 下方只展示我的待办列表
- 点击“逾期预警” -> 下方只展示逾期预警列表
- 点击“待冻结资料” -> 下方只展示待冻结资料对应列表或冻结缺口入口

这样工作台就会从“信息堆叠页”变成“按主题切换的工作视图”。

---

## 4. 推荐交互方案

## 4.1 上方数据板改成“视图切换器”

建议不要只把数据板当成跳转按钮，而要把它变成当前内容的主筛选器。

推荐交互：

1. 默认激活第一个数据板，例如“进行中的产品”
2. 点击其他数据板后：
   - 上方数据板高亮切换
   - 下方列表内容跟着切换
3. 当前激活的数据板对应下方唯一内容区

这样用户打开页面后会很清楚：

- 我现在看的是哪一类内容
- 下方这些内容是因为我选了上面这个分类才出现的

## 4.2 快捷操作上移

快捷操作建议移动到数据板下方，但仍然属于页面上半区。

推荐形式：

- 一组轻量操作按钮
- 位置放在数据板下方、内容区上方
- 和数据板一起形成“顶部行动区”

这样上方就是：

1. 数据板分类入口
2. 快捷操作按钮入口

用户打开首页后，不需要先滚动到下面再找入口。

---

## 5. 页面结构建议

建议把页面结构改成三层：

### 第一层：页面头部

- 标题
- 描述
- 当前用户标识

### 第二层：工作台入口区

- 数据板切换区
- 快捷操作按钮区

### 第三层：当前视图内容区

根据当前激活的数据板，只渲染一类内容：

- 进行中的产品视图
- 我的待办视图
- 逾期预警视图
- 待冻结资料视图

---

## 6. 建议修改文件

本次如果后续正式开发，建议主要修改：

- `plm-web/src/views/dashboard/DashboardView.vue`

当前工作台数据、快捷操作和内容结构都集中在这个文件里，属于一个很适合收敛修改的点。

本次不需要动：

- 后端接口
- 路由结构
- 其他页面

如果后续你希望把工作台数据拆出成独立 mock 文件或类型文件，那是第二步，不是这一步必须要做的事。

---

## 7. 代码结构建议

## 7.1 增加“当前激活数据板”状态

建议新增一个状态，例如：

```ts
type DashboardViewKey = 'products' | 'tasks' | 'risks' | 'freeze'

const activeMetricView = ref<DashboardViewKey>('products')
```

作用：

- 控制上方哪个数据板高亮
- 控制下方渲染哪个内容区

---

## 7.2 调整数据板配置

当前 `topMetrics` 更像“统计按钮数组”。

建议增强为：

```ts
const topMetrics = computed(() => [
  {
    key: 'products',
    label: '进行中的产品',
    value: inProgressProducts.value.length,
    hint: '点击查看当前推进中的产品列表'
  },
  {
    key: 'tasks',
    label: '我的待办',
    value: myPendingTasks.value.length,
    hint: '点击查看我的待处理任务'
  },
  {
    key: 'risks',
    label: '逾期预警',
    value: overdueRisks.value.length,
    hint: '点击查看逾期或风险项目'
  },
  {
    key: 'freeze',
    label: '待冻结资料',
    value: 4,
    hint: '点击查看待冻结的资料清单'
  }
])
```

这样它就不只是跳转数据，而是页面内部的视图定义。

---

## 7.3 数据板点击时不直接跳走，而是切换当前视图

当前点击数据板会执行：

```ts
open(metric.path)
```

建议改成：

```ts
function selectMetricView(view: DashboardViewKey) {
  activeMetricView.value = view
}
```

模板里改成：

```vue
<button
  v-for="metric in topMetrics"
  :key="metric.key"
  class="metric-card dashboard-button"
  :class="{ 'is-active': activeMetricView === metric.key }"
  type="button"
  @click="selectMetricView(metric.key)"
>
```

注意：

如果某个数据板未来仍然需要“查看全部”跳转，不要放在卡片点击上做，建议放在下方内容区标题右侧做“查看全部”按钮。

这样职责更清楚：

- 卡片负责切换视图
- 标题按钮负责跳转完整页面

---

## 7.4 快捷操作上移到顶部行动区

建议新增一个顶部操作栏，例如：

```vue
<section class="page-panel dashboard-actions-panel">
  <div class="dashboard-actions">
    <button
      v-for="action in quickActions"
      :key="action.label"
      class="dashboard-action-chip"
      type="button"
      @click="open(action.path)"
    >
      <strong>{{ action.label }}</strong>
      <span>{{ action.hint }}</span>
    </button>
  </div>
</section>
```

然后把下方原来的“快捷操作”整个模块移除。

这样页面会更紧凑，也更符合“快捷入口在上面，明细内容在下面”的逻辑。

---

## 7.5 下方内容区改成条件渲染

当前页面是两个 `split-grid dashboard-main`，把多个模块都同时展示。

建议改成单一内容区，根据 `activeMetricView` 切换：

```vue
<section v-if="activeMetricView === 'products'" class="page-panel">
  ...
</section>

<section v-else-if="activeMetricView === 'tasks'" class="page-panel">
  ...
</section>

<section v-else-if="activeMetricView === 'risks'" class="page-panel">
  ...
</section>

<section v-else class="page-panel">
  ...
</section>
```

这样下方不会再把所有东西一起堆出来。

---

## 8. 待冻结资料视图建议

当前“待冻结资料”只是顶部一个统计值，还没有明显对应的下方主视图。

建议在这次方案里先按前端 mock 方式补一个“待冻结资料视图”：

展示内容可以包括：

- 产品名称
- 当前版本
- 缺失资料类型
- 责任人
- 截止日期
- 跳转入口

建议先在 `DashboardView.vue` 内部补一组 mock 数据，例如：

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

然后做成和“我的待办”类似的列表卡片。

---

## 9. 样式建议

## 9.1 数据板激活态

既然数据板要承担“切换视图”的职责，就要有明确激活态。

建议增加：

```css
.dashboard-button.is-active {
  border-color: var(--plm-color-primary);
  background: rgba(59, 130, 246, 0.06);
  box-shadow: var(--plm-shadow-sm);
}
```

这样用户会知道：

- 当前选的是哪个分类
- 下方内容为什么变成这一类

## 9.2 顶部快捷操作按钮区

快捷操作上移后，建议样式比当前下方 `quick-grid` 更轻、更像工具栏按钮。

例如：

```css
.dashboard-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-action-chip {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--plm-color-border-light);
  border-radius: var(--plm-radius-base);
  background: #fff;
  text-align: left;
  cursor: pointer;
}
```

这会比现在下方那个大块“快捷操作模块”更利落。

---

## 10. 修改后的页面效果

如果按这份方案改，页面会从现在这种感觉：

- 上面有分类统计块
- 下面却把所有内容都一起展示
- 快捷操作夹在下面模块里

变成这种感觉：

- 上面是“工作台分类入口”
- 上面紧接着是“快捷操作入口”
- 下面只出现当前分类对应内容

具体体验变化会是：

### 10.1 点击“进行中的产品”

下方只出现：

- 进行中的产品列表

不会同时再挤着显示：

- 我的待办
- 逾期预警
- 快捷操作

### 10.2 点击“我的待办”

下方只出现：

- 我的待办列表

### 10.3 点击“逾期预警”

下方只出现：

- 风险项目列表

### 10.4 点击“待冻结资料”

下方只出现：

- 待冻结资料列表

这样工作台会更聚焦，更像“当前要处理什么，就切到什么”。

---

## 11. 推荐实施顺序

后续真要开发，我建议按下面顺序落：

1. 先加 `activeMetricView` 状态
2. 再把顶部数据板改成视图切换器
3. 再把下方内容改成条件渲染
4. 最后把快捷操作整体上移

这样做更稳，也更容易逐步观察页面变化。

---

## 12. 本次结论

这次工作台优化的核心，不是继续叠更多内容，而是把页面变成“分类驱动的单视图工作台”。

我建议的最终方向是：

1. 顶部数据板负责分类切换
2. 下方内容区只展示当前分类对应内容
3. 快捷操作上移到顶部按钮区
4. 页面不再把多个模块同时挤在一起

如果按这个方案实施，工作台会更清楚、更聚焦，也更符合实际操作习惯。

本次只完成方案文档，不修改项目代码。
