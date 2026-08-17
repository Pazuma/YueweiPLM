# PLM Process Route Template Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved workbench “new process route” flow using `Process` records, product-code-based route codes, template operations, and frontend direct-test criteria.

**Architecture:** Keep process routes as `plm_process` rows with `process_type=routing`; store template/final-selection metadata in `process_param_json` to avoid adding new root objects or migrations in this MVP. Add an independent template service and API, then let the Vue project process panel consume templates and show a backend node-check table.

**Tech Stack:** Java 17, Spring Boot, MyBatis Plus, PostgreSQL JSONB, Vue 3, Element Plus, Vitest.

## Global Constraints

- Do not create root objects named Routing, Operation, BOM, SKU, or BOMLine.
- API paths stay under `/api/v1/...`.
- Backend generates `processCode`; frontend only displays preview/result.
- Locked process routes remain read-only.
- Workbench process-route steps navigate to structured route creation, not ordinary route-file upload.
- Frontend testing is the acceptance path; APIFOX is not used for final acceptance.

---

### Task 1: Backend Route Templates And Code Generation

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/process/vo/ProcessRouteTemplateOperationVO.java`
- Create: `src/main/java/com/yuewei/plm/module/process/vo/ProcessRouteTemplateVO.java`
- Create: `src/main/java/com/yuewei/plm/module/process/service/ProcessRouteTemplateService.java`
- Create: `src/main/java/com/yuewei/plm/module/process/service/impl/ProcessRouteTemplateServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/dto/ProcessOperationDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/dto/ProcessRouteSaveDTO.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/vo/ProcessOperationVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/vo/ProcessRouteVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/controller/ProcessRouteController.java`
- Modify: `src/main/java/com/yuewei/plm/module/process/service/impl/ProcessRouteServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/process/service/impl/ProcessRouteServiceImplTest.java`

**Interfaces:**
- Produces: `GET /api/v1/process-route-templates`
- Produces: `ProcessRouteSaveDTO.routeTemplateCode`, `copyTemplateOperations`, `applicableModel`, `applicableColor`, `linkedBomVersionNo`, `finalSelected`
- Produces: route code format `{productCode}-{routeTemplateCode}-{versionNo}`

- [ ] Write failing backend tests for template-copy create and product-code route code.
- [ ] Implement template VO/service/controller.
- [ ] Extend save DTO/VO and persist metadata in `processParamJson`.
- [ ] Verify targeted backend tests pass.

### Task 2: Frontend API And Project Process Panel

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\process.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\ProjectProcessRoutePanel.vue`
- Test: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\__tests__\m4-api.spec.ts`
- Test: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\project-m4-panels.spec.ts`

**Interfaces:**
- Consumes: `getProcessRouteTemplates()`
- Consumes: extended `ProcessRouteVO` metadata.
- Produces: template selector, generated-code preview, operation rows from template, node-check table.

- [ ] Write failing frontend API/component tests for template endpoint and template-selected operation rows.
- [ ] Extend process API types and functions.
- [ ] Update the process route panel with template-first creation and node-check table.
- [ ] Verify targeted frontend tests pass.

### Task 3: Workbench Entry

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\dashboard\DashboardView.vue`
- Test: `D:\Yuewei\git\YUEWEI\plm-web\src\views\dashboard\__tests__\DashboardView.spec.ts`

**Interfaces:**
- Consumes: current timeline child step metadata.
- Produces: process-route steps show “新建工艺路线” and navigate to `/projects?...&section=process_detail`.

- [ ] Write failing dashboard test for process-route step button/navigation.
- [ ] Change process child-step labels/actions without removing ordinary attachment upload for non-process materials.
- [ ] Verify dashboard tests pass.

### Task 4: Documentation And Verification

**Files:**
- Create: `docs/backend-notes/2026-07-18-PLM工作台新建工艺路线与产品编码版本化代码实现沉淀.md`
- Copy to: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-18-PLM工作台新建工艺路线与产品编码版本化代码实现沉淀.md`

**Interfaces:**
- Produces: changed files, code logic, maintenance guidance, frontend test steps and pass criteria.

- [ ] Run backend targeted tests.
- [ ] Run frontend targeted tests.
- [ ] Write implementation沉淀文档.
- [ ] Copy document to the external沉淀目录.
