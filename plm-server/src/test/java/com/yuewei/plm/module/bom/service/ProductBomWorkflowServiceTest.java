package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import java.util.List;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import org.junit.jupiter.api.Test;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.service.CodeItemService;

class ProductBomWorkflowServiceTest {

    @Test
    void rejectsColorAssignedToTwoActiveRoutes() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomWorkflowService service = service(bomRepository);
        when(bomRepository.selectById(10L)).thenReturn(bom(10L, "draft", 0));

        BomRouteSaveDTO dye = route(1L, "DYE", List.of("黑色", "蓝色"));
        BomRouteSaveDTO clear = route(2L, "CLEAR", List.of("透明色", "蓝色"));

        assertThatThrownBy(() -> service.saveRoutes(10L, List.of(dye, clear)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("蓝色");
    }

    @Test
    void publishRejectsBomThatIsNotFrozen() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomWorkflowService service = service(bomRepository);
        when(bomRepository.selectById(10L)).thenReturn(bom(10L, "reviewing", 0));

        assertThatThrownBy(() -> service.publish(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("冻结");
    }

    @Test
    void confirmsTestBomAsSingleTotalCost() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomItemRepository itemRepository = mock(ProductBomItemRepository.class);
        ProductBomWorkflowService service = new ProductBomWorkflowService(
            bomRepository, mock(ProductBomRouteRepository.class), mock(ProductBomRouteColorRepository.class),
            itemRepository, mock(ProductBomCostSnapshotRepository.class), new BomCostCalculator(), mock(BomTimelineGate.class),
            mock(CodeItemService.class)
        );
        ProductBom testBom = bom(30L, "draft", 0);
        testBom.setBomScope("test");
        testBom.setVersionNo("T1");
        ProductBomItem item = new ProductBomItem();
        item.setQuantity(new BigDecimal("2"));
        item.setUnitCostSnapshot(new BigDecimal("5"));
        item.setLossRate(new BigDecimal("0.10"));
        when(bomRepository.selectOne(Mockito.<Wrapper<ProductBom>>any())).thenReturn(testBom);
        when(itemRepository.selectList(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(List.of(item));

        ProductBom confirmed = service.confirmTestBom(20L);

        org.assertj.core.api.Assertions.assertThat(confirmed.getStatus()).isEqualTo("confirmed");
        org.assertj.core.api.Assertions.assertThat(confirmed.getTestTotalCost()).isEqualByComparingTo("11");
        org.mockito.Mockito.verify(bomRepository).updateById(testBom);
    }

    @Test
    void saveRoutesPersistsSupplierLineCostAndManualFlags() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomRouteRepository routeRepository = mock(ProductBomRouteRepository.class);
        ProductBomRouteColorRepository colorRepository = mock(ProductBomRouteColorRepository.class);
        ProductBomItemRepository itemRepository = mock(ProductBomItemRepository.class);
        CodeItemService codeItemService = mock(CodeItemService.class);
        ProductBom bom = bom(10L, "draft", 0);
        bom.setVersionNo("V1");
        bom.setCurrencyCode("CNY");
        when(bomRepository.selectById(10L)).thenReturn(bom);
        when(routeRepository.selectList(Mockito.<Wrapper<ProductBomRoute>>any())).thenReturn(List.of());
        when(itemRepository.selectList(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(List.of());
        when(codeItemService.requireEnabledColor(null, "02")).thenReturn(color(2L, "02", "Negro"));
        Mockito.doAnswer(invocation -> {
            ProductBomRoute inserted = invocation.getArgument(0);
            inserted.setProductBomRouteId(700L);
            return 1;
        }).when(routeRepository).insert(any(ProductBomRoute.class));
        ProductBomWorkflowService service = new ProductBomWorkflowService(
            bomRepository, routeRepository, colorRepository, itemRepository, mock(ProductBomCostSnapshotRepository.class),
            new BomCostCalculator(), mock(BomTimelineGate.class), codeItemService
        );
        ProductBomItemDTO item = new ProductBomItemDTO();
        item.setLineNo(1);
        item.setItemCode("MAT-001");
        item.setItemName("TPU 原料");
        item.setQuantity(new BigDecimal("2"));
        item.setUnit("kg");
        item.setUnitCost(new BigDecimal("12.50"));
        item.setLineCost(new BigDecimal("25.00"));
        item.setSupplierCode("SUP-A");
        item.setSupplierName("东莞塑胶 A");
        item.setCurrencyCode("MXN");
        item.setMaterialSource("manual");
        item.setUnmatchedFlag(1);
        BomRouteSaveDTO route = route(100L, "DYE", List.of("02"));
        route.setItems(List.of(item));

        service.saveRoutes(10L, List.of(route));

        ArgumentCaptor<ProductBomItem> captor = ArgumentCaptor.forClass(ProductBomItem.class);
        verify(itemRepository).insert(captor.capture());
        ProductBomItem inserted = captor.getValue();
        assertThat(inserted.getProductBomRouteId()).isEqualTo(700L);
        assertThat(inserted.getSupplierCodeSnapshot()).isEqualTo("SUP-A");
        assertThat(inserted.getSupplierNameSnapshot()).isEqualTo("东莞塑胶 A");
        assertThat(inserted.getUnitCostSnapshot()).isEqualByComparingTo("12.50");
        assertThat(inserted.getLineCostSnapshot()).isEqualByComparingTo("25.00");
        assertThat(inserted.getCurrencyCode()).isEqualTo("MXN");
        assertThat(inserted.getMaterialSource()).isEqualTo("manual");
        assertThat(inserted.getUnmatchedFlag()).isEqualTo(1);
    }

    private ProductBomWorkflowService service(ProductBomRepository bomRepository) {
        return new ProductBomWorkflowService(
            bomRepository,
            mock(ProductBomRouteRepository.class),
            mock(ProductBomRouteColorRepository.class),
            mock(ProductBomItemRepository.class),
            mock(ProductBomCostSnapshotRepository.class),
            new BomCostCalculator(),
            mock(BomTimelineGate.class),
            mock(CodeItemService.class)
        );
    }

    private ProductBom bom(Long bomId, String status, Integer frozenFlag) {
        ProductBom bom = new ProductBom();
        bom.setProductBomId(bomId);
        bom.setProductId(20L);
        bom.setStatus(status);
        bom.setFrozenFlag(frozenFlag);
        bom.setDeletedFlag(0);
        return bom;
    }

    private BomRouteSaveDTO route(Long processId, String code, List<String> colors) {
        BomRouteSaveDTO route = new BomRouteSaveDTO();
        route.setProcessId(processId);
        route.setRouteCode(code);
        route.setRouteName(code);
        route.setColors(colors);
        route.setItems(List.of());
        return route;
    }

    private CodeItem color(Long id, String code, String name) {
        CodeItem item = new CodeItem();
        item.setCodeItemId(id);
        item.setCodeType("color");
        item.setCodeValue(code);
        item.setCodeName(name);
        item.setStatus("enabled");
        item.setDeletedFlag(0);
        return item;
    }
}
