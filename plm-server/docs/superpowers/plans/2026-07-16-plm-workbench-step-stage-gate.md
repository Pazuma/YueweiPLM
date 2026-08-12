# PLM Workbench Step Stage Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the confirmed PLM workbench flow where small steps are confirmed one by one, stage document gates are checked only when entering the next stage, uploads bind to a selectable step in the current stage, and File Center shows readable project/stage/step fields.

**Architecture:** Keep Product as the lifecycle object and `plm_attachment` as the file metadata object. Extend timeline constants into flattened small-step definitions with stage metadata, then reuse the same REST paths so current frontend routing stays stable.

**Tech Stack:** Java Spring Boot, MyBatis Plus, Vue 3, Element Plus, Vitest.

## Global Constraints

- Use only the existing seven core PLM objects; do not create FileVersion, QualityDocument, SKU, BOMLine, or other root objects.
- API paths remain under `/api/v1`.
- Attachment metadata remains in `plm_attachment` with `owner_object_type=Product` and `owner_object_id=product_id`.
- Normal workbench confirmation is not force advance; force advance remains out of scope.
- Testing acceptance is frontend overall testing, not Apifox.

---

### Task 1: Backend Timeline Definition

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/TimelineDefinitionProvider.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineDetailVO.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/TimelineDefinitionProviderTest.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`

**Interfaces:**
- Produces `TimelineNodeDefinition(stepNo, nodeCode, nodeName, stageCode, stageName, phaseName, requiredFileCategory)`.
- Produces provider helpers `getDefinitionByCode`, `getDefinitionByStepNo`, `isLastStepOfStage`, `getStageStepCodes`, `getRequiredDefinitionsForStage`.

- [ ] Write failing tests expecting product line to have 22 small steps and model variant to have 16 small steps.
- [ ] Run the provider tests and confirm they fail because only six stage nodes exist.
- [ ] Extend constants/provider with flattened small-step definitions and stage helpers.
- [ ] Run provider and timeline service tests until they pass.

### Task 2: Backend Confirm Gate

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`

**Interfaces:**
- `confirm(projectId, nodeKey, dto, request)` confirms the current small step and moves to the next small step automatically.
- When the next step belongs to another stage, `confirm` checks required attachments for the current stage.

- [ ] Write failing tests for step 1 confirming into step 2.
- [ ] Write failing test for step 2 blocked when required stage document is missing.
- [ ] Add `AttachmentRepository` dependency and stage document gate logic.
- [ ] Preserve `advance` as compatibility but normal frontend will stop calling it.
- [ ] Run timeline action tests until they pass.

### Task 3: Backend Attachment And File Center

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/attachment/vo/AttachmentVO.java`
- Test: `src/test/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImplTest.java`

**Interfaces:**
- Upload endpoint remains `POST /projects/{projectId}/timeline/{nodeKey}/attachments`, but `nodeKey` is now the selected small step code.
- File Center rows include `projectId`, `projectCode`, `projectName`, `timelineStageCode`, `timelineStageName`, `timelineStepCode`, `timelineStepName`.

- [ ] Write failing test that upload rejects a step outside the current stage.
- [ ] Write failing test that File Center rows are enriched with project/stage/step fields.
- [ ] Implement current-stage validation for uploads.
- [ ] Implement File Center enrichment by batch loading Products and mapping timeline definitions.
- [ ] Run attachment service tests until they pass.

### Task 4: Frontend Workbench And File Center

**Files:**
- Modify: `../plm-web/src/api/modules/project.ts`
- Modify: `../plm-web/src/api/modules/attachment.ts`
- Modify: `../plm-web/src/views/dashboard/DashboardView.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
- Modify: `../plm-web/src/views/project/components/TimelineAttachmentPanel.vue`
- Modify: `../plm-web/src/views/file/FileCenterView.vue`

**Interfaces:**
- Frontend timeline nodes expose stage metadata from backend.
- Upload dialogs select only steps in the current stage.
- Normal confirm button calls `confirmTimelineNode` and refreshes timeline; it does not call `advanceTimelineNode`.
- File Center displays project code/name/stage/step.

- [ ] Update API types for stage and step fields.
- [ ] Update normal confirm handlers to refresh after confirm without separate advance.
- [ ] Add current-stage step options to dashboard and attachment panel upload.
- [ ] Add File Center columns for project code, project name, stage and step.
- [ ] Run targeted frontend tests or type/build check where available.

### Task 5: Verification And Sediment Document

**Files:**
- Create: `docs/backend-notes/2026-07-16-PLM工作台大节点资料门禁小步骤推进代码实现沉淀.md`
- Copy to: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-16-PLM工作台大节点资料门禁小步骤推进代码实现沉淀.md`

- [ ] Run backend targeted Maven tests.
- [ ] Run frontend available test/build command.
- [ ] Write implementation sediment with modified files, code logic, frontend testing steps, passing criteria, and maintenance advice.
- [ ] Copy sediment document to the external PLM docs folder.
