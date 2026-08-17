package com.yuewei.plm.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuewei.plm.repository.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("plm_project_cost_item")
@EqualsAndHashCode(callSuper = true)
public class ProjectCostItem extends BaseEntity {
    @TableId(value = "project_cost_item_id", type = IdType.AUTO)
    private Long projectCostItemId;
    private Long productId;
    private String costCategory;
    private String costName;
    private BigDecimal amount;
    private String currencyCode;
    private String supplierName;
    private LocalDateTime occurredAt;
    private String status;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private LocalDateTime voidedAt;
    private String voidedBy;
    private String remark;
}
