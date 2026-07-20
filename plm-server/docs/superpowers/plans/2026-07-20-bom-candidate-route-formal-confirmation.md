# BOM Candidate Route Formal Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the confirmed workflow where project workbench BOMs are candidate records bound to one process route, and the "敲定工序" node confirms one formal BOM plus production operations per route.

**Architecture:** Keep BOM under Product and process routes under Process. Add a route formal BOM selection extension table instead of introducing a BOM root object. Reuse the existing operation confirmation and production color/SKU flow, but make it depend on the active formal BOM route selection.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, PostgreSQL/Flyway, Vue 3, Element Plus, Vitest.

## Global Constraints

- Do not create SKU, BOM, BOMLine, Routing, or other forbidden root objects.
- Keep API paths under `/api/v1/...`.
- Preserve unrelated dirty worktree changes; stage only files touched for this task.
- Frontend source is outside the writable root at `D:\Yuewei\git\YUEWEI\plm-web`; mirror files under `.codex_tmp/plm-web`, edit there, then copy back.
- New workbench BOM creation must require a process route and create `bomScope=candidate`, `status=draft`.
- Formal BOM selection must be one active selection per `product_id + process_id`.
- "敲定工序" must submit formal BOM selection and at least one route operation in one API call.

---

### Task 1: Database Contract for Route Formal BOM Selection

**Files:**
- Create: `src/main/resources/db/migration/V20260720_1001__bom_route_formal_selection.sql`
- Modify: `src/test/java/com/yuewei/plm/module/bom/BomWorkbenchMigrationContractTest.java`

**Interfaces:**
- Produces table `plm_product_bom_route_formal_selection`.
- Active uniqueness: `(product_id, process_id)` where `status='active' and deleted_flag=0`.

- [ ] Step 1: Write a failing migration contract test that checks the new table name, route/BOM/process fields, active uniqueness, invalidation fields, and audit columns.
- [ ] Step 2: Run `mvn -Dtest=BomWorkbenchMigrationContractTest test` and verify the new assertions fail.
- [ ] Step 3: Add Flyway migration for `plm_product_bom_route_formal_selection`.
- [ ] Step 4: Re-run the same test and verify it passes.

### Task 2: Backend Candidate BOM Creation

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/dto/ProductBomCreateDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBom.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/vo/ProductBomVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImpl.java`
- Modify: `src/test/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImplTest.java`

**Interfaces:**
- Consumes `processId` in `ProductBomCreateDTO`.
- Produces a candidate BOM and one active `ProductBomRoute` for the selected process route.

- [ ] Step 1: Write failing tests for missing route, route outside project, and successful candidate BOM creation with one route.
- [ ] Step 2: Run `mvn -Dtest=ProductBomServiceImplTest test` and verify failure.
- [ ] Step 3: Add `processId`, route validation, default `bomScope=candidate`, and route creation.
- [ ] Step 4: Add route/formal summary fields to `ProductBomVO`.
- [ ] Step 5: Re-run the focused test and verify it passes.

### Task 3: Backend Combined Formal BOM and Operation Confirmation

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/bom/entity/ProductBomRouteFormalSelection.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/repository/ProductBomRouteFormalSelectionRepository.java`
- Create: `src/main/java/com/yuewei/plm/module/bom/dto/ProductionRouteConfirmDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/dto/ProductionOperationConfirmDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/vo/ProductionConfirmationVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/ProductionConfirmationService.java`
- Modify: `src/main/java/com/yuewei/plm/module/bom/controller/ProductionConfirmationController.java`
- Modify: `src/test/java/com/yuewei/plm/module/bom/service/ProductionConfirmationServiceTest.java`

**Interfaces:**
- New API: `POST /api/v1/projects/{projectId}/production-routes/confirm`.
- Request shape: `{ "routes": [{ "processId": 101, "productBomId": 201, "productBomRouteId": 301, "operationProcessIds": [1001] }], "remark": "..." }`.
- Response includes route formal selections and selected operation IDs.

