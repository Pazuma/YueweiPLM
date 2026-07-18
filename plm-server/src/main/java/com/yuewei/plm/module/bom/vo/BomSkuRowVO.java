package com.yuewei.plm.module.bom.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BomSkuRowVO {
    private Long productId;
    private String skuCode;
    private String productName;
    private String phoneModel;
    private String color;
    private String status;
    private Long productBomRouteId;
    private String routeCode;
}
