# 46-文件中心删除操作、添加文件入口、时间范围与类型筛选前端方案修正版

## 1. 任务理解

本次只修正文档，不修改项目代码。

这次文件中心页面的优化，核心目标不是单纯多加几个按钮，而是把“文件中心”从浏览页整理成更贴近实际业务的资料管理页。结合你的补充，当前需要明确的内容有四组：

1. 具体项目下的文件列表操作列增加“删除”按钮。
2. 增加“添加文件”入口，但这个入口不是放在顶部工具栏，而是放在每个项目组头部的右侧操作区。
3. 在顶部筛选区域增加时间范围组件。
4. 在顶部筛选区域增加类型组件，用来区分“产品”和“新型号”。

你这次指出的文档错误很关键：

- “添加文件”不应该被设计成全局顶栏按钮。
- 它应该属于“当前项目组”的操作，放在项目组头部，和“文件数量”“进入产品”处于同一层级。

这意味着页面动作要分清两层：

- 项目级动作：添加文件、进入产品
- 文件级动作：查看、删除

## 2. 所属 PLM 业务链路

本次属于：

- 基础资料模块
- 文件中心页面
- 产品资料 / 新型号资料 / 测试资料 / 工程资料的前端浏览与维护入口优化

它服务的业务动作主要是：

- 快速筛选需要查看的资料范围
- 按产品 / 新型号区分资料归属
- 在指定项目下补充文件
- 删除错误上传或已废弃文件
- 进入对应产品详情继续查看上下文

## 3. 涉及前端页面

本次方案主要涉及：

- `plm-web/src/views/file/FileCenterView.vue`

配套可能涉及：

- `plm-web/src/types/foundation.ts`
- `plm-web/src/mock/foundation.ts`

如果正式实施时想把上传弹窗拆出去，也可以新增：

- `plm-web/src/views/file/components/FileUploadDialog.vue`

但从当前页面规模看，先写在 `FileCenterView.vue` 内部也完全可行。

## 4. 不涉及后端

本次只讨论前端，不涉及后端。

因此本轮不会处理：

- 文件真实上传接口
- 文件真实删除接口
- 文件存储
- 权限落库
- 审计日志落库

当前阶段只做前端结构、假数据字段和交互方案。

## 5. 现状判断

### 5.1 当前顶部工具栏能力不完整

当前页面顶部主要只有：

- 进行中项目 / 全部项目
- 搜索框

对于文件中心这种资料页来说，这还不够。用户实际需要的还有：

- 时间范围筛选
- 类型筛选

### 5.2 当前“操作列”只有查看，没有资料维护闭环

文件表格的“操作”列目前只有“查看”，这会让页面更像目录页，而不是资料管理页。至少还需要：

- 删除

### 5.3 “添加文件”必须绑定到具体项目

这是这次文档修正的重点。

如果把“添加文件”放在顶栏，会产生两个问题：

1. 用户点击后还得重新选所属项目，操作绕了一圈。
2. 这个动作容易被理解成“全局添加”，不符合页面的分组结构。

而当前页面本身已经是按项目组展示的，所以“添加文件”更适合直接放在每个项目组头部右侧。这样用户一眼就知道：

- 我要给哪个项目加文件
- 当前新增动作属于哪个项目组

### 5.4 当前页面已经有“产品 / 新型号”的结构基础

从现有数据结构看，页面其实已经区分了：

- `product_files`
- `variant_files`

也就是说，“类型筛选”并不是凭空新增概念，而是把现有结构显式做成前端筛选组件。

## 6. 修正后的页面结构方案

## 6.1 顶部工具栏怎么改

顶部工具栏不再放“添加文件”。

修正后的工具栏建议只保留三段：

1. 左侧：项目范围切换
2. 中间：时间范围 + 类型筛选
3. 右侧：搜索

建议结构如下：

```vue
<section class="page-panel file-toolbar">
  <div class="file-toolbar__left">
    <el-segmented v-model="activeView" :options="viewOptions" />
  </div>

  <div class="file-toolbar__filters">
    <el-select v-model="activeDateRange" placeholder="时间范围">
      <el-option label="最近一星期" value="7d" />
      <el-option label="最近一个月" value="30d" />
      <el-option label="最近半年" value="180d" />
    </el-select>

    <el-segmented
      v-model="activeProductType"
      :options="[
        { label: '全部', value: 'all' },
        { label: '产品', value: 'product' },
        { label: '新型号', value: 'variant' }
      ]"
    />
  </div>

  <div class="file-toolbar__right">
    <el-input v-model="keyword" placeholder="搜索文件名 / 项目 / 负责人" clearable />
  </div>
</section>
```

这里要点很明确：

- 顶栏只负责“筛选”
- 不负责“针对某个项目执行新增”

