package com.yuewei.plm.module.bom.vo;

import java.math.BigDecimal;
import java.util.List;
import com.yuewei.plm.module.bom.dto.BomRouteColorDTO;
import lombok.Data;

@Data
public class BomImportRowVO {
    private Long productId;
    private String productCode;
    private String versionNo;
    private Integer lineNo;
    private Long processId;
    private String routeCode;
    private String routeName;
    private List<String> colors;
    private List<BomRouteColorDTO> colorItems;
    private Long inventoryId;
    private String itemCode;
    private String itemName;
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private BigDecimal unitCost;
    private String currencyCode;
    private Integer substituteFlag;
    private String remark;
}
