package com.getoffer.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一 HTTP 链路日志过滤器。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceLoggingFilter extends OncePerRequestFilter {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_HTTP_PATH = "httpPath";
    private static final String MDC_HTTP_METHOD = "httpMethod";

    private final ObjectMapper objectMapper;
    private final ObservabilityHttpLogProperties properties;
    private final AntPathMatcher pathMatcher;

    public RequestTraceLoggingFilter(ObjectMapper objectMapper,
                                     ObservabilityHttpLogProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null || !properties.isEnabled()) {
            return true;
        }
        String path = normalizePath(request.getRequestURI());
        if (matchesAny(path, properties.getExcludePathPatterns())) {
            return true;
        }
        List<String> includePatterns = properties.getIncludePathPatterns();
        if (includePatterns == null || includePatterns.isEmpty()) {
            return false;
        }
        return !matchesAny(path, includePatterns);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveOrCreateHeader(request.getHeader(HEADER_TRACE_ID));
        String requestId = resolveOrCreateHeader(request.getHeader(HEADER_REQUEST_ID));
        String path = normalizePath(request.getRequestURI());
        String method = request.getMethod();

        response.setHeader(HEADER_TRACE_ID, traceId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_HTTP_PATH, path);
        MDC.put(MDC_HTTP_METHOD, method);

        long startNs = System.nanoTime();
        boolean sampled = shouldSample();

        ContentCachingRequestWrapper requestWrapper = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = response instanceof ContentCachingResponseWrapper
                ? (ContentCachingResponseWrapper) response
                : new ContentCachingResponseWrapper(response);

        Throwable error = null;
        if (sampled) {
            log.info("HTTP_IN method={}, path={}, query={}, clientIp={}, userAgent={}",
                    method,
                    path,
                    sanitizeQuery(request.getQueryString()),
                    resolveClientIp(request),
                    truncate(request.getHeader("User-Agent"), 200));
        }

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception ex) {
            error = ex;
            throw ex;
        } finally {
            long costMs = (System.nanoTime() - startNs) / 1_000_000L;
            String responseCode = extractResponseCode(responseWrapper);
            String requestBodySummary = extractRequestBodySummary(requestWrapper);
            boolean slowRequest = costMs >= Math.max(properties.getSlowRequestThresholdMs(), 0L);
            if (sampled || error != null || slowRequest) {
                String outcome = error == null ? "success" : "error";
                if (error == null) {
                    log.info("HTTP_OUT method={}, path={}, status={}, responseCode={}, costMs={}, outcome={}, requestBodySummary={}",
                            method,
                            path,
                            responseWrapper.getStatus(),
                            StringUtils.defaultIfBlank(responseCode, "-"),
                            costMs,
                            outcome,
                            requestBodySummary);
                } else {
                    log.warn("HTTP_OUT method={}, path={}, status={}, responseCode={}, costMs={}, outcome={}, errorType={}, errorMessage={}, requestBodySummary={}",
                            method,
                            path,
                            responseWrapper.getStatus(),
                            StringUtils.defaultIfBlank(responseCode, "-"),
                            costMs,
                            outcome,
                            error.getClass().getSimpleName(),
                            truncate(error.getMessage(), 200),
                            requestBodySummary);
                }
            }

            responseWrapper.copyBodyToResponse();
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_HTTP_PATH);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private String resolveOrCreateHeader(String value) {
        if (StringUtils.isNotBlank(value)) {
            return value.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean shouldSample() {
        double rate = properties.getSampleRate();
        if (rate <= 0D) {
            return false;
        }
        if (rate >= 1D) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() <= rate;
    }

    private String extractResponseCode(ContentCachingResponseWrapper responseWrapper) {
        if (responseWrapper == null) {
            return null;
        }
        byte[] body = responseWrapper.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return null;
        }
        String contentType = responseWrapper.getContentType();
        if (StringUtils.isBlank(contentType) || !contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE)) {
            return null;
        }
        try {
            Map<String, Object> responseMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            Object code = responseMap.get("code");
            return code == null ? null : String.valueOf(code);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractRequestBodySummary(ContentCachingRequestWrapper requestWrapper) {
        if (requestWrapper == null || !properties.isLogRequestBody()) {
            return "-";
        }
        byte[] body = requestWrapper.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return "-";
        }
        String contentType = requestWrapper.getContentType();
        String payload = new String(body, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(payload)) {
            return "-";
        }

        if (StringUtils.isNotBlank(contentType) && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE)) {
            try {
                Map<String, Object> source = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
                });
                Map<String, Object> summary = new LinkedHashMap<>();
                List<String> whitelist = properties.getRequestBodyWhitelist();
                if (whitelist != null) {
                    for (String key : whitelist) {
                        if (StringUtils.isBlank(key) || !source.containsKey(key)) {
                            continue;
                        }
                        summary.put(key, maskValue(key, source.get(key)));
                    }
                }
                if (summary.isEmpty()) {
                    return "-";
                }
                return truncate(objectMapper.writeValueAsString(summary), Math.max(64, properties.getMaxBodyLength()));
            } catch (Exception ignored) {
                return truncate(maskRawBody(payload), Math.max(64, properties.getMaxBodyLength()));
            }
        }
        return truncate(maskRawBody(payload), Math.max(64, properties.getMaxBodyLength()));
    }

    private Object maskValue(String key, Object value) {
        if (!isMaskField(key)) {
            if (value instanceof String text) {
                return truncate(text, 200);
            }
            return value;
        }
        return "***";
    }

    private boolean isMaskField(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        List<String> maskFields = properties.getMaskFields();
        if (maskFields == null || maskFields.isEmpty()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (String maskField : maskFields) {
            if (StringUtils.isBlank(maskField)) {
                continue;
            }
            if (normalized.equals(maskField.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeQuery(String queryString) {
        if (StringUtils.isBlank(queryString)) {
            return "-";
        }
        String[] parts = queryString.split("&");
        List<String> sanitized = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            String[] kv = part.split("=", 2);
            String key = kv[0];
            if (isMaskField(key)) {
                sanitized.add(key + "=***");
            } else {
                String value = kv.length > 1 ? kv[1] : "";
                sanitized.add(key + "=" + truncate(value, 80));
            }
        }
        return sanitized.isEmpty() ? "-" : String.join("&", sanitized);
    }

    private String maskRawBody(String payload) {
        if (StringUtils.isBlank(payload)) {
            return "-";
        }
        String result = payload;
        List<String> maskFields = properties.getMaskFields();
        if (maskFields == null || maskFields.isEmpty()) {
            return result;
        }
        for (String field : maskFields) {
            if (StringUtils.isBlank(field)) {
                continue;
            }
            String escapedField = java.util.regex.Pattern.quote(field);
            result = result.replaceAll("(?i)(\"" + escapedField + "\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
            result = result.replaceAll("(?i)(" + escapedField + "\\s*[=:]\\s*)[^,;&\\s]+", "$1***");
        }
        return result;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            String[] segments = forwarded.split(",");
            if (segments.length > 0 && StringUtils.isNotBlank(segments[0])) {
                return segments[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return StringUtils.defaultIfBlank(request.getRemoteAddr(), "unknown");
    }

    private boolean matchesAny(String path, List<String> patterns) {
        if (StringUtils.isBlank(path) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (StringUtils.isBlank(pattern)) {
                continue;
            }
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        return StringUtils.defaultIfBlank(path, "/").trim();
    }

    private String truncate(String text, int maxLength) {
        if (StringUtils.isBlank(text) || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
