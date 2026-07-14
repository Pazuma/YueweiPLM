# M2 Project Workbench Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build backend query APIs for M2 workbench in-progress projects and project timeline, using Product as the project carrier.

**Architecture:** Add a lightweight `com.yuewei.plm.module.project` query module that reads `plm_product` through the existing `ProductRepository`. The module maps `projectId` to `productId`, computes timeline nodes from fixed Java definitions, and keeps `documentCount` as `0` for this phase.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, PostgreSQL migration SQL, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Default communication and final documentation are in Chinese.
- API prefix remains `/api/v1`.
- Do not create a `Project` root object, entity, or table.
- Use only the Product core object for project views.
- `projectId` equals `productId` in M2.
- M2 is read-only for timeline; no node advance or write-side status transition is implemented.
- Product status remains `draft`, `developing`, `reviewing`, `released`, `archived`.
- Workbench in-progress status means `draft`, `developing`, `reviewing`.
- `current_step_no` is a Product table field and is clamped to `1..6` at read time.
- `documentCount` returns `0` until attachment timeline statistics exist.
- Do not revert unrelated user changes.
- No commits in this dirty workspace unless the user explicitly asks.

---

## File Structure

- Create `src/main/java/com/yuewei/plm/module/project/dto/ProjectQueryDTO.java`: pagination and filters for project query APIs.
- Create `src/main/java/com/yuewei/plm/module/project/vo/ProjectSummaryVO.java`: list and summary response.
- Create `src/main/java/com/yuewei/plm/module/project/vo/ProjectDetailVO.java`: detail response including timeline.
- Create `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`: one timeline node response.
- Create `src/main/java/com/yuewei/plm/module/project/vo/TimelineDetailVO.java`: timeline response.
- Create `src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java`: product type, node status, display names, and node definitions.
- Create `src/main/java/com/yuewei/plm/module/project/service/TimelineDefinitionProvider.java`: fixed timeline definition resolver.
- Create `src/main/java/com/yuewei/plm/module/project/service/TimelineService.java`: timeline query contract.
- Create `src/main/java/com/yuewei/plm/module/project/service/ProjectService.java`: project query contract.
- Create `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`: timeline generation.
- Create `src/main/java/com/yuewei/plm/module/project/service/impl/ProjectServiceImpl.java`: Product to project VO mapping.
- Create `src/main/java/com/yuewei/plm/module/project/controller/ProjectWorkbenchController.java`: `/workbench/projects/in-progress`.
- Create `src/main/java/com/yuewei/plm/module/project/controller/ProjectController.java`: `/projects`, `/projects/{projectId}`, `/summary`.
- Create `src/main/java/com/yuewei/plm/module/project/controller/TimelineController.java`: `/projects/{projectId}/timeline`.
- Modify `src/main/java/com/yuewei/plm/repository/entity/Product.java`: add `currentStepNo`.
- Modify `src/main/java/com/yuewei/plm/service/vo/ProductVO.java`: add `currentStepNo`.
- Modify `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java`: map `currentStepNo`.
- Create `src/main/resources/db/migration/V20260706_1000__m2_project_workbench_timeline.sql`: add `current_step_no`.
- Create tests under `src/test/java/com/yuewei/plm/module/project/service/...`.
- Create final delivery document under `docs/backend-notes/2026-07-07-PLM后端M2工作台进行中项目与项目时间轴查询代码实现沉淀.md`.

---

### Task 1: Timeline Definitions

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/TimelineDefinitionProvider.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/TimelineDefinitionProviderTest.java`

**Interfaces:**
- Produces: `TimelineDefinitionProvider#getDefinitions(String productType): List<TimelineNodeConstants.TimelineNodeDefinition>`
- Produces: `TimelineDefinitionProvider#getCurrentNodeName(String productType, Integer currentStepNo): String`
- Produces: `TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE`, `TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT`

- [ ] **Step 1: Write failing tests**

Create `TimelineDefinitionProviderTest` with tests for product-line nodes, model-variant nodes, current node name clamping, and unsupported product type.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=TimelineDefinitionProviderTest test`
Expected: FAIL because `TimelineDefinitionProvider` does not exist.

- [ ] **Step 3: Implement timeline constants and provider**

Create immutable node definitions for both product types. Throw `BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "不支持的产品类型")` for unknown product type.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=TimelineDefinitionProviderTest test`
Expected: PASS.

---

### Task 2: Timeline Service

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/vo/TimelineDetailVO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/TimelineService.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/repository/entity/Product.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`

**Interfaces:**
- Consumes: `TimelineDefinitionProvider#getDefinitions`
- Produces: `TimelineService#getTimeline(Long projectId): TimelineDetailVO`

- [ ] **Step 1: Write failing tests**

