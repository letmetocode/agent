package com.getoffer.trigger.job;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;

import java.util.Map;

/**
 * TaskExecutionRunner 持久化域支持适配器。
 */
final class TaskExecutionPersistenceSupportAdapter implements TaskExecutionRunner.PersistenceSupport {

    private final TaskExecutionRuntimeSupport runtimeSupport;

    TaskExecutionPersistenceSupportAdapter(TaskExecutionRuntimeSupport runtimeSupport) {
        this.runtimeSupport = runtimeSupport;
    }

    @Override
    public boolean safeUpdateClaimedTask(AgentTaskEntity task) {
        return runtimeSupport.safeUpdateClaimedTask(task);
    }

    @Override
    public void safeSaveExecution(TaskExecutionEntity execution) {
        runtimeSupport.safeSaveExecution(execution);
    }

    @Override
    public Map<String, Object> buildTaskData(AgentTaskEntity task) {
        return runtimeSupport.buildTaskData(task);
    }

    @Override
    public Map<String, Object> buildTaskLog(AgentTaskEntity task) {
        return runtimeSupport.buildTaskLog(task);
    }

    @Override
    public void publishTaskEvent(PlanTaskEventTypeEnum eventType, AgentTaskEntity task, Map<String, Object> data) {
        runtimeSupport.publishTaskEvent(eventType, task, data);
    }
}
