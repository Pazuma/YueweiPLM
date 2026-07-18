package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import org.mockito.Mockito;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import org.junit.jupiter.api.Test;

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
            itemRepository, mock(ProductBomCostSnapshotRepository.class), new BomCostCalculator()
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

    private ProductBomWorkflowService service(ProductBomRepository bomRepository) {
        return new ProductBomWorkflowService(
            bomRepository,
            mock(ProductBomRouteRepository.class),
            mock(ProductBomRouteColorRepository.class),
            mock(ProductBomItemRepository.class),
            mock(ProductBomCostSnapshotRepository.class),
            new BomCostCalculator()
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
}
