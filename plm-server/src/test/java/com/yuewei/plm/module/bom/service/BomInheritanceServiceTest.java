package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BomInheritanceServiceTest {

    @Test
    void copiesOnlySelectedColorsIntoTargetDraftWithoutUpdatingSource() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBomRouteRepository routeRepository = mock(ProductBomRouteRepository.class);
        ProductBomRouteColorRepository colorRepository = mock(ProductBomRouteColorRepository.class);
        ProductBomItemRepository itemRepository = mock(ProductBomItemRepository.class);
        ProductBomCostSnapshotRepository costRepository = mock(ProductBomCostSnapshotRepository.class);
        ProcessRepository processRepository = mock(ProcessRepository.class);
        BomInheritanceService service = new BomInheritanceService(
            productRepository, bomRepository, routeRepository, colorRepository, itemRepository, costRepository,
            processRepository
        );
        ProductBom source = new ProductBom();
        source.setProductBomId(10L);
        source.setProductId(1L);
        source.setBomName("产品线正式 BOM");
        source.setVersionNo("V1");
        source.setStatus("released");
        source.setCurrencyCode("CNY");
        source.setDeletedFlag(0);
        Product target = new Product();
        target.setProductId(2L);
        target.setDeletedFlag(0);
        ProductBomRoute dye = route(100L, 10L, 1000L, "DYE");
        ProductBomRoute clear = route(101L, 10L, 1001L, "CLEAR");
        when(productRepository.selectById(2L)).thenReturn(target);
        when(bomRepository.selectById(10L)).thenReturn(source);
        when(routeRepository.selectList(Mockito.<Wrapper<ProductBomRoute>>any())).thenReturn(List.of(dye, clear));
        when(colorRepository.selectList(Mockito.<Wrapper<ProductBomRouteColor>>any()))
            .thenReturn(List.of(color(100L, "黑色"), color(100L, "蓝色")), List.of(color(101L, "透明色")));
        when(itemRepository.selectList(Mockito.<Wrapper<ProductBomItem>>any())).thenReturn(List.of(), List.of());
        when(bomRepository.insert(any(ProductBom.class))).thenAnswer(invocation -> {
            ProductBom value = invocation.getArgument(0);
            value.setProductBomId(20L);
            return 1;
        });
        when(routeRepository.insert(any(ProductBomRoute.class))).thenAnswer(invocation -> {
            ProductBomRoute value = invocation.getArgument(0);
            value.setProductBomRouteId(200L);
            return 1;
        });

        ProductBom inherited = service.inherit(10L, 2L, List.of("蓝色"));

        assertThat(inherited.getProductId()).isEqualTo(2L);
        assertThat(inherited.getSourceProductBomId()).isEqualTo(10L);
        assertThat(inherited.getStatus()).isEqualTo("draft");
        ArgumentCaptor<ProductBomRouteColor> colorCaptor = ArgumentCaptor.forClass(ProductBomRouteColor.class);
        verify(colorRepository).insert(colorCaptor.capture());
        assertThat(colorCaptor.getValue().getColorName()).isEqualTo("蓝色");
        verify(routeRepository).insert(any(ProductBomRoute.class));
        verify(bomRepository, never()).updateById(source);
    }

    private ProductBomRoute route(Long routeId, Long bomId, Long processId, String code) {
        ProductBomRoute route = new ProductBomRoute();
        route.setProductBomRouteId(routeId);
        route.setProductBomId(bomId);
        route.setProcessId(processId);
        route.setRouteCode(code);
        route.setRouteName(code);
        route.setStatus("active");
        route.setDeletedFlag(0);
        return route;
    }

    private ProductBomRouteColor color(Long routeId, String name) {
        ProductBomRouteColor color = new ProductBomRouteColor();
        color.setProductBomRouteId(routeId);
        color.setColorName(name);
        color.setStatus("active");
        color.setDeletedFlag(0);
        return color;
    }
}
