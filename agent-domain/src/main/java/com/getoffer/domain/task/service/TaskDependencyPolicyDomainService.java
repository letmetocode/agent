package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Task 依赖判定领域服务：封装依赖节点状态到任务可执行决策的规则。
 */
@Service
public class TaskDependencyPolicyDomainService implements TaskDependencyPolicy {

    @Override
    public DependencyDecision resolveDependencyDecision(AgentTaskEntity task,
                                                        Map<String, TaskStatusEnum> statusByNode) {
        if (task == null || task.getStatus() != TaskStatusEnum.PENDING) {
            return DependencyDecision.WAITING;
        }

        List<String> dependencies = task.getDependencyNodeIds();
        if (dependencies == null || dependencies.isEmpty()) {
            return DependencyDecision.SATISFIED;
        }

        if (statusByNode == null || statusByNode.isEmpty()) {
            return DependencyDecision.WAITING;
        }

        for (String dependency : dependencies) {
            TaskStatusEnum status = statusByNode.get(dependency);
            if (status == TaskStatusEnum.FAILED || status == TaskStatusEnum.SKIPPED) {
                return DependencyDecision.BLOCKED;
            }
            if (status != TaskStatusEnum.COMPLETED) {
                return DependencyDecision.WAITING;
            }
        }
        return DependencyDecision.SATISFIED;
    }
}
