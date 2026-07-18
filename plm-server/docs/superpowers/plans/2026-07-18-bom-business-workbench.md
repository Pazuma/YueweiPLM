# BOM Business Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete BOM workbench workflow, XLSX import, route/color BOM costing, inheritance, release gates, and read-only BOM/process ledgers defined by the approved design.

**Architecture:** Extend the existing Product BOM tables with focused Product-owned extension tables for route links, colors, cost snapshots, and import batches. Keep all writes behind project/workbench APIs, expose separate read models for the BOM and process ledgers, and resolve SKU-like Product children through their model/color fields without creating a SKU root object.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, PostgreSQL/Flyway, Apache POI, Vue 3, TypeScript, Element Plus, Vitest, Maven.

## Global Constraints

- BOM remains Product versioned extension data; Process owns routes; Inventory supplies materials.
- Do not create BOM, SKU, Routing, PhoneModel, or other new root objects.
- Do not implement ERP, MES, WMS, CRM, OA, or DingTalk integration.
- API paths use `/api/v1`, `ResponseVO`, request IDs, structured errors, backend validation, and operation logs.
- Formal BOM state is `draft -> reviewing -> released -> archived`; freeze is recorded separately.
- All writes occur from project workbench flows; BOM and process management centers are read-only ledgers.
- Existing uncommitted work in both repositories must be preserved and merged incrementally.

---

### Task 1: Database Contract and Migration Tests

**Files:**
- Create: `src/main/resources/db/migration/V20260718_1100__bom_workbench_route_cost_import.sql`
- Create: `src/test/java/com/yuewei/plm/module/bom/BomWorkbenchMigrationContractTest.java`
- Modify: `pom.xml`

**Interfaces:**
- Produces tables `plm_product_bom_route`, `plm_product_bom_route_color`, `plm_product_bom_cost_snapshot`, `plm_product_bom_import_batch`, and BOM/item extension columns consumed by Task 2.

- [ ] **Step 1: Write the failing migration contract test**

Assert the migration text contains audit columns, `product_bom_id`, `process_id`, `color_name`, route-color active uniqueness, cost components, `currency_code`, `import_token`, and indexes.

- [ ] **Step 2: Run the test and verify failure**

Run: `mvn -Dtest=BomWorkbenchMigrationContractTest test`
Expected: FAIL because `V20260718_1100__bom_workbench_route_cost_import.sql` does not exist.

- [ ] **Step 3: Add Apache POI and migration**

Add `org.apache.poi:poi-ooxml:5.4.1`. Extend `plm_product_bom` with `bom_scope`, `source_type`, inheritance fields, test cost, calculation/release audit, and `frozen_flag`. Extend items with `product_bom_route_id`, `unit_cost_snapshot`, and `currency_code`. Create the four Product extension tables with `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_flag` and partial unique indexes.

- [ ] **Step 4: Run migration contract and compile**

Run: `mvn -Dtest=BomWorkbenchMigrationContractTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- pom.xml src/main/resources/db/migration/V20260718_1100__bom_workbench_route_cost_import.sql src/test/java/com/yuewei/plm/module/bom/BomWorkbenchMigrationContractTest.java
git commit -m "feat: add BOM workbench persistence contract"
```

### Task 2: BOM Domain Model and Read Contracts

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBom.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomItem.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomRoute.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomRouteColor.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomCostSnapshot.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomImportBatch.java`
- Create matching repositories under `src/main/java/com/yuewei/plm/module/bom/repository/`
- Create DTO/VO classes under `module/bom/dto` and `module/bom/vo`
- Create: `src/test/java/com/yuewei/plm/module/bom/vo/BomWorkbenchVOTest.java`

**Interfaces:**
- Produces `ProductBomWorkbenchVO`, `ProductBomRouteVO`, `ProductBomCostSnapshotVO`, `BomLedgerRowVO`, `BomSkuRowVO`, and `BomSummaryVO`.

- [ ] **Step 1: Write failing mapping tests**

Cover route colors, route-specific items/cost, formal/test separation, and ledger counts.

- [ ] **Step 2: Verify failure**

Run: `mvn -Dtest=BomWorkbenchVOTest test`
Expected: FAIL because the new entities and VOs are missing.

- [ ] **Step 3: Implement focused entities, repositories, DTOs, and VOs**

Use snake_case-compatible Java properties such as `productBomRouteId`, `sourceProductBomId`, `testTotalCost`, `materialCost`, and `currencyCode`. DTO validation requires positive quantity, loss rate from 0 through 1, nonblank route/color codes, and at least one color per active route.

- [ ] **Step 4: Verify mapping tests**

Run: `mvn -Dtest=BomWorkbenchVOTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/yuewei/plm/module/bom src/test/java/com/yuewei/plm/module/bom/vo/BomWorkbenchVOTest.java
git commit -m "feat: model route based BOM workbench data"
```

### Task 3: Route, Color, Cost, Version, and Inheritance Rules

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/ProductBomService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImpl.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/service/BomCostCalculator.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/service/BomInheritanceService.java`
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Modify: `src/test/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImplTest.java`
- Create: `src/test/java/com/yuewei/plm/module/bom/service/BomCostCalculatorTest.java`
- Create: `src/test/java/com/yuewei/plm/module/bom/service/BomInheritanceServiceTest.java`

