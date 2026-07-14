package com.yuewei.plm.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.vo.ResponseVO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    @Test
    void noResourceFoundReturnsResourceNotFoundError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setAttribute("requestId", "req-not-found");
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/");

        ResponseVO<Void> response = handler.handleNoResourceFound(exception, request);

        assertThat(response.getCode()).isEqualTo(ErrorCodeConstants.RESOURCE_NOT_FOUND);
        assertThat(response.getMessage()).isEqualTo("资源不存在");
        assertThat(response.getRequestId()).isEqualTo("req-not-found");
    }

    @Test
    void businessExceptionCanReturnStructuredData() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/10/publish");
        request.setAttribute("requestId", "req-gate");
        Map<String, String> data = Map.of("code", "BOM_NOT_FROZEN");
        BusinessException exception = new BusinessException(
            ErrorCodeConstants.RELEASE_GATE_NOT_PASSED,
            "发布门禁未通过",
            data
        );

        ResponseVO<Object> response = handler.handleBusiness(exception, request);

        assertThat(response.getCode()).isEqualTo(ErrorCodeConstants.RELEASE_GATE_NOT_PASSED);
        assertThat(response.getMessage()).isEqualTo("发布门禁未通过");
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getRequestId()).isEqualTo("req-gate");
    }
}
