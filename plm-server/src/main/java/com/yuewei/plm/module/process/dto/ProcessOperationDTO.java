package com.yuewei.plm.module.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProcessOperationDTO {

    @NotNull(message = "工序顺序不能为空")
    private Integer sequenceNo;

    @NotBlank(message = "工序名称不能为空")
    private String processName;

    private String processParamJson;
    private BigDecimal standardTimeMins;

    @NotBlank(message = "质量要求不能为空")
    private String qualityRequirement;

    private String remark;
}
