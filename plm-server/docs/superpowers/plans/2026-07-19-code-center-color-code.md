# 编码中心与颜色编码接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增通用编码中心，首期完成颜色编码 CRUD、启停、指定 XLSX 预览导入，并让历史 BOM、工作台路线和投产颜色统一校验启用颜色编码。

**Architecture:** 使用单表 `plm_code_item` 承载通用编码项，后端按 controller/service/repository 分层提供 CRUD 与状态接口；颜色 XLSX 导入使用一次性内存预览令牌，避免新增设计未要求的导入批次业务表。BOM 路线颜色保存 `code_item_id`、编码值和名称快照，投产和历史导入均由后端重新校验编码状态。

**Tech Stack:** Java 21、Spring Boot 3.5、MyBatis-Plus、Flyway、Apache POI、PostgreSQL、Vue 3、TypeScript、Element Plus、Vitest。

## Global Constraints

- 首期只上线 `code_type=color`，数据库与接口保留其他编码类型扩展能力。
- `code_type + code_value` 唯一；`code_value` 使用字符串保存，必须保留 `01` 等前导零。
- 编码字段仅包含 `code_item_id`、`code_type`、`code_value`、`code_name`、`status`、`sort_order` 和标准审计字段。
- 不增加中文名称、来源文件、导入批次或源更新时间业务字段。
- 不物理删除；停用编码保持历史可读，但不能进入新 BOM 和新投产选择。
- 不做 ERP/MES 正式同步。
- 保留工作区既有未提交改动；每次提交只暂存本任务明确文件。

---

### Task 1: 编码主表、领域模型与唯一约束

**Files:**
- Create: `src/main/resources/db/migration/V20260719_1002__code_center_color_codes.sql`
- Create: `src/main/java/com/yuewei/plm/module/code/entity/CodeItem.java`
- Create: `src/main/java/com/yuewei/plm/module/code/repository/CodeItemRepository.java`
- Create: `src/test/java/com/yuewei/plm/module/code/CodeCenterMigrationContractTest.java`

**Interfaces:**
- Produces: `CodeItemRepository extends BaseMapper<CodeItem>`。
- Produces: `CodeItem` 属性 `codeItemId/codeType/codeValue/codeName/status/sortOrder`。

- [ ] **Step 1: 编写失败的迁移契约测试**

```java
@Test
void migrationDefinesCodeItemAndColorReferences() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V20260719_1002__code_center_color_codes.sql"));
    assertThat(sql).contains("create table if not exists plm_code_item");
    assertThat(sql).contains("unique index").contains("code_type, code_value");
    assertThat(sql).contains("add column if not exists code_item_id");
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `mvn -Dtest=CodeCenterMigrationContractTest test`

Expected: FAIL，因为迁移文件不存在。

- [ ] **Step 3: 创建迁移**

```sql
create table if not exists plm_code_item (
    code_item_id bigserial primary key,
    code_type varchar(32) not null,
    code_value varchar(64) not null,
    code_name varchar(128) not null,
    status varchar(16) not null default 'enabled',
    sort_order integer not null default 0,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'system',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'system',
    deleted_flag integer not null default 0
);

create unique index if not exists uk_plm_code_item_type_value_active
    on plm_code_item(code_type, code_value)
    where deleted_flag = 0;

alter table if exists plm_product_bom_route_color
    add column if not exists code_item_id bigint,
    add column if not exists color_code varchar(64);

alter table if exists plm_product_production_color_decision
    add column if not exists code_item_id bigint,
    add column if not exists color_code varchar(64);
