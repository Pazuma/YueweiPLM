package com.yuewei.plm.module.bom.service;

import java.math.BigDecimal;
import java.util.Optional;

public interface BomMaterialLookup {
    Optional<Material> findByCode(String inventoryCode);

    record Material(Long inventoryId, String inventoryName, BigDecimal unitCost, String currencyCode) {
    }
}
