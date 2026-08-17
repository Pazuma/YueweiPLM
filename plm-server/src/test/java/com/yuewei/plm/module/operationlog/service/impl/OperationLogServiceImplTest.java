package com.yuewei.plm.module.operationlog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuewei.plm.common.security.CurrentUser;
import com.yuewei.plm.common.security.CurrentUserContext;
import com.yuewei.plm.module.operationlog.constant.OperationActionConstants;
import com.yuewei.plm.module.operationlog.entity.OperationLog;
import com.yuewei.plm.module.operationlog.repository.OperationLogRepository;
import com.yuewei.plm.module.operationlog.service.OperationLogCreateCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperationLogServiceImplTest {

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void logSuccessFillsOperatorRequestAndBusinessFields() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogServiceImpl service = new OperationLogServiceImpl(repository);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Request-Id")).thenReturn("m1-apifox-log-001");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/operation-logs/test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        CurrentUserContext.set(new CurrentUser(1L, "engineer01", "工程部用户一", true));
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);

        Long logId = service.logSuccess(OperationLogCreateCommand.builder()
            .action(OperationActionConstants.TEST_WRITE)
            .businessType("M1_TEST")
            .businessId("apifox-001")
            .businessCode("M1-TEST-001")
            .businessName("M1 Apifox 测试日志")
            .detailJson("{\"source\":\"junit\"}")
            .request(request)
            .build());

        verify(repository).insert(captor.capture());
        OperationLog log = captor.getValue();
        assertThat(logId).isEqualTo(log.getLogId());
        assertThat(log.getRequestId()).isEqualTo("m1-apifox-log-001");
        assertThat(log.getOperatorUserId()).isEqualTo(1L);
        assertThat(log.getOperatorUserName()).isEqualTo("工程部用户一");
        assertThat(log.getAction()).isEqualTo(OperationActionConstants.TEST_WRITE);
        assertThat(log.getBusinessType()).isEqualTo("M1_TEST");
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getCreatedBy()).isEqualTo("工程部用户一");
        assertThat(log.getDeletedFlag()).isZero();
    }

    @Test
    void logSuccessReturnsNullWhenRepositoryInsertFails() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        doThrow(new IllegalStateException("operation log table missing")).when(repository).insert(any(OperationLog.class));
        OperationLogServiceImpl service = new OperationLogServiceImpl(repository);

        Long logId = service.logSuccess(OperationLogCreateCommand.builder()
            .action(OperationActionConstants.TEST_WRITE)
            .businessType("M1_TEST")
            .businessId("apifox-001")
            .businessCode("M1-TEST-001")
            .businessName("M1 Apifox 测试日志")
            .detailJson("{\"source\":\"junit\"}")
            .build());

        assertThat(logId).isNull();
    }
}
