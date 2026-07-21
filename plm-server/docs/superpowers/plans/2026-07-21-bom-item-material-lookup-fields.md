# BOM Item Material Lookup Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the 2026-07-21 workbench BOM upload fields: supplier selection snapshot, unit price, line cost, material-code lookup, manual unmatched material support, and matching frontend workflow.

**Architecture:** Keep BOM and BOM lines as Product-owned extension data, not root objects. Extend `plm_product_bom_item` snapshots and reuse `Inventory` through a lookup endpoint. Workbench manual edit and XLSX import share the same DTO fields and cost calculation.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis Plus, PostgreSQL/Flyway, Apache POI, Vue 3, TypeScript, Element Plus, Vitest.

## Global Constraints

- Do not create BOM, BOMLine, SKU, Supplier, Material, Routing, PhoneModel, Quote, ECR, ECN, or IntegrationJob as new PLM root objects.
- Workbench BOM upload targets candidate/formal BOM under Product; BOM management center remains formal read-only ledger and historical import entry.
- Cost permission is not implemented in this round: unit price, line cost, and totals are returned and displayed normally.
- `unit_cost_snapshot` remains the unit price snapshot; add `line_cost_snapshot` for line cost display/audit.
- Material code lookup reads `plm_inventory.inventory_code`; unmatched workbench rows are allowed with `material_source=manual` and `unmatched_flag=1`.
- Historical BOM import keeps strict existing-product matching; workbench import allows unmatched materials but blocks structural errors.
- Use TDD: add/update tests before production changes.

---

### Task 1: Database and backend field contract

**Files:**
- Create: `src/main/resources/db/migration/V20260721_1000__bom_item_supplier_price_snapshot.sql`
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomItem.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/dto/ProductBomItemDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/vo/ProductBomItemVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/vo/BomImportRowVO.java`
- Modify test: `src/test/java/com/yuewei/plm/module/bom/BomWorkbenchMigrationContractTest.java`
- Modify test: `src/test/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImplTest.java`

**Interfaces:**
- Produces DTO/VO fields: `supplierCode`, `supplierName`, `unitCost`, `lineCost`, `currencyCode`, `materialSource`, `unmatchedFlag`, `lookupMessage`.
- Produces entity fields: `supplierCodeSnapshot`, `supplierNameSnapshot`, `lineCostSnapshot`, `materialSource`, `unmatchedFlag`.

- [ ] Add migration contract assertions for `supplier_code_snapshot`, `supplier_name_snapshot`, `line_cost_snapshot`, `material_source`, `unmatched_flag`, and index `idx_plm_product_bom_item_material_source`.
- [ ] Add `ProductBomServiceImplTest` expectation that saving an item persists supplier snapshot, unit price snapshot, computed line cost, manual source, and unmatched flag.
- [ ] Run the new/updated backend tests and confirm the new expectations fail before production changes.
- [ ] Add migration script and Java fields.
- [ ] Map `ProductBomItemVO.from` to return all new fields.
- [ ] Run the same backend tests and confirm they pass.

### Task 2: Material lookup endpoint

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/BomMaterialLookup.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/JdbcBomMaterialLookup.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/vo/BomMaterialLookupVO.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/controller/BomMaterialLookupController.java`
- Modify test: `src/test/java/com/yuewei/plm/module/bom/controller/ProductBomControllerTest.java`
- Create test: `src/test/java/com/yuewei/plm/module/bom/service/impl/JdbcBomMaterialLookupTest.java`

**Interfaces:**
- `GET /api/v1/inventories/material-lookup?inventoryCode=xxx`
- Response data fields: `matched`, `inventoryId`, `inventoryCode`, `inventoryName`, `specification`, `unit`, `supplierName`, `unitCost`, `currencyCode`, `message`.

- [ ] Add controller contract test for `/inventories/material-lookup`.
- [ ] Add JDBC lookup test using mocked `JdbcTemplate` result-set mapping for inventory name, specification, unit, supplier, cost, and currency.
- [ ] Run target tests and confirm they fail before production changes.
- [ ] Extend `BomMaterialLookup.Material`.
- [ ] Update JDBC SQL and row mapper.
- [ ] Add VO and controller.
- [ ] Run target tests and confirm they pass.

