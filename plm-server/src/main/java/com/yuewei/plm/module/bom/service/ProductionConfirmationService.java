package com.yuewei.plm.module.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.ProductionColorConfirmDTO;
import com.yuewei.plm.module.bom.dto.ProductionOperationConfirmDTO;
import com.yuewei.plm.module.bom.dto.ProductionRouteConfirmDTO;
import com.yuewei.plm.module.bom.entity.ProcessProductionOperationSelection;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomRoute;
import com.yuewei.plm.module.bom.entity.ProductBomRouteColor;
import com.yuewei.plm.module.bom.entity.ProductBomCostSnapshot;
import com.yuewei.plm.module.bom.entity.ProductBomRouteFormalSelection;
import com.yuewei.plm.module.bom.entity.ProductProductionColorDecision;
import com.yuewei.plm.module.bom.repository.ProcessProductionOperationSelectionRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteColorRepository;
import com.yuewei.plm.module.bom.repository.ProductBomCostSnapshotRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRouteFormalSelectionRepository;
import com.yuewei.plm.module.bom.repository.ProductProductionColorDecisionRepository;
import com.yuewei.plm.module.bom.vo.ProductionConfirmationVO;
import com.yuewei.plm.module.process.entity.ProcessEntity;
import com.yuewei.plm.module.process.repository.ProcessRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.service.CodeItemService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductionConfirmationService {
    private final ProductRepository productRepository;
    private final ProductBomRepository bomRepository;
    private final ProductBomRouteRepository routeRepository;
    private final ProcessRepository processRepository;
    private final ProductBomRouteColorRepository routeColorRepository;
    private final ProductBomCostSnapshotRepository costRepository;
    private final ProductBomRouteFormalSelectionRepository formalSelectionRepository;
    private final ProcessProductionOperationSelectionRepository operationRepository;
    private final ProductProductionColorDecisionRepository colorRepository;
    private final CodeItemService codeItemService;

    @Transactional
    public ProductionConfirmationVO confirmOperations(Long projectId, ProductionOperationConfirmDTO dto) {
        ProductBomRoute route = routeRepository.selectById(dto.getProductBomRouteId());
        if (route == null) {
            throw validation("工艺路线不属于当前项目或已失效");
        }
        ProductionRouteConfirmDTO.RouteSelection selection = new ProductionRouteConfirmDTO.RouteSelection();
        selection.setProcessId(route.getProcessId());
        selection.setProductBomId(route.getProductBomId());
        selection.setProductBomRouteId(route.getProductBomRouteId());
        selection.setOperationProcessIds(dto.getOperationProcessIds());
        ProductionRouteConfirmDTO command = new ProductionRouteConfirmDTO();
        command.setRoutes(List.of(selection));
        return confirmRoutes(projectId, command);
    }

    @Transactional
    public ProductionConfirmationVO confirmRoutes(Long projectId, ProductionRouteConfirmDTO dto) {
        Product project = requireProject(projectId);
        if (dto.getRoutes() == null || dto.getRoutes().isEmpty()) {
            throw validation("请至少选择一条工艺路线");
        }
        Set<Long> processIds = new HashSet<>();
        String batchNo = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        for (ProductionRouteConfirmDTO.RouteSelection selection : dto.getRoutes()) {
            if (selection.getOperationProcessIds() == null || selection.getOperationProcessIds().isEmpty()) {
                throw validation("请至少选择一道投产工序");
            }
            if (!processIds.add(selection.getProcessId())) {
                throw validation("同一工艺路线只能选择一份正式 BOM");
            }
            ProductBom bom = requireConfirmableBom(projectId, selection.getProductBomId());
            ProductBomRoute route = requireConfirmableBomRoute(projectId, bom, selection);
            List<ProcessEntity> operations = requireRouteOperations(route, selection.getOperationProcessIds());
            requireRouteCost(bom, route);
            archiveFormalSelections(projectId, route.getProcessId(), dto.getRemark());
            archiveOperationsByProcess(projectId, route.getProcessId());
            ProductBomRouteFormalSelection formal = new ProductBomRouteFormalSelection();
            formal.setProductId(projectId);
            formal.setProductBomId(bom.getProductBomId());
            formal.setProductBomRouteId(route.getProductBomRouteId());
            formal.setProcessId(route.getProcessId());
            formal.setBomVersionNo(bom.getVersionNo());
            formal.setSelectionBatchNo(batchNo);
            formal.setStatus("active");
            formal.setConfirmedAt(now);
            formal.setConfirmedBy("system");
            formal.setRemark(dto.getRemark());
            fillCreate(formal, now);
            formalSelectionRepository.insert(formal);
            if (!"formal".equals(bom.getBomScope())) {
                bom.setBomScope("formal");
                bom.setUpdatedAt(now);
                bom.setUpdatedBy("system");
                bomRepository.updateById(bom);
            }
            ProcessEntity routeProcess = processRepository.selectById(route.getProcessId());
            String routeVersionNo = routeProcess == null ? null : routeProcess.getVersionNo();
            for (ProcessEntity operation : operations) {
                ProcessProductionOperationSelection value = new ProcessProductionOperationSelection();
                value.setProductId(projectId);
                value.setProductBomRouteId(route.getProductBomRouteId());
                value.setProcessId(route.getProcessId());
                value.setOperationProcessId(operation.getProcessId());
                value.setRouteVersionNo(routeVersionNo);
                value.setSelectionBatchNo(batchNo);
                value.setStatus("confirmed");
                value.setConfirmedAt(now);
                value.setConfirmedBy("system");
                fillCreate(value, now);
                operationRepository.insert(value);
            }
        }
        return get(project.getProductId());
    }

    @Transactional
    public ProductionConfirmationVO confirmColors(Long projectId, ProductionColorConfirmDTO dto) {
        Product project = requireProject(projectId);
        if (dto.getColors() == null || dto.getColors().isEmpty()) {
            throw validation("请至少选择一个批量投产颜色");
        }
        List<ProductionColorConfirmDTO.ColorSelection> colors = dto.getColors().stream()
            .filter(value -> value.getColorName() != null && !value.getColorName().isBlank())
            .collect(java.util.stream.Collectors.toMap(
                value -> value.getColorName().trim().toLowerCase(Locale.ROOT),
                value -> value,
                (first, ignored) -> first,
                java.util.LinkedHashMap::new
            )).values().stream().toList();
        for (ProductionColorConfirmDTO.ColorSelection color : colors) {
            codeItemService.requireEnabledColor(color.getCodeItemId(), color.getColorCode());
            requireColorRoute(projectId, color);
        }
        archiveColors(projectId);
        String batchNo = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int createdSkuCount = 0;
        for (ProductionColorConfirmDTO.ColorSelection color : colors) {
            Product sku = findSku(projectId, color.getColorName());
            if ("model_variant".equals(project.getProductType()) && sku == null) {
                sku = createSku(project, color.getColorName(), now);
                productRepository.insert(sku);
                createdSkuCount++;
            }
            ProductProductionColorDecision decision = new ProductProductionColorDecision();
            decision.setProductId(projectId);
            decision.setCodeItemId(color.getCodeItemId());
            decision.setColorCode(color.getColorCode());
            decision.setColorName(color.getColorName().trim());
            decision.setProductBomId(color.getProductBomId());
            decision.setProductBomRouteId(color.getProductBomRouteId());
            decision.setDecisionBatchNo(batchNo);
            decision.setSelectedFlag(1);
            decision.setStatus("confirmed");
            decision.setCreatedSkuProductId(sku == null ? null : sku.getProductId());
            decision.setConfirmedAt(now);
            decision.setConfirmedBy("system");
            fillCreate(decision, now);
            colorRepository.insert(decision);
        }
        ProductionConfirmationVO result = get(projectId);
        result.setSelectedColorCount(colors.size());
        result.setColors(colors.stream().map(value -> value.getColorName().trim()).toList());
        result.setCreatedSkuCount(createdSkuCount);
        return result;
    }

    public ProductionConfirmationVO get(Long projectId) {
        requireProject(projectId);
        List<ProcessProductionOperationSelection> operations = safe(operationRepository.selectList(
            new LambdaQueryWrapper<ProcessProductionOperationSelection>()
                .eq(ProcessProductionOperationSelection::getProductId, projectId)
                .eq(ProcessProductionOperationSelection::getStatus, "confirmed")
                .eq(ProcessProductionOperationSelection::getDeletedFlag, 0)));
        List<ProductProductionColorDecision> colors = safe(colorRepository.selectList(
            new LambdaQueryWrapper<ProductProductionColorDecision>()
                .eq(ProductProductionColorDecision::getProductId, projectId)
                .eq(ProductProductionColorDecision::getStatus, "confirmed")
                .eq(ProductProductionColorDecision::getDeletedFlag, 0)));
        Map<Long, List<Long>> operationIdsByRoute = operations.stream()
            .filter(selection -> selection.getProductBomRouteId() != null)
            .collect(Collectors.groupingBy(ProcessProductionOperationSelection::getProductBomRouteId,
                Collectors.mapping(ProcessProductionOperationSelection::getOperationProcessId, Collectors.toList())));
        List<ProductionConfirmationVO.RouteSelectionVO> routeSelections = activeFormalSelections(projectId).stream()
            .map(selection -> {
                ProductBomRoute route = routeRepository.selectById(selection.getProductBomRouteId());
                return ProductionConfirmationVO.RouteSelectionVO.builder()
                    .processId(selection.getProcessId())
                    .productBomId(selection.getProductBomId())
                    .productBomRouteId(selection.getProductBomRouteId())
                    .routeName(route == null ? null : route.getRouteName())
                    .bomVersionNo(selection.getBomVersionNo())
                    .operationProcessIds(operationIdsByRoute.getOrDefault(selection.getProductBomRouteId(), List.of()))
                    .build();
            }).toList();
        return ProductionConfirmationVO.builder().productId(projectId)
            .selectedOperationCount(operations.size()).selectedColorCount(colors.size()).createdSkuCount(0)
            .operationProcessIds(operations.stream().map(ProcessProductionOperationSelection::getOperationProcessId).toList())
            .routeSelections(routeSelections)
            .colors(colors.stream().map(ProductProductionColorDecision::getColorName).toList()).build();
    }

    public void requireOperationsConfirmed(Long projectId) {
        requireProject(projectId);
        List<ProductBomRouteFormalSelection> selections = activeFormalSelections(projectId);
        if (selections.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "没有可确认的有效 BOM 路线");
        }
        for (ProductBomRouteFormalSelection selection : selections) {
            ProductBomRoute route = routeRepository.selectById(selection.getProductBomRouteId());
            if (route == null || !"active".equals(route.getStatus()) || Integer.valueOf(1).equals(route.getDeletedFlag())) {
                throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL,
                    "已确认路线失效，请重新敲定工序");
            }
            ProcessEntity routeProcess = processRepository.selectById(route.getProcessId());
            String currentVersion = routeProcess == null ? null : routeProcess.getVersionNo();
            boolean confirmed = safe(operationRepository.selectList(
                new LambdaQueryWrapper<ProcessProductionOperationSelection>()
                    .eq(ProcessProductionOperationSelection::getProductId, projectId)
                    .eq(ProcessProductionOperationSelection::getProductBomRouteId, route.getProductBomRouteId())
                    .eq(ProcessProductionOperationSelection::getRouteVersionNo, currentVersion)
                    .eq(ProcessProductionOperationSelection::getStatus, "confirmed")
                    .eq(ProcessProductionOperationSelection::getDeletedFlag, 0))).stream()
                .anyMatch(selection -> validOperation(route, selection.getOperationProcessId()));
            if (!confirmed) {
                throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL,
                    "路线 " + route.getRouteName() + " 尚未确认有效投产工序");
            }
        }
    }

    public void requireBomRoutesDetermined(Long projectId) {
        requireProject(projectId);
        List<ProductBom> boms = activeProjectBoms(projectId);
        boolean ready = boms.stream().anyMatch(bom -> !safe(routeRepository.selectList(
            new LambdaQueryWrapper<ProductBomRoute>()
                .eq(ProductBomRoute::getProductBomId, bom.getProductBomId())
                .eq(ProductBomRoute::getStatus, "active").eq(ProductBomRoute::getDeletedFlag, 0))).isEmpty());
        if (!ready) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL,
                "BOM 与工艺路线尚未定版，不能推进到敲定工序");
        }
    }

    public void requireColorsConfirmed(Long projectId) {
        if (get(projectId).getSelectedColorCount() < 1) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "正式投产颜色尚未确认，不能完成发布");
        }
    }

    private ProductBom requireConfirmableBom(Long projectId, Long productBomId) {
        ProductBom bom = bomRepository.selectById(productBomId);
        if (bom == null || !projectId.equals(bom.getProductId()) || Integer.valueOf(1).equals(bom.getDeletedFlag())) {
            throw validation("正式 BOM 必须属于当前项目");
        }
        if ("archived".equals(bom.getStatus())) {
            throw validation("已归档 BOM 不能选为正式 BOM");
        }
        return bom;
    }

    private ProductBomRoute requireConfirmableBomRoute(Long projectId, ProductBom bom,
                                                       ProductionRouteConfirmDTO.RouteSelection selection) {
        ProductBomRoute route = routeRepository.selectById(selection.getProductBomRouteId());
        if (route == null || Integer.valueOf(1).equals(route.getDeletedFlag()) || !"active".equals(route.getStatus())) {
            throw validation("正式 BOM 路线无效");
        }
        if (!projectId.equals(route.getProductId()) || !bom.getProductBomId().equals(route.getProductBomId())) {
            throw validation("正式 BOM 路线不属于当前项目或当前 BOM");
        }
        if (!selection.getProcessId().equals(route.getProcessId())) {
            throw validation("正式 BOM 路线与工艺路线不一致");
        }
        return route;
    }

    private List<ProcessEntity> requireRouteOperations(ProductBomRoute route, List<Long> operationProcessIds) {
        return operationProcessIds.stream().filter(Objects::nonNull).distinct().map(operationId -> {
            ProcessEntity operation = processRepository.selectById(operationId);
            if (operation == null || !route.getProcessId().equals(operation.getParentProcessId())
                || Integer.valueOf(1).equals(operation.getDeletedFlag())) {
                throw validation("所选工序不属于当前工艺路线");
            }
            return operation;
        }).toList();
    }

    private void requireRouteCost(ProductBom bom, ProductBomRoute route) {
        boolean hasCost = !safe(costRepository.selectList(new LambdaQueryWrapper<ProductBomCostSnapshot>()
            .eq(ProductBomCostSnapshot::getProductBomId, bom.getProductBomId())
            .eq(ProductBomCostSnapshot::getProductBomRouteId, route.getProductBomRouteId())
            .in(ProductBomCostSnapshot::getStatus, List.of("current", "baseline"))
            .eq(ProductBomCostSnapshot::getDeletedFlag, 0))).isEmpty();
        if (!hasCost) {
            throw validation("路线 " + route.getRouteName() + " 尚未完成成本计算");
        }
    }

    private void archiveFormalSelections(Long projectId, Long processId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        for (ProductBomRouteFormalSelection selection : activeFormalSelections(projectId, processId)) {
            selection.setStatus("invalidated");
            selection.setInvalidatedAt(now);
            selection.setInvalidatedReason(reason == null || reason.isBlank() ? "重新敲定正式 BOM" : reason);
            selection.setUpdatedAt(now);
            selection.setUpdatedBy("system");
            formalSelectionRepository.updateById(selection);
        }
    }

    private void archiveOperationsByProcess(Long projectId, Long processId) {
        LocalDateTime now = LocalDateTime.now();
        for (ProcessProductionOperationSelection value : safe(operationRepository.selectList(
            new LambdaQueryWrapper<ProcessProductionOperationSelection>()
                .eq(ProcessProductionOperationSelection::getProductId, projectId)
                .eq(ProcessProductionOperationSelection::getProcessId, processId)
                .eq(ProcessProductionOperationSelection::getDeletedFlag, 0)))) {
            value.setStatus("archived");
            value.setDeletedFlag(1);
            value.setUpdatedAt(now);
            value.setUpdatedBy("system");
            operationRepository.updateById(value);
        }
    }

    private boolean hasActiveFormalSelection(Long projectId, Long bomId, Long routeId, Long processId) {
        return activeFormalSelections(projectId, processId).stream()
            .anyMatch(selection -> bomId.equals(selection.getProductBomId())
                && routeId.equals(selection.getProductBomRouteId()));
    }

    private void requireColorRoute(Long projectId, ProductionColorConfirmDTO.ColorSelection color) {
        ProductBom bom = bomRepository.selectById(color.getProductBomId());
        ProductBomRoute route = routeRepository.selectById(color.getProductBomRouteId());
        if (bom == null || !projectId.equals(bom.getProductId())
            || !List.of("reviewing", "released").contains(bom.getStatus())) {
            throw validation("投产颜色必须关联当前项目已定版 BOM");
        }
        if (route == null || !bom.getProductBomId().equals(route.getProductBomId()) || !"active".equals(route.getStatus())) {
            throw validation("投产颜色关联的工艺路线无效");
        }
        if (!hasActiveFormalSelection(projectId, bom.getProductBomId(), route.getProductBomRouteId(), route.getProcessId())) {
            throw validation("投产颜色必须引用已确认的正式 BOM 路线");
        }
        boolean belongsToRoute = !safe(routeColorRepository.selectList(new LambdaQueryWrapper<ProductBomRouteColor>()
            .eq(ProductBomRouteColor::getProductBomRouteId, route.getProductBomRouteId())
            .eq(ProductBomRouteColor::getCodeItemId, color.getCodeItemId())
            .eq(ProductBomRouteColor::getColorCode, color.getColorCode())
            .eq(ProductBomRouteColor::getStatus, "active")
            .eq(ProductBomRouteColor::getDeletedFlag, 0))).isEmpty();
        if (!belongsToRoute) {
            throw validation("投产颜色不属于所选工艺路线");
        }
        boolean hasCost = !safe(costRepository.selectList(new LambdaQueryWrapper<ProductBomCostSnapshot>()
            .eq(ProductBomCostSnapshot::getProductBomId, bom.getProductBomId())
            .eq(ProductBomCostSnapshot::getProductBomRouteId, route.getProductBomRouteId())
            .in(ProductBomCostSnapshot::getStatus, List.of("current", "baseline"))
            .eq(ProductBomCostSnapshot::getDeletedFlag, 0))).isEmpty();
        if (!hasCost) {
            throw validation("投产颜色关联路线缺少有效成本快照");
        }
        if (!hasConfirmedOperation(projectId, route)) {
            throw validation("投产颜色关联路线尚未确认正式工序");
        }
    }

    private boolean hasConfirmedOperation(Long projectId, ProductBomRoute route) {
        ProcessEntity routeProcess = processRepository.selectById(route.getProcessId());
        String currentVersion = routeProcess == null ? null : routeProcess.getVersionNo();
        return safe(operationRepository.selectList(new LambdaQueryWrapper<ProcessProductionOperationSelection>()
            .eq(ProcessProductionOperationSelection::getProductId, projectId)
            .eq(ProcessProductionOperationSelection::getProductBomRouteId, route.getProductBomRouteId())
            .eq(ProcessProductionOperationSelection::getRouteVersionNo, currentVersion)
            .eq(ProcessProductionOperationSelection::getStatus, "confirmed")
            .eq(ProcessProductionOperationSelection::getDeletedFlag, 0))).stream()
            .anyMatch(selection -> validOperation(route, selection.getOperationProcessId()));
    }

    private boolean validOperation(ProductBomRoute route, Long operationId) {
        ProcessEntity operation = processRepository.selectById(operationId);
        return operation != null && route.getProcessId().equals(operation.getParentProcessId())
            && !Integer.valueOf(1).equals(operation.getDeletedFlag());
    }

    private List<ProductBom> activeProjectBoms(Long projectId) {
        return safe(bomRepository.selectList(new LambdaQueryWrapper<ProductBom>()
            .eq(ProductBom::getProductId, projectId)
            .notIn(ProductBom::getStatus, List.of("archived")).eq(ProductBom::getDeletedFlag, 0)));
    }

    private List<ProductBomRouteFormalSelection> activeFormalSelections(Long projectId) {
        return safe(formalSelectionRepository.selectList(new LambdaQueryWrapper<ProductBomRouteFormalSelection>()
            .eq(ProductBomRouteFormalSelection::getProductId, projectId)
            .eq(ProductBomRouteFormalSelection::getStatus, "active")
            .eq(ProductBomRouteFormalSelection::getDeletedFlag, 0))).stream()
            .filter(selection -> "active".equals(selection.getStatus()) && !Integer.valueOf(1).equals(selection.getDeletedFlag()))
            .toList();
    }

    private List<ProductBomRouteFormalSelection> activeFormalSelections(Long projectId, Long processId) {
        return safe(formalSelectionRepository.selectList(new LambdaQueryWrapper<ProductBomRouteFormalSelection>()
            .eq(ProductBomRouteFormalSelection::getProductId, projectId)
            .eq(ProductBomRouteFormalSelection::getProcessId, processId)
            .eq(ProductBomRouteFormalSelection::getStatus, "active")
            .eq(ProductBomRouteFormalSelection::getDeletedFlag, 0))).stream()
            .filter(selection -> "active".equals(selection.getStatus()) && !Integer.valueOf(1).equals(selection.getDeletedFlag()))
            .toList();
    }

    private List<ProductBomRoute> activeRoutes(Long bomId) {
        return safe(routeRepository.selectList(new LambdaQueryWrapper<ProductBomRoute>()
            .eq(ProductBomRoute::getProductBomId, bomId).eq(ProductBomRoute::getStatus, "active")
            .eq(ProductBomRoute::getDeletedFlag, 0)));
    }

    private Product findSku(Long projectId, String colorName) {
        List<Product> rows = productRepository.selectList(new LambdaQueryWrapper<Product>()
            .eq(Product::getParentProductId, projectId).eq(Product::getProductType, "sku")
            .eq(Product::getColor, colorName.trim()).eq(Product::getDeletedFlag, 0));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private Product createSku(Product project, String colorName, LocalDateTime now) {
        Product sku = new Product();
        sku.setParentProductId(project.getProductId());
        sku.setProductCode(project.getProductCode() + "-" + normalizedCode(colorName));
        sku.setProductName(project.getProductName() + " " + project.getModel() + " " + colorName.trim());
        sku.setProductType("sku");
        sku.setSeriesName(project.getSeriesName());
        sku.setModel(project.getModel());
        sku.setColor(colorName.trim());
        sku.setVersionNo(project.getVersionNo());
        sku.setStatus("released");
        sku.setReleasedAt(now);
        sku.setReleasedBy("system");
        sku.setCreatedAt(now);
        sku.setCreatedBy("system");
        sku.setUpdatedAt(now);
        sku.setUpdatedBy("system");
        sku.setDeletedFlag(0);
        return sku;
    }

    private String normalizedCode(String colorName) {
        String code = colorName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        return code.isBlank() ? Integer.toHexString(colorName.hashCode()).toUpperCase(Locale.ROOT) : code;
    }

    private void archiveOperations(Long productId, Long routeId) {
        for (ProcessProductionOperationSelection value : safe(operationRepository.selectList(
            new LambdaQueryWrapper<ProcessProductionOperationSelection>()
                .eq(ProcessProductionOperationSelection::getProductId, productId)
                .eq(ProcessProductionOperationSelection::getProductBomRouteId, routeId)
                .eq(ProcessProductionOperationSelection::getDeletedFlag, 0)))) {
            value.setStatus("archived");
            value.setDeletedFlag(1);
            value.setUpdatedAt(LocalDateTime.now());
            value.setUpdatedBy("system");
            operationRepository.updateById(value);
        }
    }

    private void archiveColors(Long productId) {
        for (ProductProductionColorDecision value : safe(colorRepository.selectList(
            new LambdaQueryWrapper<ProductProductionColorDecision>()
                .eq(ProductProductionColorDecision::getProductId, productId)
                .eq(ProductProductionColorDecision::getDeletedFlag, 0)))) {
            value.setStatus("archived");
            value.setDeletedFlag(1);
            value.setUpdatedAt(LocalDateTime.now());
            value.setUpdatedBy("system");
            colorRepository.updateById(value);
        }
    }

    private Product requireProject(Long projectId) {
        Product project = productRepository.selectById(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, message);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private void fillCreate(com.yuewei.plm.repository.entity.BaseEntity value, LocalDateTime now) {
        value.setCreatedAt(now);
        value.setCreatedBy("system");
        value.setUpdatedAt(now);
        value.setUpdatedBy("system");
        value.setDeletedFlag(0);
    }
}
