package com.yuewei.plm.module.bom.controller;

import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.ResponseVO;
import com.yuewei.plm.module.bom.dto.ProductBomCreateDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.dto.ProductBomUpdateDTO;
import com.yuewei.plm.module.bom.service.ProductBomService;
import com.yuewei.plm.module.bom.vo.ProductBomVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductBomController {

    private final ProductBomService productBomService;

    @GetMapping("/projects/{projectId}/boms")
    public ResponseVO<List<ProductBomVO>> listByProject(@PathVariable Long projectId, HttpServletRequest request) {
        return ResponseVO.success(productBomService.listByProject(projectId), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @GetMapping("/boms/{bomId}")
    public ResponseVO<ProductBomVO> detail(@PathVariable Long bomId, HttpServletRequest request) {
        return ResponseVO.success(productBomService.getById(bomId), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/projects/{projectId}/boms")
    public ResponseVO<ProductBomVO> create(@PathVariable Long projectId,
                                           @Valid @RequestBody ProductBomCreateDTO dto,
                                           HttpServletRequest request) {
        return ResponseVO.success(productBomService.create(projectId, dto, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PutMapping("/boms/{bomId}")
    public ResponseVO<ProductBomVO> update(@PathVariable Long bomId,
                                           @Valid @RequestBody ProductBomUpdateDTO dto,
                                           HttpServletRequest request) {
        return ResponseVO.success(productBomService.update(bomId, dto, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/boms/{bomId}/items")
    public ResponseVO<ProductBomVO> addItem(@PathVariable Long bomId,
                                            @Valid @RequestBody ProductBomItemDTO dto,
                                            HttpServletRequest request) {
        return ResponseVO.success(productBomService.addItem(bomId, dto, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PutMapping("/boms/{bomId}/items/{itemId}")
    public ResponseVO<ProductBomVO> updateItem(@PathVariable Long bomId,
                                               @PathVariable Long itemId,
                                               @Valid @RequestBody ProductBomItemDTO dto,
                                               HttpServletRequest request) {
        return ResponseVO.success(productBomService.updateItem(bomId, itemId, dto, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @DeleteMapping("/boms/{bomId}/items/{itemId}")
    public ResponseVO<ProductBomVO> deleteItem(@PathVariable Long bomId,
                                               @PathVariable Long itemId,
                                               HttpServletRequest request) {
        return ResponseVO.success(productBomService.deleteItem(bomId, itemId, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @PostMapping("/boms/{bomId}/freeze")
    public ResponseVO<ProductBomVO> freeze(@PathVariable Long bomId, HttpServletRequest request) {
        return ResponseVO.success(productBomService.freeze(bomId, request), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }
}