### Task 3: Workbench save, import, and cost behavior

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/ProductBomWorkflowService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/HistoricalBomImportService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/BomCostCalculator.java` only if needed for consistency.
- Modify test: `src/test/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImplTest.java`
- Modify test: `src/test/java/com/yuewei/plm/module/bom/service/ProductBomWorkflowServiceTest.java`
- Modify test: `src/test/java/com/yuewei/plm/module/bom/service/BomCostCalculatorTest.java` only if calculation semantics change.

**Interfaces:**
- `ProductBomServiceImpl.applyItem(ProductBomItemDTO, ProductBomItem)` calculates `lineCost = quantity * unitCost`.
- Workbench import template headers include supplier, unit price, and line cost.
- Workbench import preview does not block unmatched material code; it marks manual/unmatched and returns preview row.

- [ ] Add import test for new headers and unmatched material becoming a ready manual row.
- [ ] Add workflow test that `saveRoutes` persists supplier snapshot, line cost snapshot, and manual flags.
- [ ] Run target tests and confirm new expectations fail before production changes.
- [ ] Update item save and route save mapping to calculate line cost and default material source.
- [ ] Update workbench import headers/parser/toItem mapping.
- [ ] Update historical import headers/parser to carry new fields while preserving strict Product matching.
- [ ] Run target tests and confirm they pass.

### Task 4: Frontend API, type, and workbench UI

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\types\bom.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\bom.ts`
- Create: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\inventory.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\BomRouteEditor.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\BomImportDialog.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\ProjectBomPanel.vue`
- Modify test: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\BomRouteEditor.spec.ts`
- Modify test: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\ProjectBomPanel.spec.ts`

**Interfaces:**
- `lookupMaterialByCode(inventoryCode: string): Promise<MaterialLookupResult>`.
- BOM item edit table columns: NO, code, name, specification, quantity, unit, supplier select, unit price, line cost, loss rate, remark, operation.
- Line cost recalculates when quantity or unit price changes.

- [ ] Update frontend tests to expect material lookup auto-fill, unmatched warning/manual flag, supplier select, line cost, and risk metrics.
- [ ] Run target Vitest files and confirm new expectations fail before production changes.
- [ ] Add inventory API and update BOM API/types.
- [ ] Update `BomRouteEditor.vue` with lookup, supplier select options, real-time line cost, and validation.
- [ ] Update import dialog preview to show supplier/unit price/line cost/manual counts.
- [ ] Update project panel to show manual/supplier/cost risk counts.
- [ ] Run target Vitest files and confirm they pass.

### Task 5: Documentation, verification, and Git handoff

**Files:**
- Create: `docs/backend-notes/2026-07-21-PLM工作台BOM上传字段与物料编码自动带出代码实现沉淀.md`
- Copy final doc to: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-21-PLM工作台BOM上传字段与物料编码自动带出代码实现沉淀.md`
- Optionally update module docs if the implementation introduces durable API/field changes.

**Interfaces:**
- Implementation sediment doc lists changed docs/code and frontend direct test steps with pass criteria.

- [ ] Run backend target tests for BOM field/import/lookup changes.
- [ ] Run frontend target tests for BOM workbench components.
- [ ] Run frontend type-check if dependencies are available.
- [ ] Write implementation sediment doc with changed files, changed code, frontend direct test steps, and pass criteria.
- [ ] Verify the sediment doc exists in the target directory and contains required sections.
- [ ] Stage only this round's changed files, commit to Git for rollback, and report the commit hash.

## Self-review

- Spec coverage: fields, lookup, manual unmatched rows, import template, frontend display, risk prompts, and no cost permission are covered by Tasks 1-4.
- Scope: this plan does not create new PLM root objects and does not implement ERP/MES integration.
- Known conflict: general permission docs still say cost fields are restricted; the task-specific 2026-07-21 scheme says cost permission is not set this round, so implementation follows the task scheme and records the difference in the sediment doc.
