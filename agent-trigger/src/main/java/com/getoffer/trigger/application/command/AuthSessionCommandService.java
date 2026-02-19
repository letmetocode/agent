package com.getoffer.trigger.application.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.AuthLoginRequestDTO;
import com.getoffer.api.dto.AuthLoginResponseDTO;
import com.getoffer.api.dto.AuthLogoutResponseDTO;
import com.getoffer.api.dto.AuthMeResponseDTO;
import com.getoffer.domain.session.adapter.repository.IAuthSessionBlacklistRepository;
import com.getoffer.domain.session.model.entity.AuthSessionBlacklistEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地账号认证会话写用例（JWT + 黑名单吊销）。
 */
@Service
public class AuthSessionCommandService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_ALG = "HS256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Map<String, LocalDateTime> userLastLoginAt = new ConcurrentHashMap<>();

    private final String localUsername;
    private final String localPassword;
    private final String localDisplayName;
    private final int accessTtlMinutes;
    private final String jwtSecret;
    private final String jwtIssuer;
    private final ObjectMapper objectMapper;
    private final IAuthSessionBlacklistRepository blacklistRepository;

    public AuthSessionCommandService(String localUsername,
                                     String localPassword,
                                     String localDisplayName,
                                     int tokenTtlHours) {
        this(localUsername,
                localPassword,
                localDisplayName,
                Math.max(tokenTtlHours, 1) * 60,
                "agent-app",
                "dev-insecure-jwt-secret",
                new ObjectMapper(),
                new NoopBlacklistRepository());
    }

    @Autowired
    public AuthSessionCommandService(@Value("${app.auth.local.username:admin}") String localUsername,
                                     @Value("${app.auth.local.password:admin123}") String localPassword,
                                     @Value("${app.auth.local.display-name:Operator}") String localDisplayName,
                                     @Value("${app.auth.token.ttl-hours:24}") int tokenTtlHours,
                                     @Value("${app.auth.jwt.access-ttl-minutes:0}") int jwtAccessTtlMinutes,
                                     @Value("${app.auth.jwt.issuer:agent-app}") String jwtIssuer,
                                     @Value("${app.auth.jwt.secret:dev-insecure-jwt-secret}") String jwtSecret,
                                     ObjectMapper objectMapper,
                                     @Autowired(required = false) IAuthSessionBlacklistRepository blacklistRepository) {
        this(localUsername,
                localPassword,
                localDisplayName,
                resolveAccessTtlMinutes(tokenTtlHours, jwtAccessTtlMinutes),
                jwtIssuer,
                jwtSecret,
                objectMapper,
                blacklistRepository);
    }

    private AuthSessionCommandService(String localUsername,
                                      String localPassword,
                                      String localDisplayName,
                                      int accessTtlMinutes,
                                      String jwtIssuer,
                                      String jwtSecret,
                                      ObjectMapper objectMapper,
                                      IAuthSessionBlacklistRepository blacklistRepository) {
        this.localUsername = StringUtils.defaultIfBlank(localUsername, "admin");
        this.localPassword = StringUtils.defaultIfBlank(localPassword, "admin123");
        this.localDisplayName = StringUtils.defaultIfBlank(localDisplayName, this.localUsername);
        this.accessTtlMinutes = Math.max(accessTtlMinutes, 1);
        this.jwtIssuer = StringUtils.defaultIfBlank(jwtIssuer, "agent-app");
        this.jwtSecret = StringUtils.defaultIfBlank(jwtSecret, "dev-insecure-jwt-secret");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.blacklistRepository = blacklistRepository == null ? new NoopBlacklistRepository() : blacklistRepository;
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

        Instant now = Instant.now();
        Instant expiresAtInstant = now.plusSeconds(accessTtlMinutes * 60L);
        String jti = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("displayName", localDisplayName);
        payload.put("iss", jwtIssuer);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAtInstant.getEpochSecond());
        payload.put("jti", jti);

        String token = sign(payload);
        LocalDateTime nowAt = toLocalDateTime(now);
        LocalDateTime expiresAt = toLocalDateTime(expiresAtInstant);
        userLastLoginAt.put(username, nowAt);

        AuthLoginResponseDTO dto = new AuthLoginResponseDTO();
        dto.setUserId(username);
        dto.setDisplayName(localDisplayName);
        dto.setToken(token);
        dto.setTokenType("Bearer");
        dto.setIssuedAt(nowAt);
        dto.setExpiresAt(expiresAt);
        dto.setJti(jti);
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
        try {
            JwtClaims claims = parseAndValidate(token, true);
            AuthSessionBlacklistEntity blacklistEntity = new AuthSessionBlacklistEntity();
            blacklistEntity.setJti(claims.jti());
            blacklistEntity.setUserId(claims.userId());
            blacklistEntity.setExpiredAt(claims.expiresAt());
            blacklistEntity.setRevokedAt(LocalDateTime.now());
            blacklistEntity.setRevokeReason("USER_LOGOUT");
            blacklistRepository.save(blacklistEntity);

            dto.setSuccess(true);
            dto.setMessage("已退出登录");
            return dto;
        } catch (IllegalArgumentException ex) {
            dto.setSuccess(false);
            dto.setMessage(ex.getMessage());
            return dto;
        }
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
        JwtClaims claims = parseAndValidate(token, false);
        if (blacklistRepository.existsActiveByJti(claims.jti(), LocalDateTime.now())) {
            throw new IllegalArgumentException("登录态已失效，请重新登录");
        }
        return new AuthPrincipal(claims.userId(), claims.displayName(), claims.expiresAt(), claims.jti());
    }

    private JwtClaims parseAndValidate(String token, boolean allowExpired) {
        TokenParts parts = parseTokenParts(token);
        Map<String, Object> header = parseJson(parts.headerJson());
        Map<String, Object> payload = parseJson(parts.payloadJson());

        String alg = getString(header, "alg");
        if (!StringUtils.equals(alg, JWT_ALG)) {
            throw new IllegalArgumentException("登录令牌签名算法不支持");
        }

        String expectedSign = signRaw(parts.headerPart() + "." + parts.payloadPart());
        if (!Objects.equals(expectedSign, parts.signaturePart())) {
            throw new IllegalArgumentException("登录令牌签名无效");
        }

        String issuer = getString(payload, "iss");
        if (StringUtils.isNotBlank(jwtIssuer) && !StringUtils.equals(jwtIssuer, issuer)) {
            throw new IllegalArgumentException("登录令牌签发方不匹配");
        }

        String userId = getString(payload, "sub", "userId", "user_id");
        String displayName = StringUtils.defaultIfBlank(getString(payload, "displayName", "display_name"), localDisplayName);
        String jti = getString(payload, "jti");
        Long expEpoch = getLong(payload, "exp");
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(jti) || expEpoch == null) {
            throw new IllegalArgumentException("登录令牌缺少必要字段");
        }

        LocalDateTime expiresAt = toLocalDateTime(Instant.ofEpochSecond(expEpoch));
        if (!allowExpired && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("登录态已过期，请重新登录");
        }
        return new JwtClaims(userId, displayName, jti, expiresAt);
    }

    private String sign(Map<String, Object> payload) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", JWT_ALG);
        header.put("typ", "JWT");
        try {
            String headerPart = encodeBase64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = encodeBase64Url(objectMapper.writeValueAsBytes(payload));
            String raw = headerPart + "." + payloadPart;
            String signature = signRaw(raw);
            return raw + "." + signature;
        } catch (Exception ex) {
            throw new IllegalArgumentException("生成登录令牌失败");
        }
    }

    private String signRaw(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return encodeBase64Url(signBytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("登录令牌签名失败");
        }
    }

    private TokenParts parseTokenParts(String token) {
        if (StringUtils.isBlank(token)) {
            throw new IllegalArgumentException("未提供登录令牌");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("登录令牌格式错误");
        }
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return new TokenParts(parts[0], parts[1], parts[2], headerJson, payloadJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("登录令牌格式错误");
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            throw new IllegalArgumentException("登录令牌解析失败");
        }
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

    private String encodeBase64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static int resolveAccessTtlMinutes(int tokenTtlHours, int jwtAccessTtlMinutes) {
        if (jwtAccessTtlMinutes > 0) {
            return jwtAccessTtlMinutes;
        }
        return Math.max(tokenTtlHours, 1) * 60;
    }

    public record AuthPrincipal(String userId,
                                String displayName,
                                LocalDateTime expiresAt,
                                String jti) {
    }

    private record TokenParts(String headerPart,
                              String payloadPart,
                              String signaturePart,
                              String headerJson,
                              String payloadJson) {
    }

    private record JwtClaims(String userId,
                             String displayName,
                             String jti,
                             LocalDateTime expiresAt) {
    }

    private static class NoopBlacklistRepository implements IAuthSessionBlacklistRepository {
        @Override
        public void save(AuthSessionBlacklistEntity entity) {
        }

        @Override
        public boolean existsActiveByJti(String jti, LocalDateTime now) {
            return false;
        }
    }
}
