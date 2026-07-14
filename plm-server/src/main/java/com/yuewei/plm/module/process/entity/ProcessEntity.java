package com.yuewei.plm.module.process.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuewei.plm.repository.entity.BaseEntity;
import com.yuewei.plm.module.process.repository.typehandler.PostgresJsonbStringTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName(value = "plm_process", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class ProcessEntity extends BaseEntity {

    @TableId(value = "process_id", type = IdType.AUTO)
    private Long processId;
    private Long parentProcessId;
    private Long productId;
    private String processCode;
    private String processName;
    private String processType;
    private String versionNo;
    private Integer sequenceNo;
    @TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
    private String processParamJson;
    private BigDecimal standardTimeMins;
    private String qualityRequirement;
    private String status;
    private LocalDateTime frozenAt;
    private String frozenBy;
    private String remark;
}