**Interfaces:**
- Produces service methods `saveTestBom`, `confirmTestBom`, `saveRoutes`, `recalculateCosts`, `submitReview`, `freeze`, `publish`, `copyVersion`, and `inheritFromProductLine`.

- [ ] **Step 1: Add failing service tests**

Test one route with multiple colors, duplicate-color rejection across active routes, per-route item isolation, cost formula, released snapshot immutability, copy-to-draft, selected-color inheritance, and source immutability.

- [ ] **Step 2: Verify failures**

Run: `mvn -Dtest=ProductBomServiceImplTest,BomCostCalculatorTest,BomInheritanceServiceTest test`
Expected: FAIL on missing methods/rules.

- [ ] **Step 3: Implement minimal transactional rules**

Calculate `unitCost * quantity * (1 + lossRate)`, persist separate material/loss totals, reject publish unless frozen and every active route has colors/items/current snapshot, and copy rows rather than sharing mutable IDs.

- [ ] **Step 4: Verify service tests**

Run: `mvn -Dtest=ProductBomServiceImplTest,BomCostCalculatorTest,BomInheritanceServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/yuewei/plm/module/bom src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java src/test/java/com/yuewei/plm/module/bom
git commit -m "feat: implement BOM route cost and inheritance rules"
```

### Task 4: XLSX Preview, Atomic Commit, and Error Report

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/bom/service/BomImportService.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImpl.java`
- Create import DTO/VO classes under `module/bom/dto` and `module/bom/vo`
- Create: `src/test/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImplTest.java`

**Interfaces:**
- Produces `preview(productId, file, testBom)`, `commit(productId, importToken)`, `downloadTemplate()`, and `downloadErrors(importToken)`.

- [ ] **Step 1: Write failing import tests**

Create workbooks in memory and assert valid preview, invalid header, unknown inventory, duplicate line, duplicate color ownership, no partial writes, one-time token commit, and an XLSX error report containing row/field/value/reason.

- [ ] **Step 2: Verify failures**

Run: `mvn -Dtest=BomImportServiceImplTest test`
Expected: FAIL because import service is absent.

- [ ] **Step 3: Implement parser and transactional commit**

Use Apache POI structured workbook APIs. Store preview JSON and errors in the import batch; reject expired/failed/already-committed tokens; write data only inside the commit transaction after rechecking BOM editability.

- [ ] **Step 4: Verify import tests**

Run: `mvn -Dtest=BomImportServiceImplTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/yuewei/plm/module/bom src/test/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImplTest.java
git commit -m "feat: add atomic XLSX BOM import"
```

### Task 5: REST APIs, Ledger Queries, and Workbench Release Gates

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/controller/ProductBomController.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/controller/BomLedgerController.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/service/BomLedgerService.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/service/impl/BomLedgerServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Create: `src/test/java/com/yuewei/plm/module/bom/controller/ProductBomControllerTest.java`
- Create: `src/test/java/com/yuewei/plm/module/bom/service/impl/BomLedgerServiceImplTest.java`
- Modify: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`

**Interfaces:**
- Produces workbench endpoints under `/api/v1/projects/{productId}/boms`, import endpoints, `/api/v1/bom-ledger`, `/api/v1/boms/{bomId}/skus`, product BOM summary, and route SKU queries.

- [ ] **Step 1: Write failing controller/query/gate tests**

Assert ResponseVO contracts, formal-only ledger, SKU color resolution, conflict error, product-line production decision gate, and model-variant freeze/release gates.

- [ ] **Step 2: Verify failures**

Run: `mvn -Dtest=ProductBomControllerTest,BomLedgerServiceImplTest,TimelineActionServiceImplTest test`
Expected: FAIL on missing endpoints and gates.

- [ ] **Step 3: Implement controllers, queries, and gates**

