package com.getoffer.infrastructure.mcp;

import com.getoffer.infrastructure.util.JsonCodec;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MCP 客户端管理器（Spring AI 1.1.2 标准实现）。
 * <p>
 * 负责：
 * </p>
 * <ul>
 *   <li>根据数据库 serverConfig 构建并缓存 McpSyncClient</li>
 *   <li>支持 stdio / sse / streamable_http / auto 传输</li>
 *   <li>将 MCP tools 转换为 Spring AI ToolCallback（SyncMcpToolCallback）</li>
 * </ul>
 */
@Slf4j
@Component
public class McpClientManager {

    private static final String TRANSPORT_STDIO = "stdio";
    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_STREAMABLE = "streamable_http";
    private static final String TRANSPORT_AUTO = "auto";
    private static final long DEFAULT_READ_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 10_000L;

    private final ConcurrentMap<String, ManagedSyncClient> clients = new ConcurrentHashMap<>();
    private final JsonCodec jsonCodec;
    private final McpJsonMapper mcpJsonMapper;

    public McpClientManager(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        this.mcpJsonMapper = McpJsonMapper.getDefault();
    }

    /**
     * 按工具名获取 ToolCallback。
     */
    public ToolCallback getToolCallback(Map<String, Object> serverConfig, String toolName) {
        if (serverConfig == null || serverConfig.isEmpty() || StringUtils.isBlank(toolName)) {
            return null;
        }
        ManagedSyncClient client = getOrCreateClient(serverConfig);
        if (client == null) {
            return null;
        }
        return client.getToolCallback(toolName);
    }

