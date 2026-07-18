package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import java.util.List;
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
