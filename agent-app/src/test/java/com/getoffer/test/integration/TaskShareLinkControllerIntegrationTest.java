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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

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
public class TaskShareLinkControllerIntegrationTest extends PostgresIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void shouldCreateAndListShareLinks() throws Exception {
        Long taskId = seedTask("create-list");
        LocalDateTime beforeCreate = LocalDateTime.now();

        JsonNode created = createShareLink(taskId, null);
        long shareId = created.path("shareId").asLong();
        String token = created.path("token").asText();
        String shareUrl = created.path("shareUrl").asText();
        LocalDateTime expiresAt = parseDateTime(created.path("expiresAt").asText());

        Assertions.assertTrue(shareId > 0, "创建分享链接应返回 shareId");
        Assertions.assertFalse(token.isBlank(), "创建分享链接应返回 token");
        Assertions.assertTrue(shareUrl.contains("/share/tasks/" + taskId), "分享链接应包含任务路径");
        Assertions.assertTrue(shareUrl.contains("token=" + token), "分享链接应包含 token 参数");
        Assertions.assertTrue(expiresAt.isAfter(beforeCreate.plusHours(23)), "默认 TTL 应接近 24 小时");
        Assertions.assertTrue(expiresAt.isBefore(beforeCreate.plusHours(25)), "默认 TTL 不应偏离 24 小时过多");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_share_links WHERE id = ? AND task_id = ? AND revoked = FALSE",
                Integer.class,
                shareId,
                taskId
        );
        Assertions.assertEquals(1, count, "数据库应落地可用分享链接");

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/tasks/" + taskId + "/share-links",
                String.class
        );
        JsonNode root = readJson(response);
        assertSuccess(root);
        JsonNode list = root.path("data");
        Assertions.assertEquals(1, list.size(), "列表应返回 1 条分享链接");
        Assertions.assertEquals(shareId, list.get(0).path("shareId").asLong());
        Assertions.assertTrue(list.get(0).path("shareUrl").asText().contains("?code="), "列表分享预览链接应包含 code");
        Assertions.assertFalse(list.get(0).has("token"), "列表不应透出 token");
    }

    @Test
    public void shouldClampExpiresHoursWithinRange() throws Exception {
        Long taskId = seedTask("ttl-range");

        LocalDateTime beforeLow = LocalDateTime.now();
        JsonNode lowTtl = createShareLink(taskId, -5);
        LocalDateTime lowExpiresAt = parseDateTime(lowTtl.path("expiresAt").asText());
        Assertions.assertTrue(lowExpiresAt.isAfter(beforeLow.plusMinutes(45)), "TTL 小于 1 时应钳制到 1 小时");
        Assertions.assertTrue(lowExpiresAt.isBefore(beforeLow.plusHours(2)), "TTL 下界钳制后不应超过 2 小时");

        LocalDateTime beforeHigh = LocalDateTime.now();
        JsonNode highTtl = createShareLink(taskId, 9999);
        LocalDateTime highExpiresAt = parseDateTime(highTtl.path("expiresAt").asText());
        Assertions.assertTrue(highExpiresAt.isAfter(beforeHigh.plusHours(167)), "TTL 超限时应钳制到 max-ttl-hours");
        Assertions.assertTrue(highExpiresAt.isBefore(beforeHigh.plusHours(169)), "TTL 上界钳制后应接近 168 小时");
    }

    @Test
    public void shouldRevokeSingleShareLink() throws Exception {
        Long taskId = seedTask("revoke-single");
        JsonNode created = createShareLink(taskId, 8);
        long shareId = created.path("shareId").asLong();

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/tasks/" + taskId + "/share-links/" + shareId + "/revoke?reason=SECURITY_TEST",
                null,
                String.class
        );
        JsonNode root = readJson(response);
        assertSuccess(root);

        JsonNode data = root.path("data");
        Assertions.assertTrue(data.path("revoked").asBoolean(), "单条撤销后 revoked 应为 true");
        Assertions.assertEquals("SECURITY_TEST", data.path("revokedReason").asText());
        Assertions.assertEquals("REVOKED", data.path("status").asText());

        Integer revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_share_links WHERE id = ? AND revoked = TRUE",
                Integer.class,
                shareId
        );
        Assertions.assertEquals(1, revokedCount, "数据库应标记该链接已撤销");
    }

    @Test
    public void shouldRevokeAllShareLinksByTask() throws Exception {
        Long taskId = seedTask("revoke-all");
        createShareLink(taskId, 6);
        createShareLink(taskId, 6);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/tasks/" + taskId + "/share-links/revoke-all?reason=BATCH_REVOKE_TEST",
                null,
                String.class
        );
        JsonNode root = readJson(response);
        assertSuccess(root);

        Assertions.assertEquals(2, root.path("data").path("revokedCount").asInt(), "批量撤销应返回撤销数量");

        ResponseEntity<String> listResponse = restTemplate.getForEntity(
                baseUrl() + "/api/tasks/" + taskId + "/share-links",
                String.class
        );
        JsonNode listRoot = readJson(listResponse);
        assertSuccess(listRoot);
        JsonNode rows = listRoot.path("data");
        Assertions.assertEquals(2, rows.size(), "列表应保留已撤销历史记录");
        for (JsonNode row : rows) {
            Assertions.assertTrue(row.path("revoked").asBoolean(), "批量撤销后所有链接应为 revoked");
            Assertions.assertEquals("REVOKED", row.path("status").asText());
        }

        Integer remainActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_share_links WHERE task_id = ? AND revoked = FALSE",
                Integer.class,
                taskId
        );
        Assertions.assertEquals(0, remainActive, "数据库不应存在未撤销的分享链接");
    }

    private JsonNode createShareLink(Long taskId, Integer expiresHours) throws Exception {
        String path = "/api/tasks/" + taskId + "/share-links";
        if (expiresHours != null) {
            path = path + "?expiresHours=" + expiresHours;
        }
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl() + path, null, String.class);
        JsonNode root = readJson(response);
        assertSuccess(root);
        return root.path("data");
    }

    private Long seedTask(String suffix) {
        Long sessionId = jdbcTemplate.queryForObject(
                "INSERT INTO agent_sessions (user_id, title, is_active, meta_info) VALUES (?, ?, TRUE, '{}'::jsonb) RETURNING id",
                Long.class,
                "share-it-user-" + suffix,
                "share-it-session-" + suffix
        );

        Long routingDecisionId = jdbcTemplate.queryForObject(
                "INSERT INTO routing_decisions (session_id, decision_type, strategy, reason, metadata) VALUES (?, 'CANDIDATE', ?, ?, '{}'::jsonb) RETURNING id",
                Long.class,
                sessionId,
                "IT_TEST",
                "share-it"
        );

        Long planId = jdbcTemplate.queryForObject(
                "INSERT INTO agent_plans (session_id, route_decision_id, plan_goal, execution_graph, definition_snapshot, global_context, status, priority, version) "
                        + "VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), 'RUNNING', 0, 0) RETURNING id",
                Long.class,
                sessionId,
                routingDecisionId,
                "share-plan-" + suffix,
                "{\"nodes\":[]}",
                "{\"routeType\":\"IT\"}",
                "{}"
        );

        return jdbcTemplate.queryForObject(
                "INSERT INTO agent_tasks (plan_id, node_id, name, task_type, status, dependency_node_ids, input_context, config_snapshot, output_result, max_retries, current_retry, execution_attempt, version) "
                        + "VALUES (?, ?, ?, 'WORKER', 'COMPLETED', CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, 3, 0, 0, 0) RETURNING id",
                Long.class,
                planId,
                "node-" + suffix,
                "node-" + suffix,
                "[]",
                "{}",
                "{}",
                "task-output-" + suffix
        );
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

    private LocalDateTime parseDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignore) {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
