# Workbench Timeline Node Forms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先跑通 PLM 工作台时间轴，不正式接入钉钉：用户点击时间轴节点后用统一弹窗填写节点表单或补充资料，首节点“完善项目信息”确认后创建需求订单并直接进入下一步骤。

**Architecture:** 后端继续以 Product 承载项目和新型号线，以 Order 承载需求订单，以固定节点表单服务承载节点业务结果；本轮不引入可配置审批表，也不新增 PhoneModel/SKU/BOM 等根对象。前端将 ProjectCenterView 中分散的节点按钮、首节点表单和附件面板收敛为“点击节点 -> TimelineNodeDialog -> 节点表单/资料补充/确认”的统一交互。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis-Plus、PostgreSQL/Flyway、Vue 3、TypeScript、Element Plus、Vitest、Maven。

## Global Constraints

- 本计划文档阶段不修改业务代码；执行本计划时再进入代码变更。
- 本阶段只跑通 PLM 工作台时间轴；正式钉钉联调、签名、回调重试和线上密钥配置延后。
- 钉钉/OA 传入字段中新增并保留“钉钉审批单号”，页面只读；其他传入字段允许 PLM 用户修正，必须记录修改前值、修改后值、操作人和操作时间。
- 首节点表单字段以一张“新型号项目信息完善表”呈现，不向用户暴露后端 Product、Order、Attachment 拆分。
- 新型号生产颜色来自来源产品“正式确认批量生产颜色”，默认全部选中，允许取消部分颜色；确认进入下一步时至少保留一个颜色。
- 附件不再作为节点确认的强制门禁；所有节点只展示附件数量，例如 `0 个附件`、`2 个附件`。
- 当前节点可以编辑表单、保存草稿、确认并进入下一步；已完成节点只能查看历史业务表单和补充附件；未来节点只显示说明，不允许编辑业务字段或上传附件。
- 普通用户不再单独点击“推进下一步”；“确认当前节点”后后端先校验节点表单和业务结果，校验通过后直接推进到下一步骤。
- 订单状态机只使用 `draft -> confirmed -> in_production -> completed -> closed`；首节点完成创建需求订单后订单保持 `confirmed`，不要因为时间轴从第 1 步进入第 2 步而立即误改成 `in_production`。
- 新型号线第 2 步页面文案使用“建立型号颜色版本”，避免出现“Product 子版本建立”“待生成 Product 子版本数量”等技术口径。
- 保留现有用户改动，不重置、不覆盖无关文件；涉及 `../plm-web` 时先确认工作区差异再改。

---

## File Structure

### Backend

- Modify: `src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java`
  - 负责固定时间轴节点定义；把新型号线第 2 步文案改成“建立型号颜色版本”。
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`
  - 增加节点弹窗所需元数据：`formKey`、`formTitle`、`canOpenForm`、`canEditForm`、`canUploadAttachment`、`attachmentCountLabel`。
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
  - 在查询时间轴时返回节点状态、表单类型、附件数量、上传权限；只统计附件数量，不表达“必传”。
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
  - 移除附件阶段门禁；保留业务门禁；确认节点后直接推进下一步骤；收敛订单状态同步规则。
- Create: `src/main/java/com/yuewei/plm/module/project/variant/service/RequirementFormService.java`
  - 定义首节点表单读取、草稿保存、确认并推进接口。
- Create: `src/main/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImpl.java`
  - 从 `DingTalkModelVariantService` 中迁出首节点表单业务，负责字段变更审计、颜色选择保存、订单创建、时间轴确认推进。
- Modify: `src/main/java/com/yuewei/plm/module/project/variant/controller/RequirementFormController.java`
  - 注入 `RequirementFormService`，不再直接依赖钉钉接入服务。
- Modify: `src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkModelVariantService.java`
  - 保留模拟接入/创建项目/创建初始表单/颜色快照职责；移除 PLM 首节点表单维护职责。
- Modify: `src/main/java/com/yuewei/plm/module/order/service/ProjectOrderLifecycleSync.java`
  - 保持订单同步入口；由时间轴服务控制何时调用。
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
  - 增加 `REQUIREMENT_FORM_FIELD_CHANGE` 和 `REQUIREMENT_FORM_CONFIRM` 动作常量。

### Frontend

- Modify: `../plm-web/src/api/modules/project.ts`
  - 对齐后端时间轴节点弹窗元数据；保留 `advanceTimelineNode` 兼容旧接口，但页面不再使用普通推进按钮。
- Modify: `../plm-web/src/api/modules/order.ts`
  - 复用首节点表单接口类型；字段名保持 `dingTalkApprovalNo`、`requirementType`、`customerRequirement`、`selectedVariantColorIds`。
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
  - 节点点击打开统一弹窗；移除详情页内直接常驻的首节点表单；移除普通“推进下一节点”按钮。
- Create: `../plm-web/src/views/project/components/TimelineNodeDialog.vue`
  - 统一承载节点说明、节点表单、附件补充和通用确认动作。
- Modify: `../plm-web/src/views/project/components/ModelVariantRequirementForm.vue`
  - 增加只读模式、表单校验、颜色业务摘要、确认后事件；确认按钮文案为“确认并进入下一步”。
- Modify: `../plm-web/src/views/project/components/TimelineAttachmentPanel.vue`
  - 增加 `allowUpload`、`title`、`description` props；未来节点隐藏上传区域；当前/已完成节点允许补充资料。
- Modify: `../plm-web/src/views/project/__tests__/project-m4-panels.spec.ts`
  - 覆盖节点点击弹窗、确认即推进、附件数量展示、未来节点禁止上传。
- Modify: `../plm-web/src/views/project/__tests__/ModelVariantRequirementForm.spec.ts`
  - 覆盖首节点表单只读审批号、颜色默认全选、客户端校验、确认事件。
- Create: `../plm-web/src/views/project/__tests__/TimelineNodeDialog.spec.ts`
  - 覆盖统一弹窗权限矩阵。

### Documentation after implementation

- Create: `docs/backend-notes/2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md`
  - 记录修改了哪些代码和文档、每一步前端直测方式、合格标准。
- Copy after implementation: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md`
  - 该外部路径需要在实际执行时按权限复制；本计划阶段不写外部目录。

---

### Task 1: Backend Timeline Node Metadata And No-Attachment Gate

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`

**Interfaces:**
- Produces: `TimelineNodeVO.formKey:String`
- Produces: `TimelineNodeVO.formTitle:String`
- Produces: `TimelineNodeVO.canOpenForm:Boolean`
- Produces: `TimelineNodeVO.canEditForm:Boolean`
- Produces: `TimelineNodeVO.canUploadAttachment:Boolean`
- Produces: `TimelineNodeVO.attachmentCountLabel:String`
- Produces behavior: `TimelineActionService.confirm(projectId, nodeKey, dto, request)` no longer blocks because a stage has zero attachments.

- [ ] **Step 1: Write failing backend tests**

In `TimelineActionServiceImplTest`, replace the old attachment gate test with these tests:

```java
@Test
void confirmStageCrossingDoesNotRequireAttachments() {
    Product product = product(101L, 2);
    when(productRepository.selectById(101L)).thenReturn(product);
    when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(502L);

    var result = service.confirm(
        101L,
        "PRODUCT_LINE_INIT_APPROVE",
        TimelineActionDTO.builder().remark("go design").build(),
        request
    );

    assertThat(result.getCurrentStepNo()).isEqualTo(3);
    assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_DRAWING");
    assertThat(product.getStatus()).isEqualTo(ProductStatusConstants.DEVELOPING);
    verify(productRepository).updateById(product);
    verifyNoInteractions(attachmentRepository);
}

