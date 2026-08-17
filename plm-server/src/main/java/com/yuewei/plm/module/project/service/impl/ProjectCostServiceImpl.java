package com.yuewei.plm.module.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.bom.service.BomLedgerService;
import com.yuewei.plm.module.bom.vo.BomSummaryVO;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.dto.ProjectCostItemCreateDTO;
import com.yuewei.plm.module.project.dto.ProjectCostItemUpdateDTO;
import com.yuewei.plm.module.project.entity.ProjectCostItem;
import com.yuewei.plm.module.project.repository.ProjectCostItemRepository;
import com.yuewei.plm.module.project.service.ProjectCostService;
import com.yuewei.plm.module.project.vo.ProjectCostItemVO;
import com.yuewei.plm.module.project.vo.ProjectCostSummaryVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.BaseEntity;
import com.yuewei.plm.repository.entity.Product;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectCostServiceImpl implements ProjectCostService {

    private static final String CATEGORY_MOLD = "mold";
    private static final String CATEGORY_OTHER = "other";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_VOID = "void";
    private static final String DEFAULT_CURRENCY = "CNY";

    private final ProductRepository productRepository;
    private final ProjectCostItemRepository costItemRepository;
    private final BomLedgerService bomLedgerService;
    private final OperationLogService operationLogService;

    @Override
    public ProjectCostSummaryVO getSummary(Long projectId) {
        Product product = getProductOrThrow(projectId);
        BomSummaryVO bomSummary = bomLedgerService.getSummary(product.getProductId());
        List<ProjectCostItem> items = activeItems(product.getProductId());
        BigDecimal moldCost = sumConfirmed(items, CATEGORY_MOLD);
        BigDecimal otherCost = sumConfirmed(items, CATEGORY_OTHER);
        BigDecimal rdCost = bomSummary == null ? BigDecimal.ZERO : zero(bomSummary.getRdTotalCost());
        BigDecimal bomCost = bomSummary == null ? null : bomSummary.getCurrentBomSkuUnitCost();
        return ProjectCostSummaryVO.builder()
            .projectId(product.getProductId())
            .productId(product.getProductId())
            .bomCost(bomCost)
            .rdCost(rdCost)
            .moldCost(moldCost)
            .otherCost(otherCost)
            .totalCost(rdCost.add(moldCost).add(otherCost))
            .currencyCode(DEFAULT_CURRENCY)
            .manualItemCount(items.size())
            .confirmedManualItemCount((int) items.stream().filter(item -> STATUS_CONFIRMED.equals(item.getStatus())).count())
            .build();
    }

    @Override
    public List<ProjectCostItemVO> listItems(Long projectId) {
        Product product = getProductOrThrow(projectId);
        return activeItems(product.getProductId()).stream().map(ProjectCostItemVO::from).toList();
    }

    @Override
    @Transactional
    public ProjectCostItemVO createItem(Long projectId, ProjectCostItemCreateDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        String operator = currentUserName();
        LocalDateTime now = LocalDateTime.now();
        ProjectCostItem entity = new ProjectCostItem();
        entity.setProductId(product.getProductId());
        applyCreateFields(entity, dto);
        entity.setStatus(STATUS_DRAFT);
        fillCreateAudit(entity, now, operator);
        costItemRepository.insert(entity);
        writeLog(product, OperationActionConstants.COST_ITEM_CREATE, "{\"action\":\"create\",\"costItemId\":" + entity.getProjectCostItemId() + "}", request);
        return ProjectCostItemVO.from(entity);
    }

    @Override
    @Transactional
    public ProjectCostItemVO updateItem(Long projectId, Long costItemId, ProjectCostItemUpdateDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        ProjectCostItem entity = getItemOrThrow(product.getProductId(), costItemId);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "只有草稿成本项可以编辑");
        }
        applyUpdateFields(entity, dto);
        fillUpdateAudit(entity, currentUserName());
        costItemRepository.updateById(entity);
        writeLog(product, OperationActionConstants.COST_ITEM_UPDATE, "{\"action\":\"update\",\"costItemId\":" + entity.getProjectCostItemId() + "}", request);
        return ProjectCostItemVO.from(entity);
    }

    @Override
    @Transactional
    public ProjectCostItemVO confirmItem(Long projectId, Long costItemId, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        ProjectCostItem entity = getItemOrThrow(product.getProductId(), costItemId);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "只有草稿成本项可以确认");
        }
        String operator = currentUserName();
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_CONFIRMED);
        entity.setConfirmedAt(now);
        entity.setConfirmedBy(operator);
        fillUpdateAudit(entity, operator);
        costItemRepository.updateById(entity);
        writeLog(product, OperationActionConstants.COST_ITEM_CONFIRM, "{\"action\":\"confirm\",\"costItemId\":" + entity.getProjectCostItemId() + "}", request);
        return ProjectCostItemVO.from(entity);
    }

    @Override
    @Transactional
    public ProjectCostItemVO voidItem(Long projectId, Long costItemId, HttpServletRequest request) {
        Product product = getProductOrThrow(projectId);
        ProjectCostItem entity = getItemOrThrow(product.getProductId(), costItemId);
        if (STATUS_VOID.equals(entity.getStatus())) {
            return ProjectCostItemVO.from(entity);
        }
        String operator = currentUserName();
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_VOID);
        entity.setVoidedAt(now);
        entity.setVoidedBy(operator);
        fillUpdateAudit(entity, operator);
        costItemRepository.updateById(entity);
        writeLog(product, OperationActionConstants.COST_ITEM_VOID, "{\"action\":\"void\",\"costItemId\":" + entity.getProjectCostItemId() + "}", request);
        return ProjectCostItemVO.from(entity);
    }

    private void applyCreateFields(ProjectCostItem entity, ProjectCostItemCreateDTO dto) {
        entity.setCostCategory(normalizeCategory(dto.getCostCategory()));
        entity.setCostName(requiredText(dto.getCostName(), "成本名称不能为空"));
        entity.setAmount(zero(dto.getAmount()));
        entity.setCurrencyCode(normalizeCurrency(dto.getCurrencyCode()));
        entity.setSupplierName(trimToNull(dto.getSupplierName()));
        entity.setOccurredAt(dto.getOccurredAt());
        entity.setRemark(trimToNull(dto.getRemark()));
    }

    private void applyUpdateFields(ProjectCostItem entity, ProjectCostItemUpdateDTO dto) {
        entity.setCostCategory(normalizeCategory(dto.getCostCategory()));
        entity.setCostName(requiredText(dto.getCostName(), "成本名称不能为空"));
        entity.setAmount(zero(dto.getAmount()));
        entity.setCurrencyCode(normalizeCurrency(dto.getCurrencyCode()));
        entity.setSupplierName(trimToNull(dto.getSupplierName()));
        entity.setOccurredAt(dto.getOccurredAt());
        entity.setRemark(trimToNull(dto.getRemark()));
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private ProjectCostItem getItemOrThrow(Long productId, Long costItemId) {
        ProjectCostItem entity = costItemRepository.selectById(costItemId);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeletedFlag()) || !productId.equals(entity.getProductId())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "成本项不存在");
        }
        return entity;
    }

    private List<ProjectCostItem> activeItems(Long productId) {
        List<ProjectCostItem> values = costItemRepository.selectList(new LambdaQueryWrapper<ProjectCostItem>()
            .eq(ProjectCostItem::getProductId, productId)
            .eq(ProjectCostItem::getDeletedFlag, 0)
            .orderByDesc(ProjectCostItem::getUpdatedAt));
        return values == null ? List.of() : values;
    }

    private BigDecimal sumConfirmed(List<ProjectCostItem> items, String category) {
        return items.stream()
            .filter(item -> category.equals(item.getCostCategory()))
            .filter(item -> STATUS_CONFIRMED.equals(item.getStatus()))
            .map(ProjectCostItem::getAmount)
            .map(this::zero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalizeCategory(String category) {
        String value = requiredText(category, "成本分类不能为空");
        if (!CATEGORY_MOLD.equals(value) && !CATEGORY_OTHER.equals(value)) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "成本分类只支持模具成本或其他成本");
        }
        return value;
    }

    private String normalizeCurrency(String currencyCode) {
        String value = trimToNull(currencyCode);
        return value == null ? DEFAULT_CURRENCY : value.toUpperCase();
    }

    private String requiredText(String value, String message) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void fillCreateAudit(BaseEntity entity, LocalDateTime now, String operator) {
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        entity.setDeletedFlag(0);
    }

    private void fillUpdateAudit(BaseEntity entity, String operator) {
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operator);
    }

    private String currentUserName() {
        return CurrentUserContext.get()
            .map(CurrentUser::displayName)
            .filter(StringUtils::hasText)
            .orElse("system");
    }

    private void writeLog(Product product, String action, String detailJson, HttpServletRequest request) {
        operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("PRODUCT")
            .businessId(String.valueOf(product.getProductId()))
            .businessCode(product.getProductCode())
            .businessName(product.getProductName())
            .detailJson(detailJson)
            .request(request)
            .build());
    }
}
