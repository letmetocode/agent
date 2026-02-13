package com.getoffer.test.domain;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionConversationDomainServiceTest {

    private final SessionConversationDomainService service = new SessionConversationDomainService();

    @Test
    public void shouldPrepareNewSessionWithAssistantPriority() {
        AgentRegistryEntity helper = new AgentRegistryEntity();
        helper.setId(2L);
        helper.setKey("helper");
        helper.setIsActive(true);

        AgentRegistryEntity assistant = new AgentRegistryEntity();
        assistant.setId(3L);
        assistant.setKey("assistant");
        assistant.setIsActive(true);

        SessionConversationDomainService.SessionPreparationResult result = service.prepareSession(
                new SessionConversationDomainService.SessionPreparationCommand(
                        " dev-user ",
                        "  生成推荐文案  ",
                        null,
                        null,
                        null,
                        Map.of("tenant", "dev"),
                        null,
                        null,
                        List.of(helper, assistant)
                )
        );

        Assertions.assertTrue(result.newSession());
        Assertions.assertEquals("生成推荐文案", result.normalizedUserMessage());
        Assertions.assertEquals("assistant", result.session().getAgentKey());
        Assertions.assertEquals("CHAT_DEFAULT", result.session().getScenario());
        Assertions.assertEquals("生成推荐文案", result.session().getTitle());
        Assertions.assertEquals("chat-v3", result.session().getMetaInfo().get("entry"));
    }

    @Test
    public void shouldRejectSessionOwnerMismatch() {
        AgentSessionEntity existing = new AgentSessionEntity();
        existing.setId(11L);
        existing.setUserId("owner-a");

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () ->
                service.prepareSession(new SessionConversationDomainService.SessionPreparationCommand(
                        "owner-b",
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        existing,
                        null,
                        null
                ))
        );
        Assertions.assertTrue(exception.getMessage().contains("不属于当前 userId"));
    }

    @Test
    public void shouldBuildPlanContextWithLatestSummary() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(12L);
        session.setAgentKey("assistant");
        session.setScenario("CHAT_DEFAULT");

        SessionTurnEntity latestCompleted = new SessionTurnEntity();
        latestCompleted.setId(1201L);
        latestCompleted.setAssistantSummary("上轮摘要");

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("custom", "v");

        Map<String, Object> context = service.buildPlanExtraContext(
                new SessionConversationDomainService.PlanContextCommand(session, 99L, overrides, latestCompleted)
        );

        Assertions.assertEquals(99L, context.get("turnId"));
        Assertions.assertEquals("assistant", context.get("agentKey"));
        Assertions.assertEquals("上轮摘要", context.get("lastAssistantSummary"));
        Assertions.assertEquals("v", context.get("custom"));
    }

    @Test
    public void shouldResolveRootAppExceptionMessage() {
        AppException root = new AppException("1001", "planner failed");
        RuntimeException wrapped = new RuntimeException("wrapper", root);

        String resolved = service.resolveErrorMessage(wrapped);
        Assertions.assertEquals("planner failed", resolved);
    }
}
