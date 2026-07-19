package com.yuewei.plm.module.bom.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductionConfirmationVO {
    private Long productId;
    private Integer selectedOperationCount;
    private Integer selectedColorCount;
    private Integer createdSkuCount;
    private List<Long> operationProcessIds;
    private List<String> colors;
}
