package com.yuewei.plm.module.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.project.constant.TimelineNodeConstants;
import com.yuewei.plm.module.project.dto.ProjectQueryDTO;
import com.yuewei.plm.module.project.service.TimelineDefinitionProvider;
import com.yuewei.plm.module.project.service.TimelineService;
import com.yuewei.plm.module.project.vo.TimelineDetailVO;
import com.yuewei.plm.module.user.entity.SysUser;
import com.yuewei.plm.module.user.repository.SysUserRepository;
import com.yuewei.plm.repository.ProductRepository;
import com.yuewei.plm.repository.entity.Product;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectServiceImplTest {

    @Test
    void pageInProgressMapsProductToProjectSummary() {
        ProductRepository productRepository = mock(ProductRepository.class);
        SysUserRepository sysUserRepository = mock(SysUserRepository.class);
        TimelineService timelineService = mock(TimelineService.class);
        ProjectServiceImpl service = new ProjectServiceImpl(
            productRepository,
            sysUserRepository,
            timelineService,
            new TimelineDefinitionProvider()
        );
        Product product = product(20L);
        Page<Product> page = new Page<Product>(1, 20).setRecords(List.of(product));
        page.setTotal(1);
        when(productRepository.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setDisplayName("工程部用户一");
        when(sysUserRepository.selectBatchIds(any(Collection.class))).thenReturn(List.of(user));

        var result = service.pageInProgress(new ProjectQueryDTO());

        assertThat(result.getContent()).hasSize(1);
        var summary = result.getContent().get(0);
        assertThat(summary.getProjectId()).isEqualTo(20L);
        assertThat(summary.getProductId()).isEqualTo(20L);
        assertThat(summary.getProductTypeName()).isEqualTo("新产品线");
        assertThat(summary.getStatusName()).isEqualTo("开发中");
        assertThat(summary.getOwnerUserName()).isEqualTo("工程部用户一");
        assertThat(summary.getCurrentStepNo()).isEqualTo(2);
        assertThat(summary.getCurrentNodeName()).isEqualTo("设计确认");
        assertThat(summary.getDocumentCount()).isZero();
    }

    @Test
    void detailIncludesTimeline() {
        ProductRepository productRepository = mock(ProductRepository.class);
        SysUserRepository sysUserRepository = mock(SysUserRepository.class);
        TimelineService timelineService = mock(TimelineService.class);
        ProjectServiceImpl service = new ProjectServiceImpl(
            productRepository,
            sysUserRepository,
            timelineService,
            new TimelineDefinitionProvider()
        );
        Product product = product(21L);
        when(productRepository.selectById(21L)).thenReturn(product);
        when(sysUserRepository.selectBatchIds(any(Collection.class))).thenReturn(List.of());
        TimelineDetailVO timeline = TimelineDetailVO.builder()
            .projectId(21L)
            .productId(21L)
            .productType(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE)
            .currentStepNo(2)
            .nodes(List.of())
            .build();
        when(timelineService.getTimeline(21L)).thenReturn(timeline);

        var detail = service.getDetail(21L);

        assertThat(detail.getProjectId()).isEqualTo(21L);
        assertThat(detail.getProductId()).isEqualTo(21L);
        assertThat(detail.getTimeline()).isSameAs(timeline);
    }

    @Test
    void missingProjectThrowsNotFound() {
        ProductRepository productRepository = mock(ProductRepository.class);
        SysUserRepository sysUserRepository = mock(SysUserRepository.class);
        TimelineService timelineService = mock(TimelineService.class);
        ProjectServiceImpl service = new ProjectServiceImpl(
            productRepository,
            sysUserRepository,
            timelineService,
            new TimelineDefinitionProvider()
        );
        when(productRepository.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getSummary(404L))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCodeConstants.RESOURCE_NOT_FOUND);
    }

    private Product product(Long productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductCode("PRD-CD30-0001");
        product.setProductName("超队 3.0");
        product.setProductType(TimelineNodeConstants.PRODUCT_TYPE_PRODUCT_LINE);
        product.setModel("iPhone18");
        product.setColor("黑色");
        product.setOwnerUserId(7L);
        product.setVersionNo("A");
        product.setStatus("developing");
        product.setCurrentStepNo(2);
        product.setCreatedAt(LocalDateTime.of(2026, 7, 7, 10, 0));
        product.setUpdatedAt(LocalDateTime.of(2026, 7, 7, 11, 0));
        product.setDeletedFlag(0);
        return product;
    }
}
