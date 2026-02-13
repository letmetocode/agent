package com.getoffer.test.domain;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import com.getoffer.types.enums.TaskStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TaskRecoveryDomainServiceTest {

    private final TaskRecoveryDomainService service = new TaskRecoveryDomainService();

    @Test
    public void shouldSkipWhenTargetAlreadyFailed() {
        AgentTaskEntity target = new AgentTaskEntity();
        target.setStatus(TaskStatusEnum.FAILED);

        TaskRecoveryDomainService.RecoveryDecision decision = service.applyCriticFeedback(target, "bad");

        Assertions.assertEquals(TaskRecoveryDomainService.RecoveryDecision.ALREADY_FAILED, decision);
    }

    @Test
    public void shouldRollbackToRefiningWhenRetryNotExceeded() {
        AgentTaskEntity target = new AgentTaskEntity();
        target.setStatus(TaskStatusEnum.COMPLETED);
        target.setCurrentRetry(0);
        target.setMaxRetries(2);

        TaskRecoveryDomainService.RecoveryDecision decision = service.applyCriticFeedback(target, "需要补充引用");

        Assertions.assertEquals(TaskRecoveryDomainService.RecoveryDecision.REFINING, decision);
        Assertions.assertEquals(TaskStatusEnum.REFINING, target.getStatus());
        Assertions.assertEquals(1, target.getCurrentRetry());
        Assertions.assertNotNull(target.getInputContext());
        Assertions.assertEquals("需要补充引用", target.getInputContext().get("criticFeedback"));
    }

    @Test
    public void shouldFailWhenRetryExceeded() {
        AgentTaskEntity target = new AgentTaskEntity();
        target.setStatus(TaskStatusEnum.COMPLETED);
        target.setCurrentRetry(1);
        target.setMaxRetries(1);

        TaskRecoveryDomainService.RecoveryDecision decision = service.applyCriticFeedback(target, "仍然不满足");

        Assertions.assertEquals(TaskRecoveryDomainService.RecoveryDecision.FAILED, decision);
        Assertions.assertEquals(TaskStatusEnum.FAILED, target.getStatus());
        Assertions.assertTrue(target.getOutputResult().contains("Validation failed"));
    }
}
