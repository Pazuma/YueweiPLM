package com.yuewei.plm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.common.util.ProductCodeGenerator;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.controller.dto.ProductCreateDTO;
import com.yuewei.plm.controller.dto.ProductLifecycleActionDTO;
import com.yuewei.plm.controller.dto.ProductQueryDTO;
import com.yuewei.plm.controller.dto.ProductUpdateDTO;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.bom.service.BomInheritanceService;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.service.ProductReleaseGateValidator;
import com.yuewei.plm.service.ProductService;
import com.yuewei.plm.service.vo.ProductCreateResultVO;
import com.yuewei.plm.service.vo.ProductReleaseGateCheckVO;
import com.yuewei.plm.service.vo.ProductVO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCodeGenerator productCodeGenerator;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ProductReleaseGateValidator productReleaseGateValidator;
    private final BomInheritanceService bomInheritanceService;

    @Override
    public PageVO<ProductVO> page(ProductQueryDTO queryDTO) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
            .eq(queryDTO.getCustomerId() != null, Product::getCustomerId, queryDTO.getCustomerId())
            .eq(queryDTO.getParentProductId() != null, Product::getParentProductId, queryDTO.getParentProductId())
            .eq(StringUtils.hasText(queryDTO.getStatus()), Product::getStatus, queryDTO.getStatus())
            .eq(StringUtils.hasText(queryDTO.getProductType()), Product::getProductType, queryDTO.getProductType())
            .and(StringUtils.hasText(queryDTO.getKeyword()), wrapper -> wrapper
                .like(Product::getProductName, queryDTO.getKeyword())
                .or()
                .like(Product::getProductCode, queryDTO.getKeyword())
                .or()
                .like(Product::getModel, queryDTO.getKeyword()))
            .orderByDesc(Product::getCreatedAt);

        IPage<Product> page = productRepository.selectPage(new Page<>(queryDTO.getPage(), queryDTO.getSize()), queryWrapper);
        return PageVO.<ProductVO>builder()
            .content(page.getRecords().stream().map(this::toVO).toList())
            .page(page.getCurrent())
            .size(page.getSize())
            .totalElements(page.getTotal())
            .totalPages(page.getPages())
            .build();
    }

    @Override
    public ProductVO getById(Long productId) {
        return toVO(getProductOrThrow(productId));
    }

    @Override
    @Transactional
    public ProductCreateResultVO create(ProductCreateDTO createDTO) {
        validateVariantParent(createDTO.getProductType(), createDTO.getParentProductId());

        Product product = new Product();
        product.setParentProductId(createDTO.getParentProductId());
        product.setCustomerId(createDTO.getCustomerId());
        product.setProductCode(productCodeGenerator.generate(createDTO.getProductName()));
        product.setProductName(createDTO.getProductName());
        product.setProductType(createDTO.getProductType());
        product.setSeriesName(createDTO.getSeriesName());
        product.setModel(createDTO.getModel());
        product.setColor(createDTO.getColor());
        product.setMaterial(createDTO.getMaterial());
        product.setPackageType(createDTO.getPackageType());
        product.setSurfaceProcess(createDTO.getSurfaceProcess());
        product.setCoreProcess(createDTO.getCoreProcess());
        product.setComposition(createDTO.getComposition());
        product.setOwnerUserId(createDTO.getOwnerUserId());
        product.setVersionNo(createDTO.getVersionNo());
        product.setStatus(ProductStatusConstants.DRAFT);
        product.setLockStatus("unlocked");
        product.setRemark(createDTO.getRemark());
        product.setCreatedAt(LocalDateTime.now());
        product.setCreatedBy(createDTO.getCreatedBy());
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(createDTO.getCreatedBy());
        product.setDeletedFlag(0);

        productRepository.insert(product);
        if ("model_variant".equals(product.getProductType())) {
            bomInheritanceService.inheritLatestReleasedAllColors(product.getParentProductId(), product.getProductId());
        }
        return ProductCreateResultVO.builder()
            .productId(product.getProductId())
            .productCode(product.getProductCode())
            .versionNo(product.getVersionNo())
            .status(product.getStatus())
            .build();
    }

    @Override
    @Transactional
    public ProductVO update(Long productId, ProductUpdateDTO updateDTO) {
        Product product = getProductOrThrow(productId);
        ensureEditable(product);

        if (StringUtils.hasText(updateDTO.getProductName())) {
            product.setProductName(updateDTO.getProductName());
        }
        if (StringUtils.hasText(updateDTO.getSeriesName())) {
            product.setSeriesName(updateDTO.getSeriesName());
        }
        if (StringUtils.hasText(updateDTO.getModel())) {
            product.setModel(updateDTO.getModel());
        }
        if (StringUtils.hasText(updateDTO.getColor())) {
            product.setColor(updateDTO.getColor());
        }
        if (StringUtils.hasText(updateDTO.getMaterial())) {
            product.setMaterial(updateDTO.getMaterial());
        }
        if (StringUtils.hasText(updateDTO.getPackageType())) {
            product.setPackageType(updateDTO.getPackageType());
        }
        if (StringUtils.hasText(updateDTO.getSurfaceProcess())) {
            product.setSurfaceProcess(updateDTO.getSurfaceProcess());
        }
        if (StringUtils.hasText(updateDTO.getCoreProcess())) {
            product.setCoreProcess(updateDTO.getCoreProcess());
        }
        if (updateDTO.getComposition() != null) {
            product.setComposition(updateDTO.getComposition());
        }
        if (updateDTO.getRemark() != null) {
            product.setRemark(updateDTO.getRemark());
        }
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(updateDTO.getUpdatedBy());
        productRepository.updateById(product);
        return toVO(product);
    }

    @Override
    @Transactional
    public void freeze(Long productId, String reason, HttpServletRequest request) {
        ProductLifecycleActionDTO dto = new ProductLifecycleActionDTO();
        dto.setReason(reason);
        freeze(productId, dto, request);
    }

    @Override
    @Transactional
    public ProductVO freeze(Long productId, ProductLifecycleActionDTO dto, HttpServletRequest request) {
        // 冻结属于关键业务动作，操作人必须来自 token 上下文，不能由前端参数伪造。
        CurrentUser currentUser = requireCurrentUser();
        String operator = currentUser.displayName();
        Product product = getProductOrThrow(productId);
        if (ProductStatusConstants.RELEASED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_RELEASED, "已发布版本不可重复冻结");
        }
        if (ProductStatusConstants.ARCHIVED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "已归档项目不能冻结");
        }
        String reason = dto == null ? null : dto.getReason();
        product.setFrozenAt(LocalDateTime.now());
        product.setFrozenBy(operator);
        product.setFreezeReason(reason);
        product.setLockStatus("frozen");
        product.setLockReason(reason);
        product.setLockOperatorUserId(currentUser.userId());
        product.setLockOperatorUserName(operator);
        product.setLockOperatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(operator);
        productRepository.updateById(product);
        writeProductLog(product, OperationActionConstants.PRODUCT_FREEZE, reason, request);
        return toVO(product);
    }

    @Override
    @Transactional
    public void publish(Long productId, String operator) {
        Product product = getProductOrThrow(productId);
        ProductReleaseGateCheckVO gate = productReleaseGateValidator.check(product);
        if (!Boolean.TRUE.equals(gate.getPassed())) {
            throw new BusinessException(ErrorCodeConstants.RELEASE_GATE_NOT_PASSED, "发布门禁未通过", gate);
        }
        validateStatusTransition(product.getStatus(), ProductStatusConstants.RELEASED);
        product.setStatus(ProductStatusConstants.RELEASED);
        product.setReleasedAt(LocalDateTime.now());
        product.setReleasedBy(operator);
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(operator);
        productRepository.updateById(product);
        writeProductLog(product, OperationActionConstants.PRODUCT_PUBLISH, "兼容旧产品发布接口", null);
    }

    @Override
    public ProductReleaseGateCheckVO checkReleaseGate(Long productId) {
        Product product = getProductOrThrow(productId);
        return productReleaseGateValidator.check(product);
    }

    @Override
    @Transactional
    public ProductVO publish(Long productId, ProductLifecycleActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(productId);
        CurrentUser currentUser = requireCurrentUser();
        ProductReleaseGateCheckVO gate = productReleaseGateValidator.check(product);
        if (!Boolean.TRUE.equals(gate.getPassed())) {
            throw new BusinessException(ErrorCodeConstants.RELEASE_GATE_NOT_PASSED, "发布门禁未通过", gate);
        }
        validateStatusTransition(product.getStatus(), ProductStatusConstants.RELEASED);
        product.setStatus(ProductStatusConstants.RELEASED);
        product.setReleasedAt(LocalDateTime.now());
        product.setReleasedBy(currentUser.displayName());
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(currentUser.displayName());
        productRepository.updateById(product);
        writeProductLog(product, OperationActionConstants.PRODUCT_PUBLISH, dto == null ? null : dto.getReason(), request);
        return toVO(product);
    }

    @Override
    @Transactional
    public ProductVO archive(Long productId, ProductLifecycleActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(productId);
        CurrentUser currentUser = requireCurrentUser();
        validateStatusTransition(product.getStatus(), ProductStatusConstants.ARCHIVED);
        String reason = dto == null ? null : dto.getReason();
        product.setStatus(ProductStatusConstants.ARCHIVED);
        product.setArchivedAt(LocalDateTime.now());
        product.setArchivedBy(currentUser.displayName());
        product.setArchiveReason(reason);
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(currentUser.displayName());
        productRepository.updateById(product);
        writeProductLog(product, OperationActionConstants.PRODUCT_ARCHIVE, reason, request);
        return toVO(product);
    }

    @Override
    @Transactional
    public ProductVO abandon(Long productId, ProductLifecycleActionDTO dto, HttpServletRequest request) {
        Product product = getProductOrThrow(productId);
        CurrentUser currentUser = requireCurrentUser();
        if (ProductStatusConstants.RELEASED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "已发布产品不能废弃，只能归档或发起变更");
        }
        if (ProductStatusConstants.ARCHIVED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL, "已归档产品不能重复废弃");
        }
        String reason = dto == null ? null : dto.getReason();
        product.setStatus(ProductStatusConstants.ARCHIVED);
        product.setLockStatus("abandoned");
        product.setAbandonedAt(LocalDateTime.now());
        product.setAbandonedBy(currentUser.displayName());
        product.setAbandonReason(reason);
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(currentUser.displayName());
        productRepository.updateById(product);
        writeProductLog(product, OperationActionConstants.PRODUCT_ABANDON, reason, request);
        return toVO(product);
    }

    private String toDetailJson(String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason == null ? "" : reason));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "操作日志详情序列化失败");
        }
    }

    private Product getProductOrThrow(Long productId) {
        Product product = productRepository.selectById(productId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "产品不存在");
        }
        return product;
    }

    private CurrentUser requireCurrentUser() {
        return CurrentUserContext.get()
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.UNAUTHORIZED, "未登录或登录已失效"));
    }

    private void writeProductLog(Product product, String action, String reason, HttpServletRequest request) {
        operationLogService.logSuccess(OperationLogCreateCommand.builder()
            .action(action)
            .businessType("PRODUCT")
            .businessId(String.valueOf(product.getProductId()))
            .businessCode(product.getProductCode())
            .businessName(product.getProductName())
            .detailJson(toDetailJson(reason))
            .request(request)
            .build());
    }

    private void validateVariantParent(String productType, Long parentProductId) {
        if ("model_variant".equals(productType) && parentProductId == null) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "型号线必须指定所属产品线");
        }
    }

    private void ensureEditable(Product product) {
        if ("frozen".equals(product.getLockStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_FROZEN, "当前版本已冻结，请新建版本或发起变更");
        }
        if (ProductStatusConstants.RELEASED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_RELEASED, "已发布版本不可直接修改");
        }
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (!ProductStatusConstants.TRANSITIONS.getOrDefault(currentStatus, java.util.Set.of()).contains(targetStatus)) {
            throw new BusinessException(
                ErrorCodeConstants.STATUS_TRANSITION_ILLEGAL,
                String.format("不允许从 %s 变更为 %s", currentStatus, targetStatus)
            );
        }
    }

    private ProductVO toVO(Product product) {
        return ProductVO.builder()
            .productId(product.getProductId())
            .parentProductId(product.getParentProductId())
            .customerId(product.getCustomerId())
            .productCode(product.getProductCode())
            .productName(product.getProductName())
            .productType(product.getProductType())
            .seriesName(product.getSeriesName())
            .model(product.getModel())
            .color(product.getColor())
            .material(product.getMaterial())
            .packageType(product.getPackageType())
            .surfaceProcess(product.getSurfaceProcess())
            .coreProcess(product.getCoreProcess())
            .composition(product.getComposition())
            .ownerUserId(product.getOwnerUserId())
            .versionNo(product.getVersionNo())
            .status(product.getStatus())
            .currentStepNo(product.getCurrentStepNo())
            .effectiveDate(product.getEffectiveDate())
            .releasedAt(product.getReleasedAt())
            .releasedBy(product.getReleasedBy())
            .frozenAt(product.getFrozenAt())
            .frozenBy(product.getFrozenBy())
            .freezeReason(product.getFreezeReason())
            .archivedAt(product.getArchivedAt())
            .archivedBy(product.getArchivedBy())
            .archiveReason(product.getArchiveReason())
            .abandonedAt(product.getAbandonedAt())
            .abandonedBy(product.getAbandonedBy())
            .abandonReason(product.getAbandonReason())
            .lockStatus(product.getLockStatus())
            .remark(product.getRemark())
            .createdAt(product.getCreatedAt())
            .createdBy(product.getCreatedBy())
            .updatedAt(product.getUpdatedAt())
            .updatedBy(product.getUpdatedBy())
            .build();
    }
}