Keep action endpoints explicit (`/confirm`, `/submit-review`, `/freeze`, `/publish`, `/copy-version`). Return empty success for no SKU route, conflict error for multiple matches, and actual errors for permission/import/calculation failures.

- [ ] **Step 4: Verify backend BOM suite**

Run: `mvn -Dtest='*Bom*,TimelineActionServiceImplTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- src/main/java/com/yuewei/plm/module/bom src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java src/test/java/com/yuewei/plm/module/bom src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java
git commit -m "feat: expose BOM workbench and ledger APIs"
```

### Task 6: Frontend Contracts and API Tests

**Files:**
- Modify: `../plm-web/src/types/bom.ts`
- Modify: `../plm-web/src/types/process.ts`
- Modify: `../plm-web/src/api/modules/bom.ts`
- Modify: `../plm-web/src/api/modules/process.ts`
- Modify: `../plm-web/src/api/modules/__tests__/m4-api.spec.ts`

**Interfaces:**
- Produces typed API functions consumed by Tasks 7-9; removes `notConnected` from BOM/process center snapshot paths.

- [ ] **Step 1: Add failing API tests**

Assert exact URLs and payloads for workbench summary, route/color save, import preview/commit/error download, costs, lifecycle actions, formal ledger, BOM SKU page, and process route SKU page.

- [ ] **Step 2: Verify failures**

Run from `../plm-web`: `npm run test:run -- src/api/modules/__tests__/m4-api.spec.ts`
Expected: FAIL on missing functions or `notConnected`.

- [ ] **Step 3: Implement typed API contracts**

Use `request`/`unwrapResponse`; use `responseType: 'blob'` for templates/error reports; model no-result separately from thrown request errors.

- [ ] **Step 4: Verify API tests and type check**

Run: `npm run test:run -- src/api/modules/__tests__/m4-api.spec.ts` and `npm run type-check`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- ../plm-web/src/types/bom.ts ../plm-web/src/types/process.ts ../plm-web/src/api/modules/bom.ts ../plm-web/src/api/modules/process.ts ../plm-web/src/api/modules/__tests__/m4-api.spec.ts
git commit -m "feat: connect BOM workbench frontend APIs"
```

### Task 7: Workbench BOM Editor and Import Wizard

**Files:**
- Modify: `../plm-web/src/views/project/components/ProjectBomPanel.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
- Create: `../plm-web/src/views/project/components/BomImportDialog.vue`
- Create: `../plm-web/src/views/project/components/BomRouteEditor.vue`
- Modify: `../plm-web/src/views/project/__tests__/project-m4-panels.spec.ts`

**Interfaces:**
- Consumes Task 6 APIs; produces the only BOM write UI.

- [ ] **Step 1: Write failing component tests**

Cover test/formal segmented mode, manual row edits, route colors, duplicate-color UI block, import preview/error download/commit, cost refresh, freeze/publish/copy actions, inherited selected colors, and disabled controls outside allowed nodes.

- [ ] **Step 2: Verify failures**

Run: `npm run test:run -- src/views/project/__tests__/project-m4-panels.spec.ts`
Expected: FAIL on missing workbench controls.

- [ ] **Step 3: Implement the work-focused UI**

Use compact Element Plus tables, tabs/segmented controls, icon buttons with tooltips, explicit loading/empty/error states, route color multi-select, and a step-based import dialog. Do not add marketing copy or nested cards.

- [ ] **Step 4: Verify component test and type check**

Run: `npm run test:run -- src/views/project/__tests__/project-m4-panels.spec.ts` and `npm run type-check`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- ../plm-web/src/views/project/components/ProjectBomPanel.vue ../plm-web/src/views/project/components/BomImportDialog.vue ../plm-web/src/views/project/components/BomRouteEditor.vue ../plm-web/src/views/project/ProjectCenterView.vue ../plm-web/src/views/project/__tests__/project-m4-panels.spec.ts
git commit -m "feat: add workbench BOM editing workflow"
```

### Task 8: Read-Only BOM Ledger and Product Detail

**Files:**
- Modify: `../plm-web/src/views/bom/BomCenterView.vue`
- Modify: `../plm-web/src/views/product/ProductDetail.vue`
- Create: `../plm-web/src/views/bom/__tests__/BomCenterView.spec.ts`
- Create: `../plm-web/src/views/product/__tests__/ProductBomDialog.spec.ts`

**Interfaces:**
- Consumes formal ledger, SKU paging, summary, route detail, and workbench navigation APIs.

- [ ] **Step 1: Write failing view tests**

Assert basic list only, formal-only rows, detail drawer tabs, route switching, SKU pagination modal, direct SKU route result, workbench navigation, and distinct empty/loading/permission/calculation error states.

- [ ] **Step 2: Verify failures**

Run: `npm run test:run -- src/views/bom/__tests__/BomCenterView.spec.ts src/views/product/__tests__/ProductBomDialog.spec.ts`
Expected: FAIL on old snapshot UI.

- [ ] **Step 3: Implement ledger and product detail dialog**

Match the approved dense list/modal pattern: basic columns in the list, right drawer for details, separate paged SKU modal, no edit/delete/sync actions, and test/formal segmented product view.

- [ ] **Step 4: Verify view tests**

Run the tests from Step 2 and `npm run type-check`.
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- ../plm-web/src/views/bom ../plm-web/src/views/product/ProductDetail.vue ../plm-web/src/views/product/__tests__/ProductBomDialog.spec.ts
git commit -m "feat: add read only BOM ledger views"
```