@Test
void confirmStillRunsBusinessGateForProductionColors() {
    Product product = product(110L, 22);
    when(productRepository.selectById(110L)).thenReturn(product);
    when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(510L);

    service.confirm(110L, "PRODUCT_LINE_PRODUCTION_DECISION_STEP", new TimelineActionDTO(), request);

    verify(productionConfirmationService).requireColorsConfirmed(110L);
}
```

In `TimelineServiceImplTest`, add a metadata assertion:

```java
@Test
void timelineNodeReturnsDialogMetadataAndAttachmentCountLabel() {
    Product product = new Product();
    product.setProductId(11L);
    product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT);
    product.setCurrentStepNo(1);
    product.setDeletedFlag(0);
    when(productRepository.selectById(11L)).thenReturn(product);
    when(attachmentRepository.selectCount(Mockito.<Wrapper<Attachment>>any())).thenReturn(0L);

    var timeline = service.getTimeline(11L);
    var first = timeline.getNodes().get(0);
    var second = timeline.getNodes().get(1);

    assertThat(first.getNodeName()).isEqualTo("新型号需求确认");
    assertThat(second.getNodeName()).isEqualTo("建立型号颜色版本");
    assertThat(first.getFormKey()).isEqualTo("model_variant_requirement");
    assertThat(first.getCanOpenForm()).isTrue();
    assertThat(first.getCanEditForm()).isTrue();
    assertThat(first.getCanUploadAttachment()).isTrue();
    assertThat(first.getAttachmentCountLabel()).isEqualTo("0 个附件");
    assertThat(second.getCanOpenForm()).isTrue();
    assertThat(second.getCanEditForm()).isFalse();
    assertThat(second.getCanUploadAttachment()).isFalse();
}
```

- [ ] **Step 2: Run tests to verify they fail before implementation**

Run:

```powershell
mvn -Dtest=TimelineActionServiceImplTest,TimelineServiceImplTest test
```

Expected: FAIL. Failure reasons include missing `TimelineNodeVO` metadata getters and `confirmStageCrossingDoesNotRequireAttachments` still failing because attachment gate blocks stage crossing.

- [ ] **Step 3: Update fixed node names and VO fields**

In `TimelineNodeConstants.MODEL_VARIANT_NODES`, change step 2:

```java
step(2, "MODEL_VARIANT_PRODUCT_VERSION_CREATE", "建立型号颜色版本", "MODEL_VARIANT_EXTENSION_CONFIRM", "扩展确认", "扩展确认阶段", null)
```

In `TimelineNodeVO`, add fields:

```java
private String formKey;
private String formTitle;
private Boolean canOpenForm;
private Boolean canEditForm;
private Boolean canUploadAttachment;
private String attachmentCountLabel;
```

- [ ] **Step 4: Return node dialog metadata from TimelineServiceImpl**

Replace `toNodeVO` with this method body:

```java
private TimelineNodeVO toNodeVO(TimelineNodeDefinition definition, int currentStepNo, Long productId, Product product) {
    String nodeStatus = resolveNodeStatus(definition.stepNo(), currentStepNo);
    int documentCount = countDocuments(productId, definition.nodeCode());
    return TimelineNodeVO.builder()
        .stepNo(definition.stepNo())
        .nodeCode(definition.nodeCode())
        .nodeName(definition.nodeName())
        .stageCode(definition.stageCode())
        .stageName(definition.stageName())
        .phaseName(definition.phaseName())
        .requiredFileCategory(definition.requiredFileCategory())
        .nodeStatus(nodeStatus)
        .documentCount(documentCount)
        .attachmentCountLabel(documentCount + " 个附件")
        .formKey(resolveFormKey(definition.nodeCode()))
        .formTitle(resolveFormTitle(definition))
        .canOpenForm(true)
        .canEditForm(TimelineNodeConstants.NODE_STATUS_CURRENT.equals(nodeStatus))
        .canUploadAttachment(!TimelineNodeConstants.NODE_STATUS_PENDING.equals(nodeStatus))
        .confirmed(isCurrentNodeConfirmed(product, definition))
        .build();
}
```

Add helper methods in the same class:

```java
private String resolveFormKey(String nodeCode) {
    return switch (nodeCode) {
        case "MODEL_VARIANT_REQUIREMENT_CONFIRM" -> "model_variant_requirement";
        case "PRODUCT_LINE_PROCESS_CONFIRM" -> "production_operations";
        case "PRODUCT_LINE_PRODUCTION_DECISION_STEP" -> "production_colors";
        case "MODEL_VARIANT_RELEASE" -> "model_variant_release";
        default -> null;
    };
}

private String resolveFormTitle(TimelineNodeDefinition definition) {
    String formKey = resolveFormKey(definition.nodeCode());
    if ("model_variant_requirement".equals(formKey)) {
        return "新型号项目信息完善表";
    }
    if ("production_operations".equals(formKey)) {
        return "敲定投产工序";
    }
    if ("production_colors".equals(formKey)) {
        return "确认批量投产颜色";
    }
    if ("model_variant_release".equals(formKey)) {
        return "确认投产并建立型号颜色版本";
    }
    return definition.nodeName();
}
```

- [ ] **Step 5: Remove attachment gate from TimelineActionServiceImpl**

Remove these members from `TimelineActionServiceImpl`:

```java
private final AttachmentRepository attachmentRepository;
private void requireStageDocuments(Product product, TimelineNodeDefinition currentNode) { ... }
```

Remove this block from `confirm`:

```java
if (crossingStage) {
    requireStageDocuments(product, context.current());
}
```

Remove the unused imports:

```java
import com.yuewei.plm.module.attachment.constant.AttachmentOwnerTypeConstants;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import java.util.Set;
import java.util.stream.Collectors;
```

Keep this existing call untouched, because it validates real business result rather than attachments:

```java
requireBusinessGate(projectId, nodeKey);
```

- [ ] **Step 6: Update constructor usage in tests**

In `TimelineActionServiceImplTest#setUp`, construct the service without `attachmentRepository`:

```java
service = new TimelineActionServiceImpl(
    productRepository,
    new TimelineDefinitionProvider(),
    operationLogService,
    productionConfirmationService
);
```

Remove the `AttachmentRepository` field and helper methods from this test class after the new test no longer needs them.

- [ ] **Step 7: Run target backend tests**

Run:

