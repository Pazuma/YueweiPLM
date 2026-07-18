package com.yuewei.plm.module.bom.service;

import com.yuewei.plm.module.bom.vo.BomLedgerRowVO;
import com.yuewei.plm.module.bom.vo.BomSkuRowVO;
import com.yuewei.plm.module.bom.vo.BomSummaryVO;
import com.yuewei.plm.module.bom.vo.ProductBomWorkbenchVO;
import java.util.List;

public interface BomLedgerService {
    List<BomLedgerRowVO> listFormal();
    ProductBomWorkbenchVO getWorkbench(Long bomId);
    List<BomSkuRowVO> listSkus(Long bomId);
    List<BomSkuRowVO> listSkusForRoute(Long routeId);
    BomSummaryVO getSummary(Long productId);
}
