package com.yuewei.plm.module.project.vo;

import com.yuewei.plm.module.project.entity.ProjectCostItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectCostItemVO {
    private Long projectCostItemId;
    private Long productId;
    private String costCategory;
    private String costCategoryName;
    private String costName;
    private BigDecimal amount;
    private String currencyCode;
    private String supplierName;
    private LocalDateTime occurredAt;
    private String status;
    private String statusName;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private LocalDateTime voidedAt;
    private String voidedBy;
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static ProjectCostItemVO from(ProjectCostItem entity) {
        if (entity == null) return null;
        return ProjectCostItemVO.builder()
            .projectCostItemId(entity.getProjectCostItemId())
            .productId(entity.getProductId())
            .costCategory(entity.getCostCategory())
            .costCategoryName(categoryName(entity.getCostCategory()))
            .costName(entity.getCostName())
            .amount(entity.getAmount())
            .currencyCode(entity.getCurrencyCode())
            .supplierName(entity.getSupplierName())
            .occurredAt(entity.getOccurredAt())
            .status(entity.getStatus())
            .statusName(statusName(entity.getStatus()))
            .confirmedAt(entity.getConfirmedAt())
            .confirmedBy(entity.getConfirmedBy())
            .voidedAt(entity.getVoidedAt())
            .voidedBy(entity.getVoidedBy())
            .remark(entity.getRemark())
            .createdAt(entity.getCreatedAt())
            .createdBy(entity.getCreatedBy())
            .updatedAt(entity.getUpdatedAt())
            .updatedBy(entity.getUpdatedBy())
            .build();
    }

    private static String categoryName(String category) {
        if ("mold".equals(category)) return "模具成本";
        if ("other".equals(category)) return "其他成本";
        return category;
    }

    private static String statusName(String status) {
        if ("draft".equals(status)) return "草稿";
        if ("confirmed".equals(status)) return "已确认";
        if ("void".equals(status)) return "已作废";
        return status;
    }
}
