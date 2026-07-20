package com.yuewei.plm.module.bom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.module.bom.repository.ProductBomImportBatchRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.service.BomMaterialLookup;
import com.yuewei.plm.module.bom.service.BomProcessRouteLookup;
import com.yuewei.plm.module.bom.service.ProductBomWorkflowService;
import com.yuewei.plm.module.bom.vo.BomImportErrorVO;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.repository.CodeItemRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HistoricalBomImportServiceTest {
    @Test
    void previewMatchesUniqueProductCodeAndBuildsReadyBatch() throws Exception {
        ProductRepository products = mock(ProductRepository.class);
        when(products.selectList(any(Wrapper.class))).thenReturn(List.of(product(10L, "P-LJ")));
        BomMaterialLookup materials = mock(BomMaterialLookup.class);
        when(materials.findByCode("MAT-001")).thenReturn(Optional.of(
            new BomMaterialLookup.Material(1L, "TPU", new BigDecimal("12.50"), "CNY")));
        BomProcessRouteLookup routes = mock(BomProcessRouteLookup.class);
        when(routes.findByCode(10L, "DYE")).thenReturn(Optional.of(
            new BomProcessRouteLookup.Route(100L, "DYE", "染色路线")));
        HistoricalBomImportService service = new HistoricalBomImportService(
            mock(ProductBomImportBatchRepository.class), mock(ProductBomRepository.class), products,
            materials, routes, mock(ProductBomWorkflowService.class), colorCodes());

        var preview = service.preview("history.xlsx", workbook());

        assertThat(preview.getStatus()).isEqualTo("ready");
        assertThat(preview.getValidRows()).isEqualTo(1);
        assertThat(preview.getRows().get(0).getProductCode()).isEqualTo("P-LJ");
        assertThat(preview.getRows().get(0).getVersionNo()).isEqualTo("V1");
    }

    @Test
    void previewRejectsAmbiguousProductCode() throws Exception {
        ProductRepository products = mock(ProductRepository.class);
        when(products.selectList(any(Wrapper.class))).thenReturn(List.of(product(10L, "P-LJ"), product(11L, "P-LJ")));
        HistoricalBomImportService service = new HistoricalBomImportService(
            mock(ProductBomImportBatchRepository.class), mock(ProductBomRepository.class), products,
            mock(BomMaterialLookup.class), mock(BomProcessRouteLookup.class), mock(ProductBomWorkflowService.class), colorCodes());

        var preview = service.preview("history.xlsx", workbook());

        assertThat(preview.getStatus()).isEqualTo("invalid");
        assertThat(preview.getErrors()).extracting(BomImportErrorVO::getReason)
            .anyMatch(value -> value.contains("多个产品"));
    }

    private Product product(Long id, String code) {
        Product value = new Product();
        value.setProductId(id);
        value.setProductCode(code);
        value.setProductName("亮甲");
        value.setStatus("released");
        value.setDeletedFlag(0);
        return value;
    }

    private byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("历史BOM导入");
            String[] headers = {"产品编码", "BOM版本", "行号", "路线编码", "路线名称", "适用颜色", "物料编码", "物料名称", "规格", "单位", "用量", "损耗率", "替代料标识", "备注"};
            var header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("P-LJ");
            row.createCell(1).setCellValue("V1");
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue("DYE");
            row.createCell(4).setCellValue("染色路线");
            row.createCell(5).setCellValue("02,08");
            row.createCell(6).setCellValue("MAT-001");
            row.createCell(7).setCellValue("TPU");
            row.createCell(9).setCellValue("kg");
            row.createCell(10).setCellValue(2);
            row.createCell(11).setCellValue(0.05);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private CodeItemRepository colorCodes() {
        CodeItemRepository repository = mock(CodeItemRepository.class);
        CodeItem black = color(2L, "02", "Negro");
        CodeItem blue = color(8L, "08", "Azul Rey");
        when(repository.selectOne(any(Wrapper.class))).thenReturn(black, blue);
        return repository;
    }

    private CodeItem color(Long id, String code, String name) {
        CodeItem item = new CodeItem();
        item.setCodeItemId(id); item.setCodeType("color"); item.setCodeValue(code);
        item.setCodeName(name); item.setStatus("enabled"); item.setDeletedFlag(0);
        return item;
    }
}
