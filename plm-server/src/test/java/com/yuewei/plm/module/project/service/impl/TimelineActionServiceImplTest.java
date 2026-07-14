package com.yuewei.plm.module.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.dto.TimelineActionDTO;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineActionServiceImplTest {

    private ProductRepository productRepository;
    private OperationLogService operationLogService;
    private TimelineActionServiceImpl service;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        operationLogService = mock(OperationLogService.class);
        service = new TimelineActionServiceImpl(productRepository, new TimelineDefinitionProvider(), operationLogService);
        request = mock(HttpServletRequest.class);
        CurrentUserContext.set(new CurrentUser(1L, "engineer01", "Engineer One", true));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void confirmCurrentNodeMarksConfirmedAndWritesLog() {
        Product product = product(100L, 2);
        when(productRepository.selectById(100L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(501L);

        var result = service.confirm(
            100L,
            "PRODUCT_LINE_DESIGN_CONFIRM",
            TimelineActionDTO.builder().remark("design checked").build(),
            request
        );

        assertThat(result.getAction()).isEqualTo("confirm");
        assertThat(result.getBeforeStepNo()).isEqualTo(2);
        assertThat(result.getCurrentStepNo()).isEqualTo(2);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_CONFIRM");
        assertThat(result.getCurrentConfirmed()).isTrue();
        assertThat(result.getLogId()).isEqualTo(501L);
        assertThat(product.getTimelineCurrentConfirmed()).isTrue();
        assertThat(product.getTimelineConfirmedNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_CONFIRM");
        assertThat(product.getTimelineLastAction()).isEqualTo("confirm");
        assertThat(product.getTimelineLastReason()).isEqualTo("design checked");
        assertThat(product.getTimelineLastOperatorUserId()).isEqualTo(1L);
        assertThat(product.getTimelineLastOperatorUserName()).isEqualTo("Engineer One");
        verify(productRepository).updateById(product);
        verify(operationLogService).logSuccess(argThat(command ->
            OperationActionConstants.TIMELINE_CONFIRM.equals(command.getAction())
                && "PRODUCT".equals(command.getBusinessType())
                && "100".equals(command.getBusinessId())
                && command.getDetailJson().contains("\"nodeKey\":\"PRODUCT_LINE_DESIGN_CONFIRM\"")
        ));
    }

    @Test
    void confirmNonCurrentNodeThrowsAndDoesNotWriteLog() {
        Product product = product(101L, 2);
        when(productRepository.selectById(101L)).thenReturn(product);

        assertThatThrownBy(() -> service.confirm(
            101L,
            "PRODUCT_LINE_MOLD_TRIAL",
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
    void advanceWithoutConfirmThrowsAndDoesNotWriteLog() {
        Product product = product(102L, 2);
        product.setTimelineCurrentConfirmed(false);
        when(productRepository.selectById(102L)).thenReturn(product);

        assertThatThrownBy(() -> service.advance(
            102L,
            "PRODUCT_LINE_DESIGN_CONFIRM",
            TimelineActionDTO.builder().remark("advance").build(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL);

        verify(productRepository, never()).updateById(any(Product.class));
        verify(operationLogService, never()).logSuccess(any(OperationLogCreateCommand.class));
    }

    @Test
    void advanceConfirmedCurrentNodeMovesToNextNodeAndWritesLog() {
        Product product = product(103L, 2);
        product.setTimelineCurrentConfirmed(true);
        product.setTimelineConfirmedNodeKey("PRODUCT_LINE_DESIGN_CONFIRM");
        when(productRepository.selectById(103L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(502L);

        var result = service.advance(
            103L,
            "PRODUCT_LINE_DESIGN_CONFIRM",
            TimelineActionDTO.builder().remark("go next").build(),
            request
        );

        assertThat(result.getAction()).isEqualTo("advance");
        assertThat(result.getBeforeStepNo()).isEqualTo(2);
        assertThat(result.getCurrentStepNo()).isEqualTo(3);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_MOLD_TRIAL");
        assertThat(result.getCurrentConfirmed()).isFalse();
        assertThat(result.getProductStatus()).isEqualTo(ProductStatusConstants.DEVELOPING);
        assertThat(result.getLogId()).isEqualTo(502L);
        assertThat(product.getCurrentStepNo()).isEqualTo(3);
        assertThat(product.getTimelineCurrentConfirmed()).isFalse();
        assertThat(product.getTimelineConfirmedNodeKey()).isNull();
        assertThat(product.getTimelineLastAction()).isEqualTo("advance");
        verify(productRepository).updateById(product);
        verify(operationLogService).logSuccess(argThat(command ->
            OperationActionConstants.TIMELINE_ADVANCE.equals(command.getAction())
                && command.getDetailJson().contains("\"fromStepNo\":2")
                && command.getDetailJson().contains("\"toStepNo\":3")
        ));
    }

    @Test
    void advanceLastNodeThrowsAndDoesNotWriteLog() {
        Product product = product(104L, 6);
        product.setTimelineCurrentConfirmed(true);
        product.setTimelineConfirmedNodeKey("PRODUCT_LINE_PRODUCTION_DECISION");
        when(productRepository.selectById(104L)).thenReturn(product);

        assertThatThrownBy(() -> service.advance(
            104L,
            "PRODUCT_LINE_PRODUCTION_DECISION",
            TimelineActionDTO.builder().remark("too far").build(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL);

        verify(productRepository, never()).updateById(any(Product.class));
        verify(operationLogService, never()).logSuccess(any(OperationLogCreateCommand.class));
    }

    @Test
    void returnWithoutReasonThrowsAndDoesNotWriteLog() {
        Product product = product(105L, 3);
        when(productRepository.selectById(105L)).thenReturn(product);

        assertThatThrownBy(() -> service.returnNode(
            105L,
            "PRODUCT_LINE_MOLD_TRIAL",
            TimelineActionDTO.builder().returnToPrevious(true).build(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VALIDATION_ERROR);

        verify(productRepository, never()).updateById(any(Product.class));
        verify(operationLogService, never()).logSuccess(any(OperationLogCreateCommand.class));
    }

    @Test
    void returnToPreviousNodeRecordsReasonAndWritesLog() {
        Product product = product(106L, 3);
        product.setTimelineCurrentConfirmed(true);
        product.setTimelineConfirmedNodeKey("PRODUCT_LINE_MOLD_TRIAL");
        when(productRepository.selectById(106L)).thenReturn(product);
        when(operationLogService.logSuccess(any(OperationLogCreateCommand.class))).thenReturn(503L);

        var result = service.returnNode(
            106L,
            "PRODUCT_LINE_MOLD_TRIAL",
            TimelineActionDTO.builder()
                .reason("mold docs missing")
                .returnToPrevious(true)
                .build(),
            request
        );

        assertThat(result.getAction()).isEqualTo("return");
        assertThat(result.getBeforeStepNo()).isEqualTo(3);
        assertThat(result.getCurrentStepNo()).isEqualTo(2);
        assertThat(result.getCurrentNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_CONFIRM");
        assertThat(result.getCurrentConfirmed()).isFalse();
        assertThat(result.getLogId()).isEqualTo(503L);
        assertThat(product.getCurrentStepNo()).isEqualTo(2);
        assertThat(product.getTimelineCurrentConfirmed()).isFalse();
        assertThat(product.getTimelineConfirmedNodeKey()).isNull();
        assertThat(product.getTimelineLastAction()).isEqualTo("return");
        assertThat(product.getTimelineLastReason()).isEqualTo("mold docs missing");
        verify(productRepository).updateById(product);
        verify(operationLogService).logSuccess(argThat(command ->
            OperationActionConstants.TIMELINE_RETURN.equals(command.getAction())
                && command.getDetailJson().contains("\"reason\":\"mold docs missing\"")
                && command.getDetailJson().contains("\"toStepNo\":2")
        ));
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