## 6.2 “添加文件”正确位置

“添加文件”应放在每个项目组卡片头部右侧操作区。

也就是和下面这些内容放在一起：

- 文件数量
- 进入产品

修正后的项目组头部建议变成：

- 左侧：项目名、编码、负责人、更新时间
- 右侧：文件数、添加文件、进入产品

建议结构如下：

```vue
<div class="group-card__header">
  <div class="group-card__meta">
    <h3>{{ group.projectName }}</h3>
    <p>{{ group.productCode }} · {{ group.owner }} · 更新于 {{ group.updatedAt }}</p>
  </div>

  <div class="group-card__actions">
    <span class="subtle-text">{{ group.files.length }} 个文件</span>

    <div class="group-card__action-buttons">
      <el-button size="small" type="primary" plain @click.stop="openUploadDialog(group)">
        添加文件
      </el-button>
      <el-button link type="primary" @click.stop="openProduct(group.productId)">
        进入产品
      </el-button>
    </div>
  </div>
</div>
```

这样改之后，动作归属就非常清晰：

- 用户在“某个项目组”里点“添加文件”
- 就是在“当前项目”下新增文件

## 6.3 添加文件弹窗怎么改

因为“添加文件”已经绑定到项目组头部，所以弹窗打开时不应该再让用户重新选择项目，而应该自动带入当前项目。

也就是说：

- 点击哪个项目组的“添加文件”
- 弹窗里“所属项目”就自动显示哪个项目

弹窗里仍然需要用户选择和填写的内容是：

1. 所属环节
2. 文件分类
3. 文件名称
4. 文件版本
5. 上传文件

建议结构如下：

```vue
<el-dialog v-model="uploadDialogVisible" title="添加文件" width="560px">
  <el-form label-width="90px">
    <el-form-item label="所属项目">
      <el-input :model-value="uploadForm.projectName" disabled />
    </el-form-item>

    <el-form-item label="所属环节">
      <el-select v-model="uploadForm.stageKey" placeholder="请选择环节">
        <el-option label="立项资料" value="project_setup" />
        <el-option label="工程图纸" value="engineering" />
        <el-option label="测试资料" value="testing" />
        <el-option label="生产资料" value="production" />
        <el-option label="差异资料" value="variant" />
      </el-select>
    </el-form-item>

    <el-form-item label="文件分类">
      <el-input v-model="uploadForm.category" />
    </el-form-item>

    <el-form-item label="文件名称">
      <el-input v-model="uploadForm.fileName" />
    </el-form-item>

    <el-form-item label="版本号">
      <el-input v-model="uploadForm.versionNo" />
    </el-form-item>

    <el-form-item label="文件上传">
      <el-upload>
        ...
      </el-upload>
    </el-form-item>
  </el-form>
</el-dialog>
```

对应的方法也要改成接收当前项目组：

```ts
function openUploadDialog(group: FileProjectGroup) {
  uploadForm.productId = group.productId
  uploadForm.projectName = group.projectName
  uploadForm.stageKey = ''
  uploadForm.category = ''
  uploadForm.fileName = ''
  uploadForm.versionNo = ''
  uploadDialogVisible.value = true
}
```

这个改动的核心价值是：

- 用户不用再重复选择项目
- 新增动作天然带上下文
- 页面逻辑和视觉结构一致

## 6.4 删除按钮放哪里

删除仍然放在文件表格的“操作列”里。

因为“删除”属于文件级动作，不属于项目级动作。

建议改成：

```vue
<el-table-column label="操作" width="180">
  <template #default="{ row }">
    <div class="file-row-actions">
      <el-button link type="primary">查看</el-button>
      <el-button link type="danger" @click="openDeleteDialog(row)">删除</el-button>
    </div>
  </template>
</el-table-column>
```

删除前建议先弹确认框，避免误删。

## 6.5 时间范围组件和类型组件怎么处理

顶部筛选区建议增加两类状态：

```ts
const activeDateRange = ref<'7d' | '30d' | '180d'>('30d')
const activeProductType = ref<'all' | 'product' | 'variant'>('all')
```

对应逻辑建议如下：

### 时间范围

选项：

- 最近一星期
- 最近一个月
- 最近半年

作用：

- 优先基于 `file.uploadedAt` 筛选文件
- 如果某个项目组内有符合范围的文件，则该项目组显示
- 表格里只显示当前时间范围内的文件

### 类型

选项：

- 全部
- 产品
- 新型号

映射关系：

- `product_files` -> `product`
- `variant_files` -> `variant`

## 7. 建议修改的数据结构

虽然本次不改代码，但如果后续按文档实施，建议先补足下面这些字段。

## 7.1 `plm-web/src/types/foundation.ts`

建议补充：

