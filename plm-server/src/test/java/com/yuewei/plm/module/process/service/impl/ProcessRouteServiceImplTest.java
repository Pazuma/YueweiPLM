package com.yuewei.plm.module.process.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.process.dto.ProcessOperationDTO;
import com.yuewei.plm.module.process.dto.ProcessRouteSaveDTO;
import com.yuewei.plm.module.process.entity.ProcessEntity;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProcessRouteServiceImplTest {

    @Test
    void duplicateSequenceNoRejectsCreate() {
        ProductRepository productRepository = mock(ProductRepository.class);
        when(productRepository.selectById(10L)).thenReturn(product(10L));
        ProcessRouteServiceImpl service = new ProcessRouteServiceImpl(
            productRepository,
            mock(ProcessRepository.class),
            mock(OperationLogService.class),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> service.create(10L, routeDTO(operation(10, "{}"), operation(10, "{}")), null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.CODE_CONFLICT);
    }

    @Test
    void invalidParamJsonRejectsCreate() {
        ProductRepository productRepository = mock(ProductRepository.class);
        when(productRepository.selectById(10L)).thenReturn(product(10L));
        ProcessRouteServiceImpl service = new ProcessRouteServiceImpl(
            productRepository,
            mock(ProcessRepository.class),
            mock(OperationLogService.class),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> service.create(10L, routeDTO(operation(10, "{bad-json")), null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VALIDATION_ERROR);
    }

    @Test
    void lockedRouteRejectsUpdate() {
        ProcessRepository processRepository = mock(ProcessRepository.class);
        ProcessEntity route = new ProcessEntity();
        route.setProcessId(100L);
        route.setProductId(10L);
        route.setProcessType("routing");
        route.setStatus("locked");
        route.setDeletedFlag(0);
        when(processRepository.selectById(100L)).thenReturn(route);
        ProcessRouteServiceImpl service = new ProcessRouteServiceImpl(
            mock(ProductRepository.class),
            processRepository,
            mock(OperationLogService.class),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> service.update(100L, routeDTO(operation(10, "{}")), null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VERSION_FROZEN);
    }

    @Test
    void freezeRequiresOperations() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProcessRepository processRepository = mock(ProcessRepository.class);
        ProcessEntity route = new ProcessEntity();
        route.setProcessId(100L);
        route.setProductId(10L);
        route.setProcessType("routing");
        route.setStatus("draft");
        route.setDeletedFlag(0);
        when(processRepository.selectById(100L)).thenReturn(route);
        when(processRepository.selectList(Mockito.<Wrapper<ProcessEntity>>any())).thenReturn(List.of());
        when(productRepository.selectById(10L)).thenReturn(product(10L));
        ProcessRouteServiceImpl service = new ProcessRouteServiceImpl(
            productRepository,
            processRepository,
            mock(OperationLogService.class),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> service.freeze(100L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VALIDATION_ERROR);
    }

    private ProcessRouteSaveDTO routeDTO(ProcessOperationDTO... operations) {
        ProcessRouteSaveDTO dto = new ProcessRouteSaveDTO();
        dto.setProcessName("超队3.0 样品工艺路线");
        dto.setVersionNo("A");
        dto.setOperations(List.of(operations));
        return dto;
    }

    private ProcessOperationDTO operation(Integer sequenceNo, String paramJson) {
        ProcessOperationDTO dto = new ProcessOperationDTO();
        dto.setSequenceNo(sequenceNo);
        dto.setProcessName("注塑成型");
        dto.setProcessParamJson(paramJson);
        dto.setStandardTimeMins(new BigDecimal("15"));
        dto.setQualityRequirement("外观无缩水、无明显披锋");
        return dto;
    }

    private Product product(Long productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductCode("PRD-1");
        product.setProductName("超队3.0");
        product.setDeletedFlag(0);
        return product;
    }
}
