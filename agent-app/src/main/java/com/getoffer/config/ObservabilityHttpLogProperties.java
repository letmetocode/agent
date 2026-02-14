package com.getoffer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * HTTP 入口日志配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "observability.http-log", ignoreInvalidFields = true)
public class ObservabilityHttpLogProperties {

    /** 是否启用 HTTP 入口日志。 */
    private boolean enabled = true;

    /** 需要记录日志的路径模式。 */
    private List<String> includePathPatterns = Arrays.asList("/api/**");

    /** 需要排除日志的路径模式。 */
    private List<String> excludePathPatterns = Arrays.asList("/actuator/**", "/api/v3/chat/sessions/*/stream");

    /** 是否记录请求体摘要。 */
    private boolean logRequestBody = false;

    /** 请求体摘要白名单字段。 */
    private List<String> requestBodyWhitelist = Arrays.asList("message", "sessionId", "turnId", "planId");

    /** 需要脱敏的字段名。 */
    private List<String> maskFields = Arrays.asList("apiKey", "authorization", "password", "token", "secret");

    /** 慢请求阈值。 */
    private long slowRequestThresholdMs = 1000L;

    /** 采样比例（0~1）。 */
    private double sampleRate = 1.0D;

    /** 请求体日志最大长度。 */
    private int maxBodyLength = 1024;
}
