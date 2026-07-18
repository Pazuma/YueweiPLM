package com.yuewei.plm.module.bom.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuewei.plm.module.bom.entity.ProductBomItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BomCostCalculatorTest {

    @Test
    void separatesMaterialAndLossAndBuildsRouteTotal() {
        ProductBomItem item = new ProductBomItem();
        item.setQuantity(new BigDecimal("2"));
        item.setUnitCostSnapshot(new BigDecimal("10"));
        item.setLossRate(new BigDecimal("0.05"));

        BomCostCalculator.Result result = new BomCostCalculator().calculate(
            List.of(item),
            new BigDecimal("3"),
            new BigDecimal("2"),
            BigDecimal.ONE,
            BigDecimal.ZERO,
            new BigDecimal("0.50")
        );

        assertThat(result.materialCost()).isEqualByComparingTo("20");
        assertThat(result.lossCost()).isEqualByComparingTo("1");
        assertThat(result.totalCost()).isEqualByComparingTo("27.50");
    }

    @Test
    void nullOptionalCostsAreTreatedAsZero() {
        ProductBomItem item = new ProductBomItem();
        item.setQuantity(BigDecimal.ONE);
        item.setUnitCostSnapshot(new BigDecimal("4.25"));
        item.setLossRate(null);

        BomCostCalculator.Result result = new BomCostCalculator().calculate(
            List.of(item), null, null, null, null, null
        );

        assertThat(result.totalCost()).isEqualByComparingTo("4.25");
    }
}
