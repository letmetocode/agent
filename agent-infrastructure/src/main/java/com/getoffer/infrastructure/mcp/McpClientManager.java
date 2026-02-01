package com.getoffer.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端管理器。
 * <p>
 * 负责：
 * <ul>
 *   <li>根据配置创建和管理MCP客户端实例</li>
 *   <li>支持进程型MCP Server（stdio）、SSE 以及 Streamable HTTP</li>
 *   <li>客户端实例的缓存和复用</li>
 *   <li>应用关闭时自动清理所有客户端</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
@Slf4j
@Component
public class McpClientManager {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String TRANSPORT_STDIO = "stdio";
    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_STREAMABLE = "streamable_http";
    private static final String TRANSPORT_AUTO = "auto";
    private static final long DEFAULT_READ_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 10_000L;

    private final ConcurrentMap<String, McpClient> clients = new ConcurrentHashMap<>();
    private final JsonCodec jsonCodec;

    /**
     * 创建 MCP 客户端管理器。
     */
    public McpClientManager(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    /**
     * 获取或创建 MCP 客户端。
     */
    public McpClient getClient(Map<String, Object> serverConfig) {
        if (serverConfig == null || serverConfig.isEmpty()) {
            return null;
        }
        String serverId = resolveServerId(serverConfig);
        return clients.compute(serverId, (key, existing) -> {
            if (existing != null && existing.isRunning()) {
                return existing;
            }
            if (existing != null) {
                existing.shutdown();
            }
            return createClient(serverId, serverConfig);
        });
    }

    /**
     * 调用 MCP 工具。
     */
    public String callTool(Map<String, Object> serverConfig,
                           String toolName,
                           String input,
                           Map<String, Object> toolContext) {
        McpClient client = getClient(serverConfig);
        if (client == null) {
            return buildError("MCP server config is empty");
        }
        return client.call(toolName, input, toolContext);
    }

    /**
     * 关闭所有 MCP 客户端。
     */
    @PreDestroy
    public void shutdownAll() {
        for (McpClient client : clients.values()) {
            try {
                client.shutdown();
            } catch (Exception ex) {
                log.warn("Failed to shutdown MCP client: {}", ex.getMessage());
            }
        }
        clients.clear();
    }

    /**
     * 创建 MCP 客户端。
     */
    private McpClient createClient(String serverId, Map<String, Object> serverConfig) {
        try {
            String transport = resolveTransport(serverConfig);
            if (TRANSPORT_SSE.equals(transport)) {
                return new SseMcpClient(serverId, serverConfig);
            }
            if (TRANSPORT_AUTO.equals(transport)) {
                return new AutoHttpMcpClient(serverId, serverConfig);
            }
            if (TRANSPORT_STREAMABLE.equals(transport)) {
                return new StreamableHttpMcpClient(serverId, serverConfig);
            }
            return new StdioMcpClient(serverId, serverConfig);
        } catch (Exception ex) {
            log.warn("Failed to create MCP client '{}': {}", serverId, ex.getMessage());
            return new FailedMcpClient(ex);
        }
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

    /**
     * 解析 MCP Server ID。
     */
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

    /**
     * 构建错误响应。
     */
    private String buildError(String message) {
        return jsonCodec.writeValue(Collections.singletonMap("error", message));
    }

    /**
     * 获取字符串配置。
     */
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

    /**
     * 获取字符串列表配置。
     */
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

    /**
     * 获取 Map 配置。
     */
    private Map<String, Object> getMap(Map<String, Object> config, String key) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        Object value = config.get(key);
        if (value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) value;
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * 获取 long 配置。
     */
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

    private Map<String, String> resolveHeaders(Map<String, Object> serverConfig) {
        Map<String, Object> rawHeaders = getMap(serverConfig, "headers");
        if (rawHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        rawHeaders.forEach((key, value) -> {
            if (value != null) {
                headers.put(key, String.valueOf(value));
            }
        });
        return headers;
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

    private abstract class BaseMcpClient implements McpClient {

        protected final String serverId;
        protected final Map<String, Object> serverConfig;
        protected final long readTimeoutMs;
        protected final AtomicLong requestId = new AtomicLong(1);
        protected final Object lock = new Object();

        BaseMcpClient(String serverId, Map<String, Object> serverConfig) {
            this.serverId = serverId;
            this.serverConfig = serverConfig;
            this.readTimeoutMs = resolveReadTimeoutMs(serverConfig);
        }

        protected Map<String, Object> buildRequestPayload(String toolName, String input, Map<String, Object> toolContext) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            Object arguments = parseArguments(input);
            params.put("arguments", arguments == null ? Collections.emptyMap() : arguments);
            if (toolContext != null && !toolContext.isEmpty()) {
                params.put("context", toolContext);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jsonrpc", JSONRPC_VERSION);
            payload.put("id", requestId.getAndIncrement());
            payload.put("method", METHOD_TOOLS_CALL);
            payload.put("params", params);
            return payload;
        }

        protected Object parseArguments(String input) {
            if (StringUtils.isBlank(input)) {
                return Collections.emptyMap();
            }
            try {
                return jsonCodec.getObjectMapper().readValue(input, Object.class);
            } catch (Exception ex) {
                log.warn("Failed to parse MCP tool input as JSON (serverId={}): {}", serverId, ex.getMessage());
                return input;
            }
        }

        protected String parseResponse(String response) {
            if (StringUtils.isBlank(response)) {
                return buildError("MCP server returned empty response");
            }
            try {
                JsonNode root = jsonCodec.getObjectMapper().readTree(response);
                JsonNode errorNode = root.get("error");
                if (errorNode != null && !errorNode.isNull()) {
                    String message = errorNode.has("message") ? errorNode.get("message").asText() : errorNode.toString();
                    return buildError(message);
                }
                JsonNode resultNode = root.get("result");
                if (resultNode != null && !resultNode.isNull()) {
                    String extracted = extractContentText(resultNode);
                    if (StringUtils.isNotBlank(extracted)) {
                        return extracted;
                    }
                    return resultNode.toString();
                }
            } catch (Exception ex) {
                return response;
            }
            return response;
        }

        protected String extractContentText(JsonNode resultNode) {
            if (resultNode.isTextual()) {
                return resultNode.asText();
            }
            JsonNode contentNode = resultNode.get("content");
            if (contentNode != null && contentNode.isArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode item : contentNode) {
                    String type = item.path("type").asText();
                    if (!"text".equalsIgnoreCase(type)) {
                        continue;
                    }
                    String text = item.path("text").asText();
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
            JsonNode textNode = resultNode.get("text");
            if (textNode != null && textNode.isTextual()) {
                return textNode.asText();
            }
            return null;
        }

        protected boolean matchesId(JsonNode node, long expectedId) {
            if (node == null) {
                return false;
            }
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull()) {
                return false;
            }
            if (idNode.isNumber()) {
                return idNode.asLong() == expectedId;
            }
            if (idNode.isTextual()) {
                return String.valueOf(expectedId).equals(idNode.asText());
            }
            return false;
        }

        protected String tryExtractResponse(String payload, long expectedId) {
            if (StringUtils.isBlank(payload)) {
                return null;
            }
            try {
                JsonNode root = jsonCodec.getObjectMapper().readTree(payload);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        if (matchesId(node, expectedId)) {
                            return parseResponse(node.toString());
                        }
                    }
                    return null;
                }
                if (matchesId(root, expectedId)) {
                    return parseResponse(payload);
                }
            } catch (Exception ex) {
                log.debug("Failed to parse MCP JSON payload (serverId={}): {}", serverId, ex.getMessage());
            }
            return null;
        }

        protected String extractResponseOrFallback(String payload, long expectedId) {
            String matched = tryExtractResponse(payload, expectedId);
            if (matched != null) {
                return matched;
            }
            if (StringUtils.isNotBlank(payload)) {
                return parseResponse(payload);
            }
            return buildError("MCP server returned empty response");
        }
    }

    /**
     * stdio MCP 客户端。
     */
    private class StdioMcpClient extends BaseMcpClient {

        private Process process;
        private OutputStream output;
        private InputStream input;
        private BufferedReader errorReader;
        private Thread stderrThread;
        private volatile boolean stopping;
        private Exception initException;

        StdioMcpClient(String serverId, Map<String, Object> serverConfig) throws IOException {
            super(serverId, serverConfig);
            startProcess();
        }

        @Override
        public String call(String toolName, String input, Map<String, Object> toolContext) {
            synchronized (lock) {
                if (!ensureRunning()) {
                    return buildError("MCP client is not running");
                }
                try {
                    return sendRequest(toolName, input, toolContext);
                } catch (Exception ex) {
                    log.warn("MCP call failed (serverId={}): {}", serverId, ex.getMessage());
                    return buildError("MCP call failed: " + ex.getMessage());
                }
            }
        }

        @Override
        public boolean isRunning() {
            return process != null && process.isAlive();
        }

        @Override
        public void shutdown() {
            synchronized (lock) {
                stopping = true;
                if (stderrThread != null) {
                    stderrThread.interrupt();
                }
                closeQuietly(input);
                closeQuietly(output);
                closeQuietly(errorReader);
                if (process != null) {
                    process.destroy();
                }
                process = null;
                input = null;
                output = null;
                errorReader = null;
                stderrThread = null;
            }
        }

        private boolean ensureRunning() {
            if (initException != null) {
                return false;
            }
            if (isRunning()) {
                return true;
            }
            try {
                startProcess();
                return isRunning();
            } catch (Exception ex) {
                initException = ex;
                return false;
            }
        }

        private void startProcess() throws IOException {
            String command = getString(serverConfig, "command", "cmd");
            if (StringUtils.isBlank(command)) {
                throw new IllegalStateException("MCP server command is empty");
            }
            List<String> args = getStringList(serverConfig, "args");
            List<String> commandLine = new ArrayList<>();
            commandLine.add(command);
            commandLine.addAll(args);

            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.redirectErrorStream(false);

            String workDir = getString(serverConfig, "workDir", "workingDir");
            if (StringUtils.isNotBlank(workDir)) {
                builder.directory(new File(workDir));
            }

            Map<String, Object> env = getMap(serverConfig, "env");
            if (!env.isEmpty()) {
                env.forEach((k, v) -> builder.environment().put(k, String.valueOf(v)));
            }

            process = builder.start();
            output = new BufferedOutputStream(process.getOutputStream());
            input = new BufferedInputStream(process.getInputStream());
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            stopping = false;
            startErrorReader();
            log.info("MCP client started: {} -> {}", serverId, commandLine);
        }

        private void startErrorReader() {
            stderrThread = new Thread(() -> {
                try {
                    String line;
                    while (!stopping && (line = errorReader.readLine()) != null) {
                        if (StringUtils.isNotBlank(line)) {
                            log.warn("MCP stderr (serverId={}): {}", serverId, line);
                        }
                    }
                } catch (IOException ex) {
                    if (!stopping) {
                        log.warn("MCP stderr reader stopped (serverId={}): {}", serverId, ex.getMessage());
                    }
                }
            }, "mcp-stderr-" + serverId);
            stderrThread.setDaemon(true);
            stderrThread.start();
        }

        private String sendRequest(String toolName, String input, Map<String, Object> toolContext) throws IOException {
            Map<String, Object> payload = buildRequestPayload(toolName, input, toolContext);
            byte[] body = jsonCodec.getObjectMapper().writeValueAsBytes(payload);
            writeMessage(body);
            String response = readMessageWithTimeout();
            if (response == null) {
                return buildError("MCP server returned empty response");
            }
            return parseResponse(response);
        }

        private void writeMessage(byte[] body) throws IOException {
            String header = "Content-Length: " + body.length + "\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        }

        private String readMessageWithTimeout() throws IOException {
            if (readTimeoutMs <= 0) {
                return readMessage();
            }
            FutureTask<String> task = new FutureTask<>(this::readMessage);
            Thread readThread = new Thread(task, "mcp-read-" + serverId);
            readThread.setDaemon(true);
            readThread.start();
            try {
                return task.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                task.cancel(true);
                shutdown();
                throw new IOException("MCP response timed out after " + readTimeoutMs + " ms", ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("Failed to read MCP response", cause);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading MCP response", ex);
            }
        }

        private String readMessage() throws IOException {
            int contentLength = readContentLength();
            if (contentLength <= 0) {
                return null;
            }
            byte[] body = readBytes(contentLength);
            return new String(body, StandardCharsets.UTF_8);
        }

        private int readContentLength() throws IOException {
            String line;
            int contentLength = -1;
            while ((line = readLine(input)) != null) {
                if (line.isEmpty()) {
                    break;
                }
                int idx = line.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String name = line.substring(0, idx).trim();
                if (!"Content-Length".equalsIgnoreCase(name)) {
                    continue;
                }
                String value = line.substring(idx + 1).trim();
                try {
                    contentLength = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
            }
            return contentLength;
        }

        private String readLine(InputStream source) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int next;
            while ((next = source.read()) != -1) {
                if (next == '\n') {
                    break;
                }
                buffer.write(next);
            }
            if (next == -1 && buffer.size() == 0) {
                return null;
            }
            String line = buffer.toString(StandardCharsets.US_ASCII);
            if (line.endsWith("\r")) {
                return line.substring(0, line.length() - 1);
            }
            return line;
        }

        private byte[] readBytes(int length) throws IOException {
            byte[] buffer = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = input.read(buffer, offset, length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            if (offset < length) {
                throw new IOException("Unexpected EOF while reading MCP response");
            }
            return buffer;
        }

        private void closeQuietly(InputStream stream) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void closeQuietly(OutputStream stream) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void closeQuietly(BufferedReader stream) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Streamable HTTP MCP 客户端。
     */
    private class StreamableHttpMcpClient extends BaseMcpClient {

        private final String endpointUrl;
        private final Map<String, String> headers;
        private final HttpClient httpClient;
        private volatile String sessionId;
        private volatile Exception initException;

        StreamableHttpMcpClient(String serverId, Map<String, Object> serverConfig) {
            super(serverId, serverConfig);
            this.endpointUrl = resolveEndpointUrl(serverConfig);
            if (StringUtils.isBlank(endpointUrl)) {
                throw new IllegalStateException("MCP endpoint URL is empty");
            }
            this.headers = resolveHeaders(serverConfig);
            this.httpClient = buildHttpClient(serverConfig);
        }

        @Override
        public String call(String toolName, String input, Map<String, Object> toolContext) {
            synchronized (lock) {
                if (initException != null) {
                    return buildError("MCP client is not available");
                }
                try {
                    return doCall(toolName, input, toolContext);
                } catch (TransportUnsupportedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    log.warn("MCP HTTP call failed (serverId={}): {}", serverId, ex.getMessage());
                    return buildError("MCP call failed: " + ex.getMessage());
                }
            }
        }

        @Override
        public boolean isRunning() {
            return initException == null;
        }

        @Override
        public void shutdown() {
            initException = new IllegalStateException("closed");
        }

        private String doCall(String toolName, String input, Map<String, Object> toolContext) throws IOException, InterruptedException {
            Map<String, Object> payload = buildRequestPayload(toolName, input, toolContext);
            long id = ((Number) payload.get("id")).longValue();
            byte[] body = jsonCodec.getObjectMapper().writeValueAsBytes(payload);
            HttpRequest request = buildPostRequest(endpointUrl, body, "application/json, text/event-stream");
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            captureSessionId(response);
            int status = response.statusCode();
            if (status == 404 || status == 405) {
                throw new TransportUnsupportedException("Streamable HTTP not supported: " + status);
            }
            if (status >= 400) {
                throw new IOException("HTTP error status: " + status);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.startsWith("text/event-stream")) {
                return readSseResponse(response.body(), id);
            }
            String payloadText = readAll(response.body());
            return extractResponseOrFallback(payloadText, id);
        }

        private HttpRequest buildPostRequest(String url, byte[] body, String accept) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .header("Content-Type", "application/json")
                    .header("Accept", accept);
            if (sessionId != null) {
                builder.header("Mcp-Session-Id", sessionId);
            }
            headers.forEach(builder::header);
            if (readTimeoutMs > 0) {
                builder.timeout(Duration.ofMillis(readTimeoutMs));
            }
            return builder.build();
        }

        private void captureSessionId(HttpResponse<?> response) {
            response.headers().firstValue("Mcp-Session-Id").ifPresent(value -> sessionId = value);
        }

        private String readSseResponse(InputStream stream, long expectedId) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                SseEvent event;
                long deadline = readTimeoutMs > 0 ? System.currentTimeMillis() + readTimeoutMs : Long.MAX_VALUE;
                while ((event = readSseEvent(reader)) != null) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new IOException("MCP response timed out after " + readTimeoutMs + " ms");
                    }
                    if (StringUtils.isBlank(event.data)) {
                        continue;
                    }
                    String matched = tryExtractResponse(event.data, expectedId);
                    if (matched != null) {
                        return matched;
                    }
                }
            }
            return buildError("MCP server returned empty response");
        }
    }

    /**
     * 旧版 SSE MCP 客户端。
     */
    private class SseMcpClient extends BaseMcpClient {

        private final String sseUrl;
        private final Map<String, String> headers;
        private final HttpClient httpClient;
        private final ConcurrentMap<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        private volatile String postUrl;
        private volatile String lastEventId;
        private volatile boolean stopping;
        private volatile InputStream sseStream;
        private Thread sseThread;
        private Exception initException;
        private CompletableFuture<String> endpointFuture = new CompletableFuture<>();

        SseMcpClient(String serverId, Map<String, Object> serverConfig) {
            super(serverId, serverConfig);
            this.sseUrl = resolveSseUrl(serverConfig);
            if (StringUtils.isBlank(sseUrl)) {
                throw new IllegalStateException("SSE url is empty");
            }
            this.headers = resolveHeaders(serverConfig);
            this.httpClient = buildHttpClient(serverConfig);
            startSseStream();
        }

        @Override
        public String call(String toolName, String input, Map<String, Object> toolContext) {
            synchronized (lock) {
                if (!ensureRunning()) {
                    return buildError("MCP client is not running");
                }
                try {
                    return sendRequest(toolName, input, toolContext);
                } catch (Exception ex) {
                    log.warn("MCP SSE call failed (serverId={}): {}", serverId, ex.getMessage());
                    return buildError("MCP call failed: " + ex.getMessage());
                }
            }
        }

        @Override
        public boolean isRunning() {
            return sseThread != null && sseThread.isAlive() && initException == null;
        }

        @Override
        public void shutdown() {
            stopping = true;
            if (sseThread != null) {
                sseThread.interrupt();
            }
            closeQuietly(sseStream);
            sseStream = null;
            pending.clear();
        }

        private boolean ensureRunning() {
            if (initException != null) {
                return false;
            }
            if (isRunning()) {
                return true;
            }
            startSseStream();
            return isRunning();
        }

        private void startSseStream() {
            stopping = false;
            endpointFuture = new CompletableFuture<>();
            sseThread = new Thread(() -> {
                try {
                    HttpRequest request = buildGetRequest(sseUrl);
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    if (status >= 400) {
                        initException = new IOException("SSE connection failed: " + status);
                        return;
                    }
                    sseStream = response.body();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(sseStream, StandardCharsets.UTF_8))) {
                        SseEvent event;
                        while (!stopping && (event = readSseEvent(reader)) != null) {
                            handleSseEvent(event);
                        }
                    }
                } catch (Exception ex) {
                    if (!stopping) {
                        initException = ex;
                        log.warn("MCP SSE stream stopped (serverId={}): {}", serverId, ex.getMessage());
                    }
                }
            }, "mcp-sse-" + serverId);
            sseThread.setDaemon(true);
            sseThread.start();
        }

