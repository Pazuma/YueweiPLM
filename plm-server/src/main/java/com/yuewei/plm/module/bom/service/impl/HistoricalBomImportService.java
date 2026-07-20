package com.yuewei.plm.module.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.bom.dto.BomRouteSaveDTO;
import com.yuewei.plm.module.bom.dto.ProductBomItemDTO;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.entity.ProductBomImportBatch;
import com.yuewei.plm.module.bom.repository.ProductBomImportBatchRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.service.BomMaterialLookup;
import com.yuewei.plm.module.bom.service.BomProcessRouteLookup;
import com.yuewei.plm.module.bom.service.ProductBomWorkflowService;
import com.yuewei.plm.module.bom.vo.BomImportErrorVO;
import com.yuewei.plm.module.bom.vo.BomImportPreviewVO;
import com.yuewei.plm.module.bom.vo.BomImportRowVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.repository.CodeItemRepository;
import com.yuewei.plm.module.bom.dto.BomRouteColorDTO;
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
public class HistoricalBomImportService {
    private static final List<String> HEADERS = List.of(
        "产品编码", "BOM版本", "行号", "路线编码", "路线名称", "适用颜色", "物料编码", "物料名称",
        "规格", "单位", "用量", "损耗率", "替代料标识", "备注"
    );