```

- [ ] **Step 4: 创建实体与 Repository**

```java
@Data
@TableName("plm_code_item")
@EqualsAndHashCode(callSuper = true)
public class CodeItem extends BaseEntity {
    @TableId(value = "code_item_id", type = IdType.AUTO)
    private Long codeItemId;
    private String codeType;
    private String codeValue;
    private String codeName;
    private String status;
    private Integer sortOrder;
}
```

- [ ] **Step 5: 运行契约测试并确认 GREEN**

Run: `mvn -Dtest=CodeCenterMigrationContractTest test`

Expected: PASS。

- [ ] **Step 6: 提交任务**

```powershell
git add -- src/main/resources/db/migration/V20260719_1002__code_center_color_codes.sql src/main/java/com/yuewei/plm/module/code/entity/CodeItem.java src/main/java/com/yuewei/plm/module/code/repository/CodeItemRepository.java src/test/java/com/yuewei/plm/module/code/CodeCenterMigrationContractTest.java
git commit -m "feat: add code center data model"
```

### Task 2: 编码 CRUD、查询与启停接口

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/code/dto/CodeItemSaveDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/code/dto/CodeItemQueryDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/code/vo/CodeItemVO.java`
- Create: `src/main/java/com/yuewei/plm/module/code/service/CodeItemService.java`
- Create: `src/main/java/com/yuewei/plm/module/code/controller/CodeItemController.java`
- Create: `src/test/java/com/yuewei/plm/module/code/service/CodeItemServiceTest.java`

**Interfaces:**
- Consumes: `CodeItemRepository`。
- Produces: `PageVO<CodeItemVO> page(CodeItemQueryDTO query)`。
- Produces: `CodeItemVO create(CodeItemSaveDTO dto)`、`update(Long id, CodeItemSaveDTO dto)`、`changeStatus(Long id, String status)`。

- [ ] **Step 1: 编写失败的服务测试**

```java
@Test
void createPreservesLeadingZeroAndRejectsDuplicateWithinType() {
    when(repository.selectCount(any())).thenReturn(0L);
    CodeItemVO created = service.create(new CodeItemSaveDTO("color", "02", "Negro", 2));
    assertThat(created.getCodeValue()).isEqualTo("02");
    verify(repository).insert(argThat(item -> "02".equals(item.getCodeValue())));
}

@Test
void disabledItemRemainsQueryable() {
    CodeItem item = codeItem(1L, "color", "02", "Negro", "enabled");
    when(repository.selectById(1L)).thenReturn(item);
    assertThat(service.changeStatus(1L, "disabled").getStatus()).isEqualTo("disabled");
    verify(repository).updateById(item);
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `mvn -Dtest=CodeItemServiceTest test`

Expected: FAIL，因为服务和 DTO 尚不存在。

- [ ] **Step 3: 实现 DTO 校验和服务**

```java
public record CodeItemSaveDTO(
    @NotBlank String codeType,
    @NotBlank String codeValue,
    @NotBlank String codeName,
    @NotNull @Min(0) Integer sortOrder
) {}
```

服务创建前使用 `codeType.trim().toLowerCase()` 与原样 `codeValue.trim()` 查询重复；不得将编码解析为数字。编辑接口只允许修改 `codeName` 和 `sortOrder`，编码类型和值创建后不可修改。

- [ ] **Step 4: 实现 REST 接口**

```java
@GetMapping("/api/v1/code-items")
public ResponseVO<PageVO<CodeItemVO>> page(@Valid CodeItemQueryDTO query, HttpServletRequest request)

@PostMapping("/api/v1/code-items")
public ResponseVO<CodeItemVO> create(@Valid @RequestBody CodeItemSaveDTO dto, HttpServletRequest request)

@PutMapping("/api/v1/code-items/{id}")
public ResponseVO<CodeItemVO> update(@PathVariable Long id, @Valid @RequestBody CodeItemSaveDTO dto, HttpServletRequest request)

@PostMapping("/api/v1/code-items/{id}/enable")
public ResponseVO<CodeItemVO> enable(@PathVariable Long id, HttpServletRequest request)

@PostMapping("/api/v1/code-items/{id}/disable")
public ResponseVO<CodeItemVO> disable(@PathVariable Long id, HttpServletRequest request)
```

- [ ] **Step 5: 运行测试并确认 GREEN**

Run: `mvn -Dtest=CodeItemServiceTest test`

Expected: PASS，覆盖前导零、类型内唯一、编辑和启停。

- [ ] **Step 6: 提交任务**

```powershell
git add -- src/main/java/com/yuewei/plm/module/code src/test/java/com/yuewei/plm/module/code/service/CodeItemServiceTest.java
git commit -m "feat: add code center CRUD APIs"
```

### Task 3: 颜色 XLSX 预览与幂等提交

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/code/service/CodeItemImportService.java`
- Create: `src/main/java/com/yuewei/plm/module/code/vo/CodeImportPreviewVO.java`
- Create: `src/main/java/com/yuewei/plm/module/code/vo/CodeImportRowVO.java`
- Create: `src/main/java/com/yuewei/plm/module/code/vo/CodeImportErrorVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/code/controller/CodeItemController.java`
- Test: `src/test/java/com/yuewei/plm/module/code/service/CodeItemImportServiceTest.java`