```powershell
mvn -Dtest=TimelineActionServiceImplTest,TimelineServiceImplTest test
```

Expected: PASS. Evidence: Maven exits with code 0 and Surefire reports failures 0, errors 0.

- [ ] **Step 8: Commit Task 1**

Run:

```powershell
git add src/main/java/com/yuewei/plm/module/project/constant/TimelineNodeConstants.java src/main/java/com/yuewei/plm/module/project/vo/TimelineNodeVO.java src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java
git commit -m "feat: support timeline node dialog metadata"
```

---

### Task 2: Requirement Form Service And First-Node Confirm-And-Advance

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/project/variant/service/RequirementFormService.java`
- Create: `src/main/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/module/project/variant/controller/RequirementFormController.java`
- Modify: `src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkModelVariantService.java`
- Modify: `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java`
- Test: `src/test/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkModelVariantServiceTest.java`

**Interfaces:**
- Consumes: `TimelineActionService.confirm(Long projectId, String nodeKey, TimelineActionDTO dto, HttpServletRequest request)`
- Consumes: `OrderService.create(OrderCreateCommand command)`
- Produces: `RequirementFormService.getRequirementForm(Long projectId): RequirementFormVO`
- Produces: `RequirementFormService.saveRequirementForm(Long projectId, RequirementFormSaveDTO dto): RequirementFormVO`
- Produces: `RequirementFormService.confirmRequirementForm(Long projectId, RequirementFormSaveDTO dto, HttpServletRequest request): RequirementFormVO`

- [ ] **Step 1: Add service interface**

Create `RequirementFormService.java`:

```java
package com.yuewei.plm.module.project.variant.service;

import com.yuewei.plm.module.project.variant.dto.RequirementFormSaveDTO;
import com.yuewei.plm.module.project.variant.vo.RequirementFormVO;
import jakarta.servlet.http.HttpServletRequest;

public interface RequirementFormService {
    RequirementFormVO getRequirementForm(Long projectId);
    RequirementFormVO saveRequirementForm(Long projectId, RequirementFormSaveDTO dto);
    RequirementFormVO confirmRequirementForm(Long projectId, RequirementFormSaveDTO dto, HttpServletRequest request);
}
```

- [ ] **Step 2: Write failing service tests**

Create `RequirementFormServiceImplTest.java` with these tests:

```java
@Test
void saveDraftAllowsNoSelectedColorAndDoesNotCreateOrderOrAdvance() {
    Fixture fixture = fixture();
    fixture.givenCurrentRequirementProject();
    RequirementFormSaveDTO dto = dto("market_requirement", "", List.of());

    var result = fixture.service.saveRequirementForm(9L, dto);

    assertThat(result.getStatus()).isEqualTo("draft");
    verify(fixture.orderService, never()).create(any(OrderCreateCommand.class));
    verify(fixture.timelineActionService, never()).confirm(anyLong(), anyString(), any(), any());
}

@Test
void confirmCreatesOrderAndAdvancesTimeline() {
    Fixture fixture = fixture();
    fixture.givenCurrentRequirementProject();
    RequirementFormSaveDTO dto = dto("customer_requirement", "客户要求：首批黑色", List.of(1L));
    when(fixture.orderService.create(any(OrderCreateCommand.class))).thenReturn(order(700L));
    when(fixture.timelineActionService.confirm(eq(9L), eq("MODEL_VARIANT_REQUIREMENT_CONFIRM"), any(), same(fixture.request)))
        .thenReturn(TimelineActionResultVO.builder()
            .projectId(9L)
            .productId(9L)
            .action("confirm")
            .nodeKey("MODEL_VARIANT_REQUIREMENT_CONFIRM")
            .beforeStepNo(1)
            .currentStepNo(2)
            .currentNodeKey("MODEL_VARIANT_PRODUCT_VERSION_CREATE")
            .currentNodeName("建立型号颜色版本")
            .currentConfirmed(false)
            .productStatus("developing")
            .logId(100L)
            .build());

    var result = fixture.service.confirmRequirementForm(9L, dto, fixture.request);

    assertThat(result.getStatus()).isEqualTo("confirmed");
    verify(fixture.orderService).create(argThat(command ->
        command.getProjectId().equals(9L)
            && "DT-20260720-001".equals(command.getDingTalkApprovalNo())
            && "iPhone 18".equals(command.getPhoneModel())
            && "customer_requirement".equals(command.getOrderType())
    ));
    verify(fixture.timelineActionService).confirm(eq(9L), eq("MODEL_VARIANT_REQUIREMENT_CONFIRM"), any(), same(fixture.request));
}

@Test
void confirmRejectsWhenProjectAlreadyMovedPastRequirementNode() {
    Fixture fixture = fixture();
    fixture.givenMovedRequirementProject();
    RequirementFormSaveDTO dto = dto("market_requirement", "", List.of(1L));

    assertThatThrownBy(() -> fixture.service.confirmRequirementForm(9L, dto, fixture.request))
        .isInstanceOf(BusinessException.class)
        .extracting("code")
        .isEqualTo(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL);
    verify(fixture.orderService, never()).create(any(OrderCreateCommand.class));
    verify(fixture.timelineActionService, never()).confirm(anyLong(), anyString(), any(), any());
}
```

The fixture methods create:

```java
private RequirementFormSaveDTO dto(String requirementType, String customerRequirement, List<Long> colorIds) {
    RequirementFormSaveDTO dto = new RequirementFormSaveDTO();
    dto.setModel("iPhone 18");
    dto.setTipo("精孔磁吸壳");
    dto.setPriority("general");
    dto.setRequirementType(requirementType);
    dto.setCustomerRequirement(customerRequirement);
    dto.setSelectedVariantColorIds(colorIds);
    dto.setOperator("Engineer One");
    return dto;
}
```

- [ ] **Step 3: Run tests to verify they fail before implementation**

Run:

```powershell
mvn -Dtest=RequirementFormServiceImplTest,DingTalkModelVariantServiceTest test
```

Expected: FAIL. Failure reasons include missing `RequirementFormServiceImpl` and the controller still depending on `DingTalkModelVariantService`.

- [ ] **Step 4: Move PLM requirement-form logic into RequirementFormServiceImpl**

Create `RequirementFormServiceImpl` by moving these methods and dependencies out of `DingTalkModelVariantService`:

```java
public RequirementFormVO getRequirementForm(Long projectId)
public RequirementFormVO saveRequirementForm(Long projectId, RequirementFormSaveDTO dto)
public RequirementFormVO confirmRequirementForm(Long projectId, RequirementFormSaveDTO dto, HttpServletRequest request)
private RequirementFormVO save(Long projectId, RequirementFormSaveDTO dto, boolean confirm, HttpServletRequest request)
private RequirementFormVO toVO(Product p, RequirementForm f, List<ProductVariantColor> colors)
private Product requireProject(Long id)
private RequirementForm requireForm(Long id)
private List<ProductVariantColor> colors(Long id)
```

The `confirmRequirementForm` method must check the current node before mutating data:

```java
private void requireRequirementNode(Product product) {
    if (!"model_variant".equals(product.getProductType()) || !Integer.valueOf(1).equals(product.getCurrentStepNo())) {
        throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "只能在新型号需求确认节点确认项目信息");
    }
}
```

The confirm path must save business fields, create the order, then advance the timeline in the same transaction:

```java
OrderEntity order = orderService.create(OrderCreateCommand.builder()
    .projectId(projectId)
    .productId(projectId)
    .customerId(product.getCustomerId())
    .dingTalkApprovalNo(form.getDingTalkApprovalNo())
    .projectType(product.getProductType())
    .phoneModel(product.getModel())
    .productName(product.getProductName())
    .orderType(dto.getRequirementType())
    .orderTitle(product.getProductName())
    .customerRequirement(dto.getCustomerRequirement())
    .priorityLevel(dto.getPriority())
    .expectedDate(dto.getExpectedDeliveryDate())
    .operator(operator)
    .build());

