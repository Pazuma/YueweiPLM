# PLM Process Route Dialog Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the project workbench process-route operation editor less crowded, keep headers aligned during horizontal scrolling, and auto-fill the default product operation code when enough route data exists.

**Architecture:** This is a focused frontend change in the existing Vue 3 component. The operation editor remains a CSS grid and uses the existing payload fields: `businessOperationCode`, `businessOperationCodeManualFlag`, `operationCraftCode`, `materialStatusCode`, and `finishedProductFlag`.

**Tech Stack:** Vue 3, Element Plus, Vitest, vue-tsc.

## Global Constraints

- Do not create new PLM root objects.
- Keep Product and Process as the involved business objects.
- Keep manual product-operation-code overrides.
- Do not change backend API contracts.
- Do not save a fabricated final business code before material/status is known.

---

### Task 1: Add A Regression Test For Header Alignment And Default Code Visibility

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\project-m4-panels.spec.ts`

**Interfaces:**
- Consumes: existing `ProjectProcessRoutePanel` dialog.
- Produces: test coverage for the scroll container and auto-generated code.

- [ ] **Step 1: Write the failing test**

Add assertions to the operation-detail dialog test:

```ts
const editor = document.body.querySelector('[data-test="operation-editor"]')
expect(editor?.querySelector('[data-test="operation-editor-columns"]')).not.toBeNull()
expect(editor?.querySelectorAll('.operation-row')).toHaveLength(1)
```

Add a new test for generating the default code after selecting material/status when the template does not carry a material/status value:

```ts
const materialSelect = document.body.querySelector('[data-test="material-status-select"] .el-select') as HTMLElement
await materialSelect.click()
await flushPromises()
const option = [...document.body.querySelectorAll('.el-select-dropdown__item')]
  .find((item) => item.textContent?.includes('TPU / 10')) as HTMLElement
await option.click()
await flushPromises()
expect((document.body.querySelector('[data-test="business-operation-code-input"] input') as HTMLInputElement).value).toBe('NBA1010')
```

- [ ] **Step 2: Run test to verify it fails**

Run from `D:\Yuewei\git\YUEWEI\plm-web`:

```powershell
$env:PATH = "D:\Yuewei\program\node-v20.15.0-win-x64;$env:PATH"
.\node_modules\.bin\vitest.cmd run src\views\project\__tests__\project-m4-panels.spec.ts
```

Expected: at least the header-in-editor assertion fails before markup is changed.

### Task 2: Move Header Into The Scroll Container And Compress The Editor

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\ProjectProcessRoutePanel.vue`

**Interfaces:**
- Consumes: existing `.operation-editor__columns` and `.operation-row` grid.
- Produces: aligned sticky header and smaller operation row controls.

- [ ] **Step 1: Move `operation-editor__columns` inside `.operation-editor`**

Wrap the header and rows in the same scroll container:

```vue
<div class="operation-editor" data-test="operation-editor">
  <div class="operation-editor__columns" data-test="operation-editor-columns" aria-hidden="true">
    ...
  </div>
  <div v-for="(operation, index) in routeForm.operations" :key="index" class="operation-row">
    ...
  </div>
</div>
```

- [ ] **Step 2: Reduce dense editor typography and spacing**

Update scoped CSS:

```css
.operation-editor {
  max-height: 42vh;
  overflow: auto;
  font-size: 12px;
}
.operation-editor :deep(.el-input__wrapper),
.operation-editor :deep(.el-input-number__decrease),
.operation-editor :deep(.el-input-number__increase),
.operation-editor :deep(.el-select__wrapper) {
  min-height: 30px;
}
.operation-editor :deep(.el-input__inner),
.operation-editor :deep(.el-select__placeholder),
.operation-editor :deep(.el-select__selected-item),
.operation-editor :deep(.el-checkbox__label) {
  font-size: 12px;
}
```

### Task 3: Ensure Default Code Refreshes From Existing Craft And Material Data

**Files:**
- Modify: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\components\ProjectProcessRoutePanel.vue`

**Interfaces:**
- Consumes: `refreshBusinessOperationCode(operation)`.
- Produces: generated default code in the input when operation craft code and material/status are present.

- [ ] **Step 1: Keep existing generation rules**

Do not change `previewBusinessOperationCode`; it still returns the existing value until material/status is known.

- [ ] **Step 2: Keep refresh calls on template application and material/status change**

Verify existing calls remain:

```ts
routeForm.operations.forEach(refreshBusinessOperationCode)
function handleMaterialStatusChange(operation: ProcessOperationSavePayload) {
  refreshBusinessOperationCode(operation)
}
```

### Task 4: Verify

**Files:**
- Test: `D:\Yuewei\git\YUEWEI\plm-web\src\views\project\__tests__\project-m4-panels.spec.ts`

- [ ] **Step 1: Run focused Vitest**

```powershell
$env:PATH = "D:\Yuewei\program\node-v20.15.0-win-x64;$env:PATH"
.\node_modules\.bin\vitest.cmd run src\views\project\__tests__\project-m4-panels.spec.ts
```

Expected: all tests pass.

- [ ] **Step 2: Run TypeScript check**

```powershell
$env:PATH = "D:\Yuewei\program\node-v20.15.0-win-x64;$env:PATH"
.\node_modules\.bin\vue-tsc.cmd --noEmit
```

Expected: exit code 0.