**Interfaces:**
- Consumes: `CodeItemRepository`。
- Produces: `CodeImportPreviewVO preview(String fileName, byte[] content)`。
- Produces: `CodeImportPreviewVO commit(String token)`。

- [ ] **Step 1: 编写真实样本解析失败测试**

```java
@Test
void previewsColorWorkbookFromBusinessHeaderRow() throws Exception {
    byte[] content = Files.readAllBytes(Path.of("D:/Yuewei/Downloads/Códigos de color-2026-07-19.xlsx"));
    CodeImportPreviewVO result = service.preview("Códigos de color-2026-07-19.xlsx", content);
    assertThat(result.getRows()).hasSize(20);
    assertThat(result.getRows().get(0).getCodeValue()).isEqualTo("01");
    assertThat(result.getRows().get(0).getCodeName()).isEqualTo("Morado");
}
```

测试不得把外部文件设为构建必需资源；同时在测试内用 POI 生成等价最小工作簿，外部样本仅作为本地验收测试。

- [ ] **Step 2: 运行测试并确认 RED**

Run: `mvn -Dtest=CodeItemImportServiceTest test`

Expected: FAIL，因为导入服务尚不存在。

- [ ] **Step 3: 实现表头定位和解析**

```java
private static final List<String> HEADERS = List.of("Código color", "Nombre color", "Estado", "Actualizado");

private String mapStatus(String source) {
    return switch (source.trim().toLowerCase(Locale.ROOT)) {
        case "enabled" -> "enabled";
        case "disabled" -> "disabled";
        default -> throw validation("未知颜色状态：" + source);
    };
}
```

固定读取 `Códigos de color` 工作表，第 6 行校验表头，第 7 行开始解析。使用 `DataFormatter` 读取编码单元格，确保 `01` 不变成 `1`。

- [ ] **Step 4: 实现预览分类和一次性令牌**

预览行的 `action` 只能为 `create/update/unchanged`。使用线程安全内存存储令牌、预览结果和两小时过期时间；commit 用原子 `remove(token)` 取得数据，重复提交返回“导入令牌不存在、已过期或已提交”。

- [ ] **Step 5: 增加导入接口**

```java
@PostMapping(value = "/api/v1/code-items/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseVO<CodeImportPreviewVO> preview(@RequestParam("file") MultipartFile file, HttpServletRequest request)

@PostMapping("/api/v1/code-items/import/{token}/commit")
public ResponseVO<CodeImportPreviewVO> commit(@PathVariable String token, HttpServletRequest request)
```

- [ ] **Step 6: 运行测试并确认 GREEN**

Run: `mvn -Dtest=CodeItemImportServiceTest test`

Expected: PASS，覆盖 20 行解析、重复编码、未知状态、新增、更新、无变化和重复提交。

- [ ] **Step 7: 提交任务**

```powershell
git add -- src/main/java/com/yuewei/plm/module/code src/test/java/com/yuewei/plm/module/code/service/CodeItemImportServiceTest.java
git commit -m "feat: import color codes from xlsx"
```

### Task 4: BOM 路线、历史导入和投产颜色接入编码中心

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomRouteColor.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductProductionColorDecision.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/dto/BomRouteSaveDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/dto/ProductionColorConfirmDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/ProductBomWorkflowService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/ProductionConfirmationService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/HistoricalBomImportService.java`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/ProductBomWorkflowServiceTest.java`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/ProductionConfirmationServiceTest.java`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/impl/HistoricalBomImportServiceTest.java`

**Interfaces:**
- Consumes: `CodeItemRepository`。
- Produces: BOM 路线颜色与投产决策保存 `codeItemId/colorCode/colorName` 快照。
- Produces: 历史 BOM “适用颜色”字段按 `color` 类型编码解析。