    private final ProductBomImportBatchRepository batchRepository;
    private final ProductBomRepository bomRepository;
    private final ProductRepository productRepository;
    private final BomMaterialLookup materialLookup;
    private final BomProcessRouteLookup routeLookup;
    private final ProductBomWorkflowService workflowService;
    private final CodeItemRepository codeItemRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public BomImportPreviewVO preview(String fileName, byte[] content) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            throw validation("仅支持 xlsx 文件");
        }
        List<BomImportRowVO> rows = new ArrayList<>();
        List<BomImportErrorVO> errors = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || !validHeader(sheet.getRow(0))) {
                errors.add(new BomImportErrorVO(1, "表头", "", "表头必须与历史 BOM 导入模板一致"));
            } else {
                DataFormatter formatter = new DataFormatter();
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || text(row, 0, formatter).isBlank()) continue;
                    parseRow(rowIndex + 1, row, formatter, rows, errors);
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw validation("XLSX 解析失败：" + exception.getMessage());
        }
        String status = errors.isEmpty() && !rows.isEmpty() ? "ready" : "invalid";
        ProductBomImportBatch batch = new ProductBomImportBatch();
        batch.setImportToken(UUID.randomUUID().toString());
        batch.setBomScope("history");
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
        return BomImportPreviewVO.builder().importToken(batch.getImportToken()).status(status)
            .totalRows(batch.getTotalRows()).validRows(rows.size()).errorRows(errors.size())
            .rows(rows).errors(errors).build();
    }

    @Transactional
    public ProductBomImportBatch commit(String importToken) {
        ProductBomImportBatch batch = batchRepository.selectOne(new LambdaQueryWrapper<ProductBomImportBatch>()
            .eq(ProductBomImportBatch::getImportToken, importToken).eq(ProductBomImportBatch::getBomScope, "history")
            .eq(ProductBomImportBatch::getDeletedFlag, 0));
        if (batch == null) throw new BusinessException(ErrorCodeConstants.RESOURCE_NOT_FOUND, "历史导入批次不存在");
        if (!"ready".equals(batch.getStatus()) || batch.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw validation("历史导入批次不可提交或已过期");
        }
        batch.setStatus("committing");
        if (batchRepository.update(batch, new LambdaQueryWrapper<ProductBomImportBatch>()
            .eq(ProductBomImportBatch::getProductBomImportBatchId, batch.getProductBomImportBatchId())
            .eq(ProductBomImportBatch::getStatus, "ready")) != 1) {
            throw new BusinessException(ErrorCodeConstants.CODE_CONFLICT, "历史导入批次已被提交");
        }
        Map<String, List<BomImportRowVO>> byBom = new LinkedHashMap<>();
        readRows(batch.getPreviewJson()).forEach(row -> byBom.computeIfAbsent(
            row.getProductId() + "|" + row.getVersionNo(), key -> new ArrayList<>()).add(row));
        for (List<BomImportRowVO> bomRows : byBom.values()) createReleasedBom(bomRows);
        batch.setStatus("committed");
        batch.setCommittedAt(LocalDateTime.now());
        batch.setCommittedBy("system");
        batch.setUpdatedAt(batch.getCommittedAt());
        batch.setUpdatedBy("system");
        batchRepository.updateById(batch);
        return batch;
    }

    public byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("历史BOM导入");
            var row = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) row.createCell(index).setCellValue(HEADERS.get(index));
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "历史导入模板生成失败");
        }
    }

    private void parseRow(int sourceRowNo, Row row, DataFormatter formatter,
        List<BomImportRowVO> rows, List<BomImportErrorVO> errors) {
        String productCode = text(row, 0, formatter);
        List<Product> products = productRepository.selectList(new LambdaQueryWrapper<Product>()
            .eq(Product::getProductCode, productCode).eq(Product::getDeletedFlag, 0));
        if (products == null || products.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "产品编码", productCode, "未找到产品"));
            return;
        }
        if (products.size() != 1) {
            errors.add(new BomImportErrorVO(sourceRowNo, "产品编码", productCode, "产品编码匹配到多个产品，已阻止导入"));
            return;
        }
        Product product = products.get(0);
        String versionNo = text(row, 1, formatter).trim();
        String bomCode = "HIS-" + productCode + "-" + versionNo;
        List<ProductBom> conflicts = bomRepository.selectList(new LambdaQueryWrapper<ProductBom>()
            .eq(ProductBom::getDeletedFlag, 0)
            .and(wrapper -> wrapper.eq(ProductBom::getBomCode, bomCode)
                .or().eq(ProductBom::getProductId, product.getProductId()).eq(ProductBom::getVersionNo, versionNo)));
        if (conflicts != null && !conflicts.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "BOM版本", versionNo, "产品版本或历史 BOM 编码已存在"));
            return;
        }
        String routeCode = text(row, 3, formatter);
        var route = routeLookup.findByCode(product.getProductId(), routeCode);
        if (route.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "路线编码", routeCode, "产品下不存在该工艺路线"));
            return;
        }
        String materialCode = text(row, 6, formatter);
        var material = materialLookup.findByCode(materialCode);
        if (material.isEmpty()) {
            errors.add(new BomImportErrorVO(sourceRowNo, "物料编码", materialCode, "物料不存在"));
            return;
        }
        try {
            BomImportRowVO value = new BomImportRowVO();
            value.setProductId(product.getProductId());
            value.setProductCode(productCode);
            value.setVersionNo(required(versionNo, "BOM版本"));
            value.setLineNo(Integer.valueOf(required(text(row, 2, formatter), "行号")));
            value.setProcessId(route.get().processId());
            value.setRouteCode(route.get().routeCode());
            value.setRouteName(route.get().routeName());
            List<String> colorCodes = Arrays.stream(required(text(row, 5, formatter), "适用颜色").split("[,，]"))
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
            List<BomRouteColorDTO> colorItems = colorCodes.stream().map(this::requireEnabledColor).toList();
            value.setColorItems(colorItems);
            value.setColors(colorItems.stream().map(BomRouteColorDTO::getCodeName).toList());
            value.setInventoryId(material.get().inventoryId());
            value.setItemCode(materialCode);
            value.setItemName(material.get().inventoryName());
            value.setSpecification(text(row, 8, formatter));
            value.setUnit(required(text(row, 9, formatter), "单位"));
            value.setQuantity(new BigDecimal(required(text(row, 10, formatter), "用量")));
            value.setLossRate(text(row, 11, formatter).isBlank() ? BigDecimal.ZERO : new BigDecimal(text(row, 11, formatter)));
            value.setUnitCost(material.get().unitCost());
            value.setCurrencyCode(material.get().currencyCode());
            value.setSubstituteFlag("1".equals(text(row, 12, formatter)) ? 1 : 0);
            value.setRemark(text(row, 13, formatter));
            if (value.getQuantity().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("用量必须大于 0");
            rows.add(value);
        } catch (Exception exception) {
            errors.add(new BomImportErrorVO(sourceRowNo, "数据", "", exception.getMessage()));
        }
    }

    private void createReleasedBom(List<BomImportRowVO> rows) {
        BomImportRowVO first = rows.get(0);
        ProductBom bom = new ProductBom();
        bom.setProductId(first.getProductId());
        bom.setBomCode("HIS-" + first.getProductCode() + "-" + first.getVersionNo());
        bom.setBomName("历史 BOM " + first.getVersionNo());
        bom.setBomType("mbom");
        bom.setBomScope("formal");
        bom.setSourceType("import");
        bom.setVersionNo(first.getVersionNo());
        bom.setStatus("draft");
        bom.setFrozenFlag(0);
        bom.setCurrencyCode(first.getCurrencyCode() == null ? "CNY" : first.getCurrencyCode());
        fillCreate(bom);
        bomRepository.insert(bom);
        Map<String, List<BomImportRowVO>> byRoute = new LinkedHashMap<>();
        rows.forEach(row -> byRoute.computeIfAbsent(row.getRouteCode(), key -> new ArrayList<>()).add(row));
        workflowService.saveRoutes(bom.getProductBomId(), byRoute.values().stream().map(this::toRoute).toList());
        bom.setStatus("released");
        bom.setFrozenFlag(1);
        bom.setFrozenAt(LocalDateTime.now());
        bom.setFrozenBy("history-import");
        bom.setReleasedAt(bom.getFrozenAt());
        bom.setReleasedBy("history-import");
        bom.setUpdatedAt(bom.getFrozenAt());
        bom.setUpdatedBy("history-import");
        bomRepository.updateById(bom);
    }

    private BomRouteSaveDTO toRoute(List<BomImportRowVO> rows) {
        BomImportRowVO first = rows.get(0);
        BomRouteSaveDTO route = new BomRouteSaveDTO();
        route.setProcessId(first.getProcessId());
        route.setRouteCode(first.getRouteCode());
        route.setRouteName(first.getRouteName());
        route.setColors(rows.stream().flatMap(row -> row.getColors().stream()).distinct().toList());
        route.setColorItems(rows.stream().flatMap(row -> row.getColorItems().stream())
            .collect(java.util.stream.Collectors.toMap(BomRouteColorDTO::getCodeItemId, value -> value,
                (existing, ignored) -> existing, java.util.LinkedHashMap::new)).values().stream().toList());
        route.setItems(rows.stream().map(this::toItem).toList());
        return route;
    }

    private ProductBomItemDTO toItem(BomImportRowVO row) {
        ProductBomItemDTO item = new ProductBomItemDTO();
        item.setInventoryId(row.getInventoryId()); item.setItemCode(row.getItemCode()); item.setItemName(row.getItemName());
        item.setSpecification(row.getSpecification()); item.setLineNo(row.getLineNo()); item.setQuantity(row.getQuantity());
        item.setUnit(row.getUnit()); item.setLossRate(row.getLossRate()); item.setUnitCost(row.getUnitCost());
        item.setSubstituteFlag(row.getSubstituteFlag()); item.setRemark(row.getRemark());
        return item;
    }

    private BomRouteColorDTO requireEnabledColor(String colorCode) {
        CodeItem item = codeItemRepository.selectOne(new LambdaQueryWrapper<CodeItem>()
            .eq(CodeItem::getCodeType, "color").eq(CodeItem::getCodeValue, colorCode)
            .eq(CodeItem::getStatus, "enabled").eq(CodeItem::getDeletedFlag, 0));
        if (item == null) throw new IllegalArgumentException("颜色编码不存在或已停用：" + colorCode);
        BomRouteColorDTO value = new BomRouteColorDTO();
        value.setCodeItemId(item.getCodeItemId()); value.setCodeValue(item.getCodeValue()); value.setCodeName(item.getCodeName());
        return value;
    }

    private boolean validHeader(Row header) {
        if (header == null) return false;
        DataFormatter formatter = new DataFormatter();
        for (int index = 0; index < HEADERS.size(); index++) {
            if (!HEADERS.get(index).equals(formatter.formatCellValue(header.getCell(index)).trim())) return false;
        }
        return true;
    }

    private List<BomImportRowVO> readRows(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<BomImportRowVO>>() {}); }
        catch (Exception exception) { throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "历史导入预览数据损坏"); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new BusinessException(ErrorCodeConstants.INTERNAL_ERROR, "历史导入预览序列化失败"); }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private String text(Row row, int cell, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(cell)).trim();
    }

    private void fillCreate(com.yuewei.plm.repository.entity.BaseEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setCreatedAt(now); value.setCreatedBy("system"); value.setUpdatedAt(now); value.setUpdatedBy("system"); value.setDeletedFlag(0);
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, message);
    }
}
