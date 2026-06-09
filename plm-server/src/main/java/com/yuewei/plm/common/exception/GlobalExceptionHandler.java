package com.yuewei.plm.common.exception;

import com.yuewei.plm.common.constant.ErrorCodeConstants;
import com.yuewei.plm.common.util.RequestIdUtil;
import com.yuewei.plm.common.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseVO<Void> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseVO.error(ex.getCode(), ex.getMessage(), RequestIdUtil.getRequestId(request), OffsetDateTime.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseVO<Map<String, List<Map<String, String>>>> handleValidation(MethodArgumentNotValidException ex,
                                                                               HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldError)
            .toList();
        return ResponseVO.error(
            ErrorCodeConstants.VALIDATION_ERROR,
            "参数校验失败",
            Map.of("errors", errors),
            RequestIdUtil.getRequestId(request),
            OffsetDateTime.now()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseVO<Void> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseVO.error(
            ErrorCodeConstants.VALIDATION_ERROR,
            ex.getMessage(),
            RequestIdUtil.getRequestId(request),
            OffsetDateTime.now()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseVO<Void> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return ResponseVO.error(
            ErrorCodeConstants.INTERNAL_ERROR,
            "服务器内部错误",
            RequestIdUtil.getRequestId(request),
            OffsetDateTime.now()
        );
    }

    private Map<String, String> toFieldError(FieldError fieldError) {
        return Map.of(
            "field", fieldError.getField(),
            "message", fieldError.getDefaultMessage() == null ? "参数错误" : fieldError.getDefaultMessage()
        );
    }
}