- [ ] **Step 1: 编写颜色编码门禁失败测试**

```java
@Test
void historicalImportRejectsDisabledColorCode() {
    when(codeItemRepository.selectOne(any())).thenReturn(codeItem("color", "02", "Negro", "disabled"));
    BomImportPreviewVO result = service.preview("history.xlsx", workbookWithColor("02"));
    assertThat(result.getErrors()).anyMatch(error -> error.getReason().contains("颜色编码已停用"));
}

@Test
void productionRejectsColorNotBackedByEnabledCodeItem() {
    when(codeItemRepository.selectById(2L)).thenReturn(codeItem("color", "02", "Negro", "disabled"));
    assertThatThrownBy(() -> service.confirmColors(20L, dto(2L, "02")))
        .hasMessageContaining("颜色编码已停用");
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `mvn -Dtest=ProductBomWorkflowServiceTest,ProductionConfirmationServiceTest,HistoricalBomImportServiceTest test`

Expected: FAIL，因为 DTO、实体和服务尚未使用 `codeItemId/colorCode`。

- [ ] **Step 3: 扩展 DTO 与实体**

```java
public static class ColorSelection {
    @NotNull private Long codeItemId;
    @NotBlank private String colorCode;
    @NotBlank private String colorName;
    @NotNull private Long productBomId;
    @NotNull private Long productBomRouteId;
}
```

`BomRouteSaveDTO` 的颜色由 `List<String>` 改成明确对象 `List<BomRouteColorDTO>`，包含 `codeItemId/codeValue/codeName`。

- [ ] **Step 4: 后端保存前重新校验编码**

```java
private CodeItem requireEnabledColor(Long codeItemId, String codeValue) {
    CodeItem item = codeItemRepository.selectById(codeItemId);
    if (item == null || !"color".equals(item.getCodeType()) || !codeValue.equals(item.getCodeValue())) {
        throw validation("颜色编码不存在或与选择值不一致");
    }
    if (!"enabled".equals(item.getStatus())) throw validation("颜色编码已停用");
    return item;
}
```

路线保存、历史导入和投产确认分别调用该校验；历史导入按 `code_type=color + code_value` 查询，不接受颜色名称自由文本。

- [ ] **Step 5: 保持历史展示兼容**

读取旧记录时 `codeItemId` 可为空，继续返回旧 `colorName`；新建和修改记录必须有启用编码。不得自动猜测旧名称对应哪个编码。

- [ ] **Step 6: 运行测试并确认 GREEN**

Run: `mvn -Dtest=ProductBomWorkflowServiceTest,ProductionConfirmationServiceTest,HistoricalBomImportServiceTest test`

Expected: PASS，覆盖启用、停用、不存在、编码值不一致和历史空 ID 兼容。

- [ ] **Step 7: 提交任务**

```powershell
git add -- src/main/java/com/yuewei/plm/module/bom src/test/java/com/yuewei/plm/module/bom
git commit -m "feat: enforce color codes in BOM production flow"
```

### Task 5: 编码中心导航、列表和维护弹窗

**Files:**
- Create: `../plm-web/src/api/modules/code.ts`
- Create: `../plm-web/src/views/code/CodeCenterView.vue`
- Create: `../plm-web/src/views/code/components/CodeItemDialog.vue`
- Create: `../plm-web/src/views/code/__tests__/CodeCenterView.spec.ts`
- Modify: `../plm-web/src/router/index.ts`
- Modify: `../plm-web/src/layout/Sidebar.vue`

**Interfaces:**
- Consumes: Task 2 CRUD API。
- Produces: 路由 `/code-center` 和“基础资料 → 编码中心”导航入口。

- [ ] **Step 1: 编写失败的页面测试**

```ts
it('loads color codes and supports disable', async () => {
  vi.mocked(getCodeItems).mockResolvedValue({ content: [{ codeItemId: 2, codeType: 'color', codeValue: '02', codeName: 'Negro', status: 'enabled', sortOrder: 2 }], totalElements: 1 })
  const wrapper = mount(CodeCenterView, globalOptions)
  await flushPromises()
  expect(wrapper.text()).toContain('02')
  expect(wrapper.text()).toContain('Negro')
  await wrapper.get('[data-test="disable-code-2"]').trigger('click')
  expect(disableCodeItem).toHaveBeenCalledWith(2)
})
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `npm run test:run -- src/views/code/__tests__/CodeCenterView.spec.ts`

