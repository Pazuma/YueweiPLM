package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.ProductionColorConfirmDTO;
import com.yuewei.plm.module.bom.dto.ProductionOperationConfirmDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomCostSnapshot;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.entity.ProcessProductionOperationSelection;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProcessProductionOperationSelectionRepository;
import com.yuewei.plm.module.bom.repository.ProductProductionColorDecisionRepository;
import com.yuewei.plm.module.process.entity.ProcessEntity;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionConfirmationServiceTest {
    private ProductRepository productRepository;
    private ProductBomRepository bomRepository;
    private ProductBomRouteRepository routeRepository;
    private ProcessRepository processRepository;
    private ProductBomRouteColorRepository routeColorRepository;
    private ProductBomCostSnapshotRepository costRepository;
    private ProcessProductionOperationSelectionRepository operationRepository;
    private ProductProductionColorDecisionRepository colorRepository;
    private ProductionConfirmationService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        bomRepository = mock(ProductBomRepository.class);
        routeRepository = mock(ProductBomRouteRepository.class);
        processRepository = mock(ProcessRepository.class);
        routeColorRepository = mock(ProductBomRouteColorRepository.class);
        costRepository = mock(ProductBomCostSnapshotRepository.class);
        operationRepository = mock(ProcessProductionOperationSelectionRepository.class);
        colorRepository = mock(ProductProductionColorDecisionRepository.class);
        service = new ProductionConfirmationService(productRepository, bomRepository, routeRepository,
            processRepository, routeColorRepository, costRepository, operationRepository, colorRepository);
    }

    @Test
    void confirmOperationsRejectsOperationOutsideSelectedRoute() {
        Product project = product(10L, "product_line", 10);
        ProductBomRoute route = route(100L, 10L, 200L);
        ProcessEntity foreignOperation = operation(301L, 999L);
        when(productRepository.selectById(10L)).thenReturn(project);
        when(routeRepository.selectById(100L)).thenReturn(route);
        when(processRepository.selectById(301L)).thenReturn(foreignOperation);

        ProductionOperationConfirmDTO dto = new ProductionOperationConfirmDTO();
        dto.setProductBomRouteId(100L);
        dto.setOperationProcessIds(List.of(301L));

        assertThatThrownBy(() -> service.confirmOperations(10L, dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前工艺路线");
        verify(operationRepository, never()).insert(any(ProcessProductionOperationSelection.class));
    }

    @Test
    void confirmColorsCreatesSkuOnceForRepeatedRequest() {
        Product project = product(20L, "model_variant", 16);
        project.setParentProductId(5L);
        project.setModel("iPhone 18");
        ProductBom bom = bom(400L, 20L, "released");
        ProductBomRoute route = route(401L, 20L, 501L);
        route.setProductBomId(400L);
        when(productRepository.selectById(20L)).thenReturn(project);
        when(bomRepository.selectById(400L)).thenReturn(bom);
        when(routeRepository.selectById(401L)).thenReturn(route);
        ProductBomRouteColor routeColor = new ProductBomRouteColor();
        routeColor.setColorName("黑色");
        ProductBomCostSnapshot cost = new ProductBomCostSnapshot();
        ProcessProductionOperationSelection selection = new ProcessProductionOperationSelection();
        selection.setOperationProcessId(601L);
        ProcessEntity routeProcess = operation(501L, null);
        routeProcess.setVersionNo("V1");
        ProcessEntity selectedOperation = operation(601L, 501L);
        when(routeColorRepository.selectList(any(Wrapper.class))).thenReturn(List.of(routeColor));
        when(costRepository.selectList(any(Wrapper.class))).thenReturn(List.of(cost));
        when(operationRepository.selectList(any(Wrapper.class))).thenReturn(List.of(selection));
        when(processRepository.selectById(501L)).thenReturn(routeProcess);
        when(processRepository.selectById(601L)).thenReturn(selectedOperation);
        when(colorRepository.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(productRepository.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(product(900L, "sku", 1)));

        ProductionColorConfirmDTO.ColorSelection color = new ProductionColorConfirmDTO.ColorSelection();
        color.setColorName("黑色");
        color.setProductBomId(400L);
        color.setProductBomRouteId(401L);
        ProductionColorConfirmDTO dto = new ProductionColorConfirmDTO();
        dto.setColors(List.of(color));

        var first = service.confirmColors(20L, dto);
        var second = service.confirmColors(20L, dto);

        assertThat(first.getSelectedColorCount()).isEqualTo(1);
        assertThat(second.getSelectedColorCount()).isEqualTo(1);
        verify(productRepository).insert(any(Product.class));
    }

    private Product product(Long id, String type, int step) {
        Product value = new Product();
        value.setProductId(id);
        value.setProductCode("PRD-" + id);
        value.setProductName("亮甲");
        value.setProductType(type);
        value.setCurrentStepNo(step);
        value.setDeletedFlag(0);
        return value;
    }

    private ProductBom bom(Long id, Long productId, String status) {
        ProductBom value = new ProductBom();
        value.setProductBomId(id);
        value.setProductId(productId);
        value.setStatus(status);
        value.setVersionNo("V1");
        value.setDeletedFlag(0);
        return value;
    }

    private ProductBomRoute route(Long id, Long productId, Long processId) {
        ProductBomRoute value = new ProductBomRoute();
        value.setProductBomRouteId(id);
        value.setProductId(productId);
        value.setProcessId(processId);
        value.setRouteCode("R1");
        value.setStatus("active");
        value.setDeletedFlag(0);
        return value;
    }

    private ProcessEntity operation(Long id, Long parentId) {
        ProcessEntity value = new ProcessEntity();
        value.setProcessId(id);
        value.setParentProcessId(parentId);
        value.setStatus("confirmed");
        value.setDeletedFlag(0);
        return value;
    }
}