timelineActionService.confirm(
    projectId,
    "MODEL_VARIANT_REQUIREMENT_CONFIRM",
    TimelineActionDTO.builder().remark("新型号项目信息已完善，需求订单号：" + order.getOrderCode()).build(),
    request
);
```

- [ ] **Step 5: Add field-level audit for editable imported fields**

Add this helper record and method in `RequirementFormServiceImpl`:

```java
private record FieldChange(String fieldName, String oldValue, String newValue) {}

private void addChange(List<FieldChange> changes, String fieldName, Object oldValue, Object newValue) {
    String oldText = oldValue == null ? "" : String.valueOf(oldValue);
    String newText = newValue == null ? "" : String.valueOf(newValue);
    if (!oldText.equals(newText)) {
        changes.add(new FieldChange(fieldName, oldText, newText));
    }
}
```

Before updating Product/Form, compare at least these fields:

```java
addChange(changes, "model", product.getModel(), dto.getModel());
addChange(changes, "network_type", form.getNetworkType(), dto.getNetworkType());
addChange(changes, "hole_type", form.getHoleType(), dto.getHoleType());
addChange(changes, "mobile_function", form.getMobileFunction(), dto.getMobileFunction());
addChange(changes, "tipo", form.getTipo(), dto.getTipo());
addChange(changes, "priority", form.getPriority(), dto.getPriority());
addChange(changes, "manufacturing_location", form.getManufacturingLocation(), dto.getManufacturingLocation());
addChange(changes, "mold_marking", form.getMoldMarking(), dto.getMoldMarking());
addChange(changes, "reference_url", form.getReferenceUrl(), dto.getReferenceUrl());
addChange(changes, "remark", form.getRemark(), dto.getRemark());
addChange(changes, "expected_delivery_date", form.getExpectedDeliveryDate(), dto.getExpectedDeliveryDate());
addChange(changes, "requirement_type", form.getRequirementType(), dto.getRequirementType());
addChange(changes, "customer_requirement", form.getCustomerRequirement(), dto.getCustomerRequirement());
```

Write one operation log entry per field change:

```java
operationLogService.logSuccess(OperationLogCreateCommand.builder()
    .action(OperationActionConstants.REQUIREMENT_FORM_FIELD_CHANGE)
    .businessType("PRODUCT")
    .businessId(String.valueOf(product.getProductId()))
    .businessCode(product.getProductCode())
    .businessName(product.getProductName())
    .detailJson("{\"fieldName\":\"" + json(change.fieldName()) + "\",\"oldValue\":\"" + json(change.oldValue()) + "\",\"newValue\":\"" + json(change.newValue()) + "\",\"operator\":\"" + json(operator) + "\"}")
    .build());
```

- [ ] **Step 6: Update controller dependency**

Replace the controller field:

```java
private final RequirementFormService service;
```

Update confirm endpoint:

```java
@PostMapping("/confirm")
public ResponseVO<RequirementFormVO> confirm(
    @PathVariable Long projectId,
    @RequestBody RequirementFormSaveDTO dto,
    HttpServletRequest request
) {
    return ResponseVO.success(
        service.confirmRequirementForm(projectId, dto, request),
        RequestIdUtil.getRequestId(request),
        OffsetDateTime.now()
    );
}
```

- [ ] **Step 7: Keep DingTalkModelVariantService focused on project intake**

Remove these public methods from `DingTalkModelVariantService`:

```java
public RequirementFormVO getRequirementForm(Long projectId)
public RequirementFormVO saveRequirementForm(Long projectId, RequirementFormSaveDTO dto)
public RequirementFormVO confirmRequirementForm(Long projectId, RequirementFormSaveDTO dto)
```

Keep `receive(DingTalkModelVariantReceiveDTO dto)` and helpers used by receive, including project creation, `RequirementForm` initial draft insertion, color snapshots and integration record.

- [ ] **Step 8: Run target backend tests**

Run:

```powershell
mvn -Dtest=RequirementFormServiceImplTest,DingTalkModelVariantServiceTest,TimelineActionServiceImplTest test
```

Expected: PASS. Evidence: Maven exits with code 0 and Surefire reports failures 0, errors 0.

- [ ] **Step 9: Commit Task 2**

Run:

```powershell
git add src/main/java/com/yuewei/plm/module/project/variant/service src/main/java/com/yuewei/plm/module/project/variant/controller/RequirementFormController.java src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkModelVariantService.java src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java src/test/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImplTest.java src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkModelVariantServiceTest.java
git commit -m "feat: confirm model variant requirement through timeline"
```

---

### Task 3: Order Lifecycle Sync Rules

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImplTest.java`

**Interfaces:**
- Consumes: `ProjectOrderLifecycleSync.inProduction(Long projectId, String operator)`
- Produces behavior: confirming `MODEL_VARIANT_REQUIREMENT_CONFIRM` advances to step 2 but does not call `inProduction`; confirming actual later nodes may call `inProduction`.

- [ ] **Step 1: Write failing sync tests**

Add tests in `TimelineActionServiceImplTest`:

```java
@Test
void confirmModelVariantRequirementDoesNotMarkOrderInProduction() {
    Product product = new Product();
    product.setProductId(9L);
    product.setProductCode("MV-001");
    product.setProductName("超队 3.0 iPhone 18");
    product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT);
    product.setStatus(ProductStatusConstants.DEVELOPING);
    product.setCurrentStepNo(1);
    product.setDeletedFlag(0);
    when(productRepository.selectById(9L)).thenReturn(product);
    when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(901L);
    ProjectOrderLifecycleSync sync = mock(ProjectOrderLifecycleSync.class);
    ReflectionTestUtils.setField(service, "projectOrderLifecycleSync", sync);

    service.confirm(9L, "MODEL_VARIANT_REQUIREMENT_CONFIRM", new TimelineActionDTO(), request);

    verify(sync, never()).inProduction(anyLong(), anyString());
    assertThat(product.getCurrentStepNo()).isEqualTo(2);
}

@Test
void confirmLaterModelVariantNodeMarksOrderInProduction() {
    Product product = new Product();
    product.setProductId(9L);
    product.setProductCode("MV-001");
    product.setProductName("超队 3.0 iPhone 18");
    product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT);
    product.setStatus(ProductStatusConstants.DEVELOPING);
    product.setCurrentStepNo(2);
    product.setDeletedFlag(0);
    when(productRepository.selectById(9L)).thenReturn(product);
    when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(902L);
    ProjectOrderLifecycleSync sync = mock(ProjectOrderLifecycleSync.class);
    ReflectionTestUtils.setField(service, "projectOrderLifecycleSync", sync);

    service.confirm(9L, "MODEL_VARIANT_PRODUCT_VERSION_CREATE", new TimelineActionDTO(), request);

    verify(sync).inProduction(9L, "Engineer One");
    assertThat(product.getCurrentStepNo()).isEqualTo(3);
}
```

