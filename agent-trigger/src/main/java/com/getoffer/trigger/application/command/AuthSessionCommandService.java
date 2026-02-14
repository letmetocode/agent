package com.getoffer.trigger.application.command;

import com.getoffer.api.dto.AuthLoginRequestDTO;
import com.getoffer.api.dto.AuthLoginResponseDTO;
import com.getoffer.api.dto.AuthLogoutResponseDTO;
import com.getoffer.api.dto.AuthMeResponseDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地账号认证会话写用例。
 */
@Service
public class AuthSessionCommandService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BEARER_PREFIX = "Bearer ";

    private final Map<String, AuthSession> tokenSessions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> userLastLoginAt = new ConcurrentHashMap<>();

    private final String localUsername;
    private final String localPassword;
    private final String localDisplayName;
    private final int tokenTtlHours;

    public AuthSessionCommandService(@Value("${app.auth.local.username:admin}") String localUsername,
                                     @Value("${app.auth.local.password:admin123}") String localPassword,
                                     @Value("${app.auth.local.display-name:Operator}") String localDisplayName,
                                     @Value("${app.auth.token.ttl-hours:24}") int tokenTtlHours) {
        this.localUsername = StringUtils.defaultIfBlank(localUsername, "admin");
        this.localPassword = StringUtils.defaultIfBlank(localPassword, "admin123");
        this.localDisplayName = StringUtils.defaultIfBlank(localDisplayName, this.localUsername);
        this.tokenTtlHours = Math.max(tokenTtlHours, 1);
    }

    public AuthLoginResponseDTO login(AuthLoginRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String username = StringUtils.trimToEmpty(request.getUsername());
        String password = StringUtils.defaultString(request.getPassword());
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        if (!StringUtils.equals(username, localUsername) || !StringUtils.equals(password, localPassword)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        removeExpiredSessions();
        LocalDateTime now = LocalDateTime.now();
        String token = generateToken();
        LocalDateTime expiresAt = now.plusHours(tokenTtlHours);
        tokenSessions.put(token, new AuthSession(username, localDisplayName, expiresAt));
        userLastLoginAt.put(username, now);

        AuthLoginResponseDTO dto = new AuthLoginResponseDTO();
        dto.setUserId(username);
        dto.setDisplayName(localDisplayName);
        dto.setToken(token);
        dto.setExpiresAt(expiresAt);
        return dto;
    }

    public AuthLogoutResponseDTO logout(String authorization) {
        String token = parseToken(authorization);
        AuthLogoutResponseDTO dto = new AuthLogoutResponseDTO();
        if (StringUtils.isBlank(token)) {
            dto.setSuccess(false);
            dto.setMessage("未提供登录令牌");
            return dto;
        }
        AuthSession removed = tokenSessions.remove(token);
        dto.setSuccess(removed != null);
        dto.setMessage(removed == null ? "登录态不存在或已失效" : "已退出登录");
        return dto;
    }

    public AuthMeResponseDTO me(String authorization) {
        AuthPrincipal principal = requireValidSession(authorization);

        AuthMeResponseDTO dto = new AuthMeResponseDTO();
        dto.setUserId(principal.userId());
        dto.setDisplayName(principal.displayName());
        dto.setIsOperator(true);
        dto.setLastLoginAt(userLastLoginAt.getOrDefault(principal.userId(), LocalDateTime.now()));
        return dto;
    }

    public AuthPrincipal requireValidSession(String authorization) {
        String token = parseToken(authorization);
        return requireValidToken(token);
    }

    public AuthPrincipal requireValidToken(String token) {
        if (StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("未提供登录令牌");
        }
        removeExpiredSessions();
        AuthSession session = tokenSessions.get(token);
        if (session == null || session.expiresAt().isBefore(LocalDateTime.now())) {
            tokenSessions.remove(token);
            throw new IllegalArgumentException("登录态已过期，请重新登录");
        }
        return new AuthPrincipal(session.userId(), session.displayName(), session.expiresAt());
    }

    private String parseToken(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.length() < BEARER_PREFIX.length()) {
            return null;
        }
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.isBlank(token) ? null : token;
    }

    private void removeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        tokenSessions.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt().isBefore(now));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record AuthSession(String userId,
                               String displayName,
                               LocalDateTime expiresAt) {
    }

    public record AuthPrincipal(String userId,
                                String displayName,
                                LocalDateTime expiresAt) {
    }
}
