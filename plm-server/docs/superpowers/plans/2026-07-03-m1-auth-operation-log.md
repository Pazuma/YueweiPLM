# M1 Auth And Operation Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement simple login, current user context, token-based request authentication, operation log persistence, and Product freeze audit logging.

**Architecture:** Add Auth, User, and OperationLog modules under `module/*`, reuse `CurrentUserContext`, `ResponseVO`, `PageVO`, MyBatis-Plus repositories, and the existing Product module. Use in-memory token sessions for M1 and PostgreSQL tables for `sys_user` and `plm_operation_log`.

**Tech Stack:** Java 17, Spring Boot 3.5.0, Spring Security, MyBatis-Plus, PostgreSQL, JUnit 5, Mockito.

## Global Constraints

- API prefix must remain `/api/v1/...`.
- Do not add business root objects beyond Customer, Product, Order, ProductionOrder, Process, Inventory, Workstation.
- `sys_user` and `plm_operation_log` are public security/audit infrastructure tables, not PLM business root objects.
- Passwords use BCrypt hashes; no plaintext password storage.
- `GET /api/v1/auth/profile`, `POST /api/v1/auth/logout`, and operation log APIs require Bearer token when `app.security.enabled=true`.
- Product freeze operator must come from `CurrentUserContext`, not a forged query parameter.

---

### Task 1: Auth And Token Session

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/user/entity/SysUser.java`
- Create: `src/main/java/com/yuewei/plm/module/user/repository/SysUserRepository.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/dto/LoginDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/vo/CurrentUserVO.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/vo/LoginVO.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/service/TokenSessionService.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/service/AuthService.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/service/impl/InMemoryTokenSessionService.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/com/yuewei/plm/module/auth/controller/AuthController.java`
- Test: `src/test/java/com/yuewei/plm/module/auth/service/impl/InMemoryTokenSessionServiceTest.java`
- Test: `src/test/java/com/yuewei/plm/module/auth/service/impl/AuthServiceImplTest.java`

**Interfaces:**
- Produces: `TokenSessionService#createSession(CurrentUser)`, `getCurrentUser(String)`, `invalidate(String)`, `getExpiresInSeconds()`
- Produces: `AuthService#login(LoginDTO, HttpServletRequest)`, `profile()`, `logout(String, HttpServletRequest)`

### Task 2: Security Filter

**Files:**
- Modify: `src/main/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilter.java`
- Modify: `src/main/java/com/yuewei/plm/common/config/SecurityConfig.java`
- Test: `src/test/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `TokenSessionService#getCurrentUser(String)`
- Produces: 401 JSON response with code `40101` for missing or invalid token.

### Task 3: Operation Log Module

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/dto/OperationLogQueryDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/dto/OperationLogTestDTO.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/entity/OperationLog.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/repository/OperationLogRepository.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/service/OperationLogCreateCommand.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/service/OperationLogService.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/service/impl/OperationLogServiceImpl.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/vo/OperationLogVO.java`
- Create: `src/main/java/com/yuewei/plm/module/operationlog/controller/OperationLogController.java`
- Test: `src/test/java/com/yuewei/plm/module/operationlog/service/impl/OperationLogServiceImplTest.java`

**Interfaces:**
- Produces: `OperationLogService#logSuccess(OperationLogCreateCommand) -> Long`
- Produces: `OperationLogService#page(OperationLogQueryDTO) -> PageVO<OperationLogVO>`

### Task 4: Product Freeze Audit

**Files:**
- Modify: `src/main/java/com/yuewei/plm/controller/ProductController.java`
- Modify: `src/main/java/com/yuewei/plm/service/ProductService.java`
- Modify: `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java`

**Interfaces:**
- Consumes: `CurrentUserContext#get()`
- Consumes: `OperationLogService#logSuccess(...)`
- Produces: Product freeze writes `PRODUCT_FREEZE` operation log.

### Task 5: Database Migration And Documentation

**Files:**
- Create: `src/main/resources/db/migration/V20260703_1600__m1_auth_and_operation_log.sql`
- Create: `docs/backend-notes/2026-07-03-PLM后端M1简单登录当前用户操作日志实现沉淀.md`
- Copy final沉淀 to `D:\Yuewei\资料\PLM\docs\后端-沉淀\...`

**Verification:**
- Run targeted tests after each task.
- Run full `mvn test`.
- Document Apifox and SQL verification steps.
