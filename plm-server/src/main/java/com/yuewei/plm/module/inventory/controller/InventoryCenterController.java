package com.yuewei.plm.module.inventory.controller;

import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.ResponseVO;
import com.yuewei.plm.module.inventory.service.InventoryCenterService;
import com.yuewei.plm.module.inventory.vo.InventoryCenterSnapshotVO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventories")
public class InventoryCenterController {

    private final InventoryCenterService inventoryCenterService;

    @GetMapping("/center-snapshot")
    public ResponseVO<InventoryCenterSnapshotVO> snapshot(HttpServletRequest request) {
        return ResponseVO.success(
            inventoryCenterService.snapshot(),
            RequestIdUtil.getRequestId(request),
            OffsetDateTime.now()
        );
    }
}
