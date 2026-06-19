# 37-工艺路线筛选栏位置_展开态加深_资料区分层与BOM明细布局优化方案

## 1. 这次要解决什么

这次先不改代码，先把你提的 3 个页面问题沉淀成一份明确方案。

本轮只涉及前端，不涉及后端，重点是：

1. 当前“展开后的内容”选中态太浅，页面重点不够明显，要把颜色加深
2. 工艺路线页的“时间范围”不要单独放在搜索区下方，要放到你红圈标记的位置，和搜索条件在一行里
3. 产品详情页的“资料区”现在内容堆得太满，要在蓝框位置增加资料类型切换，只在当前类型下展示对应内容  
   - `BOM`
   - `图纸 / 文件`
   - `模具治具`
4. 版本的物料明细不要继续堆在 BOM 表格下面，应该放到你红框标记的位置，做成更直观的“左侧版本对比 + 右侧版本明细”

这份文档会继续按你熟悉的方式写：

- 改哪里
- 怎么改
- 改完长什么样
- 对应代码该写在哪

---

## 2. 我对这次需求的理解

## 2.1 工艺路线页的问题

你现在指出的不是功能缺失，而是“信息层次还不够顺”。

当前页面里：

- 搜索条件是一块
- 时间筛选又单独放成了一整块

这会导致两个感觉：

1. 页面纵向被拉长了
2. 用户会觉得“时间范围像第二套筛选系统”，不是同一组条件的一部分

更合理的做法是：

- 把时间范围组件直接放进搜索栏右侧空位
- 让它变成和 `关键字 / 路线类型 / 状态 / 来源` 并列的一条筛选链路

这样页面会更紧凑，也更像一个成熟的业务检索区。

另外，你说“展开的内容可以把颜色加深”，结合当前页面，我理解主要是这两类状态需要加强：

1. 左侧当前选中的路线卡片
2. 右侧当前激活的板块、视图或当前明细区域

也就是说，不是要全页变花，而是要让“当前正在看的东西”更容易一眼识别。

## 2.2 产品详情页资料区的问题

你这个判断很对。

现在资料区实际上把三类东西都堆在了一起：

- BOM 版本对比
- 版本物料明细
- 图纸 / 文件
- 模具治具摘要

这样会出现两个问题：

1. 用户进入资料区后，一下子看到太多块内容，不知道先看哪一个
2. 真正要做版本核对时，视线被模具、文件等内容打断

所以资料区更合适的结构应该是两层：

### 第一层：资料类型入口

在你蓝框位置增加一个二级切换栏：

- `BOM`
- `图纸 / 文件`
- `模具治具`

### 第二层：当前类型对应内容

用户点到哪个类型，就只展开哪个类型的内容。

这样改完后：

- 要核 BOM，就只看 BOM
- 要找图纸，就只看图纸
- 要看模具治具，就只看模具治具

资料区会从“堆叠展示”变成“按任务切换展示”。

## 2.3 BOM 版本物料明细的布局问题

你给的红框位置很关键。

现在的结构是：

- 上面版本对比表
- 下面版本物料明细

这种结构虽然能用，但问题是：

- 对比感偏弱
- 版本和明细的关系是“上下串行”，不是“左右并行”

如果把明细放到红框位置，做成：

- 左边：版本表
- 右边：当前选中版本明细

用户就会更容易形成一种直观感受：

- 我点的是哪个版本
- 这个版本右侧具体有什么物料
- 不同版本切换时，右侧内容立即变化

这会比“表格下面再接一个大表格”清楚很多。

---

## 3. 这次会涉及哪些文件

如果后续正式开发，主要会改下面几个文件。

### 3.1 工艺路线页相关

- `plm-web/src/components/SearchBar/index.vue`
- `plm-web/src/views/process/ProcessCenterView.vue`

### 3.2 产品详情页相关

- `plm-web/src/views/product/ProductDetail.vue`

如果后面需要把资料区切换项做成更清晰的枚举，也可以补：

- `plm-web/src/types/foundation.ts`

但这次前端模拟阶段，不一定必须先改类型文件。

---

## 4. 每一项具体怎么改

## 4.1 展开态 / 选中态颜色加深

### 要改什么

把当前激活、展开、选中的内容做得更明显一点，不要只有很轻的一层底色。

### 主要修改位置

#### 文件 1：`plm-web/src/views/process/ProcessCenterView.vue`

当前最典型的是：

- `.route-card.is-active`

现在的高亮偏浅：

```css
.route-card.is-active {
  border-color: var(--plm-color-primary);
  background: rgba(37, 99, 235, 0.04);
}
```

建议改成：

```css
.route-card.is-active {
  border-color: rgba(37, 99, 235, 0.42);
  background: rgba(37, 99, 235, 0.12);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.08);
}
```

如果还想让标题更突出，可以再补：

```css
.route-card.is-active strong {
  color: var(--plm-color-primary);
}
```

#### 文件 2：`plm-web/src/views/product/ProductDetail.vue`

