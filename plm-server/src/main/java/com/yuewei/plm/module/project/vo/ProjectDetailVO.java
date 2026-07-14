package com.yuewei.plm.module.project.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectDetailVO {
    private Long projectId;
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
    private String ownerUserName;
    private String versionNo;
    private String status;
    private Integer currentStepNo;
    private String currentNodeName;
    private Integer documentCount;
    private TimelineDetailVO timeline;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
