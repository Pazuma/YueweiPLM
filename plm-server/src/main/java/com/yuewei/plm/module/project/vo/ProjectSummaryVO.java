package com.yuewei.plm.module.project.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectSummaryVO {
    private Long projectId;
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private String productTypeName;
    private String model;
    private String color;
    private String versionNo;
    private String status;
    private String statusName;
    private Long ownerUserId;
    private String ownerUserName;
    private Integer currentStepNo;
    private String currentNodeName;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