如果资料区后续会有二级切换，也建议把当前选中的资料类型按钮状态加深。

例如：

```css
.material-switcher :deep(.el-segmented__item.is-selected) {
  background: var(--plm-color-primary);
  color: #fff;
}
```

### 改完后的效果

改完后，用户第一眼就能知道：

- 左侧哪条路线正在查看
- 资料区当前到底停在哪一类内容

不是“隐约有点蓝”，而是“当前焦点很明确”。

---

## 4.2 时间范围组件移动到搜索栏红圈位置

### 要改什么

把时间范围从独立的 `时间筛选` 区块里挪到搜索栏右侧空位。

### 为什么不能只改 `ProcessCenterView.vue`

因为现在 `SearchBar` 组件本身只有：

- 一组字段
- 搜索 / 重置按钮

它没有给页面额外插入自定义组件的插槽位置。

所以最稳妥的做法是：

1. 先给 `SearchBar` 增加一个右侧扩展插槽
2. 再在 `ProcessCenterView.vue` 里把时间范围放进去

---

### 修改位置 1：`plm-web/src/components/SearchBar/index.vue`

建议在按钮前面增加一个 `extra` 插槽。

原来核心结构大致是：

```vue
<el-form :model="form" inline class="search-form">
  <el-form-item v-for="field in fields" :key="field.prop" :label="field.label">
    ...
  </el-form-item>

  <el-form-item class="search-form__actions">
    <el-button type="primary" @click="handleSearch">搜索</el-button>
    <el-button @click="handleReset">重置</el-button>
  </el-form-item>
</el-form>
```

建议改成：

```vue
<el-form :model="form" inline class="search-form">
  <el-form-item v-for="field in fields" :key="field.prop" :label="field.label">
    ...
  </el-form-item>

  <!-- 新增：页面级扩展区域，专门放额外筛选组件 -->
  <div v-if="$slots.extra" class="search-form__extra">
    <slot name="extra" />
  </div>

  <el-form-item class="search-form__actions">
    <el-button type="primary" @click="handleSearch">搜索</el-button>
    <el-button @click="handleReset">重置</el-button>
  </el-form-item>
</el-form>
```

样式建议补一段：

```css
.search-form__extra {
  display: flex;
  align-items: center;
  margin-left: auto;
  margin-bottom: 12px;
}
```

如果担心和按钮挤在一起，可以再加：

```css
.search-form__extra {
  margin-right: 12px;
}
```

---

### 修改位置 2：`plm-web/src/views/process/ProcessCenterView.vue`

现在时间范围组件是在搜索栏下面单独放了一块：

```vue
<section class="page-panel process-filter-panel">
  <div class="toolbar-row process-filter-panel__row">
    ...
    <ProjectTimeRangeFilter v-model="timeRange" />
  </div>
</section>
```

建议改成：删掉这一整块，把时间范围放到 `SearchBar` 的 `extra` 插槽里。

示例代码：

```vue
<SearchBar
  :fields="searchFields"
  :model-value="table.query"
  @search="table.setQuery"
  @reset="table.resetQuery({ keyword: '', routeType: '', status: '', templateSource: '' })"
>
  <template #extra>
    <!-- 放到搜索栏右侧红圈位置 -->
    <ProjectTimeRangeFilter v-model="timeRange" />
  </template>
</SearchBar>
```

然后可以把下面这块删掉：

```vue
<section class="page-panel process-filter-panel">
  ...
</section>
```

### 改完后的效果

工艺路线页顶部会更紧凑：

- 左边是关键字、路线类型、状态、来源
- 右边是时间范围
- 最右边还是搜索和重置

整个筛选区会变成一行逻辑完整的检索带。

---

## 4.3 资料区增加二级资料类型切换

### 要改什么

在资料区内部增加一个二级切换栏，位置就是你蓝框标记的位置。

切换项：

- `BOM`
- `图纸 / 文件`
- `模具治具`

### 修改位置：`plm-web/src/views/product/ProductDetail.vue`

建议增加一个新的页面状态：

```ts
type MaterialSectionKey = 'bom' | 'files' | 'tooling'

const activeMaterialSection = ref<MaterialSectionKey>('bom')
```

然后在资料区标题下面加一个二级切换：

```vue
<div class="toolbar-row material-toolbar">
  <div>
    <h3 class="section-title">资料区</h3>
    <p class="page-panel-desc">
      按资料类型切换查看，避免 BOM、图纸文件和模具治具全部堆在一起。
    </p>
  </div>

  <el-segmented
    v-model="activeMaterialSection"
    :options="[
      { label: 'BOM', value: 'bom' },
      { label: '图纸 / 文件', value: 'files' },
      { label: '模具治具', value: 'tooling' }
    ]"
    class="material-switcher"
  />
</div>
```

---

## 4.4 资料区内容按类型分别展示

### 目标结构

#### 当选中 `BOM`

只展示：

- BOM 版本对比
- 当前版本物料明细

