# Auth Context and HTTP Status Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore authenticated user context in the local backend and return HTTP statuses consistent with authentication business codes.

**Architecture:** Keep the existing token filter and service-layer user checks. Correct only the global `BusinessException` HTTP mapping, then ensure the active backend starts with security enabled and verify the real request path.

**Tech Stack:** Java 17, Spring Boot 3.5, JUnit 5, AssertJ, Maven, PowerShell

## Global Constraints

- Do not change timeline, BOM, process-route, token generation, token lifetime, or permission business rules.
- Do not inject a development user when security is disabled.
- Preserve the existing response body contract.
- Preserve all unrelated dirty-worktree changes.

---

### Task 1: Map authentication business errors to correct HTTP statuses

**Files:**
- Modify: `src/test/java/com/yuewei/plm/common/exception/GlobalExceptionHandlerTest.java`
- Modify: `src/main/java/com/yuewei/plm/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `BusinessException#getCode()` and `ErrorCodeConstants.UNAUTHORIZED/FORBIDDEN`
- Produces: `ResponseEntity<ResponseVO<Object>> handleBusiness(...)`

- [x] **Step 1: Write failing tests** for unauthorized -> 401, forbidden -> 403, and ordinary business errors -> 400 while preserving the response body.
- [x] **Step 2: Run** `mvn -Dtest=GlobalExceptionHandlerTest test` and confirm failure because `handleBusiness` does not expose a selectable HTTP status.
- [x] **Step 3: Implement minimal mapping** by returning `ResponseEntity` and selecting `UNAUTHORIZED`, `FORBIDDEN`, or `BAD_REQUEST` from the business code.
- [x] **Step 4: Run** `mvn -Dtest=GlobalExceptionHandlerTest test` and confirm all tests pass.

### Task 2: Restore and verify authenticated runtime

**Files:**
- Modify only if necessary: the current backend launch script or environment configuration that sets `APP_SECURITY_ENABLED`

**Interfaces:**
- Consumes: `APP_SECURITY_ENABLED=true`
- Produces: an 8080 process whose token filter populates `CurrentUserContext`

- [x] **Step 1: Inspect the active Java process command line and repository launch scripts** to locate the runtime override.
- [x] **Step 2: Make the smallest configuration correction** so the local backend starts with `APP_SECURITY_ENABLED=true`; do not alter other runtime settings.
- [x] **Step 3: Restart only the backend process** using its existing launch mechanism.
- [x] **Step 4: Verify** `/api/v1/auth/profile` returns HTTP 200 with a valid login token and HTTP 401 with an invalid token.
- [x] **Step 5: Verify** project 9's timeline return no longer fails with `code=40101`; do not force a state change without a valid user action.

### Task 3: Regression verification

**Files:**
- No production files beyond Tasks 1 and 2

**Interfaces:**
- Consumes: completed status mapping and authenticated runtime
- Produces: regression evidence

- [x] **Step 1: Run focused security, exception-handler, controller, and timeline-action tests.**
- [x] **Step 2: Run the full Maven test suite.**
- [x] **Step 3: Review `git diff` and confirm no unrelated files were modified by this fix.**

## Execution Notes

- Active port 8080 was held by PID 30112, started from a direct `mvn spring-boot:run` parent process without the project launch script. In that runtime, unauthenticated `GET /api/v1/projects` returned HTTP 200, proving the security filter was effectively disabled.
- Restarted only the backend with `.codex_tmp/run-user-backend.ps1`, which explicitly sets `APP_SECURITY_ENABLED=true`.
- After restart, port 8080 is held by PID 8368. Unauthenticated business requests return HTTP 401, and a freshly issued login token returns HTTP 200 from `/api/v1/auth/profile` through both `8080` and the `5173` frontend proxy.
- Did not call a valid-token timeline confirm/return POST for project 9 because those endpoints mutate business state. Verified the exact confirm endpoint without token returns HTTP 401 before business logic.
