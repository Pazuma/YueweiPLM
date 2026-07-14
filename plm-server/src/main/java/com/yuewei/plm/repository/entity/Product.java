package com.yuewei.plm.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("plm_product")
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {

    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;
    private Long parentProductId;
    private Long customerId;
    private String productCode;
    private String productName;
    private String productType;
    private String seriesName;
    private String model;
    private String color;
    private String material;
    private String packageType;
    private String surfaceProcess;
    private String coreProcess;
    private String composition;
    private Long ownerUserId;
    private String versionNo;
    private String status;
    private Integer currentStepNo;
    private Boolean timelineCurrentConfirmed;
    private String timelineConfirmedNodeKey;
    private String timelineLastAction;
    private String timelineLastReason;
    private LocalDateTime timelineLastOperatedAt;
    private Long timelineLastOperatorUserId;
    private String timelineLastOperatorUserName;
    private LocalDate effectiveDate;
    private LocalDateTime releasedAt;
    private String releasedBy;
    private LocalDateTime frozenAt;
    private String frozenBy;
    private String freezeReason;
    private LocalDateTime archivedAt;
    private String archivedBy;
    private String archiveReason;
    private LocalDateTime abandonedAt;
    private String abandonedBy;
    private String abandonReason;
    private String lockStatus;
    private String lockReason;
    private Long lockOperatorUserId;
    private String lockOperatorUserName;
    private LocalDateTime lockOperatedAt;
    private String remark;
}
