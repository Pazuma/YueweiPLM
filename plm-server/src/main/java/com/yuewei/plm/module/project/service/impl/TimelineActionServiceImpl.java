package com.yuewei.plm.module.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.attachment.constant.AttachmentOwnerTypeConstants;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.bom.service.ProductionConfirmationService;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants.TimelineNodeDefinition;
import com.yuewei.plm.module.project.dto.TimelineActionDTO;
import com.yuewei.plm.module.project.service.TimelineActionService;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.module.project.vo.TimelineActionResultVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TimelineActionServiceImpl implements TimelineActionService {

    private static final String ACTION_CONFIRM = "confirm";
    private static final String ACTION_ADVANCE = "advance";
    private static final String ACTION_RETURN = "return";

    private final ProductRepository productRepository;
    private final AttachmentRepository attachmentRepository;
    private final TimelineDefinitionProvider timelineDefinitionProvider;
    private final OperationLogService operationLogService;
    private final ProductionConfirmationService productionConfirmationService;

    @Override
    @Transactional
    public TimelineActionResultVO confirm(Long projectId, String nodeKey, TimelineActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        TimelineContext context = requireCurrentNode(product, nodeKey);
        CurrentUser currentUser = requireCurrentUser();
        String remark = dto == null ? null : dto.getRemark();
        boolean hasNextStep = context.currentStepNo() < context.definitions().size();
        TimelineNodeDefinition nextNode = hasNextStep
            ? context.definitions().get(context.currentStepNo())
            : context.current();
        boolean crossingStage = hasNextStep && !context.current().stageCode().equals(nextNode.stageCode());

        requireBusinessGate(projectId, nodeKey);

        if (crossingStage) {
            requireStageDocuments(product, context.current());
        }

        applyTimelineAudit(product, currentUser, ACTION_CONFIRM, remark);
        if (hasNextStep) {
            product.setCurrentStepNo(nextNode.stepNo());
            product.setTimelineCurrentConfirmed(false);
            product.setTimelineConfirmedNodeKey(null);
            product.setStatus(resolveProductStatus(nextNode.stepNo(), context.definitions().size()));
        } else {
            product.setCurrentStepNo(context.currentStepNo());
            product.setTimelineCurrentConfirmed(true);
            product.setTimelineConfirmedNodeKey(nodeKey);
            product.setStatus(resolveProductStatus(context.currentStepNo(), context.definitions().size()));
        }
        productRepository.updateById(product);

        Long logId = writeLog(
            product,
            OperationActionConstants.TIMELINE_CONFIRM,
            detailJsonForConfirmStep(product, context.current(), nextNode, hasNextStep, remark),
            request
        );
        return buildResult(product, ACTION_CONFIRM, nodeKey, context.currentStepNo(), nextNode, !hasNextStep, logId);
    }

    private void requireBusinessGate(Long projectId, String nodeKey) {
        switch (nodeKey) {
            case "PRODUCT_LINE_PROCESS_PLAN" -> productionConfirmationService.requireBomRoutesDetermined(projectId);
            case "PRODUCT_LINE_PROCESS_CONFIRM" -> productionConfirmationService.requireOperationsConfirmed(projectId);
            case "PRODUCT_LINE_PRODUCTION_DECISION_STEP", "MODEL_VARIANT_RELEASE" ->
                productionConfirmationService.requireColorsConfirmed(projectId);
            default -> {
                // Other timeline nodes keep their existing document and status gates.
            }
        }
    }

    @Override
    @Transactional
    public TimelineActionResultVO advance(Long projectId, String nodeKey, TimelineActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        TimelineContext context = requireCurrentNode(product, nodeKey);
        if (context.currentStepNo() >= context.definitions().size()) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "最后节点不能继续推进");
        }
        if (!Boolean.TRUE.equals(product.getTimelineCurrentConfirmed()) || !nodeKey.equals(product.getTimelineConfirmedNodeKey())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "当前节点尚未确认，不能推进");
        }
        CurrentUser currentUser = requireCurrentUser();
        String remark = dto == null ? null : dto.getRemark();
        int nextStepNo = context.currentStepNo() + 1;
        TimelineNodeDefinition nextNode = context.definitions().get(nextStepNo - 1);

        product.setCurrentStepNo(nextStepNo);
        product.setTimelineCurrentConfirmed(false);
        product.setTimelineConfirmedNodeKey(null);
        product.setStatus(resolveProductStatus(nextStepNo, context.definitions().size()));
        applyTimelineAudit(product, currentUser, ACTION_ADVANCE, remark);
        productRepository.updateById(product);

        Long logId = writeLog(
            product,
            OperationActionConstants.TIMELINE_ADVANCE,
            detailJsonForMove(product, ACTION_ADVANCE, context.current(), nextNode, true, remark),
            request
        );
        return buildResult(product, ACTION_ADVANCE, nodeKey, context.currentStepNo(), nextNode, false, logId);
    }

    @Override
    @Transactional
    public TimelineActionResultVO returnNode(Long projectId, String nodeKey, TimelineActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        TimelineContext context = requireCurrentNode(product, nodeKey);
        String reason = dto == null ? null : dto.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "退回原因不能为空");
        }
        boolean returnToPrevious = Boolean.TRUE.equals(dto.getReturnToPrevious());
        if (returnToPrevious && context.currentStepNo() <= 1) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "第一个节点不能退回上一节点");
        }
        CurrentUser currentUser = requireCurrentUser();
        int targetStepNo = returnToPrevious ? context.currentStepNo() - 1 : context.currentStepNo();
        TimelineNodeDefinition targetNode = context.definitions().get(targetStepNo - 1);

        product.setCurrentStepNo(targetStepNo);
        product.setTimelineCurrentConfirmed(false);
        product.setTimelineConfirmedNodeKey(null);
        product.setStatus(resolveProductStatus(targetStepNo, context.definitions().size()));
        applyTimelineAudit(product, currentUser, ACTION_RETURN, reason);
        productRepository.updateById(product);

        Long logId = writeLog(
            product,
            OperationActionConstants.TIMELINE_RETURN,
            detailJsonForMove(product, ACTION_RETURN, context.current(), targetNode, returnToPrevious, reason),
            request
        );
        return buildResult(product, ACTION_RETURN, nodeKey, context.currentStepNo(), targetNode, false, logId);
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private TimelineContext requireCurrentNode(Product product, String nodeKey) {
        List<TimelineNodeDefinition> definitions = timelineDefinitionProvider.getDefinitions(product.getProductType());
        int currentStepNo = normalizeStepNo(product.getCurrentStepNo(), definitions.size());
        TimelineNodeDefinition current = definitions.get(currentStepNo - 1);
        if (!current.nodeCode().equals(nodeKey)) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "只能操作当前时间轴节点");
        }
        return new TimelineContext(definitions, currentStepNo, current);
    }

    private int normalizeStepNo(Integer currentStepNo, int maxStepNo) {
        if (currentStepNo == null || currentStepNo < 1) {
            return 1;
        }
        return Math.min(currentStepNo, maxStepNo);
    }

    private void requireStageDocuments(Product product, TimelineNodeDefinition currentNode) {
        List<TimelineNodeDefinition> requiredDefinitions = timelineDefinitionProvider.getRequiredDefinitionsForStage(
            product.getProductType(),
            currentNode.stageCode()
        );
        if (requiredDefinitions.isEmpty()) {
            return;
        }
        Set<String> uploadedNodeKeys = attachmentRepository.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOwnerObjectType, AttachmentOwnerTypeConstants.PRODUCT)
                .eq(Attachment::getOwnerObjectId, product.getProductId())
                .eq(Attachment::getDeletedFlag, 0))
            .stream()
            .map(Attachment::getTimelineNodeKey)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        List<String> missing = requiredDefinitions.stream()
            .filter(definition -> !uploadedNodeKeys.contains(definition.nodeCode()))
            .map(TimelineNodeDefinition::nodeCode)
            .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL,
                "current stage documents are incomplete: " + String.join(",", missing)
            );
        }
    }

    private CurrentUser requireCurrentUser() {
        return CurrentUserContext.get()
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.UNAUTHORIZED, "未登录或登录已失效"));
    }

    private void applyTimelineAudit(Product product, CurrentUser currentUser, String action, String reason) {
        LocalDateTime now = LocalDateTime.now();
        product.setTimelineLastAction(action);
        product.setTimelineLastReason(reason);
        product.setTimelineLastOperatedAt(now);
        product.setTimelineLastOperatorUserId(currentUser.userId());
        product.setTimelineLastOperatorUserName(currentUser.displayName());
        product.setUpdatedAt(now);
        product.setUpdatedBy(currentUser.displayName());
    }

    private String resolveProductStatus(int stepNo, int maxStepNo) {
        if (stepNo <= 1) {
            return ProductStatusConstants.DRAFT;
        }
        if (stepNo >= maxStepNo) {
            return ProductStatusConstants.REVIEWING;
        }
        return ProductStatusConstants.DEVELOPING;
    }

    private Long writeLog(Product product, String action, String detailJson, HttpServletRequest request) {
        return operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("PRODUCT")
            .businessId(String.valueOf(product.getProductId()))
            .businessCode(product.getProductCode())
            .businessName(product.getProductName())
            .detailJson(detailJson)
            .request(request)
            .build());
    }

    private TimelineActionResultVO buildResult(
        Product product,
        String action,
        String nodeKey,
        Integer beforeStepNo,
        TimelineNodeDefinition currentNode,
        Boolean currentConfirmed,
        Long logId
    ) {
        return TimelineActionResultVO.builder()
            .projectId(product.getProductId())
            .productId(product.getProductId())
            .action(action)
            .nodeKey(nodeKey)
            .beforeStepNo(beforeStepNo)
            .currentStepNo(product.getCurrentStepNo())
            .currentNodeKey(currentNode.nodeCode())
            .currentNodeName(currentNode.nodeName())
            .currentConfirmed(currentConfirmed)
            .productStatus(product.getStatus())
            .logId(logId)
            .build();
    }

    private String detailJsonForConfirmStep(
        Product product,
        TimelineNodeDefinition fromNode,
        TimelineNodeDefinition toNode,
        boolean moved,
        String remark
    ) {
        return "{"
            + "\"projectId\":" + product.getProductId()
            + ",\"productId\":" + product.getProductId()
            + ",\"action\":\"confirm\""
            + ",\"fromNodeKey\":\"" + json(fromNode.nodeCode()) + "\""
            + ",\"fromStepNo\":" + fromNode.stepNo()
            + ",\"toNodeKey\":\"" + json(toNode.nodeCode()) + "\""
            + ",\"toStepNo\":" + toNode.stepNo()
            + ",\"moved\":" + moved
            + ",\"remark\":\"" + json(remark) + "\""
            + "}";
    }

    private String detailJsonForMove(
        Product product,
        String action,
        TimelineNodeDefinition fromNode,
        TimelineNodeDefinition toNode,
        boolean returnToPrevious,
        String reasonOrRemark
    ) {
        String valueName = ACTION_RETURN.equals(action) ? "reason" : "remark";
        return "{"
            + "\"projectId\":" + product.getProductId()
            + ",\"productId\":" + product.getProductId()
            + ",\"action\":\"" + action + "\""
            + ",\"fromNodeKey\":\"" + json(fromNode.nodeCode()) + "\""
            + ",\"fromStepNo\":" + fromNode.stepNo()
            + ",\"toNodeKey\":\"" + json(toNode.nodeCode()) + "\""
            + ",\"toStepNo\":" + toNode.stepNo()
            + ",\"returnToPrevious\":" + returnToPrevious
            + ",\"" + valueName + "\":\"" + json(reasonOrRemark) + "\""
            + "}";
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record TimelineContext(
        List<TimelineNodeDefinition> definitions,
        int currentStepNo,
        TimelineNodeDefinition current
    ) {
    }
}
