package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskStatusEnum;

import java.util.Map;

/**
 * Task 依赖判定策略：根据依赖节点状态返回任务可推进决策。
 */
public interface TaskDependencyPolicy {

    DependencyDecision resolveDependencyDecision(AgentTaskEntity task,
                                                 Map<String, TaskStatusEnum> statusByNode);

    enum DependencyDecision {
        SATISFIED,
        WAITING,
        BLOCKED
    }
}
