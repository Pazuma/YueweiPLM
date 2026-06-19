# 48-BOM管理主页列表恢复与排序切换前端优化方案

## 1. 任务理解
本次只停留在文档阶段，不修改实际项目代码。

本次需求只针对 `BOM 管理` 主页，不涉及产品详情页、BOM 详情弹窗结构，也不涉及其他模块。

目标是将当前已经改成“按产品分组展开”的主页，恢复为更直接的列表视图，同时补上排序方式切换，让用户既能按录入/更新时间快速看最新 BOM，也能按字母编号把相关 BOM 排到一起，方便连续核对。

本轮明确要求如下：

1. `BOM 管理主页` 恢复成像之前一样的列表页。
2. 增加排序方式选择：
   - 按录入时间/更新时间排序
   - 按字母编号排序
3. 页面顶部控件换位：
   - 红框位置放 `搜索`、`重置`
   - 原来 `搜索`、`重置` 的位置放 `排序方式`
4. 只改 `BOM 管理主页`，不扩散到产品详情、SKU、文件中心等其他模块。
5. 本次只做前端方案沉淀，不涉及后端接口和数据库。

## 2. 所属业务链路
本次属于 `04-BOM管理.md` 对应的 BOM 查询与版本核对入口优化。

结合现有模块定位，BOM 管理主页承担的职责应是：

1. 快速筛选 BOM 记录
2. 扫读版本状态、成本和时间信息
3. 打开单条 BOM 的详情弹窗继续核对

因此主页更适合承担“查找 + 排序 + 列表浏览”的任务，而不是过度强调分组展开结构。对日常高频查阅来说，恢复列表更利于连续浏览。

## 3. 当前页面问题判断
结合当前页面效果和你的新要求，主页存在 4 个主要问题：

1. 当前“按产品分组”的结构不够直观，首屏更像卡片堆叠，连续比对效率不高。
2. 搜索、筛选、按钮区域的布局不顺，操作热点没有集中在一条自然的工作线上。
3. 缺少显式排序方式，用户无法主动决定“先看最新”还是“按编码集中查看”。
4. 分组结构下，同类 BOM 虽然被折叠，但当用户想连续核对多个相关编码时，反而不如标准列表直接。

## 4. 页面改造方案
### 4.1 主体结构恢复为标准列表
`BOM 管理主页` 从当前的“产品分组卡片 + 展开版本行”，恢复为标准表格列表。

建议保留字段：

1. 产品编码
2. 产品名称
3. BOM 类型
4. 当前版本
5. 状态
6. 材料成本
7. 工艺成本
8. 总成本
9. 更新时间
10. 操作

其中：

- `操作` 继续保留 `详情` 按钮
- `详情` 仍然打开当前已实现的 BOM 详情弹窗
- 不再在主页保留分组展开逻辑

这样可以回到更稳定的查阅方式：

`筛选 -> 排序 -> 顺序浏览列表 -> 点详情看弹窗`

### 4.2 顶部筛选栏保留现有筛选逻辑
筛选逻辑不做业务层变化，仍保留：

1. `BOM 搜索`
2. `BOM 类型`

搜索关键词继续覆盖以下内容：

- 产品编码
- 产品名称
- BOM 类型
- 当前版本号

这样无需调整现有数据结构，也符合当前页面的使用习惯。

### 4.3 搜索/重置与排序方式换位
本轮顶部交互按你的要求调整为：

1. 左侧仍然是 `BOM 搜索`、`BOM 类型`
2. 原本 `搜索`、`重置` 的位置改为 `排序方式`
3. 红框位置改为放置 `搜索`、`重置`

建议布局顺序如下：

```text
BOM 搜索 | BOM 类型 | 排序方式 | 搜索 | 重置
```

其中排序方式建议使用单选下拉，不新增复杂交互。

### 4.4 排序方式设计
排序方式建议提供 2 个选项：

1. `按最近录入/更新时间`
2. `按产品编码 / 字母编号`

对应逻辑建议如下：

#### 方案 A：按最近录入/更新时间
- 默认排序方式
- 使用 `updatedAt` 倒序
- 最新修改的 BOM 排在最上方
- 适合日常跟进和处理近期变更

#### 方案 B：按产品编码 / 字母编号
- 使用 `productCode + currentVersion + bomType` 组合排序
- 先按产品编码升序
- 同产品下再按版本号和 BOM 类型稳定排列
- 适合把同系列或同编码的 BOM 放在一起连续核对

这样既满足“先看最新”，也满足“相关 BOM 排到一起”的使用场景。

### 4.5 排序交互建议
排序方式建议放在筛选栏里作为显式控件，而不是依赖表头点击排序。

原因：

1. 用户更容易理解当前页面的排序状态
2. 与搜索、筛选形成一组统一的查询条件
3. 比 Element Plus 表头排序更稳定，避免用户误触后不知道当前排序规则

表头可继续保持静态展示，不强依赖多列点击排序。

## 5. 预期页面效果
改造后，主页预期会回到更工整、更容易连续浏览的状态：

1. 页面主体恢复为标准列表，首屏信息密度更可控。
2. 用户可以直接向下连续查看多条 BOM，不需要展开/折叠。
3. 搜索、筛选、排序、执行按钮都集中在同一条操作线上，交互更顺手。
4. 默认按最近更新时间排序时，更适合日常处理近期变更。
5. 切换为按产品编码排序时，同类 BOM 会排到一起，更利于批量核对。
6. 详情弹窗能力保持不变，主页与详情页职责边界不会被打乱。

## 6. 计划修改的代码文件
本次如果正式开发，预计只需要修改以下文件：

- `plm-web/src/views/bom/BomCenterView.vue`

本轮不建议修改以下文件：

