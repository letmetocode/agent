package com.getoffer.trigger.http;

import com.getoffer.api.response.Response;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 统一 API 异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Response<Object> handleAppException(AppException ex, HttpServletRequest request) {
        String code = StringUtils.defaultIfBlank(ex.getCode(), ResponseCode.UN_ERROR.getCode());
        String info = StringUtils.defaultIfBlank(ex.getInfo(), ResponseCode.UN_ERROR.getInfo());
        log.warn("HTTP_ERROR path={}, method={}, traceId={}, requestId={}, errorType={}, errorCode={}, errorMessage={}",
                resolvePath(request),
                resolveMethod(request),
                resolveTraceId(),
                resolveRequestId(),
                ex.getClass().getSimpleName(),
                code,
                info);
        return Response.<Object>builder()
                .code(code)
                .info(info)
                .build();
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public Response<Object> handleBadRequestException(Exception ex, HttpServletRequest request) {
        String info = StringUtils.defaultIfBlank(ex.getMessage(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        log.warn("HTTP_ERROR path={}, method={}, traceId={}, requestId={}, errorType={}, errorCode={}, errorMessage={}",
                resolvePath(request),
                resolveMethod(request),
                resolveTraceId(),
                resolveRequestId(),
                ex.getClass().getSimpleName(),
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                truncate(info, 300));
        return Response.<Object>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(truncate(info, 300))
                .build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Object> handleUnknownException(Exception ex, HttpServletRequest request) {
        log.error("HTTP_ERROR path={}, method={}, traceId={}, requestId={}, errorType={}, errorCode={}, errorMessage={}",
                resolvePath(request),
                resolveMethod(request),
                resolveTraceId(),
                resolveRequestId(),
                ex.getClass().getSimpleName(),
                ResponseCode.UN_ERROR.getCode(),
                truncate(ex.getMessage(), 300),
                ex);
        return Response.<Object>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }

    private String resolvePath(HttpServletRequest request) {
        return request == null ? "-" : StringUtils.defaultIfBlank(request.getRequestURI(), "-");
    }

    private String resolveMethod(HttpServletRequest request) {
        return request == null ? "-" : StringUtils.defaultIfBlank(request.getMethod(), "-");
    }

    private String resolveTraceId() {
        return StringUtils.defaultIfBlank(MDC.get("traceId"), "-");
    }

    private String resolveRequestId() {
        return StringUtils.defaultIfBlank(MDC.get("requestId"), "-");
    }

    private String truncate(String text, int maxLength) {
        if (StringUtils.isBlank(text) || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
