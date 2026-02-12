package com.getoffer.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.Application;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.task.scheduling.enabled=false",
                "executor.observability.audit-log-enabled=false",
                "executor.observability.audit-success-log-enabled=false",
                "app.share.token-salt=test-salt",
                "app.share.max-ttl-hours=168"
        }
)
@EnabledIfSystemProperty(named = "it.docker.enabled", matches = "true")
public class ShareAccessControllerIntegrationTest extends PostgresIntegrationTestSupport {

    private static final String TOKEN_SALT = "test-salt";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void shouldReadSharedTaskWhenCodeAndTokenValid() throws Exception {
        ShareSeedData seed = seedTaskAndShare("access-ok", false, false);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=" + seed.shareCode + "&token=" + seed.rawToken,
                String.class
        );
        JsonNode root = readJson(response);
        assertSuccess(root);

        JsonNode data = root.path("data");
        Assertions.assertEquals(seed.taskId, data.path("taskId").asLong());
        Assertions.assertEquals(seed.shareCode, data.path("shareCode").asText());
        Assertions.assertEquals("RESULT_AND_REFERENCES", data.path("scope").asText());
        Assertions.assertEquals("COMPLETED", data.path("status").asText());
        Assertions.assertTrue(data.path("outputResult").asText().contains("task-output"), "应返回任务输出内容");
        Assertions.assertTrue(data.path("references").isArray(), "应返回引用数组");
        Assertions.assertFalse(data.path("sharedAt").asText().isBlank(), "应返回 sharedAt");
    }

    @Test
    public void shouldRejectWhenTokenInvalid() throws Exception {
        ShareSeedData seed = seedTaskAndShare("token-invalid", false, false);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=" + seed.shareCode + "&token=wrong-token",
                String.class
        );
        JsonNode root = readJson(response);
        assertIllegal(root, "链接不存在或无效");
    }

    @Test
    public void shouldRejectWhenShareCodeInvalid() throws Exception {
        ShareSeedData seed = seedTaskAndShare("code-invalid", false, false);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=wrong-code&token=" + seed.rawToken,
                String.class
        );
        JsonNode root = readJson(response);
        assertIllegal(root, "链接不存在或无效");
    }

    @Test
    public void shouldRejectWhenShareLinkRevoked() throws Exception {
        ShareSeedData seed = seedTaskAndShare("revoked", true, false);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=" + seed.shareCode + "&token=" + seed.rawToken,
                String.class
        );
        JsonNode root = readJson(response);
        assertIllegal(root, "链接已撤销");
    }

    @Test
    public void shouldRejectWhenShareLinkExpired() throws Exception {
        ShareSeedData seed = seedTaskAndShare("expired", false, true);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=" + seed.shareCode + "&token=" + seed.rawToken,
                String.class
        );
        JsonNode root = readJson(response);
        assertIllegal(root, "链接已过期");
    }

    @Test
    public void shouldRejectWhenTaskNotExist() throws Exception {
        ShareSeedData seed = seedTaskAndShare("task-missing", false, false);
        jdbcTemplate.update("DELETE FROM agent_tasks WHERE id = ?", seed.taskId);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/share/tasks/" + seed.taskId + "?code=" + seed.shareCode + "&token=" + seed.rawToken,
                String.class
        );
        JsonNode root = readJson(response);
        assertIllegal(root, "链接不存在或无效");
    }

    private ShareSeedData seedTaskAndShare(String suffix, boolean revoked, boolean expired) {
        Long sessionId = jdbcTemplate.queryForObject(
                "INSERT INTO agent_sessions (user_id, title, is_active, meta_info) VALUES (?, ?, TRUE, '{}'::jsonb) RETURNING id",
                Long.class,
                "share-access-user-" + suffix,
                "share-access-session-" + suffix
        );

        Long routingDecisionId = jdbcTemplate.queryForObject(
                "INSERT INTO routing_decisions (session_id, decision_type, strategy, reason, metadata) VALUES (?, 'CANDIDATE', ?, ?, '{}'::jsonb) RETURNING id",
                Long.class,
                sessionId,
                "IT_TEST",
                "share-access"
        );

        Long planId = jdbcTemplate.queryForObject(
                "INSERT INTO agent_plans (session_id, route_decision_id, plan_goal, execution_graph, definition_snapshot, global_context, status, priority, version) "
                        + "VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), 'COMPLETED', 0, 0) RETURNING id",
                Long.class,
                sessionId,
                routingDecisionId,
                "share-access-plan-" + suffix,
                "{\"nodes\":[]}",
                "{\"routeType\":\"IT\"}",
                "{}"
        );

        Long taskId = jdbcTemplate.queryForObject(
                "INSERT INTO agent_tasks (plan_id, node_id, name, task_type, status, dependency_node_ids, input_context, config_snapshot, output_result, max_retries, current_retry, execution_attempt, version) "
                        + "VALUES (?, ?, ?, 'WORKER', 'COMPLETED', CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, 3, 0, 0, 0) RETURNING id",
                Long.class,
                planId,
                "node-share-access-" + suffix,
                "node-share-access-" + suffix,
                "[]",
                "{\"references\":[{\"title\":\"input-ref\",\"type\":\"doc\"}]}",
                "{\"references\":[{\"title\":\"config-ref\",\"type\":\"kb\"}]}",
                "task-output-" + suffix
        );

        String rawToken = "token-" + suffix;
        String tokenHash = hashToken(rawToken);
        String shareCode = "code-" + suffix;
        LocalDateTime expiresAt = expired ? LocalDateTime.now().minusHours(1) : LocalDateTime.now().plusHours(24);

        Long shareId = jdbcTemplate.queryForObject(
                "INSERT INTO task_share_links (task_id, share_code, token_hash, scope, expires_at, revoked, revoked_at, revoked_reason, created_by, version) "
                        + "VALUES (?, ?, ?, 'RESULT_AND_REFERENCES', ?, ?, ?, ?, 'it', 0) RETURNING id",
                Long.class,
                taskId,
                shareCode,
                tokenHash,
                expiresAt,
                revoked,
                revoked ? LocalDateTime.now() : null,
                revoked ? "IT_REVOKED" : null
        );

        ShareSeedData data = new ShareSeedData();
        data.taskId = taskId;
        data.shareId = shareId;
        data.shareCode = shareCode;
        data.rawToken = rawToken;
        return data;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = token + ":" + TOKEN_SALT;
            byte[] hashed = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("token hash failed", ex);
        }
    }

    private JsonNode readJson(ResponseEntity<String> response) throws Exception {
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), "HTTP 状态应为 2xx");
        String body = response.getBody();
        Assertions.assertNotNull(body, "响应体不能为空");
        return objectMapper.readTree(body);
    }

    private void assertSuccess(JsonNode root) {
        Assertions.assertEquals("0000", root.path("code").asText(), "响应码应为成功");
    }

    private void assertIllegal(JsonNode root, String expectedMessage) {
        Assertions.assertEquals("0002", root.path("code").asText(), "响应码应为非法参数");
        Assertions.assertEquals(expectedMessage, root.path("info").asText());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static class ShareSeedData {
        private Long taskId;
        private Long shareId;
        private String shareCode;
        private String rawToken;
    }
}
