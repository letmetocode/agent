package com.getoffer.trigger.application.command;

import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.service.TaskDependencyPolicy;
import com.getoffer.types.enums.TaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Task 调度写用例：统一承载 PENDING 任务依赖判定与状态推进。
 */
@Slf4j
@Service
public class TaskScheduleApplicationService {

    private final IAgentTaskRepository agentTaskRepository;
    private final TaskDependencyPolicy taskDependencyPolicy;

    public TaskScheduleApplicationService(IAgentTaskRepository agentTaskRepository,
                                          TaskDependencyPolicy taskDependencyPolicy) {
        this.agentTaskRepository = agentTaskRepository;
        this.taskDependencyPolicy = taskDependencyPolicy;
    }

    public ScheduleResult schedulePendingTasks() {
        List<AgentTaskEntity> pendingTasks = agentTaskRepository.findByStatus(TaskStatusEnum.PENDING);
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return ScheduleResult.empty();
        }

        int promotedCount = 0;
        int skippedCount = 0;
        int waitingCount = 0;
        int errorCount = 0;

        Map<Long, List<AgentTaskEntity>> pendingByPlan = pendingTasks.stream()
                .filter(task -> task != null && task.getPlanId() != null)
                .collect(Collectors.groupingBy(AgentTaskEntity::getPlanId));

        for (Map.Entry<Long, List<AgentTaskEntity>> entry : pendingByPlan.entrySet()) {
            Long planId = entry.getKey();
            List<AgentTaskEntity> tasksForPlan = agentTaskRepository.findByPlanId(planId);
            if (tasksForPlan == null || tasksForPlan.isEmpty()) {
                waitingCount += entry.getValue().size();
                continue;
            }

            Map<String, TaskStatusEnum> statusByNode = new HashMap<>();
            for (AgentTaskEntity task : tasksForPlan) {
                if (task != null && task.getNodeId() != null) {
                    statusByNode.put(task.getNodeId(), task.getStatus());
                }
            }

            for (AgentTaskEntity task : entry.getValue()) {
                if (task == null || task.getStatus() != TaskStatusEnum.PENDING) {
                    continue;
                }

                TaskDependencyPolicy.DependencyDecision decision =
                        taskDependencyPolicy.resolveDependencyDecision(task, statusByNode);
                if (decision == TaskDependencyPolicy.DependencyDecision.WAITING) {
                    waitingCount++;
                    continue;
                }

                if (decision == TaskDependencyPolicy.DependencyDecision.BLOCKED) {
                    try {
                        task.skip();
                        agentTaskRepository.update(task);
                        skippedCount++;
                        log.debug("Task skipped due to failed dependency. planId={}, nodeId={}",
                                planId,
                                task.getNodeId());
                    } catch (Exception ex) {
                        errorCount++;
                        log.warn("Failed to skip task. planId={}, nodeId={}, error={}",
                                planId,
                                task.getNodeId(),
                                ex.getMessage());
                    }
                    continue;
                }

                try {
                    task.markReady();
                    agentTaskRepository.update(task);
                    promotedCount++;
                    log.debug("Task promoted to READY. planId={}, nodeId={}",
                            planId,
                            task.getNodeId());
                } catch (Exception ex) {
                    errorCount++;
                    log.warn("Failed to promote task to READY. planId={}, nodeId={}, error={}",
                            planId,
                            task.getNodeId(),
                            ex.getMessage());
                }
            }
        }

        return new ScheduleResult(pendingTasks.size(), promotedCount, skippedCount, waitingCount, errorCount);
    }

    public record ScheduleResult(int pendingCount,
                                 int promotedCount,
                                 int skippedCount,
                                 int waitingCount,
                                 int errorCount) {

        public static ScheduleResult empty() {
            return new ScheduleResult(0, 0, 0, 0, 0);
        }
    }
}
