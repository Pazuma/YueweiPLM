package com.yuewei.plm.module.operationlog.vo;

import com.yuewei.plm.module.operationlog.entity.OperationLog;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogVO {

    private Long logId;
    private String requestId;
    private Long operatorUserId;
    private String operatorUserName;
    private String action;
    private String businessType;
    private String businessId;
    private String businessCode;
    private String businessName;
    private String result;
    private LocalDateTime createdAt;

    public static OperationLogVO from(OperationLog log) {
        return OperationLogVO.builder()
            .logId(log.getLogId())
            .requestId(log.getRequestId())
            .operatorUserId(log.getOperatorUserId())
            .operatorUserName(log.getOperatorUserName())
            .action(log.getAction())
            .businessType(log.getBusinessType())
            .businessId(log.getBusinessId())
            .businessCode(log.getBusinessCode())
            .businessName(log.getBusinessName())
            .result(log.getResult())
            .createdAt(log.getCreatedAt())
            .build();
    }
}
