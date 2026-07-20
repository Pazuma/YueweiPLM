package com.yuewei.plm.module.bom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.entity.ProductBomRouteFormalSelection;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteFormalSelectionRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BomLedgerServiceImplTest {

    @Test
    void ledgerReturnsFormalBomsOnly() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductBomRouteFormalSelectionRepository formalSelectionRepository = mock(ProductBomRouteFormalSelectionRepository.class);
        BomLedgerServiceImpl service = service(bomRepository, productRepository, formalSelectionRepository);
        ProductBom formal = bom(10L, "formal", "released");
        ProductBom unselectedCandidate = bom(11L, "candidate", "released");
        ProductBom draft = bom(12L, "formal", "draft");
        ProductBom test = bom(13L, "test", "confirmed");
        ProductBom unselectedFormal = bom(14L, "formal", "released");
        ProductBom historicalImport = bom(15L, "formal", "released");
        historicalImport.setSourceType("import");
        when(bomRepository.selectList(Mockito.<Wrapper<ProductBom>>any()))
            .thenReturn(List.of(formal, unselectedCandidate, draft, test, unselectedFormal, historicalImport));
        when(formalSelectionRepository.selectList(Mockito.<Wrapper<ProductBomRouteFormalSelection>>any()))
            .thenReturn(List.of(formalSelection(10L)));
        when(productRepository.selectById(1L)).thenReturn(product(1L, null, "蓝色"));

        var rows = service.listFormal();

        assertThat(rows).extracting("productBomId").containsExactly(10L, 15L);
    }

    @Test
    void skuColorConflictIsReportedInsteadOfChoosingFirstRoute() {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductBomRouteRepository routeRepository = mock(ProductBomRouteRepository.class);
        ProductBomRouteColorRepository colorRepository = mock(ProductBomRouteColorRepository.class);
        BomLedgerServiceImpl service = new BomLedgerServiceImpl(
            productRepository, bomRepository, routeRepository, colorRepository,
            mock(ProductBomItemRepository.class), mock(ProductBomCostSnapshotRepository.class),
            mock(ProductBomRouteFormalSelectionRepository.class)
        );
        when(bomRepository.selectById(10L)).thenReturn(bom(10L, "formal", "released"));
        when(productRepository.selectList(Mockito.<Wrapper<Product>>any()))
            .thenReturn(List.of(product(2L, 1L, "蓝色")));
        when(routeRepository.selectList(Mockito.<Wrapper<ProductBomRoute>>any()))
            .thenReturn(List.of(route(100L, "DYE"), route(101L, "PRINT")));
        when(colorRepository.selectList(Mockito.<Wrapper<ProductBomRouteColor>>any()))
            .thenReturn(List.of(color(100L, "蓝色"), color(101L, "蓝色")));

        assertThatThrownBy(() -> service.listSkus(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("蓝色");
    }

    private BomLedgerServiceImpl service(ProductBomRepository bomRepository, ProductRepository productRepository,
                                         ProductBomRouteFormalSelectionRepository formalSelectionRepository) {
        return new BomLedgerServiceImpl(
            productRepository, bomRepository, mock(ProductBomRouteRepository.class),
            mock(ProductBomRouteColorRepository.class), mock(ProductBomItemRepository.class),
            mock(ProductBomCostSnapshotRepository.class), formalSelectionRepository
        );
    }

    private ProductBom bom(Long id, String scope, String status) {
        ProductBom bom = new ProductBom();
        bom.setProductBomId(id);
        bom.setProductId(1L);
        bom.setBomScope(scope);
        bom.setStatus(status);
        bom.setDeletedFlag(0);
        return bom;
    }

    private Product product(Long id, Long parentId, String color) {
        Product product = new Product();
        product.setProductId(id);
        product.setParentProductId(parentId);
        product.setProductCode("SKU-" + id);
        product.setProductName("亮甲 2.0");
        product.setModel("iPhone 18");
        product.setColor(color);
        product.setStatus("developing");
        product.setDeletedFlag(0);
        return product;
    }

    private ProductBomRoute route(Long id, String code) {
        ProductBomRoute route = new ProductBomRoute();
        route.setProductBomRouteId(id);
        route.setRouteCode(code);
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

    private ProductBomRouteFormalSelection formalSelection(Long bomId) {
        ProductBomRouteFormalSelection value = new ProductBomRouteFormalSelection();
        value.setProductId(1L);
        value.setProductBomId(bomId);
        value.setProductBomRouteId(100L);
        value.setProcessId(200L);
        value.setStatus("active");
        value.setDeletedFlag(0);
        return value;
    }
}
