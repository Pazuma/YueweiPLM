package com.yuewei.plm.common.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestIdUtil {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private RequestIdUtil() {
    }

    public static String getRequestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
