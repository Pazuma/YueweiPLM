package com.yuewei.plm.module.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yuewei.plm.module.attachment.entity.Attachment;
import com.yuewei.plm.module.attachment.repository.AttachmentRepository;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TimelineServiceImplTest {

    @Test
    void buildsProductLineTimelineWithSingleCurrentNode() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        when(attachmentRepository.selectCount(Mockito.<Wrapper<Attachment>>any())).thenReturn(0L);
        TimelineServiceImpl service = new TimelineServiceImpl(productRepository, new TimelineDefinitionProvider(), attachmentRepository);
        Product product = product(10L, TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE, 2);
        product.setTimelineCurrentConfirmed(true);
        product.setTimelineConfirmedNodeKey("PRODUCT_LINE_DESIGN_CONFIRM");
        product.setTimelineLastAction("confirm");
        product.setTimelineLastReason("design checked");
        product.setTimelineLastOperatorUserId(1L);
        product.setTimelineLastOperatorUserName("Engineer One");
        when(productRepository.selectById(10L)).thenReturn(product);

        var timeline = service.getTimeline(10L);

        assertThat(timeline.getProjectId()).isEqualTo(10L);
        assertThat(timeline.getProductId()).isEqualTo(10L);
        assertThat(timeline.getProductType()).isEqualTo(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE);
        assertThat(timeline.getCurrentStepNo()).isEqualTo(2);
        assertThat(timeline.getNodes()).hasSize(6);
        assertThat(timeline.getNodes()).extracting("nodeStatus")
            .containsExactly("completed", "current", "pending", "pending", "pending", "pending");
        assertThat(timeline.getNodes()).extracting("documentCount").containsOnly(0);
        assertThat(timeline.getCurrentConfirmed()).isTrue();
        assertThat(timeline.getConfirmedNodeKey()).isEqualTo("PRODUCT_LINE_DESIGN_CONFIRM");
        assertThat(timeline.getLastAction()).isEqualTo("confirm");
        assertThat(timeline.getLastReason()).isEqualTo("design checked");
        assertThat(timeline.getLastOperatorUserId()).isEqualTo(1L);
        assertThat(timeline.getLastOperatorUserName()).isEqualTo("Engineer One");
        assertThat(timeline.getNodes()).extracting("confirmed")
            .containsExactly(false, true, false, false, false, false);
        ArgumentCaptor<Wrapper<Attachment>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(attachmentRepository, times(6)).selectCount(wrapperCaptor.capture());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Attachment.class);
        assertThat(wrapperCaptor.getAllValues())
            .allSatisfy(wrapper -> {
                LambdaQueryWrapper<Attachment> queryWrapper = (LambdaQueryWrapper<Attachment>) wrapper;
                queryWrapper.getSqlSegment();
                assertThat(queryWrapper.getParamNameValuePairs()).containsValue("Product");
            });
    }

    @Test
    void clampsCurrentStepNoAboveDefinitionSize() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        when(attachmentRepository.selectCount(Mockito.<Wrapper<Attachment>>any())).thenReturn(0L);
        TimelineServiceImpl service = new TimelineServiceImpl(productRepository, new TimelineDefinitionProvider(), attachmentRepository);
        Product product = product(11L, TimelineNodeConstants.PRODUCT_TYPE_MODEL_VARIANT, 99);
        when(productRepository.selectById(11L)).thenReturn(product);

        var timeline = service.getTimeline(11L);

        assertThat(timeline.getCurrentStepNo()).isEqualTo(6);
        assertThat(timeline.getNodes()).extracting("nodeStatus")
            .containsExactly("completed", "completed", "completed", "completed", "completed", "current");
    }

    @Test
    void missingProductThrowsNotFound() {
        ProductRepository productRepository = mock(ProductRepository.class);
        TimelineServiceImpl service = new TimelineServiceImpl(productRepository, new TimelineDefinitionProvider(), mock(AttachmentRepository.class));
        when(productRepository.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getTimeline(999L))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.RESOURCE_NOT_FOUND);
    }

    private Product product(Long productId, String productType, Integer currentStepNo) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductType(productType);
        product.setCurrentStepNo(currentStepNo);
        product.setDeletedFlag(0);
        return product;
    }
}