- [ ] **Step 2: Run sync tests to verify failure**

Run:

```powershell
mvn -Dtest=TimelineActionServiceImplTest test
```

Expected: FAIL. The first new test fails because current code calls `projectOrderLifecycleSync.inProduction` whenever current step becomes greater than 1.

- [ ] **Step 3: Add explicit production sync rule**

Replace the current generic sync block:

```java
if (projectOrderLifecycleSync != null && product.getCurrentStepNo() != null && product.getCurrentStepNo() > 1) {
    projectOrderLifecycleSync.inProduction(projectId, currentUser.displayName());
}
```

with:

```java
if (projectOrderLifecycleSync != null && shouldMarkOrderInProduction(context.current(), nextNode, hasNextStep)) {
    projectOrderLifecycleSync.inProduction(projectId, currentUser.displayName());
}
```

Add helper method:

```java
private boolean shouldMarkOrderInProduction(
    TimelineNodeDefinition fromNode,
    TimelineNodeDefinition toNode,
    boolean hasNextStep
) {
    if (!hasNextStep) {
        return false;
    }
    if ("MODEL_VARIANT_REQUIREMENT_CONFIRM".equals(fromNode.nodeCode())) {
        return false;
    }
    if ("PRODUCT_LINE_INIT_CREATE".equals(fromNode.nodeCode()) || "PRODUCT_LINE_INIT_APPROVE".equals(fromNode.nodeCode())) {
        return false;
    }
    return toNode.stepNo() > fromNode.stepNo();
}
```

- [ ] **Step 4: Ensure first-node service test asserts order stays confirmed**

In `RequirementFormServiceImplTest`, make the mocked `OrderEntity` return status `confirmed` and assert no `inProduction` sync is triggered by the requirement form service:

```java
private OrderEntity order(Long orderId) {
    OrderEntity order = new OrderEntity();
    order.setOrderId(orderId);
    order.setOrderCode("ORD-20260720-0001");
    order.setStatus("confirmed");
    return order;
}
```

The timeline service test from Step 1 proves it does not call `ProjectOrderLifecycleSync.inProduction` on step 1.

- [ ] **Step 5: Run sync regression tests**

Run:

```powershell
mvn -Dtest=TimelineActionServiceImplTest,RequirementFormServiceImplTest,OrderServiceImplTest test
```

Expected: PASS. Evidence: Maven exits with code 0 and Surefire reports failures 0, errors 0.

- [ ] **Step 6: Commit Task 3**

Run:

```powershell
git add src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java src/test/java/com/yuewei/plm/module/project/variant/service/impl/RequirementFormServiceImplTest.java
git commit -m "fix: keep requirement orders confirmed after first node"
```

---

### Task 4: Frontend Timeline Node Dialog Shell

**Files:**
- Modify: `../plm-web/src/api/modules/project.ts`
- Create: `../plm-web/src/views/project/components/TimelineNodeDialog.vue`
- Modify: `../plm-web/src/views/project/ProjectCenterView.vue`
- Test: `../plm-web/src/views/project/__tests__/TimelineNodeDialog.spec.ts`
- Test: `../plm-web/src/views/project/__tests__/project-m4-panels.spec.ts`

**Interfaces:**
- Consumes: `TimelineNodeVO.formKey`
- Consumes: `TimelineNodeVO.canEditForm`
- Consumes: `TimelineNodeVO.canUploadAttachment`
- Consumes: `TimelineNodeVO.attachmentCountLabel`
- Produces UI behavior: clicking any timeline child node opens one dialog; current node can confirm; completed node can upload supplemental attachment; future node is read-only description.

- [ ] **Step 1: Extend frontend timeline types**

In `project.ts`, add fields to `BackendTimelineNode` and `TimelineNodeVO`:

```ts
formKey?: 'model_variant_requirement' | 'production_operations' | 'production_colors' | 'model_variant_release' | null
formTitle?: string | null
canOpenForm?: boolean | null
canEditForm?: boolean | null
canUploadAttachment?: boolean | null
attachmentCountLabel?: string | null
```

In `mapTimelineNode`, map them:

```ts
formKey: node.formKey || null,
formTitle: node.formTitle || node.nodeName,
canOpenForm: node.canOpenForm !== false,
canEditForm: Boolean(node.canEditForm),
canUploadAttachment: Boolean(node.canUploadAttachment),
attachmentCountLabel: node.attachmentCountLabel || `${Number(node.documentCount || 0)} 个附件`,
```

- [ ] **Step 2: Create failing dialog tests**

Create `TimelineNodeDialog.spec.ts`:

```ts
import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TimelineNodeDialog from '../components/TimelineNodeDialog.vue'

vi.mock('../components/ModelVariantRequirementForm.vue', () => ({
  default: { props: ['projectId', 'readonly'], template: '<div data-test="requirement-form">readonly:{{ readonly }}</div>' }
}))
vi.mock('../components/TimelineAttachmentPanel.vue', () => ({
  default: { props: ['projectId', 'nodeKey', 'allowUpload'], template: '<div data-test="attachment-panel">upload:{{ allowUpload }}</div>' }
}))

const currentNode = {
  nodeKey: 'MODEL_VARIANT_REQUIREMENT_CONFIRM',
  nodeName: '新型号需求确认',
  formKey: 'model_variant_requirement',
  formTitle: '新型号项目信息完善表',
  stepNo: 1,
  status: 'current',
  nodeStatus: 'current',
  phaseName: '扩展确认阶段',
  summary: '等待节点处理',
  ownerRole: '工程',
  documentCount: 0,
  attachmentCountLabel: '0 个附件',
  canOpenForm: true,
  canEditForm: true,
  canUploadAttachment: true
}

describe('TimelineNodeDialog', () => {
  it('opens current node with editable form and upload enabled', () => {
    const wrapper = mount(TimelineNodeDialog, {
      props: { modelValue: true, projectId: 9, node: currentNode },
      global: { plugins: [ElementPlus] }
    })
    expect(wrapper.text()).toContain('新型号项目信息完善表')
    expect(wrapper.get('[data-test="requirement-form"]').text()).toContain('readonly:false')
    expect(wrapper.get('[data-test="attachment-panel"]').text()).toContain('upload:true')
  })

  it('opens future node as description only and upload disabled', () => {
    const wrapper = mount(TimelineNodeDialog, {
      props: {
        modelValue: true,
        projectId: 9,
        node: { ...currentNode, nodeKey: 'MODEL_VARIANT_MOLD_TEST', formKey: null, nodeStatus: 'pending', status: 'pending', canEditForm: false, canUploadAttachment: false }
      },
      global: { plugins: [ElementPlus] }
    })
    expect(wrapper.text()).toContain('该节点尚未开始')
    expect(wrapper.get('[data-test="attachment-panel"]').text()).toContain('upload:false')
  })
})
```