Expected: FAIL，因为页面和 API 不存在。

- [ ] **Step 3: 实现 API 类型与请求**

```ts
export interface CodeItem {
  codeItemId: number
  codeType: string
  codeValue: string
  codeName: string
  status: 'enabled' | 'disabled'
  sortOrder: number
  updatedAt?: string
}

export const getCodeItems = (params: CodeItemQuery) => request.get('/code-items', { params }).then(unwrapPage<CodeItem>)
```

- [ ] **Step 4: 实现列表与维护弹窗**

页面默认 `codeType=color`，提供关键字、状态筛选，列表展示设计文档规定字段。新增弹窗允许填写编码值、名称和排序；编辑时编码类型和值只读。启停操作使用确认框并刷新列表。

- [ ] **Step 5: 注册路由与导航**

```ts
{
  path: 'code-center',
  name: 'CodeCenter',
  component: () => import('@/views/code/CodeCenterView.vue'),
  meta: { title: '编码中心', permission: 'product:view', breadcrumb: ['基础资料', '编码中心'] }
}
```

- [ ] **Step 6: 运行测试与类型检查**

Run: `npm run test:run -- src/views/code/__tests__/CodeCenterView.spec.ts`

Expected: PASS。

Run: `npm run type-check`

Expected: exit 0。

- [ ] **Step 7: 提交任务**

```powershell
git add -- ../plm-web/src/api/modules/code.ts ../plm-web/src/views/code ../plm-web/src/router/index.ts ../plm-web/src/layout/Sidebar.vue
git commit -m "feat: add code center management page"
```

### Task 6: 前端 XLSX 导入与 BOM/投产颜色选择

**Files:**
- Create: `../plm-web/src/views/code/components/CodeImportDialog.vue`
- Modify: `../plm-web/src/api/modules/code.ts`
- Modify: `../plm-web/src/views/code/CodeCenterView.vue`
- Modify: `../plm-web/src/api/modules/bom.ts`
- Modify: `../plm-web/src/views/project/components/ProjectBomPanel.vue`
- Modify: `../plm-web/src/views/project/components/ProductionConfirmationDialog.vue`
- Test: `../plm-web/src/views/code/__tests__/CodeImportDialog.spec.ts`
- Test: `../plm-web/src/views/project/__tests__/ProductionConfirmationDialog.spec.ts`

**Interfaces:**
- Consumes: Task 3 导入 API、Task 4 BOM DTO。
- Produces: XLSX 预览/提交 UI 与启用颜色编码候选。

- [ ] **Step 1: 编写失败的导入和颜色候选测试**

