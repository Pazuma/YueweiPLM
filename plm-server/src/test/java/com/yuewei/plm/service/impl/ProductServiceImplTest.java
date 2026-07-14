package com.yuewei.plm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuewei.plm.common.config.JacksonConfig;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.common.util.ProductCodeGenerator;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.service.ProductReleaseGateValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductServiceImplTest {

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void freezeUsesCurrentUserAndWritesProductFreezeOperationLog() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductCodeGenerator productCodeGenerator = mock(ProductCodeGenerator.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ProductServiceImpl service = new ProductServiceImpl(
            productRepository,
            productCodeGenerator,
            operationLogService,
            new JacksonConfig().objectMapper(),
            mock(ProductReleaseGateValidator.class)
        );
        Product product = new Product();
        product.setProductId(100L);
        product.setProductCode("PRD-CD30-IP18-BLK-A");
        product.setProductName("超队 3.0 iPhone18 黑色");
        product.setStatus("draft");
        product.setDeletedFlag(0);
        when(productRepository.selectById(100L)).thenReturn(product);
        CurrentUserContext.set(new CurrentUser(1L, "engineer01", "工程部用户一", true));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<OperationLogCreateCommand> logCaptor = ArgumentCaptor.forClass(OperationLogCreateCommand.class);

        service.freeze(100L, "M1操作人验证", request);

        verify(productRepository).updateById(productCaptor.capture());
        Product frozen = productCaptor.getValue();
        assertThat(frozen.getFrozenBy()).isEqualTo("工程部用户一");
        assertThat(frozen.getLockOperatorUserId()).isEqualTo(1L);
        assertThat(frozen.getLockOperatorUserName()).isEqualTo("工程部用户一");
        assertThat(frozen.getUpdatedBy()).isEqualTo("工程部用户一");

        verify(operationLogService).logSuccess(logCaptor.capture());
        OperationLogCreateCommand command = logCaptor.getValue();
        assertThat(command.getAction()).isEqualTo(OperationActionConstants.PRODUCT_FREEZE);
        assertThat(command.getBusinessType()).isEqualTo("PRODUCT");
        assertThat(command.getBusinessId()).isEqualTo("100");
        assertThat(command.getBusinessCode()).isEqualTo("PRD-CD30-IP18-BLK-A");
        assertThat(command.getBusinessName()).isEqualTo("超队 3.0 iPhone18 黑色");
        assertThat(command.getDetailJson()).contains("M1操作人验证");
        assertThat(command.getRequest()).isSameAs(request);
    }

    @Test
    void getByIdMapsCurrentStepNo() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductCodeGenerator productCodeGenerator = mock(ProductCodeGenerator.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ProductServiceImpl service = new ProductServiceImpl(
            productRepository,
            productCodeGenerator,
            operationLogService,
            new JacksonConfig().objectMapper(),
            mock(ProductReleaseGateValidator.class)
        );
        Product product = new Product();
        product.setProductId(101L);
        product.setProductCode("PRD-CD30-0002");
        product.setProductName("超队 3.0");
        product.setProductType("product_line");
        product.setStatus("developing");
        product.setCurrentStepNo(3);
        product.setDeletedFlag(0);
        when(productRepository.selectById(101L)).thenReturn(product);

        var vo = service.getById(101L);

        assertThat(vo.getCurrentStepNo()).isEqualTo(3);
    }
}
