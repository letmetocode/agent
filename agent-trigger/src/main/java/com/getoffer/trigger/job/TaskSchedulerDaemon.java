package com.getoffer.trigger.job;

import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scheduler daemon: promote pending tasks to READY when dependencies are satisfied.
 */
@Slf4j
@Component
public class TaskSchedulerDaemon {

    private final IAgentTaskRepository agentTaskRepository;

    public TaskSchedulerDaemon(IAgentTaskRepository agentTaskRepository) {
        this.agentTaskRepository = agentTaskRepository;
    }

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:1000}", scheduler = "daemonScheduler")
    public void promotePendingTasks() {
        List<AgentTaskEntity> pendingTasks = agentTaskRepository.findByStatus(TaskStatusEnum.PENDING);
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return;
        }

        Map<Long, List<AgentTaskEntity>> pendingByPlan = pendingTasks.stream()
                .filter(task -> task.getPlanId() != null)
                .collect(Collectors.groupingBy(AgentTaskEntity::getPlanId));

        for (Map.Entry<Long, List<AgentTaskEntity>> entry : pendingByPlan.entrySet()) {
            Long planId = entry.getKey();
            List<AgentTaskEntity> tasksForPlan = agentTaskRepository.findByPlanId(planId);
            if (tasksForPlan == null || tasksForPlan.isEmpty()) {
                continue;
            }

            Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
            for (AgentTaskEntity task : tasksForPlan) {
                if (task.getNodeId() != null) {
                    statusByNode.put(task.getNodeId(), task.getStatus());
                }
            }

            for (AgentTaskEntity task : entry.getValue()) {
                if (task == null || task.getStatus() != TaskStatusEnum.PENDING) {
                    continue;
                }
                DependencyCheckResult result = checkDependencies(task, statusByNode);
                if (result == DependencyCheckResult.WAITING) {
                    continue;
                }
                if (result == DependencyCheckResult.BLOCKED) {
                    try {
                        task.skip();
                        agentTaskRepository.update(task);
                        log.debug("Task skipped due to failed dependency. planId={}, nodeId={}", planId, task.getNodeId());
                    } catch (Exception ex) {
                        log.warn("Failed to skip task. planId={}, nodeId={}, error={}",
                                planId, task.getNodeId(), ex.getMessage());
                    }
                    continue;
                }
                try {
                    task.markReady();
                    agentTaskRepository.update(task);
                    log.debug("Task promoted to READY. planId={}, nodeId={}", planId, task.getNodeId());
                } catch (Exception ex) {
                    log.warn("Failed to promote task to READY. planId={}, nodeId={}, error={}",
                            planId, task.getNodeId(), ex.getMessage());
                }
            }
        }
    }

    private DependencyCheckResult checkDependencies(AgentTaskEntity task, Map<String, TaskStatusEnum> statusByNode) {
        List<String> dependencies = task.getDependencyNodeIds();
        if (dependencies == null || dependencies.isEmpty()) {
            return DependencyCheckResult.SATISFIED;
        }
        if (statusByNode == null) {
            return DependencyCheckResult.WAITING;
        }
        for (String dependency : dependencies) {
            TaskStatusEnum status = statusByNode.get(dependency);
            if (status == TaskStatusEnum.FAILED || status == TaskStatusEnum.SKIPPED) {
                return DependencyCheckResult.BLOCKED;
            }
            if (status != TaskStatusEnum.COMPLETED) {
                return DependencyCheckResult.WAITING;
            }
        }
        return DependencyCheckResult.SATISFIED;
    }

    private enum DependencyCheckResult {
        SATISFIED,
        WAITING,
        BLOCKED
    }
}
