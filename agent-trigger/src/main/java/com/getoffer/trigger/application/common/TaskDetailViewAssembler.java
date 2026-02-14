package com.getoffer.trigger.application.common;

import com.getoffer.api.dto.TaskDetailDTO;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Task 详情视图组装器：统一 TaskDetailDTO 映射与最新执行耗时加载。
 */
@Component
public class TaskDetailViewAssembler {

    private final ITaskExecutionRepository taskExecutionRepository;

    public TaskDetailViewAssembler(ITaskExecutionRepository taskExecutionRepository) {
        this.taskExecutionRepository = taskExecutionRepository;
    }

    public TaskDetailDTO toTaskDetailDTO(AgentTaskEntity task) {
        return toTaskDetailDTO(task, null);
    }

    public TaskDetailDTO toTaskDetailDTO(AgentTaskEntity task, Map<Long, Long> latestExecutionTimeMap) {
        if (task == null) {
            return null;
        }
        TaskDetailDTO dto = new TaskDetailDTO();
        dto.setTaskId(task.getId());
        dto.setPlanId(task.getPlanId());
        dto.setNodeId(task.getNodeId());
        dto.setName(task.getName());
        dto.setTaskType(task.getTaskType() == null ? null : task.getTaskType().name());
        dto.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        dto.setDependencyNodeIds(task.getDependencyNodeIds());
        dto.setInputContext(task.getInputContext());
        dto.setConfigSnapshot(task.getConfigSnapshot());
        dto.setOutputResult(task.getOutputResult());
        dto.setMaxRetries(task.getMaxRetries());
        dto.setCurrentRetry(task.getCurrentRetry());
        dto.setClaimOwner(task.getClaimOwner());
        dto.setClaimAt(task.getClaimAt());
        dto.setLeaseUntil(task.getLeaseUntil());
        dto.setExecutionAttempt(task.getExecutionAttempt());
        if (latestExecutionTimeMap == null) {
            dto.setLatestExecutionTimeMs(resolveLatestExecutionTimeMs(task.getId()));
        } else {
            dto.setLatestExecutionTimeMs(task.getId() == null ? null : latestExecutionTimeMap.get(task.getId()));
        }
        dto.setVersion(task.getVersion());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }

    public Map<Long, Long> resolveLatestExecutionTimeMap(List<AgentTaskEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> taskIds = tasks.stream()
                .map(AgentTaskEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> latestMap = taskExecutionRepository.findLatestExecutionTimeByTaskIds(taskIds);
        return latestMap == null ? Collections.emptyMap() : latestMap;
    }

    private Long resolveLatestExecutionTimeMs(Long taskId) {
        if (taskId == null) {
            return null;
        }
        Map<Long, Long> latestMap = taskExecutionRepository.findLatestExecutionTimeByTaskIds(Collections.singletonList(taskId));
        if (latestMap == null || latestMap.isEmpty()) {
            return null;
        }
        return latestMap.get(taskId);
    }
}
