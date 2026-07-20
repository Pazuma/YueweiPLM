package com.yuewei.plm.module.code.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yuewei.plm.common.exception.BusinessException;
import com.yuewei.plm.module.code.dto.CodeItemSaveDTO;
import com.yuewei.plm.module.code.dto.CodeItemQueryDTO;
import com.yuewei.plm.module.code.entity.CodeItem;
import com.yuewei.plm.module.code.repository.CodeItemRepository;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeItemServiceTest {
    private CodeItemRepository repository;
    private CodeItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(CodeItemRepository.class);
        service = new CodeItemService(repository);
    }

    @Test
    void createPreservesLeadingZero() {
        when(repository.selectCount(any(Wrapper.class))).thenReturn(0L);
        CodeItemSaveDTO dto = new CodeItemSaveDTO();
        dto.setCodeType("color");
        dto.setCodeValue("02");
        dto.setCodeName("Negro");
        dto.setSortOrder(2);

        var created = service.create(dto);

        assertThat(created.getCodeValue()).isEqualTo("02");
        verify(repository).insert(any(CodeItem.class));
    }

    @Test
    void pageAllowsStatusToBeOmitted() {
        when(repository.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
        CodeItemQueryDTO query = new CodeItemQueryDTO();

        var result = service.page(query);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void createRejectsDuplicateWithinType() {
        when(repository.selectCount(any(Wrapper.class))).thenReturn(1L);
        CodeItemSaveDTO dto = new CodeItemSaveDTO();
        dto.setCodeType("color");
        dto.setCodeValue("02");
        dto.setCodeName("Negro");
        dto.setSortOrder(2);

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("编码已存在");
    }

    @Test
    void changeStatusKeepsItemQueryable() {
        CodeItem item = new CodeItem();
        item.setCodeItemId(2L);
        item.setCodeType("color");
        item.setCodeValue("02");
        item.setCodeName("Negro");
        item.setStatus("enabled");
        item.setSortOrder(2);
        item.setDeletedFlag(0);
        when(repository.selectById(2L)).thenReturn(item);

        var disabled = service.changeStatus(2L, "disabled");

        assertThat(disabled.getStatus()).isEqualTo("disabled");
        assertThat(item.getDeletedFlag()).isZero();
        verify(repository).updateById(item);
    }
}