- [ ] **Step 3: Run frontend tests to verify failure**

Run from `../plm-web`:

```powershell
npm run test:run -- src/views/project/__tests__/TimelineNodeDialog.spec.ts
```

Expected: FAIL because `TimelineNodeDialog.vue` does not exist.

- [ ] **Step 4: Implement TimelineNodeDialog.vue**

Create a dialog with this script shape:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { confirmTimelineNode, type TimelineNodeVO } from '@/api/modules/project'
import ModelVariantRequirementForm from './ModelVariantRequirementForm.vue'
import TimelineAttachmentPanel from './TimelineAttachmentPanel.vue'

const props = defineProps<{ modelValue: boolean; projectId: number; node: TimelineNodeVO | null }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'changed'): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const isCurrent = computed(() => props.node?.nodeStatus === 'current')
const isPending = computed(() => props.node?.nodeStatus === 'pending')
const readonly = computed(() => !props.node?.canEditForm)
const allowUpload = computed(() => Boolean(props.node?.canUploadAttachment))
const title = computed(() => props.node?.formTitle || props.node?.nodeName || '时间轴节点')

async function confirmGenericNode() {
  if (!props.node) return
  await ElMessageBox.confirm('确认后系统会校验该节点业务结果，并直接进入下一步骤。', '确认当前节点', {
    confirmButtonText: '确认并进入下一步',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await confirmTimelineNode(props.projectId, props.node.nodeKey)
  emit('changed')
  visible.value = false
}

function onFormConfirmed() {
  emit('changed')
  visible.value = false
}
</script>
```

Template rules:

```vue
<template>
  <el-dialog v-model="visible" :title="title" width="860px" destroy-on-close>
    <template v-if="node">
      <el-alert
        v-if="isPending"
        title="该节点尚未开始"
        description="可以查看节点说明；项目推进到该节点后才能填写表单或上传资料。"
        type="info"
        show-icon
        :closable="false"
      />

      <ModelVariantRequirementForm
        v-if="node.formKey === 'model_variant_requirement'"
        :project-id="projectId"
        :readonly="readonly"
        @confirmed="onFormConfirmed"
      />

      <div v-else class="timeline-node-dialog__summary">
        <p>{{ node.summary || '当前节点用于补充业务信息、查看资料并完成节点确认。' }}</p>
        <el-button v-if="isCurrent" type="primary" data-test="timeline-node-confirm-generic" @click="confirmGenericNode">
          确认并进入下一步
        </el-button>
      </div>

      <TimelineAttachmentPanel
        :project-id="projectId"
        :node-key="node.nodeKey"
        :allow-upload="allowUpload"
        :title="`${node.nodeName}资料`"
        :description="allowUpload ? '可补充该节点相关资料；资料不是推进必填项。' : '未来节点不能提前上传资料。'"
        @changed="emit('changed')"
      />
    </template>
  </el-dialog>
</template>
```

- [ ] **Step 5: Wire node click in ProjectCenterView.vue**

Add state:

```ts
const nodeDialogVisible = ref(false)
const nodeDialogNode = ref<TimelineNodeVO | null>(null)

function openTimelineNodeDialog(node: ProductFlowStageNode | TimelineNodeVO) {
  const source = detailPresentation.value?.timeline?.find((item) => item.nodeKey === node.nodeKey)
  nodeDialogNode.value = source || null
  nodeDialogVisible.value = Boolean(source)
}
```

In child node cards, add click handling and keyboard access:

```vue
<div
  v-for="node in activeSkuFlowStage.childNodes"
  :key="node.nodeKey"
  class="flow-child-node"
  role="button"
  tabindex="0"
  @click="openTimelineNodeDialog(node)"
  @keydown.enter.prevent="openTimelineNodeDialog(node)"
>
```

Render dialog once near the end of the detail dialog:

```vue
<TimelineNodeDialog
  v-model="nodeDialogVisible"
  :project-id="detailTarget.productId"
  :node="nodeDialogNode"
  @changed="handleLifecycleChanged"
/>
```

Remove the inline first-node form block from the SKU flow detail section:

```vue
<ModelVariantRequirementForm
  v-if="Number(detailTarget.currentStepNo) === 1"
  :project-id="detailTarget.productId"
  @confirmed="handleLifecycleChanged"
/>
```

- [ ] **Step 6: Remove separate normal advance button**

In `ProjectCenterView.vue`, remove the button with `data-test="project-timeline-advance"`.

Keep `handleAdvanceCurrentNode` only if other legacy tests still import it indirectly; otherwise remove the function and the `advanceTimelineNode` import from the page.

Change the current-node confirm button text to open the same dialog:

```vue
<el-button
  v-if="activeProductFlowNode"
  data-test="project-timeline-open-current"
  type="primary"
  :disabled="Boolean(timelineActionLoading)"
  @click="openTimelineNodeDialog(activeProductFlowNode)"
>
  处理当前节点
</el-button>
```

- [ ] **Step 7: Run frontend dialog tests**

Run from `../plm-web`:

```powershell
npm run test:run -- src/views/project/__tests__/TimelineNodeDialog.spec.ts src/views/project/__tests__/project-m4-panels.spec.ts
```

Expected: PASS. Evidence: Vitest exits with code 0 and reports all selected tests passed.

- [ ] **Step 8: Commit Task 4**

Run:

```powershell
git add src/api/modules/project.ts src/views/project/components/TimelineNodeDialog.vue src/views/project/ProjectCenterView.vue src/views/project/__tests__/TimelineNodeDialog.spec.ts src/views/project/__tests__/project-m4-panels.spec.ts
git commit -m "feat: open timeline nodes in a unified dialog"
```

---

### Task 5: Requirement Form UX And Attachment Panel Permissions

**Files:**
- Modify: `../plm-web/src/views/project/components/ModelVariantRequirementForm.vue`
- Modify: `../plm-web/src/views/project/components/TimelineAttachmentPanel.vue`
- Modify: `../plm-web/src/views/project/__tests__/ModelVariantRequirementForm.spec.ts`
- Modify: `../plm-web/src/views/project/__tests__/TimelineNodeDialog.spec.ts`

**Interfaces:**
- Consumes: `RequirementFormVO.colors[].selected`
- Consumes: `TimelineAttachmentPanel.allowUpload`
- Produces UI copy: `本型号将生产颜色：黑色、白色，共 2 个颜色版本`
- Produces behavior: future node cannot upload; completed/current node can supplement files; no “必传/待上传” tag appears.

- [ ] **Step 1: Write failing form and attachment tests**

Add to `ModelVariantRequirementForm.spec.ts`:

```ts
it('validates requirement type and at least one color before confirm', async () => {
  orderApi.getRequirementForm.mockResolvedValue({
    projectId: 9,
    dingTalkApprovalNo: 'DT-001',
    productName: '超队 3.0',
    model: 'iPhone 18',
    tipo: '精孔磁吸壳',
    status: 'draft',
    colors: [{ variantColorId: 1, colorCode: '01', colorName: '黑色', selected: false }]
  })
  const wrapper = mount(ModelVariantRequirementForm, { props: { projectId: 9 }, global: { plugins: [ElementPlus] } })
  await flushPromises()

  await wrapper.get('[data-test="requirement-confirm"]').trigger('click')

  expect(orderApi.confirmRequirementForm).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('至少保留一个生产颜色')
})

it('shows selected color business summary', async () => {
  const wrapper = mount(ModelVariantRequirementForm, { props: { projectId: 9 }, global: { plugins: [ElementPlus] } })
  await flushPromises()

  expect(wrapper.text()).toContain('本型号将生产颜色：黑色、透明色，共 2 个颜色版本')
})
```

Add to `TimelineNodeDialog.spec.ts`:

```ts
it('does not render required attachment wording', () => {
  const wrapper = mount(TimelineNodeDialog, {
    props: { modelValue: true, projectId: 9, node: currentNode },
    global: { plugins: [ElementPlus] }
  })
  expect(wrapper.text()).not.toContain('必传')
  expect(wrapper.text()).not.toContain('待上传')
  expect(wrapper.text()).toContain('0 个附件')
})
```

- [ ] **Step 2: Run tests to verify failure**

Run from `../plm-web`:

```powershell
npm run test:run -- src/views/project/__tests__/ModelVariantRequirementForm.spec.ts src/views/project/__tests__/TimelineNodeDialog.spec.ts
```

Expected: FAIL. Failure reasons include missing client validation, missing business color summary, and `TimelineAttachmentPanel` not accepting `allowUpload`.

- [ ] **Step 3: Update ModelVariantRequirementForm props and computed values**

Add props and readonly controls:

```ts
const props = withDefaults(defineProps<{ projectId: number; readonly?: boolean }>(), {
  readonly: false
})
```

Add computed summary:

```ts
const selectedColors = computed(() => (form.colors || []).filter((item) => item.selected))
const colorSummary = computed(() => {
  if (!selectedColors.value.length) return '本型号暂未选择生产颜色'
  const names = selectedColors.value.map((item) => item.colorName || item.colorCode || `颜色${item.variantColorId}`).join('、')
  return `本型号将生产颜色：${names}，共 ${selectedColors.value.length} 个颜色版本`
})
const validationMessage = ref('')
```

Add confirm validation:

```ts
function validateBeforeConfirm() {
  validationMessage.value = ''
  if (!selectedColors.value.length) {
    validationMessage.value = '至少保留一个生产颜色'
    return false
  }
  if (!form.requirementType) {
    validationMessage.value = '请选择订单类型'
    return false
  }
  if (form.requirementType === 'customer_requirement' && !String(form.customerRequirement || '').trim()) {
    validationMessage.value = '请填写客户要求'
    return false
  }
  return true
}
```

In the confirm path:

```ts
async function save(confirm = false) {
  if (props.readonly) return
  if (confirm && !validateBeforeConfirm()) return
  saving.value = true
  try {
    const value = confirm ? await confirmRequirementForm(props.projectId, payload()) : await saveRequirementForm(props.projectId, payload())
    Object.assign(form, value)
    ElMessage.success(confirm ? '信息已确认，已进入下一步骤' : '草稿已保存')
    if (confirm) emit('confirmed')
  } finally {
    saving.value = false
  }
}
```

Set all editable inputs to `:disabled="readonly"` and set the approval number input `readonly`.

Add template lines:

```vue
<el-alert v-if="validationMessage" :title="validationMessage" type="warning" show-icon :closable="false" />
<p class="color-summary" data-test="production-color-summary">{{ colorSummary }}</p>
<el-button data-test="requirement-save" :disabled="readonly" :loading="saving" @click="save(false)">保存草稿</el-button>
<el-button data-test="requirement-confirm" type="primary" :disabled="readonly" :loading="saving" @click="save(true)">确认并进入下一步</el-button>
```

- [ ] **Step 4: Update TimelineAttachmentPanel upload permissions**

Change props:

```ts
const props = withDefaults(defineProps<{
  projectId: number
  nodeKey: string | null
  allowUpload?: boolean
  title?: string
  description?: string
}>(), {
  allowUpload: true,
  title: '节点资料',
  description: '可补充当前节点相关资料；资料不是推进必填项。'
})
```

Update upload permission:

```ts
const canUpload = computed(() => Boolean(props.allowUpload && props.nodeKey && selectedFile.value && !uploading.value))
```

Hide upload bar when upload is not allowed:

```vue
<div v-if="allowUpload" class="attachment-upload-bar">
  ...
</div>
<el-alert
  v-else
  title="该节点当前不能上传资料"
  description="只有当前节点和已完成节点可以补充资料；未来节点不能提前上传。"
  type="info"
  show-icon
  :closable="false"
/>
```

Use configurable title and description:

```vue
<h4 class="section-title">{{ title }}</h4>
<p class="page-panel-desc">{{ description }}</p>
```

- [ ] **Step 5: Run selected frontend tests**

Run from `../plm-web`:

```powershell
npm run test:run -- src/views/project/__tests__/ModelVariantRequirementForm.spec.ts src/views/project/__tests__/TimelineNodeDialog.spec.ts src/views/project/__tests__/project-m4-panels.spec.ts
```

Expected: PASS. Evidence: Vitest exits with code 0 and all selected tests pass.

- [ ] **Step 6: Commit Task 5**

Run:

```powershell
git add src/views/project/components/ModelVariantRequirementForm.vue src/views/project/components/TimelineAttachmentPanel.vue src/views/project/__tests__/ModelVariantRequirementForm.spec.ts src/views/project/__tests__/TimelineNodeDialog.spec.ts
git commit -m "feat: refine requirement form and attachment permissions"
```

---

### Task 6: Direct Frontend Verification And Implementation Notes

**Files:**
- Create: `docs/backend-notes/2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md`
- Copy after implementation: `D:\Yuewei\资料\PLM\docs\整体测试\2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md`

**Interfaces:**
- Consumes: all code changes from Tasks 1-5.
- Produces: implementation notes with changed files, changed behavior, frontend direct test steps and pass criteria.

- [ ] **Step 1: Run backend regression tests**

Run:

```powershell
mvn -Dtest=TimelineActionServiceImplTest,TimelineServiceImplTest,RequirementFormServiceImplTest,DingTalkModelVariantServiceTest,OrderServiceImplTest test
```

Expected: PASS. Evidence: Maven exits with code 0 and Surefire reports failures 0, errors 0.

- [ ] **Step 2: Run frontend tests and type/build checks**

Run from `../plm-web`:

```powershell
npm run test:run -- src/views/project/__tests__/ModelVariantRequirementForm.spec.ts src/views/project/__tests__/TimelineNodeDialog.spec.ts src/views/project/__tests__/project-m4-panels.spec.ts src/views/order/__tests__/OrderCenterView.spec.ts
npm run type-check
npm run build
```

Expected: PASS. Evidence: Vitest exits with code 0, `vue-tsc --noEmit` exits with code 0, `vite build` exits with code 0.

- [ ] **Step 3: Manual frontend test 1 - 首节点弹窗**

Start backend and frontend using the project’s existing local commands. In the browser:

1. 进入“工作台/项目中心”。
2. 打开一个由模拟钉钉审批创建的新型号项目。
3. 点击第 1 步“新型号需求确认”。
4. Verify: 弹出“新型号项目信息完善表”。
5. Verify: “钉钉审批单号”显示在最上方或表单基本信息区，输入框只读。
6. Verify: 手机型号、4G/5G、孔位、Tipo、紧急度、制造地、模具印字等字段可编辑。
7. Verify: 颜色区显示“本型号将生产颜色：黑色、透明色，共 2 个颜色版本”一类业务话术，默认颜色全部选中。

合格标准：用户不需要看到 Product 子版本数量、SKU、后端对象拆分等技术说法。

- [ ] **Step 4: Manual frontend test 2 - 草稿保存不推进**

1. 在首节点弹窗取消全部颜色。
2. 点击“保存草稿”。
3. 刷新项目详情。

合格标准：

- 项目仍停留在第 1 步“新型号需求确认”。
- 需求订单列表未新增重复订单。
- 再次打开首节点弹窗时，颜色选择保持草稿状态。

- [ ] **Step 5: Manual frontend test 3 - 确认并进入下一步**

1. 在首节点弹窗选择订单类型。
2. 如果订单类型选择“客户订单”，填写客户要求。
3. 至少保留一个生产颜色。
4. 点击“确认并进入下一步”。
5. 打开项目时间轴。
6. 打开需求订单列表。

合格标准：

- 项目时间轴直接进入第 2 步“建立型号颜色版本”。
- 用户没有单独点击“推进下一节点”。
- 需求订单列表新增一条订单。
- 需求订单列表最左侧显示钉钉审批编号。
- 订单号由后端生成，例如 `ORD-20260720-0001`。
- “手机型号”列显示实际型号，例如 `iPhone 18`。
- 订单状态为 `confirmed`，不是 `in_production`。

- [ ] **Step 6: Manual frontend test 4 - 已完成节点可补充资料**

1. 点击已完成的第 1 步“新型号需求确认”。
2. Verify: 表单以只读方式展示。
3. 上传一个附件。
4. 关闭弹窗并刷新时间轴。

合格标准：

- 已完成节点业务字段不能编辑。
- 上传成功后节点旁显示 `1 个附件` 或对应数量。
- 页面不显示“必传”“待上传”。

- [ ] **Step 7: Manual frontend test 5 - 未来节点禁止提前上传**

1. 点击当前步骤之后的任一未来节点。
2. 查看弹窗内容。

合格标准：

- 弹窗显示节点说明和“该节点尚未开始”提示。
- 不显示上传控件。
- 不显示保存、确认按钮。

- [ ] **Step 8: Manual frontend test 6 - 无附件也可确认普通节点**

1. 将项目推进到一个没有专属表单的当前节点。
2. 保持该节点附件数为 `0 个附件`。
3. 点击节点弹窗内“确认并进入下一步”。

合格标准：

- 后端不因为缺少附件阻止推进。
- 如果该节点没有业务结果门禁，则进入下一步骤。
- 如果该节点有业务结果门禁，错误提示必须说明缺少的业务结果，例如“尚未确认批量投产颜色”，而不是提示缺少附件。

- [ ] **Step 9: Manual frontend test 7 - 项目放弃同步订单**

1. 对已生成需求订单的新型号项目执行“放弃/停止”操作。
2. 填写放弃原因。
3. 打开需求订单列表。

合格标准：

- 项目不可继续推进。
- 对应需求订单状态同步为 `closed`。
- 需求订单显示关闭原因、关闭时间、关闭操作人。
- 其他页面看到的项目/订单状态一致。

- [ ] **Step 10: Write implementation notes**

Create `docs/backend-notes/2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md` with this structure:

```markdown
# 工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀

## 1. 本次修改范围

## 2. 修改的代码文件

## 3. 修改的文档文件

## 4. 核心业务变化

## 5. 后端测试方式与合格标准

## 6. 前端自动化测试方式与合格标准

## 7. 前端直接测试步骤与合格标准

## 8. 风险与回归关注点
```

Copy it to the overall-test directory after the local file is reviewed.

- [ ] **Step 11: Commit Task 6**

Run:

```powershell
git add docs/backend-notes/2026-07-20-工作台时间轴节点弹窗与新型号首节点推进代码实现沉淀.md
git commit -m "docs: record timeline node dialog verification"
```

---

## Self-Review

**Spec coverage**

- DingTalk formal integration deferred: covered in Global Constraints and Task 2 split.
- First step opens a form dialog: covered in Tasks 2, 4, 5 and manual tests 1-3.
- Later steps use unified dialog: covered in Task 4.
- Some nodes do not require upload: covered in Task 1 and manual test 6.
- Attachments are optional supplemental资料: covered in Tasks 1 and 5.
- Current/completed/future node permissions: covered in Task 4 and manual tests 4-5.
- Confirm node validates form/business result and directly advances: covered in Tasks 1-4.
- No ordinary separate advance click: covered in Task 4.
- 首节点完成后创建订单、钉钉审批编号在最左侧、系统编号改为手机型号、新产品手机型号显示 `--`: order list UI was implemented in the prior DingTalk/order plan; this plan retains direct verification in Task 6 and does not duplicate that already-scoped work.
- Project stop/abandon syncs order: covered in Task 6 direct verification and retained through `ProjectOrderLifecycleSync`.

**Placeholder scan**

- The plan contains no unresolved placeholder wording.
- Each task includes concrete files, interfaces, commands and expected results.

**Type consistency**

- Backend fields use `formKey`, `formTitle`, `canOpenForm`, `canEditForm`, `canUploadAttachment`, `attachmentCountLabel`.
- Frontend maps the same camelCase API properties.
- First-node form action consistently uses `/api/v1/projects/{projectId}/requirement-form/confirm`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-20-workbench-timeline-node-forms.md`.

Two execution options:

1. Subagent-Driven - use `superpowers:subagent-driven-development` with one fresh worker per task and review between tasks.
2. Inline Execution - use `superpowers:executing-plans` in this session and execute tasks with checkpoints.

Because the user explicitly said “先不要动代码”，do not execute either option until the user gives a clear development instruction.
