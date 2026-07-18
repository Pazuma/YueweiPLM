package com.yuewei.plm.module.bom.service;

import com.yuewei.plm.module.bom.entity.ProductBomItem;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BomCostCalculator {

    public Result calculate(
        List<ProductBomItem> items,
        BigDecimal processCost,
        BigDecimal packageCost,
        BigDecimal laborCost,
        BigDecimal toolingCost,
        BigDecimal otherCost
    ) {
        BigDecimal material = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (ProductBomItem item : items) {
            BigDecimal base = zero(item.getUnitCostSnapshot()).multiply(zero(item.getQuantity()));
            material = material.add(base);
            loss = loss.add(base.multiply(zero(item.getLossRate())));
        }
        BigDecimal total = material
            .add(loss)
            .add(zero(processCost))
            .add(zero(packageCost))
            .add(zero(laborCost))
            .add(zero(toolingCost))
            .add(zero(otherCost));
        return new Result(
            material, loss, zero(processCost), zero(packageCost), zero(laborCost),
            zero(toolingCost), zero(otherCost), total
        );
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record Result(
        BigDecimal materialCost,
        BigDecimal lossCost,
        BigDecimal processCost,
        BigDecimal packageCost,
        BigDecimal laborCost,
        BigDecimal toolingCost,
        BigDecimal otherCost,
        BigDecimal totalCost
    ) {
    }
}