        private void handleSseEvent(SseEvent event) {
            if (event == null) {
                return;
            }
            if (StringUtils.isNotBlank(event.id)) {
                lastEventId = event.id;
            }
            String eventName = StringUtils.defaultIfBlank(event.event, "message");
            if ("endpoint".equalsIgnoreCase(eventName)) {
                if (StringUtils.isNotBlank(event.data)) {
                    postUrl = event.data.trim();
                    endpointFuture.complete(postUrl);
                    log.info("MCP SSE endpoint resolved: {} -> {}", serverId, postUrl);
                }
                return;
            }
            if (!"message".equalsIgnoreCase(eventName)) {
                return;
            }
            if (StringUtils.isBlank(event.data)) {
                return;
            }
            dispatchMessage(event.data);
        }

        private void dispatchMessage(String payload) {
            try {
                JsonNode root = jsonCodec.getObjectMapper().readTree(payload);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        handleJsonNode(node);
                    }
                } else {
                    handleJsonNode(root);
                }
            } catch (Exception ex) {
                log.warn("Failed to parse SSE message (serverId={}): {}", serverId, ex.getMessage());
            }
        }

        private void handleJsonNode(JsonNode node) {
            if (node == null) {
                return;
            }
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull()) {
                return;
            }
            long id = idNode.isNumber() ? idNode.asLong() : -1L;
            if (id < 0) {
                return;
            }
            CompletableFuture<String> future = pending.remove(id);
            if (future != null) {
                future.complete(parseResponse(node.toString()));
            }
        }

        private String sendRequest(String toolName, String input, Map<String, Object> toolContext)
                throws IOException, InterruptedException, TimeoutException, ExecutionException {
            Map<String, Object> payload = buildRequestPayload(toolName, input, toolContext);
            long id = ((Number) payload.get("id")).longValue();
            String endpoint = awaitEndpoint();
            byte[] body = jsonCodec.getObjectMapper().writeValueAsBytes(payload);
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(id, future);
            try {
                HttpRequest request = buildPostRequest(endpoint, body);
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP error status: " + response.statusCode());
                }
                if (readTimeoutMs > 0) {
                    return future.get(readTimeoutMs, TimeUnit.MILLISECONDS);
                }
                return future.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("Failed to read MCP response", cause);
            } finally {
                pending.remove(id);
            }
        }

        private String awaitEndpoint() throws TimeoutException, InterruptedException, ExecutionException {
            if (StringUtils.isNotBlank(postUrl)) {
                return postUrl;
            }
            if (readTimeoutMs > 0) {
                return endpointFuture.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            }
            return endpointFuture.get();
        }

        private HttpRequest buildGetRequest(String url) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("Accept", "text/event-stream");
            if (StringUtils.isNotBlank(lastEventId)) {
                builder.header("Last-Event-Id", lastEventId);
            }
            headers.forEach(builder::header);
            return builder.build();
        }

        private HttpRequest buildPostRequest(String url, byte[] body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            headers.forEach(builder::header);
            if (readTimeoutMs > 0) {
                builder.timeout(Duration.ofMillis(readTimeoutMs));
            }
            return builder.build();
        }
    }

    /**
     * Streamable HTTP + SSE 兼容。
     */
    private class AutoHttpMcpClient extends BaseMcpClient {

        private McpClient delegate;
        private volatile boolean fallbackAttempted;

        AutoHttpMcpClient(String serverId, Map<String, Object> serverConfig) {
            super(serverId, serverConfig);
            this.delegate = new StreamableHttpMcpClient(serverId, serverConfig);
        }

        @Override
        public String call(String toolName, String input, Map<String, Object> toolContext) {
            try {
                return delegate.call(toolName, input, toolContext);
            } catch (TransportUnsupportedException ex) {
                if (fallbackAttempted) {
                    return buildError(ex.getMessage());
                }
                fallbackAttempted = true;
                log.info("Streamable HTTP unsupported, fallback to SSE (serverId={})", serverId);
                delegate = new SseMcpClient(serverId, serverConfig);
                return delegate.call(toolName, input, toolContext);
            }
        }

        @Override
        public boolean isRunning() {
            return delegate != null && delegate.isRunning();
        }

        @Override
        public void shutdown() {
            if (delegate != null) {
                delegate.shutdown();
            }
        }
    }

    private class FailedMcpClient implements McpClient {

        private final Exception exception;

        FailedMcpClient(Exception exception) {
            this.exception = exception;
        }

        @Override
        public String call(String toolName, String input, Map<String, Object> toolContext) {
            return buildError("MCP client is not available: " + exception.getMessage());
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }

    private HttpClient buildHttpClient(Map<String, Object> serverConfig) {
        long connectTimeoutMs = resolveConnectTimeoutMs(serverConfig);
        HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if (connectTimeoutMs > 0) {
            builder.connectTimeout(Duration.ofMillis(connectTimeoutMs));
        }
        return builder.build();
    }

    private String readAll(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private SseEvent readSseEvent(BufferedReader reader) throws IOException {
        String line;
        String event = null;
        String id = null;
        StringBuilder data = new StringBuilder();
        boolean started = false;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (!started) {
                    continue;
                }
                return new SseEvent(event, data.length() == 0 ? null : data.toString(), id);
            }
            started = true;
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("event:")) {
                event = line.substring(6).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append("\n");
                }
                data.append(line.substring(5).trim());
                continue;
            }
            if (line.startsWith("id:")) {
                id = line.substring(3).trim();
            }
        }
        if (!started) {
            return null;
        }
        return new SseEvent(event, data.length() == 0 ? null : data.toString(), id);
    }

    private void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class SseEvent {
        private final String event;
        private final String data;
        private final String id;

        SseEvent(String event, String data, String id) {
            this.event = event;
            this.data = data;
            this.id = id;
        }
    }

    private static class TransportUnsupportedException extends RuntimeException {
        TransportUnsupportedException(String message) {
            super(message);
        }
    }
}
