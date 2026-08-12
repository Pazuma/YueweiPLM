package com.yuewei.plm.module.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.dto.BomRouteColorDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.dto.TestBomSaveDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomCostSnapshot;
import com.yuewei.plm.module.bom.entity.ProductBomItem;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.entity.ProductBomRouteFormalSelection;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomItemRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteFormalSelectionRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.service.CodeItemService;
import com.yuewei.plm.module.process.entity.ProcessEntity;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductBomWorkflowService {
    private static final String ACTIVE = "active";
    private static final String PROCESS_TYPE_ROUTING = "routing";
    private static final String DEFAULT_VARIANT = "BASE";

    private final ProductBomRepository bomRepository;
    private final ProductBomRouteRepository routeRepository;
    private final ProductBomRouteColorRepository colorRepository;
    private final ProductBomItemRepository itemRepository;
    private final ProductBomCostSnapshotRepository costRepository;
    private final ProductBomRouteFormalSelectionRepository formalSelectionRepository;
    private final BomCostCalculator costCalculator;
    private final BomTimelineGate timelineGate;
    private final CodeItemService codeItemService;
    private final ProcessRepository processRepository;

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
        String operator = "system";
        String batchNo = UUID.randomUUID().toString();
        List<ProductBomRoute> createdRoutes = new java.util.ArrayList<>();
        for (BomRouteSaveDTO dto : routes) {
            ProcessEntity processRoute = requireProductRoute(bom.getProductId(), dto.getProcessId());
            ProductBomRoute route = new ProductBomRoute();
            route.setProductBomId(bomId);
            route.setProductId(bom.getProductId());
            route.setProcessId(processRoute.getProcessId());
            route.setRouteCode(processRoute.getProcessCode());
            route.setRouteName(processRoute.getProcessName());
            route.setSharedBomGroupCode(blankToDefault(dto.getSharedBomGroupCode(), "BOM-" + bomId));
            route.setRouteVariantNo(blankToDefault(dto.getRouteVariantNo(), DEFAULT_VARIANT));
            route.setVariantName(blankToDefault(dto.getVariantName(), "基础用料"));
            route.setVariantSourceType(dto.getSourceProductBomRouteId() == null ? "manual" : "copied");
            route.setSourceProductBomRouteId(dto.getSourceProductBomRouteId());
            route.setStatus(ACTIVE);
            fillCreate(route, now);
            routeRepository.insert(route);
            createdRoutes.add(route);
            for (BomRouteColorDTO selected : normalizedColors(dto)) {
                CodeItem code = codeItemService.requireEnabledColor(selected.getCodeItemId(), selected.getCodeValue());
                ProductBomRouteColor color = new ProductBomRouteColor();
                color.setProductBomId(bomId);
                color.setProductBomRouteId(route.getProductBomRouteId());
                color.setCodeItemId(code.getCodeItemId());
                color.setColorCode(code.getCodeValue());
                color.setColorName(code.getCodeName());
                color.setStatus(ACTIVE);
                fillCreate(color, now);
                colorRepository.insert(color);
            }
            for (ProductBomItemDTO itemDTO : dto.getItems()) {
                ProductBomItem item = toItem(bom, route, itemDTO, now);
                itemRepository.insert(item);
            }
        }
        syncFormalSelections(bom, createdRoutes, now, operator, batchNo);
    }

    @Transactional
    public List<ProductBomCostSnapshot> recalculateCosts(Long bomId, List<BomRouteSaveDTO> costInputs) {
        ProductBom bom = requireEditable(bomId);
        List<ProductBomRoute> routes = activeRoutes(bomId);
        List<BomRouteSaveDTO> inputs = costInputs == null ? List.of() : costInputs;
        return routes.stream().map(route -> {
            List<ProductBomItem> items = activeItems(route.getProductBomRouteId());
            BomRouteSaveDTO input = inputs.stream()
                .filter(value -> value.getProductBomRouteId() != null
                    ? value.getProductBomRouteId().equals(route.getProductBomRouteId())
                    : value.getProcessId() != null && value.getProcessId().equals(route.getProcessId()))
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
        return publish(bomId);
    }

    @Transactional
    public ProductBom freeze(Long bomId) {
        ProductBom bom = requireBom(bomId);
        timelineGate.requireFreezeOrPublishNode(bom.getProductId());
        if (!List.of("draft", "released", "frozen").contains(bom.getStatus())) {
            throw validation("只有草稿、已冻结或已发布的正式 BOM 可以冻结");
        }
        requireCompleteRoutes(bomId, true);
        bom.setFrozenFlag(1);
        if (!"released".equals(bom.getStatus())) {
            bom.setStatus("frozen");
        }
        bom.setFrozenAt(LocalDateTime.now());
        bom.setFrozenBy("system");
        touch(bom);
        bomRepository.updateById(bom);
        return bom;
    }

    @Transactional
    public ProductBom publish(Long bomId) {
        ProductBom bom = requireBom(bomId);
        timelineGate.requireFreezeOrPublishNode(bom.getProductId());
        if (!List.of("draft", "frozen", "released").contains(bom.getStatus())) {
            throw validation("只有草稿、已冻结或已发布的正式 BOM 可以发布");
        }
        requireCompleteRoutes(bomId, true);
        bom.setFrozenFlag(0);
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
        Set<String> assignedVariants = new HashSet<>();
        Map<String, Long> groupProcessIds = new HashMap<>();
        for (BomRouteSaveDTO route : routes) {
            String groupCode = blankToDefault(route.getSharedBomGroupCode(), "DEFAULT");
            Long groupProcessId = groupProcessIds.putIfAbsent(groupCode, route.getProcessId());
            if (groupProcessId != null && !groupProcessId.equals(route.getProcessId())) {
                throw validation("同一套 BOM 的颜色副本必须绑定同一条工艺路线");
            }
            String variantKey = groupCode
                + ":" + blankToDefault(route.getRouteVariantNo(), DEFAULT_VARIANT);
            if (!assignedVariants.add(variantKey)) {
                throw validation("同一套 BOM 的副本编号不能重复");
            }
            if ((route.getColorItems() == null || route.getColorItems().isEmpty())
                && (route.getColors() == null || route.getColors().isEmpty())) {
                throw validation("工艺路线至少需要一个适用颜色");
            }
            if (route.getItems() == null || route.getItems().isEmpty()) {
                throw validation("颜色副本至少需要一条 BOM 明细");
            }
            for (BomRouteColorDTO color : normalizedColors(route)) {
                String normalized = color.getCodeValue() == null ? "" : color.getCodeValue().trim();
                if (normalized.isEmpty()) {
                    throw validation("适用颜色不能为空");
                }
                if (!assignedColors.add(normalized)) {
                    throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT,
                        "颜色编码 " + normalized + " 不能同时归属多条有效工艺路线");
                }
            }
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private List<BomRouteColorDTO> normalizedColors(BomRouteSaveDTO route) {
        if (route.getColorItems() != null && !route.getColorItems().isEmpty()) return route.getColorItems();
        return route.getColors().stream().map(name -> {
            BomRouteColorDTO value = new BomRouteColorDTO();
            value.setCodeItemId(null); value.setCodeValue(name); value.setCodeName(name);
            return value;
        }).toList();
    }

    private ProcessEntity requireProductRoute(Long productId, Long processId) {
        ProcessEntity route = processRepository.selectById(processId);
        if (route == null || Integer.valueOf(1).equals(route.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "工艺路线不存在");
        }
        if (!productId.equals(route.getProductId()) || !PROCESS_TYPE_ROUTING.equals(route.getProcessType())) {
            throw validation("请选择当前产品下已有的有效工艺路线");
        }
        if ("archived".equals(route.getStatus())) {
            throw validation("工艺路线已归档，不能关联 BOM");
        }
        return route;
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
        if (dto.getUnitCost() != null && dto.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
            throw validation("单价不能为负数");
        }
        if (dto.getLineCost() != null && dto.getLineCost().compareTo(BigDecimal.ZERO) < 0) {
            throw validation("单个成本不能为负数");
        }
        BigDecimal unitCost = dto.getUnitCost() == null ? BigDecimal.ZERO : dto.getUnitCost();
        BigDecimal lineCost = dto.getLineCost() == null ? dto.getQuantity().multiply(unitCost) : dto.getLineCost();
        ProductBomItem item = new ProductBomItem();
        item.setProductBomId(bom.getProductBomId());
        item.setProductBomRouteId(route.getProductBomRouteId());
        item.setSharedBomGroupCode(route.getSharedBomGroupCode());
        item.setProductId(bom.getProductId());
        item.setInventoryId(dto.getInventoryId());
        item.setItemCode(dto.getItemCode());
        item.setItemName(dto.getItemName());
        item.setSpecification(dto.getSpecification());
        item.setLineNo(dto.getLineNo());
        item.setQuantity(dto.getQuantity());
        item.setUnit(dto.getUnit());
        item.setLossRate(dto.getLossRate() == null ? BigDecimal.ZERO : dto.getLossRate());
        item.setUnitCostSnapshot(unitCost);
        item.setSupplierCodeSnapshot(dto.getSupplierCode());
        item.setSupplierNameSnapshot(dto.getSupplierName());
        item.setLineCostSnapshot(lineCost);
        item.setSubstituteFlag(dto.getSubstituteFlag() == null ? 0 : dto.getSubstituteFlag());
        item.setRemark(dto.getRemark());
        item.setVersionNo(bom.getVersionNo());
        item.setStatus("draft");
        item.setCurrencyCode(hasText(dto.getCurrencyCode()) ? dto.getCurrencyCode().trim() :
            (bom.getCurrencyCode() == null ? "CNY" : bom.getCurrencyCode()));
        item.setMaterialSource(normalizeMaterialSource(dto));
        item.setUnmatchedFlag(normalizeUnmatchedFlag(dto, item.getMaterialSource()));
        fillCreate(item, now);
        return item;
    }

    private String normalizeMaterialSource(ProductBomItemDTO dto) {
        if (hasText(dto.getMaterialSource())) {
            return dto.getMaterialSource().trim();
        }
        return Integer.valueOf(1).equals(dto.getUnmatchedFlag()) ? "manual" : "inventory";
    }

    private Integer normalizeUnmatchedFlag(ProductBomItemDTO dto, String materialSource) {
        if (dto.getUnmatchedFlag() != null) {
            return dto.getUnmatchedFlag();
        }
        return "manual".equals(materialSource) ? 1 : 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        if ("archived".equals(bom.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_FROZEN, "已归档 BOM 不可修改");
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
        return safeList(routeRepository.selectList(new LambdaQueryWrapper<ProductBomRoute>()
            .eq(ProductBomRoute::getProductBomId, bomId).eq(ProductBomRoute::getStatus, ACTIVE)
            .eq(ProductBomRoute::getDeletedFlag, 0)));
    }

    private List<ProductBomRouteColor> activeColors(Long routeId) {
        return safeList(colorRepository.selectList(new LambdaQueryWrapper<ProductBomRouteColor>()
            .eq(ProductBomRouteColor::getProductBomRouteId, routeId).eq(ProductBomRouteColor::getStatus, ACTIVE)
            .eq(ProductBomRouteColor::getDeletedFlag, 0)));
    }

    private List<ProductBomItem> activeItems(Long routeId) {
        return safeList(itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomRouteId, routeId).eq(ProductBomItem::getDeletedFlag, 0)));
    }

    private List<ProductBomCostSnapshot> currentCosts(Long bomId, Long routeId) {
        return safeList(costRepository.selectList(new LambdaQueryWrapper<ProductBomCostSnapshot>()
            .eq(ProductBomCostSnapshot::getProductBomId, bomId)
            .eq(ProductBomCostSnapshot::getProductBomRouteId, routeId)
            .eq(ProductBomCostSnapshot::getStatus, "current")
            .eq(ProductBomCostSnapshot::getDeletedFlag, 0)));
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
        List<ProductBomRouteColor> colors = colorRepository.selectList(new LambdaQueryWrapper<ProductBomRouteColor>()
            .eq(ProductBomRouteColor::getProductBomId, bomId).eq(ProductBomRouteColor::getDeletedFlag, 0));
        if (colors != null) {
            colors.forEach(color -> {
                color.setDeletedFlag(1);
                color.setStatus("inactive");
                touch(color);
                colorRepository.updateById(color);
            });
        }
        List<ProductBomItem> items = itemRepository.selectList(new LambdaQueryWrapper<ProductBomItem>()
            .eq(ProductBomItem::getProductBomId, bomId).eq(ProductBomItem::getDeletedFlag, 0));
        if (items != null) {
            items.forEach(item -> {
                item.setDeletedFlag(1);
                touch(item);
                itemRepository.updateById(item);
            });
        }
    }

    private void syncFormalSelections(ProductBom bom, List<ProductBomRoute> routes, LocalDateTime now,
                                      String operator, String batchNo) {
        if (!isFormalBom(bom) || routes == null || routes.isEmpty()) {
            return;
        }
        List<ProductBomRouteFormalSelection> existing = formalSelectionRepository.selectList(
            new LambdaQueryWrapper<ProductBomRouteFormalSelection>()
                .eq(ProductBomRouteFormalSelection::getProductId, bom.getProductId())
                .eq(ProductBomRouteFormalSelection::getProductBomId, bom.getProductBomId())
                .eq(ProductBomRouteFormalSelection::getStatus, ACTIVE)
                .eq(ProductBomRouteFormalSelection::getDeletedFlag, 0));
        if (existing != null) {
            for (ProductBomRouteFormalSelection selection : existing) {
                selection.setStatus("invalidated");
                selection.setInvalidatedAt(now);
                selection.setInvalidatedReason("重新保存工艺路线后同步正式选择");
                selection.setUpdatedAt(now);
                selection.setUpdatedBy(operator);
                formalSelectionRepository.updateById(selection);
            }
        }
        for (ProductBomRoute route : routes) {
            ProductBomRouteFormalSelection selection = new ProductBomRouteFormalSelection();
            selection.setProductId(bom.getProductId());
            selection.setProductBomId(bom.getProductBomId());
            selection.setProductBomRouteId(route.getProductBomRouteId());
            selection.setProcessId(route.getProcessId());
            selection.setBomVersionNo(bom.getVersionNo());
            selection.setSelectionBatchNo(batchNo);
            selection.setStatus(ACTIVE);
            selection.setConfirmedAt(now);
            selection.setConfirmedBy(operator);
            selection.setRemark("sync formal selection after route save");
            fillCreate(selection, now);
            formalSelectionRepository.insert(selection);
        }
    }

    private boolean isFormalBom(ProductBom bom) {
        return "formal".equals(bom.getBomScope())
            || "released".equals(bom.getStatus())
            || "frozen".equals(bom.getStatus());
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
