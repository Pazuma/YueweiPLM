package com.yuewei.plm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.util.ProductCodeGenerator;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.controller.dto.ProductCreateDTO;
import com.yuewei.plm.controller.dto.ProductQueryDTO;
import com.yuewei.plm.controller.dto.ProductUpdateDTO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.service.ProductService;
import com.yuewei.plm.service.vo.ProductCreateResultVO;
import com.yuewei.plm.service.vo.ProductVO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCodeGenerator productCodeGenerator;

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
    public void freeze(Long productId, String operator, String reason) {
        Product product = getProductOrThrow(productId);
        if (ProductStatusConstants.RELEASED.equals(product.getStatus())) {
            throw new BusinessException(ErrorCodeConstants.VERSION_RELEASED, "已发布版本不可重复冻结");
        }
        product.setFrozenAt(LocalDateTime.now());
        product.setFrozenBy(operator);
        product.setFreezeReason(reason);
        product.setLockStatus("frozen");
        product.setLockReason(reason);
        product.setLockOperatorUserName(operator);
        product.setLockOperatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(operator);
        productRepository.updateById(product);
    }

    @Override
    @Transactional
    public void publish(Long productId, String operator) {
        Product product = getProductOrThrow(productId);
        validateStatusTransition(product.getStatus(), ProductStatusConstants.RELEASED);
        product.setStatus(ProductStatusConstants.RELEASED);
        product.setReleasedAt(LocalDateTime.now());
        product.setReleasedBy(operator);
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(operator);
        productRepository.updateById(product);
    }

    private Product getProductOrThrow(Long productId) {
        Product product = productRepository.selectById(productId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "产品不存在");
        }
        return product;
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
            .effectiveDate(product.getEffectiveDate())
            .releasedAt(product.getReleasedAt())
            .releasedBy(product.getReleasedBy())
            .frozenAt(product.getFrozenAt())
            .frozenBy(product.getFrozenBy())
            .freezeReason(product.getFreezeReason())
            .lockStatus(product.getLockStatus())
            .remark(product.getRemark())
            .createdAt(product.getCreatedAt())
            .createdBy(product.getCreatedBy())
            .updatedAt(product.getUpdatedAt())
            .updatedBy(product.getUpdatedBy())
            .build();
    }
}
