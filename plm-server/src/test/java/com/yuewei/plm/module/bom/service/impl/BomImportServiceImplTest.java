package com.yuewei.plm.module.bom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuewei.plm.module.bom.repository.ProductBomImportBatchRepository;
import com.yuewei.plm.module.bom.repository.ProductBomRepository;
import com.yuewei.plm.module.bom.entity.ProductBom;
import com.yuewei.plm.module.bom.service.BomMaterialLookup;
import com.yuewei.plm.module.bom.service.BomProcessRouteLookup;
import com.yuewei.plm.module.bom.service.ProductBomWorkflowService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BomImportServiceImplTest {

    @Test
    void previewsValidWorkbookWithoutWritingBomRows() throws Exception {
        ProductBomImportBatchRepository batchRepository = mock(ProductBomImportBatchRepository.class);
        ProductBomRepository bomRepository = editableBomRepository();
        BomMaterialLookup materialLookup = mock(BomMaterialLookup.class);
        ProductBomWorkflowService workflowService = mock(ProductBomWorkflowService.class);
        BomProcessRouteLookup routeLookup = mock(BomProcessRouteLookup.class);
        when(materialLookup.findByCode("MAT-001")).thenReturn(Optional.of(
            new BomMaterialLookup.Material(1L, "TPU 原料", new BigDecimal("12.50"), "CNY")
        ));
        when(routeLookup.findByCode(10L, "DYE")).thenReturn(Optional.of(
            new BomProcessRouteLookup.Route(100L, "DYE", "染色路线")
        ));
        BomImportServiceImpl service = new BomImportServiceImpl(
            batchRepository, bomRepository, materialLookup, routeLookup, workflowService
        );

        var preview = service.preview(10L, 20L, "formal.xlsx", workbook(true));

        assertThat(preview.getStatus()).isEqualTo("ready");
        assertThat(preview.getValidRows()).isEqualTo(1);
        assertThat(preview.getErrors()).isEmpty();
        assertThat(preview.getRows().get(0).getProcessId()).isEqualTo(100L);
        assertThat(preview.getImportToken()).isNotBlank();
    }

    @Test
    void invalidHeaderProducesDownloadableErrorWorkbook() throws Exception {
        ProductBomImportBatchRepository batchRepository = mock(ProductBomImportBatchRepository.class);
        BomImportServiceImpl service = new BomImportServiceImpl(
            batchRepository, editableBomRepository(), mock(BomMaterialLookup.class), mock(BomProcessRouteLookup.class),
            mock(ProductBomWorkflowService.class)
        );

        var preview = service.preview(10L, 20L, "broken.xlsx", workbook(false));
        byte[] report = service.buildErrorReport(preview.getErrors());

        assertThat(preview.getStatus()).isEqualTo("invalid");
        assertThat(preview.getErrors()).extracting("field").contains("表头");
        assertThat(report).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue()).isEqualTo("错误原因");
        }
    }

    @Test
    void rejectsBomOwnedByAnotherProductBeforeParsing() throws Exception {
        ProductBomRepository bomRepository = mock(ProductBomRepository.class);
        ProductBom bom = new ProductBom();
        bom.setProductId(99L);
        bom.setBomScope("formal");
        bom.setStatus("draft");
        when(bomRepository.selectById(20L)).thenReturn(bom);
        BomImportServiceImpl service = new BomImportServiceImpl(
            mock(ProductBomImportBatchRepository.class), bomRepository, mock(BomMaterialLookup.class),
            mock(BomProcessRouteLookup.class), mock(ProductBomWorkflowService.class)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.preview(10L, 20L, "formal.xlsx", workbook(true)))
            .isInstanceOf(com.yuewei.plm.common.exception.BusinessException.class)
            .hasMessageContaining("BOM");
    }

    private ProductBomRepository editableBomRepository() {
        ProductBomRepository repository = mock(ProductBomRepository.class);
        ProductBom bom = new ProductBom();
        bom.setProductId(10L);
        bom.setBomScope("formal");
        bom.setStatus("draft");
        bom.setFrozenFlag(0);
        when(repository.selectById(20L)).thenReturn(bom);
        return repository;
    }

    private byte[] workbook(boolean validHeader) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("BOM导入");
            var header = sheet.createRow(0);
            String[] headers = {
                "行号", "路线编码", "路线名称", "适用颜色", "物料编码", "物料名称",
                "规格", "单位", "用量", "损耗率", "替代料标识", "备注"
            };
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(index == 0 && !validHeader ? "错误列" : headers[index]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("DYE");
            row.createCell(2).setCellValue("染色路线");
            row.createCell(3).setCellValue("黑色,蓝色");
            row.createCell(4).setCellValue("MAT-001");
            row.createCell(5).setCellValue("TPU 原料");
            row.createCell(7).setCellValue("kg");
            row.createCell(8).setCellValue(2);
            row.createCell(9).setCellValue(0.05);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
