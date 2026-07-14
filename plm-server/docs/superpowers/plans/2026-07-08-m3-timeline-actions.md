# M3 Timeline Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement timeline node confirm, advance, and return endpoints for Product-backed projects.

**Architecture:** Keep projectId mapped to productId. Store current confirmation and latest timeline action on `plm_product`, use `current_step_no` for node position, and use `plm_operation_log` for successful action audit history.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, PostgreSQL migration SQL, JUnit 5, Mockito.

## Global Constraints

- Do not add a new Project root object.
- Do not add SKU, BOM, ECR, ECN, Quote, or other forbidden root objects.
- API path prefix stays `/api/v1`.
- Successful timeline actions must write `plm_operation_log`.
- Failed timeline actions must not write successful operation logs.
- Illegal actions must return explicit business errors through `BusinessException`.
- APIFOX acceptance steps must be documented in the final implementation note.

---

### Task 1: Timeline Action Domain Tests

**Files:**
- Create: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`
- Later Create: `src/main/java/com/yuewei/plm/module/project/dto/TimelineActionDTO.java`
- Later Create: `src/main/java/com/yuewei/plm/module/project/vo/TimelineActionResultVO.java`
- Later Create: `src/main/java/com/yuewei/plm/module/project/service/TimelineActionService.java`
- Later Create: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`

**Interfaces:**
- Produces: service behavior for `confirm`, `advance`, and `returnNode`.

- [ ] **Step 1: Write failing service tests**

Cover current-node confirm success, wrong-node confirm failure, unconfirmed advance failure, confirmed advance success, last-node advance failure, return reason validation, and return-to-previous success.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=TimelineActionServiceImplTest test`
Expected: compilation failure because M3 DTO/VO/service classes do not exist yet.

### Task 2: Timeline Action Implementation

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/dto/TimelineActionDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/vo/TimelineActionResultVO.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/TimelineActionService.java`
- Create: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/repository/entity/Product.java`
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Create: `src/main/resources/db/migration/V20260708_1000__m3_timeline_actions.sql`

**Interfaces:**
- Consumes: `ProductRepository`, `TimelineDefinitionProvider`, `OperationLogService`, `CurrentUserContext`.
- Produces: `TimelineActionService.confirm`, `advance`, `returnNode`.

- [ ] **Step 1: Implement minimal M3 classes and fields**
- [ ] **Step 2: Run service tests**

Run: `mvn -Dtest=TimelineActionServiceImplTest test`
Expected: tests pass.

### Task 3: Controller And Timeline Query Enhancement

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/controller/TimelineController.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineDetailVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
- Modify: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`
- Create: `src/test/java/com/yuewei/plm/module/project/controller/TimelineControllerActionTest.java`

**Interfaces:**
- Consumes: `TimelineActionService`.
- Produces: POST `/projects/{projectId}/timeline/{nodeKey}/confirm`, `/advance`, `/return`.

- [ ] **Step 1: Write failing controller/query tests**
- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -Dtest=TimelineControllerActionTest,TimelineServiceImplTest test`
Expected: failure before controller/query implementation.

- [ ] **Step 3: Implement controller endpoints and query confirmation fields**
- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=TimelineControllerActionTest,TimelineServiceImplTest test`
Expected: tests pass.

### Task 4: Verification And Documentation

**Files:**
- Create: `docs/backend-notes/2026-07-08-PLM后端M3时间轴节点确认推进退回代码实现沉淀.md`
- Copy to: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-08-PLM后端M3时间轴节点确认推进退回代码实现沉淀.md`

**Interfaces:**
- Produces final implementation note with changed files, code logic, APIFOX tests, and acceptance criteria.

- [ ] **Step 1: Run focused M3 tests**

Run: `mvn -Dtest=TimelineActionServiceImplTest,TimelineControllerActionTest,TimelineServiceImplTest test`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Run full tests**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Write implementation note**
- [ ] **Step 4: Verify document exists and key sections are present**

Run: `Test-Path` and `Select-String` against both doc locations.
Expected: target file exists and contains APIFOX, changed files, test criteria, and test result sections.