    @PreDestroy
    public void shutdownAll() {
        for (ManagedSyncClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception ex) {
                log.warn("Failed to shutdown MCP sync client: {}", ex.getMessage());
            }
        }
        clients.clear();
    }

    private ManagedSyncClient getOrCreateClient(Map<String, Object> serverConfig) {
        String serverId = resolveServerId(serverConfig);
        return clients.compute(serverId, (key, existing) -> {
            if (existing != null && existing.isAvailable()) {
                return existing;
            }
            if (existing != null) {
                existing.close();
            }
            return createClient(serverId, serverConfig);
        });
    }

    private ManagedSyncClient createClient(String serverId, Map<String, Object> serverConfig) {
        String transport = resolveTransport(serverConfig);
        if (TRANSPORT_AUTO.equals(transport)) {
            ManagedSyncClient streamable = tryCreateClient(serverId, serverConfig, TRANSPORT_STREAMABLE);
            if (streamable != null) {
                return streamable;
            }
            ManagedSyncClient sse = tryCreateClient(serverId, serverConfig, TRANSPORT_SSE);
            if (sse != null) {
                return sse;
            }
            return tryCreateClient(serverId, serverConfig, TRANSPORT_STDIO);
        }
        return tryCreateClient(serverId, serverConfig, transport);
    }

    private ManagedSyncClient tryCreateClient(String serverId,
                                              Map<String, Object> serverConfig,
                                              String transport) {
        if (StringUtils.isBlank(transport)) {
            return null;
        }
        try {
            McpSyncClient syncClient = buildSyncClient(serverConfig, transport);
            log.info("MCP sync client initialized: serverId={}, transport={}", serverId, transport);
            return new ManagedSyncClient(serverId, transport, syncClient);
        } catch (Exception ex) {
            log.warn("Failed to create MCP sync client (serverId={}, transport={}): {}",
                    serverId, transport, ex.getMessage());
            return null;
        }
    }

    private McpSyncClient buildSyncClient(Map<String, Object> serverConfig, String transport) {
        McpClientTransport clientTransport = buildSyncTransport(serverConfig, transport);
        Duration timeout = Duration.ofMillis(resolveReadTimeoutMs(serverConfig));
        McpSyncClient syncClient = McpClient.sync(clientTransport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("agent-app", "Agent", "1.0"))
                .build();
        syncClient.initialize();
        return syncClient;
    }

    private McpClientTransport buildSyncTransport(Map<String, Object> serverConfig, String transport) {
        if (TRANSPORT_STDIO.equals(transport)) {
            return buildSyncStdioTransport(serverConfig);
        }
        if (TRANSPORT_SSE.equals(transport)) {
            return buildSyncSseTransport(serverConfig);
        }
        if (TRANSPORT_STREAMABLE.equals(transport)) {
            return buildSyncStreamableTransport(serverConfig);
        }
        throw new IllegalStateException("Unsupported MCP transport: " + transport);
    }

    private StdioClientTransport buildSyncStdioTransport(Map<String, Object> serverConfig) {
        String command = getString(serverConfig, "command", "cmd");
        if (StringUtils.isBlank(command)) {
            throw new IllegalStateException("MCP stdio command is empty");
        }

        ServerParameters.Builder builder = ServerParameters.builder(command);
        List<String> args = getStringList(serverConfig, "args");
        if (!args.isEmpty()) {
            builder.args(args);
        }
        Map<String, String> env = resolveEnv(serverConfig);
        if (!env.isEmpty()) {
            builder.env(env);
        }

        StdioClientTransport transport = new StdioClientTransport(builder.build(), mcpJsonMapper);
        transport.setStdErrorHandler(stderr -> {
            if (StringUtils.isNotBlank(stderr)) {
                log.warn("MCP stdio stderr: {}", stderr);
            }
        });
        return transport;
    }

    private HttpClientSseClientTransport buildSyncSseTransport(Map<String, Object> serverConfig) {
        String sseUrl = resolveSseUrl(serverConfig);
        if (StringUtils.isBlank(sseUrl)) {
            throw new IllegalStateException("MCP SSE URL is empty");
        }

        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(sseUrl)
                .jsonMapper(mcpJsonMapper)
                .connectTimeout(Duration.ofMillis(resolveConnectTimeoutMs(serverConfig)));

        Map<String, String> headers = resolveHeaders(serverConfig);
        if (!headers.isEmpty()) {
            builder.customizeRequest(requestBuilder -> headers.forEach(requestBuilder::header));
        }

        return builder.build();
    }

    private HttpClientStreamableHttpTransport buildSyncStreamableTransport(Map<String, Object> serverConfig) {
        String endpoint = resolveEndpointUrl(serverConfig);
        if (StringUtils.isBlank(endpoint)) {
            throw new IllegalStateException("MCP Streamable HTTP endpoint is empty");
        }

        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(endpoint)
                .jsonMapper(mcpJsonMapper)
                .connectTimeout(Duration.ofMillis(resolveConnectTimeoutMs(serverConfig)));

        Map<String, String> headers = resolveHeaders(serverConfig);
        if (!headers.isEmpty()) {
            builder.customizeRequest(requestBuilder -> headers.forEach(requestBuilder::header));
        }

        return builder.build();
    }

    private String resolveTransport(Map<String, Object> serverConfig) {
        String transport = getString(serverConfig, "transport", "mcpTransport", "protocol");
        if (StringUtils.isNotBlank(transport)) {
            return normalizeTransport(transport);
        }
        String command = getString(serverConfig, "command", "cmd");
        if (StringUtils.isNotBlank(command)) {
            return TRANSPORT_STDIO;
        }
        String sseUrl = getString(serverConfig, "sseUrl", "sse_url");
        if (StringUtils.isNotBlank(sseUrl)) {
            return TRANSPORT_SSE;
        }
        String httpUrl = resolveEndpointUrl(serverConfig);
        if (StringUtils.isNotBlank(httpUrl)) {
            return TRANSPORT_STREAMABLE;
        }
        return TRANSPORT_STDIO;
    }

    private String normalizeTransport(String transport) {
        String normalized = transport.trim().toLowerCase();
        if ("streamable-http".equals(normalized) || "streamable".equals(normalized) || "http".equals(normalized)) {
            return TRANSPORT_STREAMABLE;
        }
        if ("stdio".equals(normalized)) {
            return TRANSPORT_STDIO;
        }
        if ("sse".equals(normalized)) {
            return TRANSPORT_SSE;
        }
        if ("auto".equals(normalized)) {
            return TRANSPORT_AUTO;
        }
        return normalized;
    }

    private String resolveServerId(Map<String, Object> serverConfig) {
        String serverId = getString(serverConfig, "serverId", "server_id", "name", "serverName", "id");
        if (StringUtils.isNotBlank(serverId)) {
            return serverId;
        }
        String payload = jsonCodec.writeValue(serverConfig);
        if (StringUtils.isBlank(payload)) {
            return UUID.randomUUID().toString();
        }
        return UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private long resolveReadTimeoutMs(Map<String, Object> serverConfig) {
        long timeout = getLong(serverConfig, DEFAULT_READ_TIMEOUT_MS,
                "readTimeoutMs", "read_timeout_ms", "timeoutMs", "timeout_ms", "responseTimeoutMs", "response_timeout_ms");
        return timeout > 0 ? timeout : DEFAULT_READ_TIMEOUT_MS;
    }

    private long resolveConnectTimeoutMs(Map<String, Object> serverConfig) {
        long timeout = getLong(serverConfig, DEFAULT_CONNECT_TIMEOUT_MS,
                "connectTimeoutMs", "connect_timeout_ms");
        return timeout > 0 ? timeout : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    private String resolveEndpointUrl(Map<String, Object> serverConfig) {
        String endpoint = getString(serverConfig, "url", "endpoint", "mcpEndpoint", "mcp_endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            return endpoint;
        }
        return getString(serverConfig, "baseUrl", "base_url");
    }

    private String resolveSseUrl(Map<String, Object> serverConfig) {
        String sseUrl = getString(serverConfig, "sseUrl", "sse_url");
        if (StringUtils.isNotBlank(sseUrl)) {
            return sseUrl;
        }
        return resolveEndpointUrl(serverConfig);
    }

    private Map<String, String> resolveHeaders(Map<String, Object> serverConfig) {
        Map<String, Object> headerMap = getMap(serverConfig, "headers");
        if (headerMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headerMap.forEach((key, value) -> {
            if (value != null) {
                headers.put(key, String.valueOf(value));
            }
        });
        return headers;
    }

    private Map<String, String> resolveEnv(Map<String, Object> serverConfig) {
        Map<String, Object> envMap = getMap(serverConfig, "env");
        if (envMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> env = new LinkedHashMap<>();
        envMap.forEach((key, value) -> {
            if (value != null) {
                env.put(key, String.valueOf(value));
            }
        });
        return env;
    }

    private String getString(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private List<String> getStringList(Map<String, Object> config, String key) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyList();
        }
        Object value = config.get(key);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = text.split("\\s+");
            List<String> result = new ArrayList<>();
            Collections.addAll(result, parts);
            return result;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> config, String key) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        Object value = config.get(key);
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private long getLong(Map<String, Object> config, long defaultValue, String... keys) {
        if (config == null || config.isEmpty() || keys == null || keys.length == 0) {
            return defaultValue;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static class ManagedSyncClient {

        private final String serverId;
        private final String transport;
        private final McpSyncClient syncClient;
        private final ConcurrentMap<String, ToolCallback> callbackCache = new ConcurrentHashMap<>();

        ManagedSyncClient(String serverId, String transport, McpSyncClient syncClient) {
            this.serverId = serverId;
            this.transport = transport;
            this.syncClient = syncClient;
        }

        ToolCallback getToolCallback(String toolName) {
            ToolCallback cached = callbackCache.get(toolName);
            if (cached != null) {
                return cached;
            }

            synchronized (this) {
                cached = callbackCache.get(toolName);
                if (cached != null) {
                    return cached;
                }

                refreshToolCallbacks();
                ToolCallback resolved = callbackCache.get(toolName);
                if (resolved == null) {
                    log.warn("MCP tool '{}' not found on server '{}'", toolName, serverId);
                }
                return resolved;
            }
        }

        boolean isAvailable() {
            return syncClient != null;
        }

        void close() {
            if (syncClient == null) {
                return;
            }
            try {
                if (!syncClient.closeGracefully()) {
                    syncClient.close();
                }
            } catch (Exception ex) {
                log.warn("Failed to close MCP sync client '{}': {}", serverId, ex.getMessage());
            }
        }

        private void refreshToolCallbacks() {
            List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(Collections.singletonList(syncClient));
            callbackCache.clear();
            for (ToolCallback callback : callbacks) {
                if (callback == null || callback.getToolDefinition() == null) {
                    continue;
                }

                String callbackName = callback.getToolDefinition().name();
                if (StringUtils.isNotBlank(callbackName)) {
                    callbackCache.put(callbackName, callback);
                }

                if (callback instanceof SyncMcpToolCallback syncMcpToolCallback) {
                    String originalToolName = syncMcpToolCallback.getOriginalToolName();
                    if (StringUtils.isNotBlank(originalToolName)) {
                        callbackCache.putIfAbsent(originalToolName, callback);
                    }
                }
            }
            log.info("Loaded {} MCP tool callbacks for server '{}' via {}",
                    callbackCache.size(), serverId, transport);
        }
    }
}
