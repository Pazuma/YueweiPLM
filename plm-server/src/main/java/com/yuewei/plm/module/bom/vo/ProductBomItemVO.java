package com.yuewei.plm.module.bom.vo;

import com.yuewei.plm.module.bom.entity.ProductBomItem;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductBomItemVO {

    private Long productBomItemId;
    private Long productBomId;
    private Long inventoryId;
    private String itemCode;
    private String itemName;
    private String specification;
    private Integer lineNo;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal lossRate;
    private Integer substituteFlag;
    private String remark;
    private String status;

    public static ProductBomItemVO from(ProductBomItem item) {
        return ProductBomItemVO.builder()
            .productBomItemId(item.getProductBomItemId())
            .productBomId(item.getProductBomId())
            .inventoryId(item.getInventoryId())
            .itemCode(item.getItemCode())
            .itemName(item.getItemName())
            .specification(item.getSpecification())
            .lineNo(item.getLineNo())
            .quantity(item.getQuantity())
            .unit(item.getUnit())
            .lossRate(item.getLossRate())
            .substituteFlag(item.getSubstituteFlag())
            .remark(item.getRemark())
            .status(item.getStatus())
            .build();
    }
}
