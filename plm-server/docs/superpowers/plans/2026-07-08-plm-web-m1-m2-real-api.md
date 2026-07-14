# PLM Web M1/M2 Real API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the frontend mock login and project timeline query path with the backend M1/M2 real REST APIs while leaving M3 timeline actions out of this pass.

**Architecture:** Keep the change in the frontend API boundary first, then connect only the dashboard and project center surfaces that already show M1/M2 data. Backend `Project` is a view over `Product`; the frontend must not introduce a new root business object and must adapt missing display fields with explicit frontend defaults.

**Tech Stack:** Vue 3, TypeScript, Pinia, Axios, Element Plus, Spring Boot REST API `/api/v1/...`.

## Global Constraints

- Default communication and documentation language: Chinese.
- PLM core objects remain Customer, Product, Order, ProductionOrder, Process, Inventory, Workstation.
- M2 `projectId` equals backend `productId`; no new Project root object is created.
- Only M1 and M2 real interfaces are connected in this task.
- M3 confirm/advance/return APIs are not connected because the user said M3 has not been tested.
- Response shape is backend `ResponseVO<T>`: `{ code, message, data, requestId, timestamp }`.
- Failed backend calls must surface an error message; no empty object should be used to hide errors.
- Documentation must list changed code, changed documents, frontend test steps, and pass criteria.

---

### Task 1: Add Typed Response Helpers

**Files:**
- Modify: `../plm-web/src/api/request.ts`

**Interfaces:**
- Produces: `unwrapResponse<T>(response: AxiosResponse<ApiResponse<T>>): T`
- Produces: `unwrapPage<T>(response: AxiosResponse<ApiResponse<PageResponse<T>>>): PageResponse<T>`
- Produces: `ApiResponse<T>` and `PageResponse<T>` interfaces used by API modules.

- [ ] **Step 1: Add response types and unwrap helpers**

Add these exports without changing the existing Axios interceptor return behavior, so legacy mock modules continue to work:

```ts
import type { AxiosResponse } from 'axios'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId?: string
  timestamp?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function unwrapResponse<T>(response: AxiosResponse<ApiResponse<T>>): T {
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '接口请求失败')
  }
  return body.data
}

export function unwrapPage<T>(response: AxiosResponse<ApiResponse<PageResponse<T>>>): PageResponse<T> {
  return unwrapResponse(response)
}
```

- [ ] **Step 2: Verify types**

Run from `../plm-web`:

```powershell
npm run type-check
```

Expected at this point: it may still fail because API modules are not yet adapted, but `request.ts` itself must not introduce syntax errors.

### Task 2: Connect M1 Auth APIs

**Files:**
- Modify: `../plm-web/src/api/modules/auth.ts`
- Modify: `../plm-web/src/stores/user.ts`
- Modify: `../plm-web/src/views/login/LoginView.vue`

**Interfaces:**
- Consumes: `unwrapResponse<T>()` from Task 1.
- Produces: `login(payload): Promise<LoginResponse>`
- Produces: `getProfile(): Promise<ProfileResponse>`
- Produces: `getProfileByToken(token): Promise<{ profile: UserProfile; permissions: string[] }>`
- Produces: `logout(): Promise<void>`

- [ ] **Step 1: Replace mock login with real API call**

Use `request.post('/auth/login', payload)` and map backend `data.user` to the existing `UserProfile` shape. Use backend demo accounts `engineer01 / plm123456` and `engineer02 / plm123456` in the login page tips.

- [ ] **Step 2: Replace profile restore with real `/auth/profile`**

`getProfileByToken()` should call `getProfile()` because the request interceptor already reads `localStorage.plm_token`.

- [ ] **Step 3: Call backend logout**

Pinia `logout()` should call the API when a token exists, then clear local state in `finally` so a failed network call does not keep the browser stuck logged in.

- [ ] **Step 4: Type-check**

Run:

```powershell
npm run type-check
```

Expected: auth/store/login changes compile, or any remaining failure is unrelated and recorded.

### Task 3: Connect M2 Project Query APIs

**Files:**
- Modify: `../plm-web/src/api/modules/project.ts`

**Interfaces:**
- Consumes: `unwrapPage<T>()` and `unwrapResponse<T>()`.
- Produces: `getWorkbenchInProgressProjects(params?): Promise<ProjectSummaryVO[]>`
- Produces: `getProjects(params?): Promise<ProjectSummaryVO[]>`
- Produces: `getProjectDetail(projectId): Promise<ProjectDetailVO>`
- Produces: `getProjectSummary(projectId): Promise<ProjectSummaryVO>`
- Produces: `getProjectTimeline(projectId): Promise<TimelineDetailVO>`