### Task 9: Process Ledger Route-to-SKU Query

**Files:**
- Modify: `../plm-web/src/views/process/ProcessCenterView.vue`
- Create: `../plm-web/src/views/process/__tests__/ProcessCenterView.spec.ts`
- Modify backend process controller/service/VO files only where the shared BOM ledger query cannot provide route SKU data.

**Interfaces:**
- Consumes route SKU paging; produces the approved route list and SKU modal.

- [ ] **Step 1: Write failing process view test**

Assert route/product basic information, clickable “查看 SKU”, modal title with route code, columns SKU code/product/phone model/color/status, and pagination.

- [ ] **Step 2: Verify failure**

Run: `npm run test:run -- src/views/process/__tests__/ProcessCenterView.spec.ts`
Expected: FAIL because the current center does not provide the approved ledger modal.

- [ ] **Step 3: Implement route list and SKU modal**

Preserve existing process master/template work already in the dirty tree. Add the formal route ledger as a focused view and keep published data read-only.

- [ ] **Step 4: Verify process view and related backend test**

Run frontend test, `npm run type-check`, and the relevant Maven process/BOM tests.
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- ../plm-web/src/views/process/ProcessCenterView.vue ../plm-web/src/views/process/__tests__/ProcessCenterView.spec.ts src/main/java/com/yuewei/plm/module/process src/test/java/com/yuewei/plm/module/process
git commit -m "feat: show route related SKUs in process ledger"
```

### Task 10: Documentation, Full Verification, and Direct Frontend Acceptance

**Files:**
- Modify: `D:/Yuewei/资料/PLM/docs/modules/04-BOM管理.md`
- Modify: `D:/Yuewei/资料/PLM/docs/14-API接口定义文档.md`
- Create: `docs/backend-notes/2026-07-18-PLM-BOM实际业务板块代码实现沉淀.md`

**Interfaces:**
- Produces the requested delivery record with every changed document/code file and frontend acceptance steps/results.

- [ ] **Step 1: Update source documentation**

Document workbench ownership, route-to-colors-to-BOM relation, SKU resolution, APIs, states, import contract, cost snapshots, and release gates. Do not document ERP/MES integration as implemented.

- [ ] **Step 2: Run automated verification**

Backend: `mvn test`.

Frontend: `npm run test:run`, `npm run type-check`, and `npm run build` from `../plm-web`.

Expected: all commands exit 0 with no failing tests or type/build errors.

- [ ] **Step 3: Start backend and frontend**

Start Spring Boot on an available backend port and Vite on an available frontend port. Record exact URLs and process IDs in the implementation note.

- [ ] **Step 4: Execute direct frontend acceptance**

Use the in-app browser/Playwright to execute all 14 acceptance cases from the design: test BOM, correct and incorrect XLSX, error report, multiple routes/colors, duplicate color block, route cost switch, inheritance/cancel color, source isolation, release gates, copy version/history, BOM ledger drawer/SKU modal, process SKU modal, product detail states, and final timeline gate.

Expected: each case reaches the exact UI/result defined in the design; capture screenshots for major workflows and record any seed data used.

- [ ] **Step 5: Write implementation note**

List referenced/modified documents, every changed code file grouped by database/backend/frontend/tests, each frontend action, expected qualified result, actual result, limitations, and URLs.

- [ ] **Step 6: Commit documentation**

```powershell
git add -- docs/backend-notes/2026-07-18-PLM-BOM实际业务板块代码实现沉淀.md
git commit -m "docs: record BOM workbench implementation and acceptance"
```
