# BOM Upload and SKU Production Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement historical BOM batch import, project BOM/process gates, production color decisions, and idempotent SKU creation from the approved design.

**Architecture:** Keep BOM and SKU as Product-owned records. Add focused confirmation tables for selected operations and production colors, extend the existing BOM import service with a separate historical-import workflow, and integrate gates into the existing timeline service. Reuse the existing BOM workbench and ledger UI with focused dialogs.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, PostgreSQL/Flyway, Apache POI, Vue 3, TypeScript, Element Plus, Vitest.

## Global Constraints

- Do not create BOM, SKU, or PhoneModel root objects; SKU remains a Product child view.
- Historical import is available only in BOM Center; new-product import remains in project workbench.
- No ERP, MES, WMS, OA, or DingTalk integration.
- Backend enforces all state, version, ownership, and idempotency rules.
- Existing user changes in the dirty worktree must be preserved.

---

### Task 1: Confirmation Persistence and Domain APIs

**Files:**
- Create: `src/main/resources/db/migration/V20260719_1000__bom_production_confirmation.sql`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProcessProductionOperationSelection.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductProductionColorDecision.java`
- Create: corresponding repositories, DTOs, VOs, service and controller classes under `module/bom`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/ProductionConfirmationServiceTest.java`

**Interfaces:**
- `GET /api/v1/projects/{projectId}/production-confirmation`
- `POST /api/v1/projects/{projectId}/production-operations/confirm`
- `POST /api/v1/projects/{projectId}/production-colors/confirm`
- `ProductionConfirmationService.requireOperationsConfirmed(projectId)`
- `ProductionConfirmationService.requireColorsConfirmed(projectId)`

- [ ] Write failing service tests for route ownership, non-empty operation selection, color validation, and idempotent confirmation.
- [ ] Run `mvn -Dtest=ProductionConfirmationServiceTest test` and verify RED failures are caused by missing production service types.
- [ ] Add migration, entities, repositories, DTO/VO, service, and controller.
- [ ] Run the focused test and verify GREEN.

### Task 2: Historical BOM Batch Import

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/BomImportService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/controller/ProductBomController.java`
- Modify: import batch/entity/VO types as required
- Test: `src/test/java/com/yuewei/plm/module/bom/service/impl/BomImportServiceImplTest.java`

**Interfaces:**
- `POST /api/v1/boms/history/import/preview`
- `POST /api/v1/boms/history/import/{importToken}/commit`
- Existing template and error-report downloads remain shared.

- [ ] Add failing tests for unique product-code matching, duplicate/no product error isolation, history import grouping, and direct `released` status after commit.
- [ ] Run focused tests and verify RED.
- [ ] Extend the xlsx template/parser with product code and BOM version while retaining project-import compatibility.
- [ ] Implement historical preview/commit as a separate path; never create or associate a project.
- [ ] Run focused tests and verify GREEN.

### Task 3: Timeline Gates and Idempotent SKU Creation

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/TimelineActionService.java` dependencies as required
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/ProductionConfirmationServiceTest.java`

**Interfaces:**
- Product-line step 9 requires determined BOM/routes.
- Product-line step 10 requires confirmed production operations.
- Product-line step 22 requires confirmed production colors.
- Model-variant final step confirms colors and creates Product child SKU rows once.

- [ ] Add failing gate tests for steps 9, 10, 22 and model-variant final release.
- [ ] Run focused tests and verify RED.
- [ ] Inject focused gate services into timeline actions and write auditable action logs.
- [ ] Implement SKU creation using Product child records and a unique idempotency rule based on model project plus color.
- [ ] Run focused tests and verify GREEN.

### Task 4: Frontend Workflows

**Files:**
- Modify: `../plm-web/src/api/modules/bom.ts`
- Modify: `../plm-web/src/types/bom.ts`
- Modify: `../plm-web/src/views/bom/BomCenterView.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
- Create: `../plm-web/src/views/bom/components/HistoricalBomImportDialog.vue`
- Create: `../plm-web/src/views/project/components/ProductionConfirmationDialog.vue`
- Test: `../plm-web/src/views/bom/__tests__/BomCenterView.spec.ts`
- Test: `../plm-web/src/views/project/__tests__/ProductionConfirmationDialog.spec.ts`

**Interfaces:**
- BOM Center historical import preview, error download, and commit.
- Step 10 operation selection dialog.
- Product-line step 22 and model-variant final-step color selection defaulting to all eligible colors.

- [ ] Write failing component tests for historical import button, error state, default-all color selection, disabled invalid colors, and operation confirmation.
- [ ] Run focused Vitest files and verify RED.
- [ ] Implement API types and dialogs using existing Element Plus patterns.
- [ ] Wire dialogs to timeline child-step actions and refresh project state after confirmation.
- [ ] Run focused Vitest files and verify GREEN.

### Task 5: Documentation and End-to-End Verification

**Files:**
- Modify: `D:/Yuewei/资料/PLM/docs/modules/04-BOM管理.md`
- Modify: `D:/Yuewei/资料/PLM/docs/modules/01-产品主数据与SKU.md`
- Modify: `D:/Yuewei/资料/PLM/docs/14-API接口定义文档.md`
- Create: `D:/Yuewei/资料/PLM/docs/整体测试/2026-07-19-PLM-BOM上传投产确认与SKU创建代码实现沉淀.md`

- [ ] Run backend focused tests, then `mvn test`.
- [ ] Run frontend focused tests, then `npm run test:unit -- --run` and `npm run build`.
- [ ] Start backend and frontend development servers.
- [ ] Use the frontend to verify historical import, project BOM upload, operation confirmation, production color selection, and SKU creation.
- [ ] Record exact modified files, browser steps, observed results, and qualification criteria in the implementation document.
- [ ] Verify no placeholders and no unrelated files are staged.
