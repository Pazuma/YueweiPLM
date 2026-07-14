# PLM Web Project Detail Attachment Panel Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent project detail dialogs from creating invalid timeline attachment requests while fixing the Element Plus collapse model type warning.

**Architecture:** Keep the fix inside `ProjectCenterView.vue`. Add a focused Vitest regression around `ProjectCenterView` with child panels stubbed so the test observes attachment panel mount/props without making real API calls.

**Tech Stack:** Vue 3, Element Plus, Vitest, @vue/test-utils.

## Global Constraints

- Do not change backend APIs.
- Keep M3 action buttons in the current-node area.
- Use the x64 Node executable when running local verification on this machine.

---

### Task 1: Attachment Panel Mount Guard Regression

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\project-m4-panels.spec.ts`
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\ProjectCenterView.vue`

**Interfaces:**
- Consumes: `ProjectCenterView.vue` renders `TimelineAttachmentPanel` with `projectId` and `nodeKey` props.
- Produces: Attachment panel mounts only when the user opens product `materials` or SKU `production_docs`.

- [ ] **Step 1: Write the failing test**

Add a test that mounts `ProjectCenterView`, opens a product detail row, and asserts no `TimelineAttachmentPanel` is mounted while the active detail section is `current_node`.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
& "D:\Yuewei\program\node-v20.15.0-win-x64\node.exe" .\node_modules\vitest\vitest.mjs run src\views\project\__tests__\project-m4-panels.spec.ts
```

Expected before implementation: the new test fails because the hidden attachment panel is still mounted.

- [ ] **Step 3: Implement minimal code**

Change hidden attachment sections from always-mounted `v-show` usage to type-and-tab-gated `v-if` usage. Clear `detailPresentation` and `detailBomVersion` when opening a new detail to remove stale timeline state during async loading. Change `skuFlowTableExpanded` from `ref(false)` to `ref<string[]>([])`.

- [ ] **Step 4: Run tests and type check**

Run:

```powershell
& "D:\Yuewei\program\node-v20.15.0-win-x64\node.exe" .\node_modules\vitest\vitest.mjs run src\views\project\__tests__\project-m4-panels.spec.ts
& "D:\Yuewei\program\node-v20.15.0-win-x64\node.exe" .\node_modules\vue-tsc\bin\vue-tsc.js --noEmit
```

Expected after implementation: tests pass and type check passes.
