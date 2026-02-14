package com.getoffer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.response.Response;
import com.getoffer.trigger.application.command.AuthSessionCommandService;
import com.getoffer.types.enums.ResponseCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * `/api/**` 统一鉴权过滤器（白名单接口除外）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiAuthFilter extends OncePerRequestFilter {

    public static final String REQ_ATTR_AUTH_USER_ID = "auth.userId";
    public static final String REQ_ATTR_AUTH_DISPLAY_NAME = "auth.displayName";

    private static final String API_PREFIX = "/api/";
    private static final String ACCESS_TOKEN_PARAM = "accessToken";
    private static final List<String> WHITELIST_PATTERNS = List.of(
            "/api/auth/login",
            "/api/share/tasks/**"
    );

    private final ObjectMapper objectMapper;
    private final AuthSessionCommandService authSessionCommandService;
    private final AntPathMatcher antPathMatcher;

    public ApiAuthFilter(ObjectMapper objectMapper,
                         AuthSessionCommandService authSessionCommandService) {
        this.objectMapper = objectMapper;
        this.authSessionCommandService = authSessionCommandService;
        this.antPathMatcher = new AntPathMatcher();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request.getRequestURI());
        if (!path.startsWith(API_PREFIX)) {
            return true;
        }
        for (String pattern : WHITELIST_PATTERNS) {
            if (antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = normalizePath(request.getRequestURI());
        try {
            String authorization = resolveAuthorization(request, path);
            AuthSessionCommandService.AuthPrincipal principal = authSessionCommandService.requireValidSession(authorization);
            request.setAttribute(REQ_ATTR_AUTH_USER_ID, principal.userId());
            request.setAttribute(REQ_ATTR_AUTH_DISPLAY_NAME, principal.displayName());
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            if (log.isDebugEnabled()) {
                log.debug("Auth rejected. method={}, path={}, reason={}", request.getMethod(), path, ex.getMessage());
            }
            writeUnauthorized(response, ex.getMessage());
        }
    }

    private String resolveAuthorization(HttpServletRequest request, String path) {
        String authorization = StringUtils.trimToNull(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (StringUtils.isNotBlank(authorization)) {
            return authorization;
        }
        if (antPathMatcher.match("/api/v3/chat/sessions/*/stream", path)) {
            String accessToken = StringUtils.trimToNull(request.getParameter(ACCESS_TOKEN_PARAM));
            if (StringUtils.isNotBlank(accessToken)) {
                return "Bearer " + accessToken;
            }
        }
        return authorization;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        Response<Void> body = Response.<Void>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(StringUtils.defaultIfBlank(message, "未登录或登录态已失效"))
                .build();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return "/";
        }
        return path.trim();
    }
}