- [ ] **Step 1: Replace project mock data with backend calls**

Use these paths:

```text
GET /workbench/projects/in-progress
GET /projects
GET /projects/{projectId}
GET /projects/{projectId}/summary
GET /projects/{projectId}/timeline
```

- [ ] **Step 2: Add mapper functions**

Map backend fields:

```text
nodeCode -> nodeKey
nodeStatus -> status
currentNodeName -> currentStage
updatedAt -> updatedAt
documentCount -> documentCount
```

Fill frontend-only display fields with explicit neutral defaults such as `activeBomVersion: ''`, `completionRate` derived from `currentStepNo / 6`, and cost fields as `0`.

- [ ] **Step 3: Keep M3 action functions out of real API path**

Leave `confirmTimelineNode`, `advanceTimelineNode`, and `returnTimelineNode` as non-used mock placeholders or mark them as not connected. Do not wire them to backend M3 in this task.

- [ ] **Step 4: Type-check**

Run:

```powershell
npm run type-check
```

Expected: project API module compiles.

### Task 4: Wire Dashboard And Project Center To M1/M2

**Files:**
- Modify: `../plm-web/src/views/dashboard/DashboardView.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`

**Interfaces:**
- Consumes: `getWorkbenchInProgressProjects()`.
- Consumes: `getProjects()`.
- Consumes: `getProjectTimeline()`.

- [ ] **Step 1: Dashboard load real in-progress projects**

Replace the hardcoded `inProgressProducts` computed source with a `ref<DashboardProductItem[]>`, load it in `onMounted`, and keep existing task/risk/freeze sections as local demo data.

- [ ] **Step 2: Project center load real M2 list**

Replace `getProductList()` in `loadData()` with `getProjects()` from `project.ts`. Keep existing detail presentation fallback for sections not covered by M2.

- [ ] **Step 3: Project detail overlay should prefer M2 timeline**

When opening a project detail, call `getProjectTimeline(row.productId)` and map nodes into `detailPresentation.timeline` where possible. If the timeline call fails, keep the current fallback presentation but show the backend error through the existing request error handling.

- [ ] **Step 4: Type-check and build**

Run:

```powershell
npm run type-check
npm run build
```

Expected: both commands exit 0.

### Task 5: Frontend Direct Verification And Documentation

**Files:**
- Create: `docs/backend-notes/2026-07-08-PLM前端M1M2实际接口接入代码实现沉淀.md`

**Interfaces:**
- Produces: a Chinese implementation note with changed files, code logic, frontend test steps, pass criteria, and actual test results.

- [ ] **Step 1: Start or reuse backend**

Backend should run with security enabled:

```powershell
$env:APP_SECURITY_ENABLED="true"
mvn spring-boot:run
```

Pass criteria: `http://localhost:8080/api/v1/health` returns `code=0`.

- [ ] **Step 2: Start frontend**

Run from `../plm-web`:

```powershell
npm run dev
```

Pass criteria: Vite prints a local URL such as `http://localhost:5173/`.

- [ ] **Step 3: Frontend direct test**

Use browser:

```text
1. Open /login.
2. Login with engineer01 / plm123456.
3. Confirm dashboard loads and in-progress projects are populated from backend.
4. Open 项目管理.
5. Confirm project list shows backend Product-as-project rows.
6. Open a project detail.
7. Confirm current node and timeline match M2 data.
8. Refresh page and confirm profile restore keeps login state.
9. Logout and confirm returning to login clears token.
```

Pass criteria: no blank page, no console-blocking runtime error, requests hit `/api/v1/auth/*`, `/api/v1/workbench/projects/in-progress`, and `/api/v1/projects*`.

- [ ] **Step 4: Write沉淀文档**

Document:

```text
1. 本次目标
2. 引用文档
3. 修改的代码文件
4. 修改的文档文件
5. 代码逻辑
6. 作用功能
7. 前端直接测试步骤
8. 每一步合格标准
9. 实际验证结果
10. 后续维护建议
11. M3 未接入说明
```

- [ ] **Step 5: Final verification**

Run:

```powershell
npm run type-check
npm run build
```

Expected: both commands exit 0, or any failure is recorded with exact output and cause.

## Self-Review

- Spec coverage: M1 auth/profile/logout and M2 project/workbench/timeline are covered by Tasks 2-4.
- M3 exclusion: explicitly covered by Global Constraints and Task 3.
- Documentation: covered by Task 5.
- Frontend direct testing: covered by Task 5.
- No backend object expansion: project remains Product-as-project through M2 API mapping.
