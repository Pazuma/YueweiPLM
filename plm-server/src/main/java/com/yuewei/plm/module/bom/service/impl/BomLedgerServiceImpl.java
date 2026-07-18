package com.yuewei.plm.module.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomCostSnapshot;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.module.bom.service.BomLedgerService;
import com.yuewei.plm.module.bom.vo.BomLedgerRowVO;
import com.yuewei.plm.module.bom.vo.BomSkuRowVO;
import com.yuewei.plm.module.bom.vo.BomSummaryVO;
import com.yuewei.plm.module.bom.vo.ProductBomCostSnapshotVO;
import com.yuewei.plm.module.bom.vo.ProductBomItemVO;
import com.yuewei.plm.module.bom.vo.ProductBomRouteVO;
import com.yuewei.plm.module.bom.vo.ProductBomWorkbenchVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BomLedgerServiceImpl implements BomLedgerService {
    private final ProductRepository productRepository;
    private final ProductBomRepository bomRepository;
    private final ProductBomRouteRepository routeRepository;
    private final ProductBomRouteColorRepository colorRepository;
    private final ProductBomItemRepository itemRepository;
    private final ProductBomCostSnapshotRepository costRepository;

    @Override
    public List<BomLedgerRowVO> listFormal() {
        List<ProductBom> boms = safe(bomRepository.selectList(new LambdaQueryWrapper<ProductBom>()
            .eq(ProductBom::getBomScope, "formal").eq(ProductBom::getDeletedFlag, 0)
            .orderByDesc(ProductBom::getUpdatedAt)));
        return boms.stream().filter(bom -> "formal".equals(bom.getBomScope())).map(bom -> {
            Product product = productRepository.selectById(bom.getProductId());
            return BomLedgerRowVO.builder()
                .productBomId(bom.getProductBomId()).productId(bom.getProductId()).bomCode(bom.getBomCode())
                .productCode(product == null ? null : product.getProductCode())
                .productName(product == null ? null : product.getProductName())
                .model(product == null ? null : product.getModel()).versionNo(bom.getVersionNo())
                .routeCount(activeRoutes(bom.getProductBomId()).size())
                .skuCount(countSkusWithoutConflict(bom)).status(bom.getStatus())
                .sourceType(bom.getSourceType()).updatedAt(bom.getUpdatedAt()).build();
        }).toList();
    }

    @Override
    public ProductBomWorkbenchVO getWorkbench(Long bomId) {
        ProductBom bom = requireBom(bomId);
        List<ProductBomRouteVO> routeVOs = activeRoutes(bomId).stream().map(route ->
            ProductBomRouteVO.builder()
                .productBomRouteId(route.getProductBomRouteId()).productBomId(bomId)
                .processId(route.getProcessId()).routeCode(route.getRouteCode()).routeName(route.getRouteName())
                .status(route.getStatus()).colors(activeColors(route.getProductBomRouteId()).stream()
                    .map(ProductBomRouteColor::getColorName).toList())
                .items(activeItems(route.getProductBomRouteId()).stream().map(ProductBomItemVO::from).toList())
                .costSnapshot(currentCost(bomId, route.getProductBomRouteId())).build()
        ).toList();
        List<ProductBomItemVO> testItems = "test".equals(bom.getBomScope())
            ? safe(itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
                .eq(ProductBomItem::getProductBomId, bomId).eq(ProductBomItem::getDeletedFlag, 0)))
                .stream().map(ProductBomItemVO::from).toList()
            : List.of();
        return ProductBomWorkbenchVO.builder().productBomId(bomId).productId(bom.getProductId())
            .bomCode(bom.getBomCode()).bomName(bom.getBomName()).bomScope(bom.getBomScope())
            .versionNo(bom.getVersionNo()).status(bom.getStatus()).testTotalCost(bom.getTestTotalCost())
            .calculatedAt(bom.getCalculatedAt()).testItems(testItems).routes(routeVOs).build();
    }

    @Override
    public List<BomSkuRowVO> listSkus(Long bomId) {
        ProductBom bom = requireBom(bomId);
        List<ProductBomRoute> routes = activeRoutes(bomId);
        Map<String, List<ProductBomRoute>> byColor = routesByColor(routes);
        List<Product> products = safe(productRepository.selectList(new LambdaQueryWrapper<Product>()
            .eq(Product::getParentProductId, bom.getProductId()).eq(Product::getDeletedFlag, 0)));
        if (products.isEmpty()) {
            Product owner = productRepository.selectById(bom.getProductId());
            if (owner != null && owner.getColor() != null && !owner.getColor().isBlank()) products = List.of(owner);
        }
        List<BomSkuRowVO> result = new ArrayList<>();
        for (Product product : products) {
            List<ProductBomRoute> matches = byColor.getOrDefault(product.getColor(), List.of());
            if (matches.size() > 1) {
                throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT,
                    "颜色 " + product.getColor() + " 匹配到多条有效工艺路线");
            }
            ProductBomRoute route = matches.isEmpty() ? null : matches.get(0);
            result.add(BomSkuRowVO.builder().productId(product.getProductId()).skuCode(product.getProductCode())
                .productName(product.getProductName()).phoneModel(product.getModel()).color(product.getColor())
                .status(product.getStatus()).productBomRouteId(route == null ? null : route.getProductBomRouteId())
                .routeCode(route == null ? null : route.getRouteCode()).build());
        }
        return result;
    }

    @Override
    public List<BomSkuRowVO> listSkusForRoute(Long routeId) {
        ProductBomRoute route = routeRepository.selectById(routeId);
        if (route == null || Integer.valueOf(1).equals(route.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "工艺路线不存在");
        }
        return listSkus(route.getProductBomId()).stream()
            .filter(sku -> routeId.equals(sku.getProductBomRouteId())).toList();
    }

    @Override
    public BomSummaryVO getSummary(Long productId) {
        List<ProductBom> values = safe(bomRepository.selectList(new LambdaQueryWrapper<ProductBom>()
            .eq(ProductBom::getProductId, productId).eq(ProductBom::getDeletedFlag, 0)
            .orderByDesc(ProductBom::getProductBomId)));
        ProductBom test = values.stream().filter(value -> "test".equals(value.getBomScope())).findFirst().orElse(null);
        List<ProductBomWorkbenchVO> formal = values.stream().filter(value -> "formal".equals(value.getBomScope()))
            .map(value -> getWorkbench(value.getProductBomId())).toList();
        return BomSummaryVO.builder().testTotalCost(test == null ? null : test.getTestTotalCost())
            .testCalculatedAt(test == null ? null : test.getCalculatedAt())
            .testVersionNo(test == null ? null : test.getVersionNo()).formalVersions(formal).build();
    }

    private Map<String, List<ProductBomRoute>> routesByColor(List<ProductBomRoute> routes) {
        Map<String, List<ProductBomRoute>> values = new HashMap<>();
        for (ProductBomRoute route : routes) {
            for (ProductBomRouteColor color : activeColors(route.getProductBomRouteId())) {
                values.computeIfAbsent(color.getColorName(), key -> new ArrayList<>()).add(route);
            }
        }
        return values;
    }

    private int countSkusWithoutConflict(ProductBom bom) {
        try {
            return listSkus(bom.getProductBomId()).size();
        } catch (BusinessException exception) {
            return 0;
        }
    }

    private ProductBom requireBom(Long bomId) {
        ProductBom bom = bomRepository.selectById(bomId);
        if (bom == null || Integer.valueOf(1).equals(bom.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "BOM 不存在");
        }
        return bom;
    }

    private List<ProductBomRoute> activeRoutes(Long bomId) {
        return safe(routeRepository.selectList(new LambdaQueryWrapper<ProductBomRoute>()
            .eq(ProductBomRoute::getProductBomId, bomId).eq(ProductBomRoute::getStatus, "active")
            .eq(ProductBomRoute::getDeletedFlag, 0)));
    }

    private List<ProductBomRouteColor> activeColors(Long routeId) {
        return safe(colorRepository.selectList(new LambdaQueryWrapper<ProductBomRouteColor>()
            .eq(ProductBomRouteColor::getProductBomRouteId, routeId).eq(ProductBomRouteColor::getStatus, "active")
            .eq(ProductBomRouteColor::getDeletedFlag, 0)));
    }

    private List<ProductBomItem> activeItems(Long routeId) {
        return safe(itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomRouteId, routeId).eq(ProductBomItem::getDeletedFlag, 0)));
    }

    private ProductBomCostSnapshotVO currentCost(Long bomId, Long routeId) {
        List<ProductBomCostSnapshot> values = safe(costRepository.selectList(
            new LambdaQueryWrapper<ProductBomCostSnapshot>().eq(ProductBomCostSnapshot::getProductBomId, bomId)
                .eq(ProductBomCostSnapshot::getProductBomRouteId, routeId)
                .in(ProductBomCostSnapshot::getStatus, List.of("current", "baseline"))
                .eq(ProductBomCostSnapshot::getDeletedFlag, 0).orderByDesc(ProductBomCostSnapshot::getCalculatedAt)
        ));
        if (values.isEmpty()) return null;
        ProductBomCostSnapshot value = values.get(0);
        return ProductBomCostSnapshotVO.builder().materialCost(value.getMaterialCost()).lossCost(value.getLossCost())
            .processCost(value.getProcessCost()).packageCost(value.getPackageCost()).laborCost(value.getLaborCost())
            .toolingCost(value.getToolingCost()).otherCost(value.getOtherCost()).totalCost(value.getTotalCost())
            .currencyCode(value.getCurrencyCode()).calculatedAt(value.getCalculatedAt()).build();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
