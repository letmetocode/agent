package com.getoffer.test.domain;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskPromptDomainServiceTest {

    private final TaskPromptDomainService service = new TaskPromptDomainService();

    @Test
    public void shouldBuildWorkerPromptWithTemplateAndFilteredContext() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setName("写文案");

        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "任务={{taskName}}|目标={{planGoal}}|A={{a}}|CTX={{context}}");
        config.put("contextKeys", List.of("a"));
        task.setConfigSnapshot(config);

        Map<String, Object> input = new HashMap<>();
        input.put("a", "from_input");
        input.put("c", "ignored");
        task.setInputContext(input);

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setPlanGoal("完成目标");
        Map<String, Object> global = new HashMap<>();
        global.put("a", "from_plan");
        global.put("b", "ignored");
        plan.setGlobalContext(global);

        String prompt = service.buildWorkerPrompt(task, plan, String::valueOf);

        Assertions.assertTrue(prompt.contains("任务=写文案"));
        Assertions.assertTrue(prompt.contains("目标=完成目标"));
        Assertions.assertTrue(prompt.contains("A=from_input"));
        Assertions.assertTrue(prompt.contains("a=from_input"));
        Assertions.assertFalse(prompt.contains("ignored"));
    }

    @Test
    public void shouldResolveTargetNodeIdFromConfigOrDependency() {
        AgentTaskEntity task = new AgentTaskEntity();
        Map<String, Object> config = new HashMap<>();
        config.put("targetNodeId", "worker-1");
        task.setConfigSnapshot(config);

        Assertions.assertEquals("worker-1", service.resolveTargetNodeId(task));

        AgentTaskEntity fallbackTask = new AgentTaskEntity();
        fallbackTask.setConfigSnapshot(new HashMap<>());
        fallbackTask.setDependencyNodeIds(List.of("worker-2"));

        Assertions.assertEquals("worker-2", service.resolveTargetNodeId(fallbackTask));
    }

    @Test
    public void shouldBuildRetrySystemPromptWhenRetried() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setCurrentRetry(2);
        Map<String, Object> input = new HashMap<>();
        input.put("feedback", "缺少引用");
        task.setInputContext(input);

        String prompt = service.buildRetrySystemPrompt(task);

        Assertions.assertNotNull(prompt);
        Assertions.assertTrue(prompt.contains("第 2 次尝试"));
        Assertions.assertTrue(prompt.contains("缺少引用"));
    }
}
