# PLM Web M4 Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 M4 BOM、工艺路线、时间轴附件和文件中心后端能力接入 Vue 3 前端，形成以 Product 项目时间轴为主线的真实业务闭环。

**Architecture:** 保留现有项目中心页面编排，把 M4 行为拆到项目私有组件；API 模块只处理 HTTP 与后端 DTO，组件依据后端 `draft/frozen/locked` 状态决定可编辑性。文件中心只负责真实归档查询、下载和删除，上传必须从项目当前时间轴节点发起。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Axios、Vitest、Spring Boot 3、PostgreSQL、Flyway

## Global Constraints

- 只复用 Product、Process、Attachment 等既有领域归属，不新增 BOM、Routing、FileVersion 根对象。
- 所有接口使用 `/api/v1`，失败必须保留错误语义，不能转换为空数组。
- BOM 冻结和工艺锁定后以重新查询的后端状态为准，前端不得维护假状态。
- 文件上传使用 `FormData`，浏览器负责 multipart boundary；下载使用 Blob 并释放 Object URL。
- 保留现有 Element Plus 工作台视觉体系，不做无关页面重构。
- 当前工作区存在用户未提交改动，不自动提交、不回滚、不清理。

---

### Task 1: Frontend test foundation and request contract

**Files:**
- Modify: `../plm-web/package.json`
- Modify: `../plm-web/package-lock.json`
- Modify: `../plm-web/vite.config.ts`
- Create: `../plm-web/src/api/modules/__tests__/m4-api.spec.ts`
- Modify: `../plm-web/src/api/request.ts`

**Interfaces:**
- Produces: `npm run test`, FormData-safe request interceptor, reusable API response unwrapping.
- Consumes: current Axios instance and `/api/v1` response envelope.

- [ ] Add Vitest as a development dependency and add `test`/`test:run` scripts.
- [ ] Write failing tests proving BOM, process and attachment functions call real endpoints with exact paths and payloads.
- [ ] Run `npm run test:run -- src/api/modules/__tests__/m4-api.spec.ts` and confirm failures are caused by current mock implementations.
- [ ] Update request handling so FormData removes the fixed JSON content type while JSON requests remain unchanged.
- [ ] Re-run the focused test and confirm the request contract tests pass.

### Task 2: Real BOM API and project BOM panel

**Files:**
- Modify: `../plm-web/src/api/modules/bom.ts`
- Create: `../plm-web/src/views/project/components/ProjectBomPanel.vue`

**Interfaces:**
- Produces: `getProjectBoms`, `getBomDetail`, `createProjectBom`, `updateBom`, `addBomItem`, `updateBomItem`, `deleteBomItem`, `freezeBom` using backend DTO names.
- Consumes: `ProductBomVO` and `ProductBomItemVO` returned by M4 backend.

- [ ] Define backend-aligned BOM DTO and save payload types.
- [ ] Replace project-level BOM mocks with Axios calls and `unwrapResponse`.
- [ ] Keep `getBomCenterSnapshot` unchanged because the global BOM center has no backend aggregate endpoint.
- [ ] Build a project-private panel with list/detail selection, create/edit dialogs and item maintenance.
- [ ] Disable mutation controls for `status=frozen`, confirm before freeze/delete, and reload from backend after mutation.
- [ ] Verify empty success, loading, business error and frozen read-only states.

### Task 3: Real process API and project process panel

**Files:**
- Modify: `../plm-web/src/api/modules/process.ts`
- Create: `../plm-web/src/views/project/components/ProjectProcessRoutePanel.vue`

**Interfaces:**
- Produces: project route list/detail/create/update/freeze functions with `processName`, `processParamJson`, `qualityRequirement` and complete operation arrays.
- Consumes: M4 `ProcessRouteVO` and nested `ProcessOperationVO`.

- [ ] Define backend-aligned process DTO and save payload types.
- [ ] Replace project-level process mocks with real Axios calls while preserving the global process center mock snapshot.
- [ ] Build route list and editor with ordered operations, JSON validation and quality requirement validation.
- [ ] Submit the complete operation array on update and reload after save/freeze.
- [ ] Disable editing for `status=locked` and display a clear lock state.

### Task 4: Real attachment API and timeline attachment panel

**Files:**
- Modify: `../plm-web/src/api/modules/attachment.ts`
- Create: `../plm-web/src/utils/file.ts`
- Create: `../plm-web/src/views/project/components/TimelineAttachmentPanel.vue`

