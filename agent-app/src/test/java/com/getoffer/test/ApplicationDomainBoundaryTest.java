package com.getoffer.test;

import com.getoffer.domain.planning.service.PlanFinalizationDomainService;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.trigger.application.command.ChatConversationCommandService;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.trigger.job.PlanStatusDaemon;
import com.getoffer.trigger.job.TaskExecutor;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

public class ApplicationDomainBoundaryTest {

    @Test
    public void applicationServicesShouldDependOnDomainServices() {
        Assertions.assertTrue(hasFieldType(ChatConversationCommandService.class, SessionConversationDomainService.class));
        Assertions.assertTrue(hasFieldType(TurnFinalizeApplicationService.class, PlanFinalizationDomainService.class));
        Assertions.assertTrue(hasFieldType(PlanStatusDaemon.class, PlanTransitionDomainService.class));
        Assertions.assertTrue(hasFieldType(TaskExecutor.class, TaskDispatchDomainService.class));
        Assertions.assertTrue(hasFieldType(TaskExecutor.class, TaskExecutionDomainService.class));
        Assertions.assertTrue(hasFieldType(TaskExecutor.class, TaskPromptDomainService.class));
        Assertions.assertTrue(hasFieldType(TaskExecutor.class, TaskEvaluationDomainService.class));
        Assertions.assertTrue(hasFieldType(TaskExecutor.class, TaskRecoveryDomainService.class));
    }

    @Test
    public void legacyTriggerServiceWrappersShouldBeRemoved() {
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.getoffer.trigger.service.ConversationOrchestratorService"));
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.getoffer.trigger.service.TurnResultService"));
    }

    private boolean hasFieldType(Class<?> owner, Class<?> expectedType) {
        return Arrays.stream(owner.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type.equals(expectedType));
    }
}