```ts
export interface FileRecord {
  fileId: string
  fileName: string
  category: string
  owner: string
  uploadedAt: string
  versionNo: string
  productId: number
  stageKey?: string
  stageLabel?: string
}

export interface FileProjectGroup {
  groupId: string
  projectName: string
  productCode: string
  productId: number
  owner: string
  updatedAt: string
  productType?: 'product' | 'variant'
  files: FileRecord[]
}

export type FileDateRange = '7d' | '30d' | '180d'
export type FileProductTypeFilter = 'all' | 'product' | 'variant'
```

## 7.2 `plm-web/src/mock/foundation.ts`

为了支撑前端假数据交互，建议：

- 给每个项目组加 `productType`
- 给每个文件加 `stageKey` / `stageLabel`

示例：

```ts
{
  fileId: 'f-101-1',
  fileName: 'PRD-CD30 结构图纸 V3.pdf',
  category: '工程图纸',
  owner: '工程部',
  uploadedAt: '2026-06-01',
  versionNo: 'V3',
  productId: 101,
  stageKey: 'engineering',
  stageLabel: '工程图纸'
}
```

## 8. 建议修改的代码文件

本次仍然只输出方案，但如果后续实施，建议涉及的文件如下。

## 8.1 `plm-web/src/views/file/FileCenterView.vue`

主要改动点：

1. 顶部工具栏补时间范围、类型、搜索布局
2. 项目组头部右侧新增“添加文件”
3. 文件表格操作列新增“删除”
4. 新增上传弹窗
5. 新增删除确认弹窗
6. 调整筛选逻辑，支持范围、类型、关键字联动

## 8.2 `plm-web/src/types/foundation.ts`

补类型字段，给页面状态和 mock 数据提供结构支撑。

## 8.3 `plm-web/src/mock/foundation.ts`

补齐项目类型、文件环节等假数据字段。

## 9. 建议增加的方法与状态

后续实施时，页面里大概率会增加这些状态：

```ts
const activeDateRange = ref<'7d' | '30d' | '180d'>('30d')
const activeProductType = ref<'all' | 'product' | 'variant'>('all')
const uploadDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
```

上传表单建议：

```ts
const uploadForm = reactive({
  productId: undefined as number | undefined,
  projectName: '',
  stageKey: '',
  category: '',
  fileName: '',
  versionNo: ''
})
```

方法建议：

```ts
function openUploadDialog(group: FileProjectGroup) {
  uploadForm.productId = group.productId
  uploadForm.projectName = group.projectName
  uploadDialogVisible.value = true
}

function openDeleteDialog(file: FileRecord) {
  ...
}

function confirmDelete() {
  ...
}
```

## 10. 修改后的页面效果

改完之后，页面的使用体验应该是下面这种感觉。

### 10.1 顶部工具栏

从原来的：

- 项目范围切换
- 搜索

变成：

- 项目范围切换
- 时间范围筛选
- 类型筛选
- 搜索

这里不再放“添加文件”。

### 10.2 项目组头部

每个项目组头部右侧会有一组清晰动作：

- 文件数
- 添加文件
- 进入产品

这样用户会自然理解：

- 顶部负责筛选
- 项目头部负责项目级动作

### 10.3 文件表格

每行文件会有：

- 查看
- 删除

这样文件中心就具备最基本的资料维护能力。

### 10.4 添加文件流程

用户操作路径会变成：

1. 找到某个项目组
2. 点击该项目组头部的“添加文件”
3. 弹窗自动带出当前项目
4. 用户只需要补所属环节、分类、名称、版本、上传内容

这个路径比顶栏全局添加更顺。

## 11. 风险点

### 11.1 不能把“添加文件”误做成全局动作

这是本次文档修正最重要的一点。

如果把“添加文件”放顶栏，用户会在新增时失去上下文，页面结构也会变得别扭。所以实施时一定要坚持：

- “添加文件”属于项目组头部动作
- 不是顶栏全局动作

### 11.2 当前阶段仍然只是前端模拟

即使做出上传弹窗和删除确认，也只是前端假数据交互，不代表真实文件上传和删除已经打通。

### 11.3 时间范围建议按文件时间筛

建议优先基于 `file.uploadedAt`，不要只看 `group.updatedAt`，不然筛选结果会偏粗。

## 12. 结论

这次文档修正后的核心结论是：

- 顶部工具栏只保留筛选能力，不放“添加文件”
- “添加文件”放在每个项目组头部右侧，和“文件数”“进入产品”同一层级
- 上传弹窗打开时自动带入当前项目，避免重复选择
- “删除”继续留在文件行操作列，形成文件级动作闭环

这样页面层级会更顺，也更符合你现在要的业务操作习惯。

---

本文件已按你的反馈修正“添加文件位置”这一关键错误。
当前仍然只是方案文档，未改动任何项目代码。
