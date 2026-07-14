package com.yuewei.plm.module.project.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimelineNodeVO {
    private Integer stepNo;
    private String nodeCode;
    private String nodeName;
    private String nodeStatus;
    private Integer documentCount;
    private Boolean confirmed;
}
