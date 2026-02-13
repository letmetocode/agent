package com.getoffer.test.domain;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TaskEvaluationDomainServiceTest {

    private final TaskEvaluationDomainService service = new TaskEvaluationDomainService();

    @Test
    public void shouldDetectValidationRequirementFromConfig() {
        AgentTaskEntity task = new AgentTaskEntity();
        Map<String, Object> config = new HashMap<>();
        config.put("validate", true);
        task.setConfigSnapshot(config);

        Assertions.assertTrue(service.needsValidation(task));
    }

    @Test
    public void shouldMarkValidationFailedWhenContainsFailKeyword() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setConfigSnapshot(new HashMap<>());

        TaskEvaluationDomainService.ValidationResult result =
                service.evaluateValidation(task, "本次结果失败，需要重做");

        Assertions.assertFalse(result.valid());
    }

    @Test
    public void shouldParseCriticDecisionFromPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pass", true);
        payload.put("feedback", "通过");

        TaskEvaluationDomainService.CriticDecision decision =
                service.parseCriticDecision("{\"pass\":true}", payload);

        Assertions.assertTrue(decision.pass());
        Assertions.assertEquals("通过", decision.feedback());
    }

    @Test
    public void shouldFailWhenCriticPayloadInvalid() {
        TaskEvaluationDomainService.CriticDecision decision =
                service.parseCriticDecision("not json", null);

        Assertions.assertFalse(decision.pass());
        Assertions.assertEquals("Critic输出格式错误", decision.feedback());
    }
}
