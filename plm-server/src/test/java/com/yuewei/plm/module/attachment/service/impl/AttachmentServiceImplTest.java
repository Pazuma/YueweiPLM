package com.yuewei.plm.module.attachment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuewei.plm.common.config.AppProperties;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.infrastructure.storage.LocalStorageClient;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentDownloadLogRepository;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.operationlog.service.OperationLogService;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentServiceImplTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void uploadTimelineAttachmentWritesMetadataAndLocalFile() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setLocalRoot(tempDir.toString());
        ProductRepository productRepository = mock(ProductRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        when(productRepository.selectById(10L)).thenReturn(product(10L));
        when(attachmentRepository.insert(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment inserted = invocation.getArgument(0);
            inserted.setAttachmentId(300L);
            return 1;
        });
        AttachmentServiceImpl service = service(appProperties, productRepository, attachmentRepository);
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes());

        var vo = service.uploadTimelineAttachment(
            10L,
            "PRODUCT_LINE_SAMPLE_PROCESS",
            file,
            "testing",
            "V1",
            "M4验收",
            null
        );

        assertThat(vo.getAttachmentId()).isEqualTo(300L);
        assertThat(vo.getFileCategory()).isEqualTo("testing");
        assertThat(Files.exists(tempDir.resolve(vo.getStorageKey()))).isTrue();
        ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).insert(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getOwnerObjectType()).isEqualTo("Product");
    }

    @Test
    void downloadMissingLocalFileReturnsExplicitError() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setLocalRoot(tempDir.toString());
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        Attachment attachment = new Attachment();
        attachment.setAttachmentId(300L);
        attachment.setOriginalFileName("missing.txt");
        attachment.setStorageKey("missing/missing.txt");
        attachment.setDeletedFlag(0);
        when(attachmentRepository.selectById(300L)).thenReturn(attachment);
        AttachmentServiceImpl service = service(appProperties, mock(ProductRepository.class), attachmentRepository);

        assertThatThrownBy(() -> service.download(300L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.FILE_SERVICE_ERROR);
    }

    private AttachmentServiceImpl service(AppProperties appProperties,
                                          ProductRepository productRepository,
                                          AttachmentRepository attachmentRepository) {
        return new AttachmentServiceImpl(
            productRepository,
            attachmentRepository,
            mock(AttachmentDownloadLogRepository.class),
            new TimelineDefinitionProvider(),
            new LocalStorageClient(appProperties),
            appProperties,
            mock(OperationLogService.class)
        );
    }

    private Product product(Long productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE);
        product.setDeletedFlag(0);
        return product;
    }
}
