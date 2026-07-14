package com.yuewei.plm.module.bom.vo;

import com.yuewei.plm.module.bom.entity.ProductBom;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductBomVO {

    private Long productBomId;
    private Long productId;
    private String bomCode;
    private String bomName;
    private String bomType;
    private String versionNo;
    private String status;
    private LocalDateTime frozenAt;
    private String frozenBy;
    private String remark;
    private List<ProductBomItemVO> items;

    public static ProductBomVO from(ProductBom bom, List<ProductBomItemVO> items) {
        return ProductBomVO.builder()
            .productBomId(bom.getProductBomId())
            .productId(bom.getProductId())
            .bomCode(bom.getBomCode())
            .bomName(bom.getBomName())
            .bomType(bom.getBomType())
            .versionNo(bom.getVersionNo())
            .status(bom.getStatus())
            .frozenAt(bom.getFrozenAt())
            .frozenBy(bom.getFrozenBy())
            .remark(bom.getRemark())
            .items(items)
            .build();
    }
}