- `plm-web/src/types/foundation.ts`
- `plm-web/src/mock/foundation.ts`
- `plm-web/src/api/modules/foundation.ts`
- `plm-web/src/views/product/ProductDetail.vue`

原因：

1. 当前 BOM 主页所需字段已经具备：
   - `productCode`
   - `productName`
   - `bomType`
   - `currentVersion`
   - `status`
   - `materialCost`
   - `processCost`
   - `totalCost`
   - `updatedAt`
2. 排序需求属于纯前端展示逻辑，可以直接基于现有 `rows` 做 `computed` 排序。
3. 搜索/重置与排序方式换位，可以在 `BomCenterView.vue` 内部定制页面级表单结构，不必改通用组件。

## 7. 计划使用的技术与实现逻辑
### 7.1 前端技术
- Vue 3 `script setup`
- TypeScript
- Element Plus
- CSS Flex + 表格布局

### 7.2 数据处理逻辑
建议在 `BomCenterView.vue` 中保留当前数据源：

- `rows`
- `searchModel`
- `detailVisible`
- `detailRow`
- `productPresentationMap`

新增或调整以下页面级状态：

- `sortMode`
- `filteredRows`
- `sortedRows`

建议逻辑示意：

```ts
const sortMode = ref('updated_desc')

const filteredRows = computed(() => {
  // 继续按关键词和 BOM 类型过滤
})

const sortedRows = computed(() => {
  const list = [...filteredRows.value]

  if (sortMode.value === 'code_asc') {
    return list.sort((a, b) => {
      return `${a.productCode}-${a.currentVersion}-${a.bomType}`.localeCompare(
        `${b.productCode}-${b.currentVersion}-${b.bomType}`
      )
    })
  }

  return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
})
```

### 7.3 结构改造逻辑
建议去掉当前以下主页结构：

- `groupedRows`
- `expandedGroups`
- `toggleGroup`
- `ensureExpandedGroup`
- 整个 `bom-group-list / bom-group-card / bom-version-list`

恢复为：

1. 一个页面级筛选栏
2. 一个排序方式控件
3. 一个标准 `el-table`
4. 一个详情弹窗

也就是说，这次只是把主页从“分组浏览模式”切回“列表浏览模式”，详情弹窗不用推倒重做。

### 7.4 搜索栏换位实现逻辑
由于当前通用 `SearchBar` 组件的按钮区是固定在右侧且顺序固定，本次如果坚持“只修改 BOM 管理主页”，建议不要改通用组件，而是在 `BomCenterView.vue` 中直接写页面专用的查询栏。

推荐原因：

1. 不会影响其他模块正在使用的 `SearchBar`
2. 可以精准控制 `排序方式`、`搜索`、`重置` 的位置
3. 修改范围更符合“只改 BOM 管理主页”的限制

## 8. 可参考的代码结构示意
### 8.1 顶部查询栏示意
```vue
<section class="page-panel bom-toolbar">
  <el-form inline>
    <el-form-item label="BOM 搜索">
      <el-input v-model="searchModel.keyword" />
    </el-form-item>

    <el-form-item label="BOM 类型">
      <el-select v-model="searchModel.bomType" />
    </el-form-item>

    <el-form-item label="排序方式">
      <el-select v-model="sortMode">
        <el-option label="按最近更新时间" value="updated_desc" />
        <el-option label="按产品编码排序" value="code_asc" />
      </el-select>
    </el-form-item>

    <div class="bom-toolbar__actions">
      <el-button type="primary">搜索</el-button>
      <el-button>重置</el-button>
    </div>
  </el-form>
</section>
```

### 8.2 主列表结构示意
```vue
<el-table :data="sortedRows" border stripe>
  <el-table-column prop="productCode" label="产品编码" min-width="160" />
  <el-table-column prop="productName" label="产品名称" min-width="220" />
  <el-table-column prop="bomType" label="BOM 类型" width="110" />
  <el-table-column prop="currentVersion" label="当前版本" width="110" />
  <el-table-column prop="updatedAt" label="更新时间" width="140" />
  <el-table-column label="操作" width="100">
    <template #default="{ row }">
      <el-button link type="primary" @click="openDetail(row)">详情</el-button>
    </template>
  </el-table-column>
</el-table>
```

### 8.3 排序逻辑示意
```ts
const sortedRows = computed(() => {
  const list = [...filteredRows.value]

  switch (sortMode.value) {
    case 'code_asc':
      return list.sort((a, b) => a.productCode.localeCompare(b.productCode))
    default:
      return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  }
})
```

## 9. 预期效果总结
如果按本方案正式落地，主页会形成以下结果：

1. `BOM 管理主页` 回到标准列表页，更接近此前熟悉的浏览方式。
2. 用户可以按时间看最新 BOM，也可以按编码把相关 BOM 排到一起。
3. 顶部控件位置更清晰，操作顺序更贴合使用习惯。
4. 详情弹窗能力延续，不影响已完成的小窗查看方式。
5. 本次改动严格限定在 `BOM 管理主页`，不会牵动其他模块。

## 10. 风险点与说明
1. 如果后续业务还希望支持更多排序方式，例如按状态、按总成本，则需要提前规划排序枚举，避免后续继续堆按钮。
2. 若版本号存在复杂排序规则，例如 `A.10` 与 `A.2` 的自然排序，后续可能需要补充更细的版本排序函数。
3. 当前 `updatedAt` 实际承载的是前端展示时间口径，若后续要严格区分“录入时间”和“最近更新时间”，需要明确字段定义。
4. 本次方案只针对 `BOM 管理主页`，不包含 BOM 详情弹窗和产品详情页调整。
5. 本次只涉及文档沉淀，不修改项目代码。
