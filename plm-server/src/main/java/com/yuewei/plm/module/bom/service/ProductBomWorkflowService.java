package com.yuewei.plm.module.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.dto.TestBomSaveDTO;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductBomWorkflowService {
    private static final String ACTIVE = "active";

    private final ProductBomRepository bomRepository;
    private final ProductBomRouteRepository routeRepository;
    private final ProductBomRouteColorRepository colorRepository;
    private final ProductBomItemRepository itemRepository;
    private final ProductBomCostSnapshotRepository costRepository;
    private final BomCostCalculator costCalculator;

    @Transactional
    public ProductBom saveTestBom(Long productId, TestBomSaveDTO dto) {
        ProductBom bom = findTestBom(productId);
        LocalDateTime now = LocalDateTime.now();
        if (bom == null) {
            bom = new ProductBom();
            bom.setProductId(productId);
            bom.setBomCode("TEST-BOM-" + productId);
            bom.setBomName("测试 BOM");
            bom.setBomType("test");
            bom.setBomScope("test");
            bom.setSourceType("manual");
            bom.setStatus("draft");
            bom.setCurrencyCode("CNY");
            bom.setFrozenFlag(0);
            fillCreate(bom, now);
            bomRepository.insert(bom);
        } else if ("archived".equals(bom.getStatus())) {
            throw validation("已归档测试 BOM 不可修改");
        }
        bom.setVersionNo(dto.getVersionNo());
        bom.setStatus("draft");
        touch(bom);
        bomRepository.updateById(bom);
        List<ProductBomItem> existing = itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomId, bom.getProductBomId()).eq(ProductBomItem::getDeletedFlag, 0));
        if (existing != null) {
            existing.forEach(item -> {
                item.setDeletedFlag(1);
                touch(item);
                itemRepository.updateById(item);
            });
        }
        int index = 0;
        for (ProductBomItemDTO itemDTO : dto.getItems()) {
            ProductBomItem item = toTestItem(bom, itemDTO, ++index, now);
            itemRepository.insert(item);
        }
        return bom;
    }

    @Transactional
    public ProductBom confirmTestBom(Long productId) {
        ProductBom bom = findTestBom(productId);
        if (bom == null) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "测试 BOM 不存在");
        }
        List<ProductBomItem> items = itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomId, bom.getProductBomId()).eq(ProductBomItem::getDeletedFlag, 0));
        if (items == null || items.isEmpty()) {
            throw validation("测试 BOM 至少需要一条明细");
        }
        BomCostCalculator.Result cost = costCalculator.calculate(items, null, null, null, null, null);
        bom.setTestTotalCost(cost.totalCost());
        bom.setCalculatedAt(LocalDateTime.now());
        bom.setConfirmedAt(bom.getCalculatedAt());
        bom.setConfirmedBy("system");
        bom.setStatus("confirmed");
        touch(bom);
        bomRepository.updateById(bom);
        return bom;
    }

    @Transactional
    public void saveRoutes(Long bomId, List<BomRouteSaveDTO> routes) {
        ProductBom bom = requireEditable(bomId);
        validateRoutes(routes);
        archiveExistingRoutes(bomId);
        LocalDateTime now = LocalDateTime.now();
        for (BomRouteSaveDTO dto : routes) {
            ProductBomRoute route = new ProductBomRoute();
            route.setProductBomId(bomId);
            route.setProductId(bom.getProductId());
            route.setProcessId(dto.getProcessId());
            route.setRouteCode(dto.getRouteCode().trim());
            route.setRouteName(dto.getRouteName().trim());
            route.setStatus(ACTIVE);
            fillCreate(route, now);
            routeRepository.insert(route);
            for (String colorName : dto.getColors()) {
                ProductBomRouteColor color = new ProductBomRouteColor();
                color.setProductBomId(bomId);
                color.setProductBomRouteId(route.getProductBomRouteId());
                color.setColorName(colorName.trim());
                color.setStatus(ACTIVE);
                fillCreate(color, now);
                colorRepository.insert(color);
            }
            for (ProductBomItemDTO itemDTO : dto.getItems()) {
                ProductBomItem item = toItem(bom, route, itemDTO, now);
                itemRepository.insert(item);
            }
        }
    }

    @Transactional
    public List<ProductBomCostSnapshot> recalculateCosts(Long bomId, List<BomRouteSaveDTO> costInputs) {
        ProductBom bom = requireEditable(bomId);
        List<ProductBomRoute> routes = activeRoutes(bomId);
        return routes.stream().map(route -> {
            List<ProductBomItem> items = activeItems(route.getProductBomRouteId());
            BomRouteSaveDTO input = costInputs.stream()
                .filter(value -> value.getProcessId().equals(route.getProcessId()))
                .findFirst()
                .orElse(new BomRouteSaveDTO());
            BomCostCalculator.Result result = costCalculator.calculate(
                items, input.getProcessCost(), input.getPackageCost(), input.getLaborCost(),
                input.getToolingCost(), input.getOtherCost()
            );
            archiveCurrentCost(bomId, route.getProductBomRouteId());
            ProductBomCostSnapshot snapshot = snapshot(bom, route, result);
            costRepository.insert(snapshot);
            return snapshot;
        }).toList();
    }

    @Transactional
    public ProductBom submitReview(Long bomId) {
        ProductBom bom = requireEditable(bomId);
        requireCompleteRoutes(bomId, false);
        bom.setStatus("reviewing");
        touch(bom);
        bomRepository.updateById(bom);
        return bom;
    }

    @Transactional
    public ProductBom freeze(Long bomId) {
        ProductBom bom = requireBom(bomId);
        if (!"reviewing".equals(bom.getStatus())) {
            throw validation("只有审核中的正式 BOM 可以冻结");
        }
        requireCompleteRoutes(bomId, true);
        bom.setFrozenFlag(1);
        bom.setFrozenAt(LocalDateTime.now());
        bom.setFrozenBy("system");
        touch(bom);
        bomRepository.updateById(bom);
        return bom;
    }

    @Transactional
    public ProductBom publish(Long bomId) {
        ProductBom bom = requireBom(bomId);
        if (!Integer.valueOf(1).equals(bom.getFrozenFlag())) {
            throw validation("正式 BOM 发布前必须先冻结");
        }
        if (!"reviewing".equals(bom.getStatus())) {
            throw validation("只有审核中的正式 BOM 可以发布");
        }
        requireCompleteRoutes(bomId, true);
        bom.setStatus("released");
        bom.setReleasedAt(LocalDateTime.now());
        bom.setReleasedBy("system");
        touch(bom);
        bomRepository.updateById(bom);
        return bom;
    }

    private void validateRoutes(List<BomRouteSaveDTO> routes) {
        if (routes == null || routes.isEmpty()) {
            throw validation("正式 BOM 至少需要一条有效工艺路线");
        }
        Set<String> assignedColors = new HashSet<>();
        for (BomRouteSaveDTO route : routes) {
            if (route.getColors() == null || route.getColors().isEmpty()) {
                throw validation("工艺路线至少需要一个适用颜色");
            }
            for (String color : route.getColors()) {
                String normalized = color == null ? "" : color.trim();
                if (normalized.isEmpty()) {
                    throw validation("适用颜色不能为空");
                }
                if (!assignedColors.add(normalized)) {
                    throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT,
                        "颜色 " + normalized + " 不能同时归属多条有效工艺路线");
                }
            }
        }
    }

    private void requireCompleteRoutes(Long bomId, boolean requireCost) {
        List<ProductBomRoute> routes = activeRoutes(bomId);
        if (routes.isEmpty()) {
            throw validation("正式 BOM 至少需要一条有效工艺路线");
        }
        for (ProductBomRoute route : routes) {
            if (activeColors(route.getProductBomRouteId()).isEmpty()) {
                throw validation("路线 " + route.getRouteName() + " 缺少适用颜色");
            }
            if (activeItems(route.getProductBomRouteId()).isEmpty()) {
                throw validation("路线 " + route.getRouteName() + " 缺少 BOM 明细");
            }
            if (requireCost && currentCosts(bomId, route.getProductBomRouteId()).isEmpty()) {
                throw validation("路线 " + route.getRouteName() + " 尚未完成成本计算");
            }
        }
    }

    private ProductBomItem toItem(ProductBom bom, ProductBomRoute route, ProductBomItemDTO dto, LocalDateTime now) {
        if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw validation("BOM 明细用量必须大于 0");
        }
        if (dto.getLossRate() != null
            && (dto.getLossRate().compareTo(BigDecimal.ZERO) < 0 || dto.getLossRate().compareTo(BigDecimal.ONE) > 0)) {
            throw validation("损耗率必须在 0 到 1 之间");
        }
        ProductBomItem item = new ProductBomItem();
        item.setProductBomId(bom.getProductBomId());
        item.setProductBomRouteId(route.getProductBomRouteId());
        item.setProductId(bom.getProductId());
        item.setInventoryId(dto.getInventoryId());
        item.setItemCode(dto.getItemCode());
        item.setItemName(dto.getItemName());
        item.setSpecification(dto.getSpecification());
        item.setLineNo(dto.getLineNo());
        item.setQuantity(dto.getQuantity());
        item.setUnit(dto.getUnit());
        item.setLossRate(dto.getLossRate() == null ? BigDecimal.ZERO : dto.getLossRate());
        item.setUnitCostSnapshot(dto.getUnitCost() == null ? BigDecimal.ZERO : dto.getUnitCost());
        item.setSubstituteFlag(dto.getSubstituteFlag() == null ? 0 : dto.getSubstituteFlag());
        item.setRemark(dto.getRemark());
        item.setVersionNo(bom.getVersionNo());
        item.setStatus("draft");
        item.setCurrencyCode(bom.getCurrencyCode() == null ? "CNY" : bom.getCurrencyCode());
        fillCreate(item, now);
        return item;
    }

    private ProductBomItem toTestItem(ProductBom bom, ProductBomItemDTO dto, int fallbackLineNo, LocalDateTime now) {
        ProductBomRoute placeholder = new ProductBomRoute();
        placeholder.setProductBomRouteId(null);
        ProductBomItem item = toItem(bom, placeholder, dto, now);
        item.setProductBomRouteId(null);
        if (item.getLineNo() == null) item.setLineNo(fallbackLineNo);
        return item;
    }

    private ProductBom findTestBom(Long productId) {
        return bomRepository.selectOne(new LambdaQueryWrapper<ProductBom>()
            .eq(ProductBom::getProductId, productId).eq(ProductBom::getBomScope, "test")
            .eq(ProductBom::getDeletedFlag, 0).orderByDesc(ProductBom::getProductBomId).last("limit 1"));
    }

    private ProductBomCostSnapshot snapshot(ProductBom bom, ProductBomRoute route, BomCostCalculator.Result value) {
        ProductBomCostSnapshot snapshot = new ProductBomCostSnapshot();
        snapshot.setProductBomId(bom.getProductBomId());
        snapshot.setProductBomRouteId(route.getProductBomRouteId());
        snapshot.setProductId(bom.getProductId());
        snapshot.setVersionNo(bom.getVersionNo());
        snapshot.setMaterialCost(value.materialCost());
        snapshot.setLossCost(value.lossCost());
        snapshot.setProcessCost(value.processCost());
        snapshot.setPackageCost(value.packageCost());
        snapshot.setLaborCost(value.laborCost());
        snapshot.setToolingCost(value.toolingCost());
        snapshot.setOtherCost(value.otherCost());
        snapshot.setTotalCost(value.totalCost());
        snapshot.setCurrencyCode(bom.getCurrencyCode() == null ? "CNY" : bom.getCurrencyCode());
        snapshot.setSourceSnapshotJson("{}");
        snapshot.setCalculatedAt(LocalDateTime.now());
        snapshot.setStatus("current");
        fillCreate(snapshot, snapshot.getCalculatedAt());
        return snapshot;
    }

    private ProductBom requireEditable(Long bomId) {
        ProductBom bom = requireBom(bomId);
        if ("released".equals(bom.getStatus()) || Integer.valueOf(1).equals(bom.getFrozenFlag())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_FROZEN, "BOM 已冻结或发布，请复制新版本后修改");
        }
        return bom;
    }

    private ProductBom requireBom(Long bomId) {
        ProductBom bom = bomRepository.selectById(bomId);
        if (bom == null || Integer.valueOf(1).equals(bom.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "BOM 不存在");
        }
        return bom;
    }

    private List<ProductBomRoute> activeRoutes(Long bomId) {
        return routeRepository.selectList(new LambdaQueryWrapper<ProductBomRoute>()
            .eq(ProductBomRoute::getProductBomId, bomId).eq(ProductBomRoute::getStatus, ACTIVE)
            .eq(ProductBomRoute::getDeletedFlag, 0));
    }

    private List<ProductBomRouteColor> activeColors(Long routeId) {
        return colorRepository.selectList(new LambdaQueryWrapper<ProductBomRouteColor>()
            .eq(ProductBomRouteColor::getProductBomRouteId, routeId).eq(ProductBomRouteColor::getStatus, ACTIVE)
            .eq(ProductBomRouteColor::getDeletedFlag, 0));
    }

    private List<ProductBomItem> activeItems(Long routeId) {
        return itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomRouteId, routeId).eq(ProductBomItem::getDeletedFlag, 0));
    }

    private List<ProductBomCostSnapshot> currentCosts(Long bomId, Long routeId) {
        return costRepository.selectList(new LambdaQueryWrapper<ProductBomCostSnapshot>()
            .eq(ProductBomCostSnapshot::getProductBomId, bomId)
            .eq(ProductBomCostSnapshot::getProductBomRouteId, routeId)
            .eq(ProductBomCostSnapshot::getStatus, "current")
            .eq(ProductBomCostSnapshot::getDeletedFlag, 0));
    }

    private void archiveExistingRoutes(Long bomId) {
        List<ProductBomRoute> existing = routeRepository.selectList(new LambdaQueryWrapper<ProductBomRoute>()
            .eq(ProductBomRoute::getProductBomId, bomId).eq(ProductBomRoute::getDeletedFlag, 0));
        if (existing == null) {
            return;
        }
        existing.forEach(route -> {
            route.setDeletedFlag(1);
            route.setStatus("inactive");
            touch(route);
            routeRepository.updateById(route);
        });
    }

    private void archiveCurrentCost(Long bomId, Long routeId) {
        List<ProductBomCostSnapshot> existing = currentCosts(bomId, routeId);
        existing.forEach(snapshot -> {
            snapshot.setStatus("history");
            touch(snapshot);
            costRepository.updateById(snapshot);
        });
    }

    private void fillCreate(com.yuewei.plm.repository.entity.BaseEntity entity, LocalDateTime now) {
        entity.setCreatedAt(now);
        entity.setCreatedBy("system");
        entity.setUpdatedAt(now);
        entity.setUpdatedBy("system");
        entity.setDeletedFlag(0);
    }

    private void touch(com.yuewei.plm.repository.entity.BaseEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy("system");
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, message);
    }
}
