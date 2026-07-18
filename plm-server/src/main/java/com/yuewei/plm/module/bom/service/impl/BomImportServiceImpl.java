package com.yuewei.plm.module.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.entity.ProductBomImportBatch;
import com.yuewei.plm.module.bom.repository.ProductBomImportBatchRepository;
import com.yuewei.plm.module.bom.service.BomImportService;
import com.yuewei.plm.module.bom.service.BomMaterialLookup;
import com.yuewei.plm.module.bom.service.BomProcessRouteLookup;
import com.yuewei.plm.module.bom.service.ProductBomWorkflowService;
import com.yuewei.plm.module.bom.vo.BomImportErrorVO;
import com.yuewei.plm.module.bom.vo.BomImportPreviewVO;
import com.yuewei.plm.module.bom.vo.BomImportRowVO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BomImportServiceImpl implements BomImportService {
    private static final List<String> HEADERS = List.of(
        "行号", "路线编码", "路线名称", "适用颜色", "物料编码", "物料名称",
        "规格", "单位", "用量", "损耗率", "替代料标识", "备注"
    );

    private final ProductBomImportBatchRepository batchRepository;
    private final BomMaterialLookup materialLookup;
    private final BomProcessRouteLookup routeLookup;
    private final ProductBomWorkflowService workflowService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public BomImportPreviewVO preview(Long productId, Long bomId, String fileName, byte[] content) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "仅支持 xlsx 文件");
        }
        List<BomImportRowVO> rows = new ArrayList<>();
        List<BomImportErrorVO> errors = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || !validHeader(sheet.getRow(0))) {
                errors.add(new BomImportErrorVO(1, "表头", "", "表头必须与 BOM 导入模板一致"));
            } else {
                DataFormatter formatter = new DataFormatter();
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || formatter.formatCellValue(row.getCell(0)).isBlank()) {
                        continue;
                    }
                    parseRow(productId, rowIndex + 1, row, formatter, rows, errors);
                }
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "XLSX 解析失败：" + exception.getMessage());
        }
        String status = errors.isEmpty() && !rows.isEmpty() ? "ready" : "invalid";
        String token = UUID.randomUUID().toString();
        ProductBomImportBatch batch = new ProductBomImportBatch();
        batch.setProductId(productId);
        batch.setProductBomId(bomId);
        batch.setImportToken(token);
        batch.setBomScope("formal");
        batch.setFileName(fileName);
        batch.setStatus(status);
        batch.setTotalRows(rows.size() + errors.size());
        batch.setValidRows(rows.size());
        batch.setErrorRows(errors.size());
        batch.setPreviewJson(writeJson(rows));
        batch.setErrorJson(writeJson(errors));
        batch.setExpiresAt(LocalDateTime.now().plusHours(2));
        fillCreate(batch);
        batchRepository.insert(batch);
        return BomImportPreviewVO.builder().importToken(token).status(status)
            .totalRows(batch.getTotalRows()).validRows(rows.size()).errorRows(errors.size())
            .rows(rows).errors(errors).build();
    }

    @Override
    @Transactional
    public ProductBomImportBatch commit(String importToken) {
        ProductBomImportBatch batch = batchRepository.selectOne(new LambdaQueryWrapper<ProductBomImportBatch>()
            .eq(ProductBomImportBatch::getImportToken, importToken).eq(ProductBomImportBatch::getDeletedFlag, 0));
        if (batch == null) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "导入批次不存在");
        }
        if (!"ready".equals(batch.getStatus()) || batch.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "导入批次不可提交或已过期");
        }
        List<BomImportRowVO> rows = readRows(batch.getPreviewJson());
        Map<String, List<BomImportRowVO>> byRoute = new LinkedHashMap<>();
        rows.forEach(row -> byRoute.computeIfAbsent(row.getRouteCode(), key -> new ArrayList<>()).add(row));
        List<BomRouteSaveDTO> routes = byRoute.values().stream().map(this::toRoute).toList();
        workflowService.saveRoutes(batch.getProductBomId(), routes);
        batch.setStatus("committed");
        batch.setCommittedAt(LocalDateTime.now());
        batch.setCommittedBy("system");
        batch.setUpdatedAt(batch.getCommittedAt());
        batch.setUpdatedBy("system");
        batchRepository.updateById(batch);
        return batch;
    }

    @Override
    public List<BomImportErrorVO> getErrors(String importToken) {
        ProductBomImportBatch batch = batchRepository.selectOne(new LambdaQueryWrapper<ProductBomImportBatch>()
            .eq(ProductBomImportBatch::getImportToken, importToken).eq(ProductBomImportBatch::getDeletedFlag, 0));
        if (batch == null) {
            throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "导入批次不存在");
        }
        try {
            return objectMapper.readValue(batch.getErrorJson(), new TypeReference<List<BomImportErrorVO>>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "导入错误数据损坏");
        }
    }

    @Override
    public byte[] buildErrorReport(List<BomImportErrorVO> errors) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("错误报告");
            var header = sheet.createRow(0);
            List.of("原始行号", "字段", "原值", "错误原因").forEach(value ->
                header.createCell(header.getLastCellNum() < 0 ? 0 : header.getLastCellNum()).setCellValue(value));
            for (int index = 0; index < errors.size(); index++) {
                BomImportErrorVO error = errors.get(index);
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(error.getRowNo());
                row.createCell(1).setCellValue(error.getField());
                row.createCell(2).setCellValue(error.getOriginalValue());
                row.createCell(3).setCellValue(error.getReason());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "错误报告生成失败");
        }
    }

    @Override
    public byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("BOM导入");
            var header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) {
                header.createCell(index).setCellValue(HEADERS.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "导入模板生成失败");
        }
    }

    private boolean validHeader(Row header) {
        if (header == null) return false;
        DataFormatter formatter = new DataFormatter();
        for (int index = 0; index < HEADERS.size(); index++) {
            if (!HEADERS.get(index).equals(formatter.formatCellValue(header.getCell(index)).trim())) return false;
        }
        return true;
    }

    private void parseRow(
        Long productId, int sourceRowNo, Row row, DataFormatter formatter,
        List<BomImportRowVO> rows, List<BomImportErrorVO> errors
    ) {
        String materialCode = text(row, 4, formatter);
        String routeCode = text(row, 1, formatter);
        var route = routeLookup.findByCode(productId, routeCode);
        if (route.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "路线编码", routeCode, "产品下不存在该工艺路线"));
            return;
        }
        var material = materialLookup.findByCode(materialCode);
        if (material.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "物料编码", materialCode, "物料不存在"));
            return;
        }
        try {
            BomImportRowVO value = new BomImportRowVO();
            value.setLineNo(Integer.valueOf(text(row, 0, formatter)));
            value.setProcessId(route.get().processId());
            value.setRouteCode(route.get().routeCode());
            value.setRouteName(route.get().routeName());
            value.setColors(Arrays.stream(required(text(row, 3, formatter), "适用颜色").split("[,，]"))
                .map(String::trim).filter(color -> !color.isBlank()).distinct().toList());
            value.setInventoryId(material.get().inventoryId());
            value.setItemCode(materialCode);
            value.setItemName(material.get().inventoryName());
            value.setSpecification(text(row, 6, formatter));
            value.setUnit(required(text(row, 7, formatter), "单位"));
            value.setQuantity(new BigDecimal(required(text(row, 8, formatter), "用量")));
            value.setLossRate(text(row, 9, formatter).isBlank() ? BigDecimal.ZERO : new BigDecimal(text(row, 9, formatter)));
            value.setUnitCost(material.get().unitCost());
            value.setCurrencyCode(material.get().currencyCode());
            value.setSubstituteFlag("1".equals(text(row, 10, formatter)) ? 1 : 0);
            value.setRemark(text(row, 11, formatter));
            if (value.getQuantity().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("用量必须大于 0");
            if (value.getLossRate().compareTo(BigDecimal.ZERO) < 0 || value.getLossRate().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("损耗率必须在 0 到 1 之间");
            }
            rows.add(value);
        } catch (Exception exception) {
            errors.add(new BomImportErrorVO(sourceRowNo, "数据", "", exception.getMessage()));
        }
    }

    private BomRouteSaveDTO toRoute(List<BomImportRowVO> rows) {
        BomImportRowVO first = rows.get(0);
        BomRouteSaveDTO route = new BomRouteSaveDTO();
        route.setProcessId(first.getProcessId());
        route.setRouteCode(first.getRouteCode());
        route.setRouteName(first.getRouteName());
        route.setColors(first.getColors());
        route.setItems(rows.stream().map(this::toItem).toList());
        return route;
    }

    private ProductBomItemDTO toItem(BomImportRowVO row) {
        ProductBomItemDTO item = new ProductBomItemDTO();
        item.setInventoryId(row.getInventoryId());
        item.setItemCode(row.getItemCode());
        item.setItemName(row.getItemName());
        item.setSpecification(row.getSpecification());
        item.setLineNo(row.getLineNo());
        item.setQuantity(row.getQuantity());
        item.setUnit(row.getUnit());
        item.setLossRate(row.getLossRate());
        item.setUnitCost(row.getUnitCost());
        item.setSubstituteFlag(row.getSubstituteFlag());
        item.setRemark(row.getRemark());
        return item;
    }

    private List<BomImportRowVO> readRows(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<BomImportRowVO>>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "导入预览数据损坏");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "导入预览序列化失败");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private String text(Row row, int cell, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(cell)).trim();
    }

    private void fillCreate(ProductBomImportBatch batch) {
        LocalDateTime now = LocalDateTime.now();
        batch.setCreatedAt(now);
        batch.setCreatedBy("system");
        batch.setUpdatedAt(now);
        batch.setUpdatedBy("system");
        batch.setDeletedFlag(0);
    }
}
