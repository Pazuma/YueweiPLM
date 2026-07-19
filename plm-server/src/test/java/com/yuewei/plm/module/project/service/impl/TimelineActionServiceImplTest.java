package com.yuewei.plm.module.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.bom.service.ProductionConfirmationService;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.dto.TimelineActionDTO;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineActionServiceImplTest {

    private ProductRepository productRepository;
    private AttachmentRepository attachmentRepository;
    private OperationLogService operationLogService;
    private ProductionConfirmationService productionConfirmationService;
    private TimelineActionServiceImpl service;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        operationLogService = mock(OperationLogService.class);
        productionConfirmationService = mock(ProductionConfirmationService.class);
        service = new TimelineActionServiceImpl(
            productRepository,
            attachmentRepository,
            new TimelineDefinitionProvider(),
            operationLogService,
            productionConfirmationService
        );
        request = mock(HttpServletRequest.class);
        CurrentUserContext.set(new CurrentUser(1L, "engineer01", "Engineer One", true));
    }

    @Test
    void confirmProcessPlanRequiresDeterminedBomRoutes() {
        Product product = product(108L, 9);
        when(productRepository.selectById(108L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(508L);

        service.confirm(108L, "PRODUCT_LINE_PROCESS_PLAN", new TimelineActionDTO(), request);

        verify(productionConfirmationService).requireBomRoutesDetermined(108L);
    }

    @Test
    void confirmProcessStepRequiresProductionOperations() {
        Product product = product(109L, 10);
        when(productRepository.selectById(109L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(509L);

        service.confirm(109L, "PRODUCT_LINE_PROCESS_CONFIRM", new TimelineActionDTO(), request);

        verify(productionConfirmationService).requireOperationsConfirmed(109L);
    }

    @Test
    void confirmProductionDecisionRequiresColors() {
        Product product = product(110L, 22);
        when(productRepository.selectById(110L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(510L);

        service.confirm(110L, "PRODUCT_LINE_PRODUCTION_DECISION_STEP", new TimelineActionDTO(), request);

        verify(productionConfirmationService).requireColorsConfirmed(110L);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void confirmCurrentSmallStepMovesToNextSmallStepAndWritesLog() {
        Product product = product(100L, 1);
        when(productRepository.selectById(100L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(501L);

        var result = service.confirm(
            100L,
            "PRODUCT_LINE_INIT_CREATE",
            TimelineActionDTO.builder().remark("init docs ready").build(),
            request
        );

        assertThat(result.getAction()).isEqualTo("confirm");
        assertThat(result.getBeforeStepNo()).isEqualTo(1);
        assertThat(result.getCurrentStepNo()).isEqualTo(2);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_INIT_APPROVE");
        assertThat(result.getCurrentConfirmed()).isFalse();
        assertThat(result.getLogId()).isEqualTo(501L);
        assertThat(product.getCurrentStepNo()).isEqualTo(2);
        assertThat(product.getTimelineCurrentConfirmed()).isFalse();
        assertThat(product.getTimelineConfirmedNodeKey()).isNull();
        assertThat(product.getTimelineLastAction()).isEqualTo("confirm");
        assertThat(product.getTimelineLastReason()).isEqualTo("init docs ready");
        verify(productRepository).updateById(product);
        verify(operationLogService).logSuccess(argThat(command ->
            OperationActionConstants.TIMELINE_CONFIRM.equals(command.getAction())
                && "PRODUCT".equals(command.getBusinessType())
                && "100".equals(command.getBusinessId())
                && command.getDetailJson().contains("\"fromNodeKey\":\"PRODUCT_LINE_INIT_CREATE\"")
                && command.getDetailJson().contains("\"toNodeKey\":\"PRODUCT_LINE_INIT_APPROVE\"")
        ));
    }

    @Test
    void confirmLastStepOfStageWithoutRequiredDocumentThrows() {
        Product product = product(101L, 2);
        when(productRepository.selectById(101L)).thenReturn(product);
        when(attachmentRepository.selectList(anyWrapper())).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirm(
            101L,
            "PRODUCT_LINE_INIT_APPROVE",
            TimelineActionDTO.builder().remark("go design").build(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("stage documents")
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL);

        verify(productRepository, never()).updateById(any(Product.class));
        verify(operationLogService, never()).logSuccess(any(OperationLogCreateCommand.class));
    }

    @Test
    void confirmLastStepOfStageWithRequiredDocumentMovesToNextStage() {
        Product product = product(102L, 2);
        when(productRepository.selectById(102L)).thenReturn(product);
        when(attachmentRepository.selectList(anyWrapper())).thenReturn(List.of(attachment("PRODUCT_LINE_INIT_CREATE", "other")));
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(502L);

        var result = service.confirm(
            102L,
            "PRODUCT_LINE_INIT_APPROVE",
            TimelineActionDTO.builder().remark("go design").build(),
            request
        );

        assertThat(result.getCurrentStepNo()).isEqualTo(3);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_DRAWING");
        assertThat(result.getCurrentConfirmed()).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatusConstants.DEVELOPING);
        verify(productRepository).updateById(product);
    }

    @Test
    void confirmLastStepOfStageWithRequiredDocumentIgnoresAttachmentCategory() {
        Product product = product(107L, 2);
        when(productRepository.selectById(107L)).thenReturn(product);
        when(attachmentRepository.selectList(anyWrapper())).thenReturn(List.of(attachment("PRODUCT_LINE_INIT_CREATE", "testing")));
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(504L);

        var result = service.confirm(
            107L,
            "PRODUCT_LINE_INIT_APPROVE",
            TimelineActionDTO.builder().remark("go design with uploaded file").build(),
            request
        );

        assertThat(result.getCurrentStepNo()).isEqualTo(3);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_DRAWING");
        assertThat(result.getCurrentConfirmed()).isFalse();
        verify(productRepository).updateById(product);
    }

    @Test
    void confirmNonCurrentStepThrowsAndDoesNotWriteLog() {
        Product product = product(103L, 2);
        when(productRepository.selectById(103L)).thenReturn(product);

        assertThatThrownBy(() -> service.confirm(
            103L,
            "PRODUCT_LINE_DESIGN_DRAWING",
            TimelineActionDTO.builder().remark("wrong").build(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL);

        verify(productRepository, never()).updateById(any(Product.class));
        verify(operationLogService, never()).logSuccess(any(OperationLogCreateCommand.class));
    }

    @Test
    void returnToPreviousStepRecordsReasonAndWritesLog() {
        Product product = product(106L, 3);
        product.setTimelineCurrentConfirmed(true);
        product.setTimelineConfirmedNodeKey("PRODUCT_LINE_DESIGN_DRAWING");
        when(productRepository.selectById(106L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(503L);

        var result = service.returnNode(
            106L,
            "PRODUCT_LINE_DESIGN_DRAWING",
            TimelineActionDTO.builder()
                .reason("drawing needs rework")
                .returnToPrevious(true)
                .build(),
            request
        );

        assertThat(result.getAction()).isEqualTo("return");
        assertThat(result.getBeforeStepNo()).isEqualTo(3);
        assertThat(result.getCurrentStepNo()).isEqualTo(2);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_INIT_APPROVE");
        assertThat(result.getCurrentConfirmed()).isFalse();
        assertThat(product.getCurrentStepNo()).isEqualTo(2);
        assertThat(product.getTimelineCurrentConfirmed()).isFalse();
        assertThat(product.getTimelineConfirmedNodeKey()).isNull();
        assertThat(product.getTimelineLastAction()).isEqualTo("return");
        assertThat(product.getTimelineLastReason()).isEqualTo("drawing needs rework");
        verify(productRepository).updateById(product);
        verify(operationLogService).logSuccess(argThat(command ->
            OperationActionConstants.TIMELINE_RETURN.equals(command.getAction())
                && command.getDetailJson().contains("\"reason\":\"drawing needs rework\"")
                && command.getDetailJson().contains("\"toStepNo\":2")
        ));
    }

    @SuppressWarnings("unchecked")
    private Wrapper<Attachment> anyWrapper() {
        return any(Wrapper.class);
    }

    private Attachment attachment(String nodeKey, String category) {
        Attachment attachment = new Attachment();
        attachment.setTimelineNodeKey(nodeKey);
        attachment.setFileCategory(category);
        attachment.setDeletedFlag(0);
        return attachment;
    }

    private Product product(Long productId, Integer currentStepNo) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductCode("PRD-CD30-0001");
        product.setProductName("Super Captain 3.0");
        product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE);
        product.setStatus(ProductStatusConstants.DEVELOPING);
        product.setCurrentStepNo(currentStepNo);
        product.setDeletedFlag(0);
        return product;
    }
}
