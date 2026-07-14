package com.yuewei.plm.module.bom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.ProductBomCreateDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProductBomServiceImplTest {

    @Test
    void createBomAndAddItemWritesDraftData() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomItemRepository itemRepository = mock(ProductBomItemRepository.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ProductBomServiceImpl service = new ProductBomServiceImpl(productRepository, bomRepository, itemRepository, operationLogService);
        Product product = product(10L);
        ProductBom bom = bom(100L, 10L, "draft");
        when(productRepository.selectById(10L)).thenReturn(product);
        when(bomRepository.insert(any(ProductBom.class))).thenAnswer(invocation -> {
            ProductBom inserted = invocation.getArgument(0);
            inserted.setProductBomId(100L);
            return 1;
        });
        when(bomRepository.selectById(100L)).thenReturn(bom);
        when(itemRepository.selectList(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(List.of());
        when(itemRepository.selectCount(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(0L);
        when(itemRepository.insert(any(ProductBomItem.class))).thenAnswer(invocation -> {
            ProductBomItem inserted = invocation.getArgument(0);
            inserted.setProductBomItemId(200L);
            return 1;
        });

        ProductBomCreateDTO createDTO = new ProductBomCreateDTO();
        createDTO.setBomName("超队3.0 样品BOM");
        createDTO.setBomType("ebom");
        createDTO.setVersionNo("A");
        var created = service.create(10L, createDTO, null);
        var withItem = service.addItem(100L, itemDTO(10), null);

        assertThat(created.getProductBomId()).isEqualTo(100L);
        assertThat(withItem.getProductBomId()).isEqualTo(100L);
        ArgumentCaptor<ProductBomItem> itemCaptor = ArgumentCaptor.forClass(ProductBomItem.class);
        verify(itemRepository).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProductId()).isEqualTo(10L);
        assertThat(itemCaptor.getValue().getVersionNo()).isEqualTo("A");
        assertThat(itemCaptor.getValue().getUnit()).isEqualTo("kg");
        verify(operationLogService, Mockito.atLeastOnce()).logSuccess(any());
    }

    @Test
    void frozenBomRejectsItemChange() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomServiceImpl service = new ProductBomServiceImpl(
            mock(ProductRepository.class),
            bomRepository,
            mock(ProductBomItemRepository.class),
            mock(OperationLogService.class)
        );
        when(bomRepository.selectById(100L)).thenReturn(bom(100L, 10L, "frozen"));

        assertThatThrownBy(() -> service.addItem(100L, itemDTO(10), null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.VERSION_FROZEN);
    }

    @Test
    void duplicateLineNoRejectsAddItem() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomItemRepository itemRepository = mock(ProductBomItemRepository.class);
        ProductBomServiceImpl service = new ProductBomServiceImpl(
            productRepository,
            bomRepository,
            itemRepository,
            mock(OperationLogService.class)
        );
        when(bomRepository.selectById(100L)).thenReturn(bom(100L, 10L, "draft"));
        when(productRepository.selectById(10L)).thenReturn(product(10L));
        when(itemRepository.selectCount(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(1L);

        assertThatThrownBy(() -> service.addItem(100L, itemDTO(10), null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.CODE_CONFLICT);
    }

    private Product product(Long productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductCode("PRD-1");
        product.setProductName("超队3.0");
        product.setDeletedFlag(0);
        return product;
    }

    private ProductBom bom(Long bomId, Long productId, String status) {
        ProductBom bom = new ProductBom();
        bom.setProductBomId(bomId);
        bom.setProductId(productId);
        bom.setBomCode("BOM-1");
        bom.setBomName("样品BOM");
        bom.setBomType("ebom");
        bom.setVersionNo("A");
        bom.setStatus(status);
        bom.setDeletedFlag(0);
        return bom;
    }

    private ProductBomItemDTO itemDTO(Integer lineNo) {
        ProductBomItemDTO dto = new ProductBomItemDTO();
        dto.setLineNo(lineNo);
        dto.setItemName("TPU 原料");
        dto.setQuantity(new BigDecimal("0.08"));
        dto.setUnit("kg");
        dto.setLossRate(new BigDecimal("0.02"));
        dto.setSubstituteFlag(0);
        return dto;
    }
}