```ts
it('shows create update unchanged and error counts before commit', async () => {
  vi.mocked(previewCodeImport).mockResolvedValue({ token: 't1', createCount: 20, updateCount: 0, unchangedCount: 0, errorCount: 0, rows: [] })
  const wrapper = mount(CodeImportDialog, dialogOptions)
  await wrapper.vm.preview(file)
  expect(wrapper.text()).toContain('新增 20')
})

it('submits codeItemId and colorCode for selected production color', async () => {
  vi.mocked(getEnabledColorCodes).mockResolvedValue([{ codeItemId: 2, codeValue: '02', codeName: 'Negro' }])
  const wrapper = mount(ProductionConfirmationDialog, dialogOptions)
  await flushPromises()
  await wrapper.get('[data-test="confirm-colors"]').trigger('click')
  expect(confirmProductionColors).toHaveBeenCalledWith(expect.any(Number), expect.objectContaining({ colors: [expect.objectContaining({ codeItemId: 2, colorCode: '02' })] }))
})
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `npm run test:run -- src/views/code/__tests__/CodeImportDialog.spec.ts src/views/project/__tests__/ProductionConfirmationDialog.spec.ts`

Expected: FAIL，因为导入弹窗和颜色编码字段尚未实现。

- [ ] **Step 3: 实现导入弹窗**

弹窗接受 `.xlsx`，展示文件名、预览统计、逐行 action 和错误；只有 `errorCount=0` 且存在 create/update 行时允许确认。提交成功关闭弹窗并触发列表刷新。

- [ ] **Step 4: 将 BOM 和投产颜色改为编码选择**

使用 `GET /code-items?codeType=color&status=enabled` 获取候选，选项标签显示 `02 - Negro`，值使用 `codeItemId`。提交时同时携带 `codeItemId/codeValue/codeName`，不得只提交名称。

- [ ] **Step 5: 运行聚焦测试与类型检查**

Run: `npm run test:run -- src/views/code/__tests__/CodeImportDialog.spec.ts src/views/project/__tests__/ProductionConfirmationDialog.spec.ts`

Expected: PASS。

Run: `npm run type-check`

Expected: exit 0。

- [ ] **Step 6: 提交任务**

```powershell
git add -- ../plm-web/src/api/modules/code.ts ../plm-web/src/api/modules/bom.ts ../plm-web/src/views/code ../plm-web/src/views/project/components/ProjectBomPanel.vue ../plm-web/src/views/project/components/ProductionConfirmationDialog.vue ../plm-web/src/views/project/__tests__/ProductionConfirmationDialog.spec.ts
git commit -m "feat: use color codes in BOM production UI"
```

### Task 7: 全量验证、真实前端验收与文档沉淀

**Files:**
- Modify: `D:/Yuewei/资料/PLM/docs/modules/04-BOM管理.md`
- Modify: `D:/Yuewei/资料/PLM/docs/14-API接口定义文档.md`
- Create: `D:/Yuewei/资料/PLM/docs/整体测试/2026-07-19-PLM编码中心与颜色编码接入代码实现沉淀.md`
- Modify: `docs/superpowers/specs/2026-07-19-code-center-color-code-design.md`

**Interfaces:**
- Consumes: Tasks 1-6 的完整实现。
- Produces: 可复现的运行、前端验收和回滚证据。

- [ ] **Step 1: 执行完整后端测试**

Run: `mvn test`

Expected: BUILD SUCCESS，0 failures，0 errors。

- [ ] **Step 2: 执行完整前端验证**

Run: `npm run type-check`

Expected: exit 0。

Run: `npm run test:run`

Expected: 所有测试通过。

Run: `npm run build`

Expected: 构建成功；既有 chunk size 警告可以记录但不能出现编译错误。

- [ ] **Step 3: 启动当前代码并验证 Flyway**

Run: `mvn spring-boot:run`

Expected: Flyway 应用 `V20260719_1002`，应用启动成功且无 checksum 错误。

Run: `npm run dev`

Expected: Vite 页面可从 `http://127.0.0.1:5173` 打开。

- [ ] **Step 4: 使用浏览器完成前端直接验收**

按设计文档第 9.2 节执行 10 项步骤，至少保存以下截图：

- 编码中心列表。
- 新增/编辑弹窗。
- XLSX 20 条预览。
- 导入后的 `01 / Morado`、`02 / Negro`。
- 停用颜色不进入 BOM 或投产候选。
- 历史 BOM 使用不存在颜色编码时的明确错误。

Expected: 页面无重叠，浏览器控制台 0 个阻塞性错误，重复导入不增加记录数。

- [ ] **Step 5: 更新主文档与实施沉淀**

沉淀文档必须列出修改文档、数据库迁移、后端代码、前端代码、API、每一步前端测试方式、合格标准、实际结果、未验证项和 ERP/MES 未实施边界。

- [ ] **Step 6: 执行提交前检查**

Run: `git diff --check`

Expected: 无 whitespace error。

Run: `rg -n "TODO|TBD|FIXME" src/main/java/com/yuewei/plm/module/code ../plm-web/src/views/code docs/superpowers/specs/2026-07-19-code-center-color-code-design.md`

Expected: 无未完成占位符。

- [ ] **Step 7: 提交最终文档与验收调整**

```powershell
git add -- docs/superpowers/specs/2026-07-19-code-center-color-code-design.md
git commit -m "docs: record code center verification"
```

外部 `D:/Yuewei/资料/PLM/docs` 文档不在 Git 仓库中，使用文件哈希记录写入结果。
