# M4 BOM Process Attachment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement M4 backend APIs for Product BOM, Process routes, timeline attachments, file center search/download, operation logs, and Flyway-backed database migration.

**Architecture:** Keep the seven-core-object boundary: BOM is Product extension data, route/operation are Process rows, attachments are Product-owned metadata plus local file storage. Follow the existing Spring Boot + MyBatis-Plus layering: Controller, Service, Repository, Entity, DTO, VO.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, PostgreSQL, Flyway, JUnit 5, Mockito.

## Global Constraints

- API prefix remains `/api/v1/...`.
- Do not create BOM, Routing, FileVersion as root business objects.
- Use snake_case database fields and camelCase Java fields.
- Freeze/lock actions must block later modification.
- All key actions must write `plm_operation_log`.
- Local file storage must return real streams and explicit errors when physical files are missing.

---

### Task 1: Flyway and M4 database migration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-prod.yml`
- Create: `src/main/resources/db/migration/V20260710_1000__m4_bom_process_attachment.sql`

**Interfaces:**
- Produces tables/columns used by BOM, process, attachment modules.
- Produces Flyway auto-migration during Spring Boot startup.

- [ ] Add Flyway dependencies for PostgreSQL.
- [ ] Enable Flyway with `classpath:db/migration`.
- [ ] Add idempotent SQL for `plm_product_bom`, `plm_product_bom_item`, `plm_process`, `plm_attachment`, `plm_attachment_download_log`.
- [ ] Verify SQL is idempotent using `create table if not exists`, `add column if not exists`, and safe indexes.
- [ ] Run `mvn -DskipTests compile`.

### Task 2: BOM module

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/bom/**`
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Test: `src/test/java/com/yuewei/plm/module/bom/service/impl/ProductBomServiceImplTest.java`

**Interfaces:**
- `ProductBomService` exposes list/detail/create/update/addItem/updateItem/deleteItem/freeze.
- `ProductBomController` exposes the requested BOM APIs.

- [ ] Write failing service tests for create, add 3 items, duplicate line number, freeze, and modify-after-freeze rejection.
- [ ] Implement BOM entities, repositories, DTOs, VOs.
- [ ] Implement BOM service with product existence validation and operation logs.
- [ ] Implement BOM controller using existing `ResponseVO`.
- [ ] Run `mvn -Dtest=ProductBomServiceImplTest test`.

### Task 3: Process route module

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/process/**`
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Test: `src/test/java/com/yuewei/plm/module/process/service/impl/ProcessRouteServiceImplTest.java`

**Interfaces:**
- `ProcessRouteService` exposes list/detail/create/update/freeze.
- `ProcessRouteController` exposes the requested process route APIs.

- [ ] Write failing service tests for route creation, sequence duplicate rejection, invalid JSON rejection, freeze, and modify-after-lock rejection.
- [ ] Implement Process entity, repository, DTOs, VOs.
- [ ] Implement route service using `process_type=routing` and operations using `process_type=operation`.
- [ ] Implement route controller.
- [ ] Run `mvn -Dtest=ProcessRouteServiceImplTest test`.

### Task 4: Attachment, file center, and real local storage

**Files:**
- Modify: `src/main/java/com/yuewei/plm/infrastructure/storage/StorageClient.java`
- Modify: `src/main/java/com/yuewei/plm/infrastructure/storage/LocalStorageClient.java`
- Modify: `src/main/java/com/yuewei/plm/common/config/AppProperties.java`
- Modify: `src/main/java/com/yuewei/plm/common/constant/ErrorCodeConstants.java`
- Create: `src/main/java/com/yuewei/plm/module/attachment/**`
- Test: `src/test/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImplTest.java`

**Interfaces:**
- `StorageClient.store/read/exists/delete` handle physical files.
- `AttachmentService` handles upload, node list, file center page, detail, download, delete.

- [ ] Write failing tests for upload metadata and physical file, file center filtering, download missing-file error, and delete.
- [ ] Expand storage configuration for max size and allowed extensions.
- [ ] Implement real local storage with `Resource` download.
- [ ] Implement attachment entities, repositories, DTOs, VOs, service and controllers.
- [ ] Run `mvn -Dtest=AttachmentServiceImplTest test`.

### Task 5: Timeline count and dev security fix

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/common/config/SecurityConfig.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/common/security/SecurityConfigTest.java`

**Interfaces:**
- Timeline node `documentCount` counts `plm_attachment`.
- Dev security disabled means `/api/v1/**` can pass without authentication.

- [ ] Write/update failing tests for document count and security-disabled behavior.
- [ ] Inject `AttachmentRepository` or a count service into timeline service.
- [ ] Update SecurityConfig to permit all when `app.security.enabled=false`.
- [ ] Run targeted tests.

### Task 6: Documentation and verification

**Files:**
- Create: `docs/backend-notes/2026-07-10-PLM后端M4接入BOM工艺路线时间轴文件上传代码实现沉淀.md`
- Copy to: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-10-PLM后端M4接入BOM工艺路线时间轴文件上传代码实现沉淀.md`

**Interfaces:**
- Document includes modified docs, modified code, logic, Apifox steps, Flyway startup notes, Maven commands, and maintenance notes.

- [ ] Run focused M4 tests.
- [ ] Run broader Maven test if time and dependencies allow.
- [ ] Write repository doc.
- [ ] Copy doc to backend沉淀 directory.
- [ ] Report verification result and any remaining risks.

