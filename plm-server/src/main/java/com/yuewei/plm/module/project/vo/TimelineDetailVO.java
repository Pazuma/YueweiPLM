package com.yuewei.plm.module.project.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimelineDetailVO {
    private Long projectId;
    private Long productId;
    private String productType;
    private Integer currentStepNo;
    private Boolean currentConfirmed;
    private String confirmedNodeKey;
    private String lastAction;
    private String lastReason;
    private LocalDateTime lastOperatedAt;
    private Long lastOperatorUserId;
    private String lastOperatorUserName;
    private List<TimelineNodeVO> nodes;
}
