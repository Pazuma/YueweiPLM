# PLM Frontend Runtime Mock Data Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove runtime frontend mock business data while keeping test fixtures available for automated tests.

**Architecture:** Add one shared `notConnected` API helper, then convert runtime mock-backed API modules to reject with an explicit unconnected-state error instead of returning fake data. Update visible pages and shared selectors to show empty or unconnected states instead of fake rows.

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, Element Plus.

## Global Constraints

- Frontend only: do not modify backend Java, database, migrations, or backend APIs.
- Keep test data: do not delete `src/mock/*`; runtime code must stop importing it.
- Keep existing backend-connected frontend APIs unchanged, especially project, attachment, project BOM, and project process routes.
- Non-test files must not import `@/mock/*` or call `mockResolve` for business data after the cleanup.
- If a backend API is not implemented, the frontend must show an explicit unconnected or empty state, not fabricated business data.

---

### Task 1: Regression Test For Runtime Mock Cleanup

**Files:**
- Create: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\__tests__\no-runtime-mock-data.spec.ts`

**Interfaces:**
- Consumes: runtime API exports from `dashboard`, `bom`, `process`, `foundation`, `supplier`, `approval`, `customer`, `system`, and `product`.
- Produces: failing tests proving these APIs must reject with a clear unconnected-state error.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'

import { getApprovalTasks, getApprovalTemplateOptions, getApprovalTemplates } from '@/api/modules/approval'
import { getBomCenterSnapshot } from '@/api/modules/bom'
import { getCustomerDetail, getCustomerList } from '@/api/modules/customer'
import { getDashboardSnapshot } from '@/api/modules/dashboard'
import {
  getBomCenterRows,
  getFileSections,
  getFoundationProducts,
  getInventoryCenterSnapshot,
  getProductPresentation,
  getReportCenterSnapshot,
  getTestCenterSnapshot
} from '@/api/modules/foundation'
import { getProductDetail, getProductList } from '@/api/modules/product'
import { getProcessCenterSnapshot } from '@/api/modules/process'
import { getSupplierCenterSnapshot } from '@/api/modules/supplier'
import { getSystemPermissionGroups, getSystemRoles, getSystemUsers } from '@/api/modules/system'

const runtimeMockBackedCalls = [
  ['dashboard snapshot', () => getDashboardSnapshot()],
  ['bom center snapshot', () => getBomCenterSnapshot()],
  ['process center snapshot', () => getProcessCenterSnapshot()],
  ['foundation products', () => getFoundationProducts()],
  ['file sections', () => getFileSections()],
  ['test center snapshot', () => getTestCenterSnapshot()],
  ['inventory center snapshot', () => getInventoryCenterSnapshot()],
  ['bom center rows', () => getBomCenterRows()],
  ['product presentation', () => getProductPresentation(101)],
  ['report center snapshot', () => getReportCenterSnapshot()],
  ['supplier center snapshot', () => getSupplierCenterSnapshot()],
  ['approval tasks', () => getApprovalTasks()],
  ['approval templates', () => getApprovalTemplates()],
  ['approval template options', () => getApprovalTemplateOptions()],
  ['customer list', () => getCustomerList()],
  ['customer detail', () => getCustomerDetail(101)],
  ['system users', () => getSystemUsers()],
  ['system roles', () => getSystemRoles()],
  ['system permission groups', () => getSystemPermissionGroups()],
  ['product list', () => getProductList()],
  ['product detail', () => getProductDetail(101)]
] as const

describe('runtime mock data cleanup', () => {
  it.each(runtimeMockBackedCalls)('%s does not return frontend mock data at runtime', async (_name, callApi) => {
    await expect(callApi()).rejects.toThrow('整体测试阶段不展示前端假数据')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/api/modules/__tests__/no-runtime-mock-data.spec.ts`

Expected: FAIL because current API modules still resolve mock data.

### Task 2: Shared Unconnected API Helper And API Module Cleanup

**Files:**
- Create: `D:\Yuewei\git\YUEWEI\plm-web\src\api\notConnected.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\dashboard.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\bom.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\process.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\foundation.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\supplier.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\approval.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\customer.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\system.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\api\modules\product.ts`

**Interfaces:**
- Produces: `FrontendFeatureNotConnectedError` and `notConnected<T>(featureName: string): Promise<T>`.
- Produces: runtime APIs that reject with an explicit business message.

- [ ] **Step 1: Add helper**

```ts
export class FrontendFeatureNotConnectedError extends Error {
  constructor(featureName: string) {
    super(`${featureName}接口未接入，整体测试阶段不展示前端假数据`)
    this.name = 'FrontendFeatureNotConnectedError'
  }
}

export function notConnected<T>(featureName: string): Promise<T> {
  return Promise.reject(new FrontendFeatureNotConnectedError(featureName))
}
```

- [ ] **Step 2: Replace each runtime mock-backed API export**

Use `notConnected<T>('模块名称')` for mock-only APIs. Keep real `request.get/post/put/delete` exports in `bom.ts` and `process.ts` unchanged.

- [ ] **Step 3: Run targeted test**

Run: `npm run test:run -- src/api/modules/__tests__/no-runtime-mock-data.spec.ts`

Expected: PASS.

### Task 3: Runtime Page And Component Empty States

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\dashboard\DashboardView.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\product\ProductList.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\module\ModulePlaceholderView.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\components\UserSelector\index.vue`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\system\SystemFieldManagementView.vue`

**Interfaces:**
- Consumes: `notConnected` errors from API modules.
- Produces: visible empty/unconnected state instead of fake rows or fake-data labels.

- [ ] **Step 1: Replace dashboard hardcoded lists with empty runtime arrays**

Set pending tasks, overdue risks, and pending freeze items to empty computed arrays or refs. Keep backend-connected in-progress projects.

- [ ] **Step 2: Replace hardcoded create-project option lists with empty arrays**

Keep create project dialog structure, but make unavailable backend-backed selects empty until backend APIs exist.

- [ ] **Step 3: Remove fake-data labels**

Replace visible labels `前端假数据演示` and `仅前端假数据` with `接口未接入` or `后端数据`.

- [ ] **Step 4: Stop component/page direct `@/mock` imports**

Change `UserSelector` to show no options by default, and change system field management to an explicit unconnected empty state.

### Task 4: Implementation Sediment Document

**Files:**
- Create: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-17-PLM前端假数据清理只保留测试数据代码实现沉淀.md`
- Create or update copy: `D:\Yuewei\git\YUEWEI\plm-server\docs\backend-notes\2026-07-17-PLM前端假数据清理只保留测试数据代码实现沉淀.md`

**Interfaces:**
- Produces: reviewed handoff document listing changed files, code logic, test method, acceptance criteria, and maintenance notes.

- [ ] **Step 1: Write sediment document**

Include changed frontend files, exact behavior change, why backend was untouched, test steps, and remaining backend integration follow-up.

### Task 5: Verification

**Files:**
- No code changes.

**Interfaces:**
- Produces: command evidence for final response.

- [ ] **Step 1: Static scan**

Run: `rg -n "from ['\"]@/mock|mockResolve\(|前端假数据|仅前端假数据" src`

Expected: no non-test runtime files match.

- [ ] **Step 2: Targeted test**

Run: `npm run test:run -- src/api/modules/__tests__/no-runtime-mock-data.spec.ts`

Expected: test passes.

- [ ] **Step 3: Type check**

Run: `npm run type-check`

Expected: exit code 0.