**Interfaces:**
- Produces: upload/list/detail/page/download/delete functions, `saveBlob`, file size formatter.
- Consumes: current `projectId`, current `nodeKey`, M4 attachment DTO and `PageResponse`.

- [ ] Replace all M4 attachment mocks with real Axios calls.
- [ ] Upload `file`, `fileCategory`, optional `versionNo` and `remark` through FormData.
- [ ] Download a Blob, use `originalFileName`, and always call `URL.revokeObjectURL`.
- [ ] Build current-node attachment panel with category/version/remark fields and real file selection.
- [ ] Reload list after upload/delete and emit a change event so project timeline counts can refresh.
- [ ] Show a distinct empty state only after a successful empty response; retain error state on request failure.

### Task 5: Project center composition

**Files:**
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`

**Interfaces:**
- Consumes: `ProjectBomPanel`, `ProjectProcessRoutePanel`, `TimelineAttachmentPanel`, `detailTarget.productId`, and current timeline `nodeKey`.
- Produces: Product and SKU detail tabs backed by real M4 data.

- [ ] Import the three project-private components.
- [ ] Replace product BOM, process and materials mock sections with the M4 panels.
- [ ] Replace SKU BOM, process and production-doc sections with the same Product-bound panels.
- [ ] Pass the active timeline node key to attachment upload and refresh the timeline after attachment changes.
- [ ] Preserve unrelated M3 timeline action and project presentation behavior.

### Task 6: Real file center

**Files:**
- Modify: `../plm-web/src/views/file/FileCenterView.vue`

**Interfaces:**
- Consumes: paged `getFileCenterAttachments`, `downloadAttachment`, `deleteAttachment`, project navigation.
- Produces: server-side project/node/category/keyword filters and pagination.

- [ ] Replace foundation mock sections with the real paged attachment query.
- [ ] Provide keyword, project ID, node key and category filters with explicit search/reset commands.
- [ ] Display file metadata in a scan-friendly table and expose download/delete actions.
- [ ] Add pagination using backend `page`, `size`, `totalElements`.
- [ ] Remove fake upload and fake delete behavior; direct users to project current-node attachments for upload.

### Task 7: Automated and browser verification

**Files:**
- Modify only when a verified defect requires a regression fix.

**Interfaces:**
- Consumes: local PostgreSQL/Docker, Spring Boot on `8080`, Vite on `5173`.
- Produces: fresh verification evidence for API, type, build and browser workflow.

- [ ] Run focused Vitest tests and the complete frontend test suite.
- [ ] Run `npm run type-check` and fix every TypeScript error introduced by M4.
- [ ] Run `npm run build` and confirm exit code 0.
- [ ] Run the existing backend M4 tests and confirm no backend regression.
- [ ] Start backend with security enabled and start Vite with `VITE_API_BASE_URL=http://localhost:8080`.
- [ ] Use the browser to verify login, BOM create/item/freeze, process create/edit/freeze, attachment upload/download/delete, and file-center query.
- [ ] Check desktop and narrow viewport for overflow, overlap and unusable controls.

### Task 8: Implementation deposit document

**Files:**
- Create: `docs/frontend-notes/2026-07-14-PLM-M4前后端接入代码实现与前端测试沉淀.md`
- Copy to: `D:\Yuewei\资料\PLM\docs\实施记录\2026-07-14-PLM-M4前后端接入代码实现与前端测试沉淀.md`

**Interfaces:**
- Consumes: actual diff and fresh verification output.
- Produces: exact changed-file inventory, code logic, maintenance notes, beginner-oriented frontend test steps and pass criteria.

- [ ] Record every actual code/document change and explicitly state whether backend business code changed.
- [ ] Document startup commands, login, browser navigation, Network checks and expected result for every M4 step.
- [ ] Record automated test/type-check/build/backend/browser results without inventing unexecuted evidence.
- [ ] Copy the final document to the external PLM implementation directory and verify SHA-256 equality.

## Self-Review

- Spec coverage: BOM、工艺路线、时间轴附件、文件中心、下载、删除、冻结和锁定均有对应任务。
- Placeholder scan: no TODO/TBD implementation placeholders.
- Type consistency: backend identifiers remain `productBomId`, `productBomItemId`, `processId`, `attachmentId`; query uses `nodeKey` while response uses `timelineNodeKey`.
