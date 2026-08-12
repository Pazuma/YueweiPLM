# DingTalk Model Variant Order Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现钉钉新型号审批模拟接入、PLM 第一节点单表完善、正式投产颜色选择、需求订单真实列表以及项目生命周期与订单状态同步。

**Architecture:** 复用 Product 作为项目根对象、Order 作为需求对象、现有投产颜色确认表作为来源颜色池。新增集成记录、项目颜色快照和订单扩展字段，通过后端事务服务完成幂等创建、首节点确认和生命周期同步；Vue 页面只消费真实 REST API。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis-Plus、PostgreSQL/Flyway、Vue 3、TypeScript、Element Plus、Vitest。

## Global Constraints

- 不新增七个核心对象之外的根对象。
- 不做钉钉正式联调，只提供可由连接器调用且可在前端模拟验证的 PLM 接入接口。
- Order 状态只使用 `draft → confirmed → in_production → completed → closed`。
- 所有新字段使用 snake_case，所有状态变化记录操作日志。
- 保留当前工作区既有修改，不重置、不覆盖无关文件。

---

### Task 1: Order Persistence And Query API

**Files:**
- Create: `src/main/resources/db/migration/V20260720_1000__dingtalk_model_variant_orders.sql`
- Create: `src/main/java/com/yuewei/plm/module/order/**`
- Test: `src/test/java/com/yuewei/plm/module/order/service/OrderServiceTest.java`

**Interfaces:**
- Produces: `OrderService.page(OrderQueryDTO)`、`OrderService.findByProjectId(Long)`、订单状态同步接口。

- [ ] 写失败测试，覆盖列表字段、关键字筛选、新产品型号显示空值和状态同步。
- [ ] 运行 `mvn -Dtest=OrderServiceTest test`，确认因模块缺失失败。
- [ ] 实现实体、Repository、DTO、VO、Service、Controller 和迁移。
- [ ] 再次运行目标测试，确认通过。

### Task 2: DingTalk Intake And First-Step Completion

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/integration/dingtalk/**`
- Create: `src/main/java/com/yuewei/plm/module/project/variant/**`
- Test: `src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkModelVariantServiceTest.java`

**Interfaces:**
- Produces: `POST /api/v1/integrations/dingtalk/model-variant-approvals`、`GET/PUT/POST /api/v1/projects/{projectId}/requirement-form...`。

- [ ] 写失败测试，覆盖审批幂等、正式投产颜色默认全选、非法颜色拒绝、草稿可空和确认至少一个颜色。
- [ ] 运行目标测试确认按预期失败。
- [ ] 实现接入、颜色快照、字段完善、字段审计和订单生成事务。
- [ ] 运行目标测试确认通过。

### Task 3: Project And Order Lifecycle Synchronization

**Files:**
- Modify: `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/order/service/ProjectOrderLifecycleSyncTest.java`

**Interfaces:**
- Consumes: Task 1 的订单同步服务。
- Produces: 项目推进、发布、放弃时的同事务订单状态更新。

- [ ] 写失败测试，覆盖推进为进行中、发布为已完成、放弃为已关闭及关闭信息。
- [ ] 运行目标测试确认失败原因是同步能力缺失。
- [ ] 在现有事务边界内调用订单同步服务并写日志。
- [ ] 运行目标测试和相关项目生命周期测试确认通过。

### Task 4: Vue Real API And Direct Frontend Verification

**Files:**
- Create: `../plm-web/src/api/modules/order.ts`
- Modify: `../plm-web/src/views/order/OrderCenterView.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
- Test: `../plm-web/src/views/order/__tests__/OrderCenterView.spec.ts`
- Test: `../plm-web/src/views/project/__tests__/ModelVariantRequirementForm.spec.ts`

**Interfaces:**
- Consumes: Task 1、2 的订单和第一节点接口。

- [ ] 写失败组件测试，覆盖真实 API、列顺序、型号 `--`、单表字段、颜色默认全选和提交校验。
- [ ] 运行 `npm run test:run -- <test files>` 确认失败。
- [ ] 实现 API 映射、订单列表和第一节点完善表。
- [ ] 运行目标组件测试、`npm run type-check` 和 `npm run build`。
- [ ] 启动前后端，用浏览器直接创建模拟钉钉项目、完善首节点、查看订单并验证放弃同步。

### Task 5: Documentation And Final Verification

**Files:**
- Create: `docs/backend-notes/2026-07-20-钉钉新型号开模审批接入PLM代码实现沉淀.md`
- Copy: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-20-钉钉新型号开模审批接入PLM代码实现沉淀.md`

- [ ] 记录全部修改文档和代码文件。
- [ ] 逐步记录前端直接测试方式、输入、预期结果和合格标准。
- [ ] 运行后端目标测试及相关回归测试。
- [ ] 运行前端目标测试、类型检查和构建。
- [ ] 复核文档、代码、数据库迁移和页面行为一致。