#### 当选中 `图纸 / 文件`

只展示：

- 文件列表

#### 当选中 `模具治具`

只展示：

- 模具治具摘要
- 关联模具治具入口

---

### 建议模板结构

```vue
<section v-else-if="activeSection === 'materials'" class="page-panel detail-subpanel">
  <div class="toolbar-row material-toolbar">
    ...
  </div>

  <div v-if="activeMaterialSection === 'bom'" class="page-stack">
    <!-- 只放 BOM -->
  </div>

  <div v-else-if="activeMaterialSection === 'files'" class="page-stack">
    <!-- 只放图纸 / 文件 -->
  </div>

  <div v-else class="page-stack">
    <!-- 只放模具治具 -->
  </div>
</section>
```

### 为什么这样改

这样改完后，资料区不再是“所有资料一起端出来”，而是：

- 我现在要核 BOM，就进 BOM
- 我现在要找文件，就进文件
- 我现在要看模具治具，就进模具治具

任务感会更强，认知负担更低。

---

## 4.5 BOM 版本明细移到红框位置

### 要改什么

把当前版本物料明细从“表格下方纵向展示”，改成“右侧并排展示”。

### 修改位置：`plm-web/src/views/product/ProductDetail.vue`

当前的 `bom-compare-layout` 是上下堆叠的：

```css
.bom-compare-layout {
  display: grid;
  gap: 16px;
}
```

建议改成桌面双栏：

```css
.bom-compare-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 16px;
  align-items: start;
}
```

移动端继续自动改成纵向：

```css
@media (max-width: 1280px) {
  .bom-compare-layout {
    grid-template-columns: 1fr;
  }
}
```

### 模板结构建议

```vue
<div class="bom-compare-layout">
  <div class="bom-compare-layout__table">
    <el-table
      :data="presentation.bomCompareRows"
      border
      stripe
      highlight-current-row
      @row-click="(row: { versionNo: string }) => selectBomVersion(row.versionNo)"
    >
      ...
    </el-table>
  </div>

  <div class="bom-compare-layout__detail">
    <div class="toolbar-row">
      <div>
        <h4 class="section-title">{{ selectedBomTitle }}</h4>
        <p class="page-panel-desc">
          当前选中的版本物料明细放在右侧，切换版本时这里同步变化。
        </p>
      </div>
    </div>

    <el-table :data="selectedBomItems" border stripe>
      ...
    </el-table>
  </div>
</div>
```

### 改完后的效果

资料区里看 BOM 时，页面会更像一个“对比工作台”：

- 左边点版本
- 右边看明细

对比时不用上下拉着看，效率会高很多。

---

## 5. 建议的代码调整顺序

如果后面你确认要正式改代码，我建议按这个顺序做：

1. 先改 `SearchBar` 组件，加 `extra` 插槽
2. 再改 `ProcessCenterView.vue`，把时间范围挪进搜索栏
3. 再调工艺路线页的选中态颜色
4. 再改 `ProductDetail.vue`，加入资料区二级切换
5. 最后把 BOM 明细改成左右布局

### 为什么这样排

因为：

- 第 1、2 步是工艺路线页的结构调整
- 第 3 步是视觉加强
- 第 4、5 步是产品详情页的资料区重构

这样做改动边界比较清楚，不容易互相打架。

---

## 6. 本次涉及的前端技术

这次方案依然基于现有技术栈，不需要引新框架：

- Vue 3 `script setup`
- TypeScript
- Element Plus
  - `el-segmented`
  - `el-table`
  - `el-form`
  - `el-button`
- 组件插槽 `slot`
- 响应式布局 `grid`

这次最关键的实现点其实是两个：

1. `SearchBar` 组件加扩展插槽
2. `ProductDetail` 资料区从“堆叠结构”调整为“任务切换结构”

---

## 7. 改完后的整体页面感觉

如果按这份方案落下去，页面会有几个很明显的变化：

### 工艺路线页

- 顶部筛选区更紧凑
- 时间范围终于和其他筛选项属于同一层
- 当前选中的路线更显眼

### 产品详情页资料区

- 不再一进资料区就看到一大坨内容
- 用户可以先决定自己现在是在看：
  - BOM
  - 文件
  - 模具治具
- BOM 版本对比会更像真正的左右联动查看

整体上会从“内容都在，但有点挤”变成“内容分层更清楚，用户知道自己现在在看哪一类东西”。

---

## 8. 本次结论

这份文档是实施前沉淀，当前结论是：

- 已明确时间范围组件要挪到工艺路线搜索栏红圈位置
- 已明确要通过 `SearchBar` 插槽来实现，而不是继续额外堆一个筛选面板
- 已明确资料区要加二级资料类型切换
- 已明确 BOM 明细改到右侧红框位置，形成左右联动结构
- 已明确当前选中 / 展开内容的视觉状态要加深

当前还没有修改任何实际代码。  
如果你确认这份方案没问题，下一步就可以按这份文档正式落前端。

