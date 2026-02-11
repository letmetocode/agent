package com.getoffer.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.Application;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.trigger.job.PlanStatusDaemon;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.task.scheduling.enabled=false",
                "executor.observability.audit-log-enabled=false",
                "executor.observability.audit-success-log-enabled=false"
        }
)
@EnabledIfSystemProperty(named = "it.docker.enabled", matches = "true")
public class SessionChatPlanSseIntegrationTest extends PostgresIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IWorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired
    private IAgentTaskRepository agentTaskRepository;

    @Autowired
    private PlanStatusDaemon planStatusDaemon;

    @Autowired
    private ISessionTurnRepository sessionTurnRepository;

    @Autowired
    private ISessionMessageRepository sessionMessageRepository;

    @Test
    public void shouldFinishPlanAndPushSseEventAfterChatFlow() throws Exception {
        seedWorkflowDefinition();

        Long sessionId = createSession();
        ChatResult chat = triggerChat(sessionId);

        CompletableFuture<Boolean> sseFuture = CompletableFuture.supplyAsync(
                () -> waitPlanFinishedEvent(chat.planId)
        );

        Thread.sleep(300L);
        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(chat.planId);
        Assertions.assertFalse(tasks.isEmpty(), "chat 后应展开至少一个任务");

        for (AgentTaskEntity task : tasks) {
            task.setStatus(TaskStatusEnum.COMPLETED);
            task.setOutputResult("task-done-" + task.getId());
            agentTaskRepository.update(task);
        }

        planStatusDaemon.syncPlanStatuses();

        Boolean gotPlanFinished = sseFuture.get(8, TimeUnit.SECONDS);
        Assertions.assertTrue(Boolean.TRUE.equals(gotPlanFinished), "SSE 应收到 PlanFinished 事件");

        SessionTurnEntity turn = sessionTurnRepository.findById(chat.turnId);
        Assertions.assertNotNull(turn);
        Assertions.assertEquals(TurnStatusEnum.COMPLETED, turn.getStatus());
        Assertions.assertNotNull(turn.getFinalResponseMessageId());

        List<SessionMessageEntity> messages = sessionMessageRepository.findByTurnId(chat.turnId);
        boolean hasAssistant = messages.stream().anyMatch(msg -> msg.getRole() == MessageRoleEnum.ASSISTANT);
        Assertions.assertTrue(hasAssistant, "回合结束后应写入 assistant 消息");
    }

    private void seedWorkflowDefinition() {
        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setDefinitionKey("it-sse-flow");
        definition.setTenantId("DEFAULT");
        definition.setCategory("IT");
        definition.setName("sse_flow");
        definition.setVersion(1);
        definition.setRouteDescription("integration sse test");
        definition.setStatus(WorkflowDefinitionStatusEnum.ACTIVE);
        definition.setIsActive(true);
        definition.setCreatedBy("it");

        Map<String, Object> node = new HashMap<>();
        node.put("id", "node-1");
        node.put("type", "WORKER");
        node.put("name", "worker-1");

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", List.of(node));
        graph.put("edges", new ArrayList<>());
        definition.setGraphDefinition(graph);
        definition.setInputSchema(new HashMap<>());
        definition.setDefaultConfig(new HashMap<>());
        definition.setToolPolicy(new HashMap<>());
        definition.setConstraints(new HashMap<>());
        definition.setInputSchemaVersion("v1");
        workflowDefinitionRepository.save(definition);
    }

    private Long createSession() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", "it-user");
        request.put("title", "it-session");

        ResponseEntity<String> response = postJson("/api/sessions", request);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("data").path("sessionId").asLong();
    }

    private ChatResult triggerChat(Long sessionId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("message", "please run integration sse test flow");

        ResponseEntity<String> response = postJson("/api/sessions/" + sessionId + "/chat", request);
        JsonNode root = objectMapper.readTree(response.getBody());

        ChatResult result = new ChatResult();
        result.planId = root.path("data").path("planId").asLong();
        result.turnId = root.path("data").path("turnId").asLong();
        Assertions.assertTrue(result.planId > 0, "chat 应返回 planId");
        Assertions.assertTrue(result.turnId > 0, "chat 应返回 turnId");
        return result;
    }

    private ResponseEntity<String> postJson(String path, Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "http://127.0.0.1:" + port + path,
                new HttpEntity<>(payload, headers),
                String.class
        );
    }

    private boolean waitPlanFinishedEvent(Long planId) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/api/plans/" + planId + "/stream");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(6000);
            connection.connect();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:") && line.contains("PlanFinished")) {
                        return true;
                    }
                }
            }
        } catch (SocketTimeoutException timeout) {
            return false;
        } catch (Exception ex) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private static final class ChatResult {
        private Long planId;
        private Long turnId;
    }
}
