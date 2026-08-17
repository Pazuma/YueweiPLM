package com.yuewei.plm.module.operationlog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class OperationLogQueryDTO {

    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(200)
    private long size = 20;

    private Long operatorUserId;
    private String action;
    private String businessType;
    private String businessId;
    private String requestId;
}
