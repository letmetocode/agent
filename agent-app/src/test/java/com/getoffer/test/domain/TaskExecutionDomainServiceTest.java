package com.getoffer.test.domain;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TaskExecutionDomainServiceTest {

    private final TaskExecutionDomainService service = new TaskExecutionDomainService();

    @Test
    public void shouldResolveAttemptFromPersistedMaxAttempt() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setCurrentRetry(3);

        Integer attempt = service.resolveAttemptNumber(7, task);

        Assertions.assertEquals(8, attempt);
    }

    @Test
    public void shouldResolveAttemptFromCurrentRetryWhenNoPersistedAttempt() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setCurrentRetry(2);

        Integer attempt = service.resolveAttemptNumber(null, task);

        Assertions.assertEquals(3, attempt);
    }

    @Test
    public void shouldBlockTimeoutRetryWhenTaskOutOfRetryBudget() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setCurrentRetry(2);
        task.setMaxRetries(2);

        boolean retryable = service.canTimeoutRetry(task, 0, 2);

        Assertions.assertFalse(retryable);
    }

    @Test
    public void shouldApplyTimeoutRetryAndAppendFeedback() {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setCurrentRetry(1);

        service.applyTimeoutRetry(task, "timeout");

        Assertions.assertEquals(2, task.getCurrentRetry());
        Assertions.assertNotNull(task.getInputContext());
        Assertions.assertEquals("timeout", task.getInputContext().get("feedback"));
        Assertions.assertEquals("timeout", task.getInputContext().get("validationFeedback"));
    }
}
