package com.yuewei.plm.module.process.vo;

import com.yuewei.plm.module.process.entity.ProcessEntity;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessOperationVO {

    private Long processId;
    private Long parentProcessId;
    private Integer sequenceNo;
    private String processName;
    private String processParamJson;
    private BigDecimal standardTimeMins;
    private String qualityRequirement;
    private String status;
    private String remark;

    public static ProcessOperationVO from(ProcessEntity entity) {
        return ProcessOperationVO.builder()
            .processId(entity.getProcessId())
            .parentProcessId(entity.getParentProcessId())
            .sequenceNo(entity.getSequenceNo())
            .processName(entity.getProcessName())
            .processParamJson(entity.getProcessParamJson())
            .standardTimeMins(entity.getStandardTimeMins())
            .qualityRequirement(entity.getQualityRequirement())
            .status(entity.getStatus())
            .remark(entity.getRemark())
            .build();
    }
}
