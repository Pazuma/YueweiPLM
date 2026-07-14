package com.yuewei.plm.module.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.constant.ProductStatusConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.dto.ProjectQueryDTO;
import com.yuewei.plm.module.project.service.ProjectService;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.module.project.service.TimelineService;
import com.yuewei.plm.module.project.vo.ProjectDetailVO;
import com.yuewei.plm.module.project.vo.ProjectSummaryVO;
import com.yuewei.plm.module.user.entity.SysUser;
import com.yuewei.plm.module.user.repository.SysUserRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final List<String> IN_PROGRESS_STATUSES = List.of(
        ProductStatusConstants.DRAFT,
        ProductStatusConstants.DEVELOPING,
        ProductStatusConstants.REVIEWING
    );

    private final ProductRepository productRepository;
    private final SysUserRepository sysUserRepository;
    private final TimelineService timelineService;
    private final TimelineDefinitionProvider timelineDefinitionProvider;

    @Override
    public PageVO<ProjectSummaryVO> pageInProgress(ProjectQueryDTO queryDTO) {
        LambdaQueryWrapper<Product> wrapper = baseQuery(queryDTO)
            .in(Product::getStatus, IN_PROGRESS_STATUSES)
            .orderByDesc(Product::getUpdatedAt);
        return pageProducts(queryDTO, wrapper);
    }

    @Override
    public PageVO<ProjectSummaryVO> page(ProjectQueryDTO queryDTO) {
        LambdaQueryWrapper<Product> wrapper = baseQuery(queryDTO)
            .eq(StringUtils.hasText(queryDTO.getStatus()), Product::getStatus, queryDTO.getStatus())
            .orderByDesc(Product::getUpdatedAt);
        return pageProducts(queryDTO, wrapper);
    }

    @Override
    public ProjectDetailVO getDetail(Long projectId) {
        Product product = getProductOrThrow(projectId);
        Map<Long, String> ownerNames = loadOwnerNames(List.of(product));
        Integer currentStepNo = normalizeStepNo(product.getCurrentStepNo());
        return ProjectDetailVO.builder()
            .projectId(product.getProductId())
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
            .ownerUserName(ownerNames.get(product.getOwnerUserId()))
            .versionNo(product.getVersionNo())
            .status(product.getStatus())
            .currentStepNo(currentStepNo)
            .currentNodeName(currentNodeName(product.getProductType(), currentStepNo))
            .documentCount(0)
            .timeline(timelineService.getTimeline(projectId))
            .remark(product.getRemark())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    @Override
    public ProjectSummaryVO getSummary(Long projectId) {
        Product product = getProductOrThrow(projectId);
        return toSummaryVO(product, loadOwnerNames(List.of(product)));
    }

    private PageVO<ProjectSummaryVO> pageProducts(ProjectQueryDTO queryDTO, LambdaQueryWrapper<Product> wrapper) {
        IPage<Product> page = productRepository.selectPage(new Page<>(queryDTO.getPage(), queryDTO.getSize()), wrapper);
        Map<Long, String> ownerNames = loadOwnerNames(page.getRecords());
        return PageVO.<ProjectSummaryVO>builder()
            .content(page.getRecords().stream().map(product -> toSummaryVO(product, ownerNames)).toList())
            .page(page.getCurrent())
            .size(page.getSize())
            .totalElements(page.getTotal())
            .totalPages(page.getPages())
            .build();
    }

    private LambdaQueryWrapper<Product> baseQuery(ProjectQueryDTO queryDTO) {
        return new LambdaQueryWrapper<Product>()
            .eq(StringUtils.hasText(queryDTO.getProductType()), Product::getProductType, queryDTO.getProductType())
            .eq(queryDTO.getOwnerUserId() != null, Product::getOwnerUserId, queryDTO.getOwnerUserId())
            .and(StringUtils.hasText(queryDTO.getKeyword()), wrapper -> wrapper
                .like(Product::getProductName, queryDTO.getKeyword())
                .or()
                .like(Product::getProductCode, queryDTO.getKeyword())
                .or()
                .like(Product::getModel, queryDTO.getKeyword()))
            .ne(Product::getDeletedFlag, 1);
    }

    private ProjectSummaryVO toSummaryVO(Product product, Map<Long, String> ownerNames) {
        Integer currentStepNo = normalizeStepNo(product.getCurrentStepNo());
        return ProjectSummaryVO.builder()
            .projectId(product.getProductId())
            .productId(product.getProductId())
            .productCode(product.getProductCode())
            .productName(product.getProductName())
            .productType(product.getProductType())
            .productTypeName(productTypeName(product.getProductType()))
            .model(product.getModel())
            .color(product.getColor())
            .versionNo(product.getVersionNo())
            .status(product.getStatus())
            .statusName(statusName(product.getStatus()))
            .ownerUserId(product.getOwnerUserId())
            .ownerUserName(ownerNames.get(product.getOwnerUserId()))
            .currentStepNo(currentStepNo)
            .currentNodeName(currentNodeName(product.getProductType(), currentStepNo))
            .documentCount(0)
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    private Map<Long, String> loadOwnerNames(Collection<Product> products) {
        Set<Long> ownerIds = products.stream()
            .map(Product::getOwnerUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return sysUserRepository.selectBatchIds(ownerIds).stream()
            .collect(Collectors.toMap(SysUser::getUserId, SysUser::getDisplayName, (left, right) -> left));
    }

    private Product getProductOrThrow(Long projectId) {
        Product product = productRepository.selectById(projectId);
        if (product == null || Integer.valueOf(1).equals(product.getDeletedFlag())) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return product;
    }

    private Integer normalizeStepNo(Integer currentStepNo) {
        if (currentStepNo == null || currentStepNo < 1) {
            return 1;
        }
        return Math.min(currentStepNo, 6);
    }

    private String currentNodeName(String productType, Integer currentStepNo) {
        return timelineDefinitionProvider.getCurrentNodeName(productType, currentStepNo);
    }

    private String productTypeName(String productType) {
        if (TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE.equals(productType)) {
            return TimelineNodeConstants.PRODUCT_TYPE_NAME_PRODUCT_LINE;
        }
        if (TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT.equals(productType)) {
            return TimelineNodeConstants.PRODUCT_TYPE_NAME_MODEL_VARIANT;
        }
        return productType;
    }

    private String statusName(String status) {
        return switch (status) {
            case ProductStatusConstants.DRAFT -> "草稿";
            case ProductStatusConstants.DEVELOPING -> "开发中";
            case ProductStatusConstants.REVIEWING -> "评审中";
            case ProductStatusConstants.RELEASED -> "已发布";
            case ProductStatusConstants.ARCHIVED -> "已归档";
            default -> status;
        };
    }
}