- [ ] Step 1: Write failing tests that selecting BOM A then BOM B for the same process invalidates A and leaves one active formal BOM.
- [ ] Step 2: Write failing tests that operations cannot be confirmed without a product BOM route and at least one operation.
- [ ] Step 3: Run `mvn -Dtest=ProductionConfirmationServiceTest test` and verify failure.
- [ ] Step 4: Implement entity/repository/DTO/VO and service transaction.
- [ ] Step 5: Update legacy `/production-operations/confirm` to delegate to the combined route confirmation.
- [ ] Step 6: Update production color confirmation and gates to accept only active formal route selections.
- [ ] Step 7: Re-run focused tests and verify they pass.

### Task 4: Ledger and Workbench Query Compatibility

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/bom/service/impl/BomLedgerServiceImpl.java`
- Modify: `src/test/java/com/yuewei/plm/module/bom/service/impl/BomLedgerServiceImplTest.java`

**Interfaces:**
- BOM ledger lists only released BOMs with active formal route selections.
- Candidate BOMs stay visible in project workbench only.

- [ ] Step 1: Write failing tests that candidate BOMs are excluded from ledger and selected released BOMs are included.
- [ ] Step 2: Run `mvn -Dtest=BomLedgerServiceImplTest test` and verify failure.
- [ ] Step 3: Filter ledger rows through active formal selections.
- [ ] Step 4: Re-run focused test.

### Task 5: Frontend API Types and Tests

**Files:**
- Modify mirror then copy back: `../plm-web/src/types/bom.ts`
- Modify mirror then copy back: `../plm-web/src/api/modules/bom.ts`
- Modify mirror then copy back: `../plm-web/src/api/modules/__tests__/m4-api.spec.ts`

**Interfaces:**
- `ProductBomSavePayload` gains `processId`.
- Add `confirmProductionRoutes(projectId, payload)`.
- `ProductionConfirmation` gains `routeSelections`.

- [ ] Step 1: Mirror frontend files into `.codex_tmp/plm-web`.
- [ ] Step 2: Write failing API tests for `processId` creation and `/production-routes/confirm`.
- [ ] Step 3: Run `npm run test:run -- src/api/modules/__tests__/m4-api.spec.ts`.
- [ ] Step 4: Add types and API function.
- [ ] Step 5: Copy files back and re-run the focused API test.

### Task 6: Frontend Workbench and Confirmation UI

**Files:**
- Modify mirror then copy back: `../plm-web/src/views/project/components/ProjectBomPanel.vue`
- Modify mirror then copy back: `../plm-web/src/views/project/components/ProductionConfirmationDialog.vue`
- Modify mirror then copy back: `../plm-web/src/views/project/__tests__/ProjectBomPanel.spec.ts`
- Modify mirror then copy back: `../plm-web/src/views/project/__tests__/ProductionConfirmationDialog.spec.ts`
- Modify mirror then copy back: `../plm-web/src/views/project/__tests__/project-m4-panels.spec.ts`

**Interfaces:**
- Workbench button label is `新建 BOM`.
- Creation dialog requires associated process route.
- "敲定工序" shows route cards, formal BOM radio choices, and operation checkboxes.
- Submit uses `confirmProductionRoutes`.

- [ ] Step 1: Write failing component tests for the new button, route-required create flow, formal BOM radio, and disabled operation submit without formal BOM.
- [ ] Step 2: Run the focused Vitest files and verify failure.
- [ ] Step 3: Implement the UI changes with existing Element Plus density/style.
- [ ] Step 4: Re-run focused component tests.

### Task 7: Documentation, Verification, and Commit

**Files:**
- Modify external docs: `D:\Yuewei\资料\PLM\docs\modules\04-BOM管理.md`
- Modify external docs: `D:\Yuewei\资料\PLM\docs\14-API接口定义文档.md`
- Create external document: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-20-PLM工作台候选BOM关联工艺路线与正式BOM确认代码实现沉淀.md`

**Interfaces:**
- Implementation document lists changed docs/code and frontend direct test steps with pass criteria.

- [ ] Step 1: Update external module/API docs through mirrored copies.
- [ ] Step 2: Create implementation/acceptance document.
- [ ] Step 3: Run focused backend tests, focused frontend tests, type-check/build as feasible.
- [ ] Step 4: Start backend and frontend for direct frontend verification where tooling allows.
- [ ] Step 5: If browser automation is unavailable, record the exact limitation and provide manual frontend acceptance steps in the implementation document.
- [ ] Step 6: Review `git diff`, stage exact related files, and commit.