Create `TimelineServiceImplTest` to verify a product-line at step 2 returns 6 nodes with statuses `completed/current/pending`, `documentCount=0`, and `projectId=productId`; verify missing product throws `40401`; verify step greater than 6 clamps to 6.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=TimelineServiceImplTest test`
Expected: FAIL because timeline service files do not exist.

- [ ] **Step 3: Implement VO, service interface, and service implementation**

Use `ProductRepository#selectById`. Treat `deletedFlag=1` as not found. Clamp `currentStepNo` to `1..definition size`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=TimelineServiceImplTest test`
Expected: PASS.

---

### Task 3: Project Query Service

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/dto/ProjectQueryDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/vo/ProjectSummaryVO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/vo/ProjectDetailVO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/ProjectService.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/impl/ProjectServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/ProjectServiceImplTest.java`

**Interfaces:**
- Consumes: `TimelineService#getTimeline`
- Consumes: `TimelineDefinitionProvider#getCurrentNodeName`
- Produces: `ProjectService#pageInProgress(ProjectQueryDTO): PageVO<ProjectSummaryVO>`
- Produces: `ProjectService#page(ProjectQueryDTO): PageVO<ProjectSummaryVO>`
- Produces: `ProjectService#getDetail(Long projectId): ProjectDetailVO`
- Produces: `ProjectService#getSummary(Long projectId): ProjectSummaryVO`

- [ ] **Step 1: Write failing tests**

Create `ProjectServiceImplTest` to verify in-progress query filters statuses to `draft/developing/reviewing`, maps `projectId`, `currentNodeName`, `statusName`, `productTypeName`, and `ownerUserName`; verify detail includes timeline; verify missing product throws `40401`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ProjectServiceImplTest test`
Expected: FAIL because project service files do not exist.

- [ ] **Step 3: Implement DTO, VO, service interface, and service implementation**

Use MyBatis-Plus `LambdaQueryWrapper<Product>` and `Page<Product>`. Query owner names from `SysUserRepository` in batch and map missing names to `null`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ProjectServiceImplTest test`
Expected: PASS.

---

### Task 4: Controllers, Product Field Mapping, and Migration

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/controller/ProjectWorkbenchController.java`
- Create: `src/main/java/com/yuewei/plm/module/project/controller/ProjectController.java`
- Create: `src/main/java/com/yuewei/plm/module/project/controller/TimelineController.java`
- Modify: `src/main/java/com/yuewei/plm/service/vo/ProductVO.java`
- Modify: `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java`
- Create: `src/main/resources/db/migration/V20260706_1000__m2_project_workbench_timeline.sql`
- Test: existing service tests and project service tests.

**Interfaces:**
- Consumes: `ProjectService`
- Consumes: `TimelineService`
- Produces: `GET /api/v1/workbench/projects/in-progress`
- Produces: `GET /api/v1/projects`
- Produces: `GET /api/v1/projects/{projectId}`
- Produces: `GET /api/v1/projects/{projectId}/summary`
- Produces: `GET /api/v1/projects/{projectId}/timeline`

- [ ] **Step 1: Write/extend failing test**

Extend an existing service test or add a Product VO mapping test so `ProductServiceImpl#getById` returns `currentStepNo`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ProductServiceImplTest test`
Expected: FAIL until `currentStepNo` is mapped.

- [ ] **Step 3: Implement controllers, Product VO mapping, and migration SQL**

Controllers use `ResponseVO.success(..., RequestIdUtil.getRequestId(request), OffsetDateTime.now())`. Migration adds `current_step_no integer not null default 1`.

- [ ] **Step 4: Run project and product tests**

Run: `mvn -Dtest=TimelineDefinitionProviderTest,TimelineServiceImplTest,ProjectServiceImplTest,ProductServiceImplTest test`
Expected: PASS.

---

### Task 5: Delivery Document and Full Verification

**Files:**
- Create: `docs/backend-notes/2026-07-07-PLM后端M2工作台进行中项目与项目时间轴查询代码实现沉淀.md`

**Interfaces:**
- Produces: Chinese delivery document listing modified docs, modified code, and Apifox test steps.

- [ ] **Step 1: Write delivery document**

Document references read, code files changed, API list, database migration, and Apifox test steps for login, no-token rejection, create product line, query in-progress projects, filters, detail, summary, timeline, not-found, and model-variant timeline.

- [ ] **Step 2: Run full verification**

Run: `mvn test`
Expected: PASS. If full test is too slow or environment-blocked, run the focused M2 test command and report the limitation.

- [ ] **Step 3: Inspect changed files**

Run: `git status --short`
Expected: M2 files appear, unrelated pre-existing dirty files may still appear.

- [ ] **Step 4: Final response**

Summarize changed code, changed docs, verification evidence, Apifox coverage, skills used, and note that `agent-memory` was unavailable if still true.

---

## Self-Review

- Spec coverage: Covers workbench in-progress list, project list/detail/summary/timeline, Product-only project mapping, `current_step_no`, owner user display name, fixed timeline definitions, and Apifox delivery documentation.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: Service signatures and VO names are consistent across tasks.
