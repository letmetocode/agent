package com.getoffer.test;

import com.getoffer.domain.planning.service.PlanFinalizationDomainService;
import com.getoffer.domain.planning.service.PlanTransitionDomainService;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.trigger.application.command.ChatConversationCommandService;
import com.getoffer.trigger.application.command.PlanStatusSyncApplicationService;
import com.getoffer.trigger.application.command.TaskScheduleApplicationService;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;
import com.getoffer.trigger.job.PlanStatusDaemon;
import com.getoffer.trigger.job.TaskSchedulerDaemon;
import com.getoffer.trigger.job.TaskExecutor;
import com.getoffer.domain.task.service.TaskAgentSelectionDomainService;
import com.getoffer.domain.task.service.TaskBlackboardDomainService;
import com.getoffer.domain.task.service.TaskDispatchDomainService;
import com.getoffer.domain.task.service.TaskExecutionDomainService;
import com.getoffer.domain.task.service.TaskJsonDomainService;
import com.getoffer.domain.task.service.TaskPromptDomainService;
import com.getoffer.domain.task.service.TaskEvaluationDomainService;
import com.getoffer.domain.task.service.TaskRecoveryDomainService;
import com.getoffer.domain.task.service.TaskPersistencePolicyDomainService;
import com.getoffer.domain.task.service.TaskDependencyPolicy;
import com.getoffer.trigger.application.command.TaskPersistenceApplicationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

public class ApplicationDomainBoundaryTest {

    @Test
    public void applicationServicesShouldDependOnDomainServices() {
        Assertions.assertTrue(hasInjectionType(ChatConversationCommandService.class, SessionConversationDomainService.class));
        Assertions.assertTrue(hasInjectionType(TurnFinalizeApplicationService.class, PlanFinalizationDomainService.class));
        Assertions.assertTrue(hasInjectionType(PlanStatusDaemon.class, PlanStatusSyncApplicationService.class));
        Assertions.assertTrue(hasInjectionType(PlanStatusSyncApplicationService.class, PlanTransitionDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskAgentSelectionDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskDispatchDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskExecutionDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskPromptDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskEvaluationDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskRecoveryDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskBlackboardDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskJsonDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskExecutor.class, TaskPersistenceApplicationService.class));
        Assertions.assertTrue(hasInjectionType(TaskPersistenceApplicationService.class, TaskPersistencePolicyDomainService.class));
        Assertions.assertTrue(hasInjectionType(TaskSchedulerDaemon.class, TaskScheduleApplicationService.class));
        Assertions.assertTrue(hasInjectionType(TaskScheduleApplicationService.class, TaskDependencyPolicy.class));
    }

    @Test
    public void legacyTriggerServiceWrappersShouldBeRemoved() {
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.getoffer.trigger.service.ConversationOrchestratorService"));
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.getoffer.trigger.service.TurnResultService"));
    }

    private boolean hasInjectionType(Class<?> owner, Class<?> expectedType) {
        boolean byField = Arrays.stream(owner.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type.equals(expectedType));
        if (byField) {
            return true;
        }
        return Arrays.stream(owner.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type.equals(expectedType));
    }
}
