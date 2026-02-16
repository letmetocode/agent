package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.valobj.PlanTaskStatusStat;
import com.getoffer.types.enums.TaskStatusEnum;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentTaskRepository {

    /**
     * 保存任务
     */
    AgentTaskEntity save(AgentTaskEntity entity);

    /**
     * 更新任务 (带乐观锁)
     */
    AgentTaskEntity update(AgentTaskEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentTaskEntity findById(Long id);

    /**
     * 根据计划 ID 查询
     */
    List<AgentTaskEntity> findByPlanId(Long planId);

    /**
     * 根据计划 ID 和状态查询
     */
    List<AgentTaskEntity> findByPlanIdAndStatus(Long planId, TaskStatusEnum status);

    /**
     * 根据计划 ID 和节点 ID 查询
     */
    AgentTaskEntity findByPlanIdAndNodeId(Long planId, String nodeId);

    /**
     * 查询所有任务
     */
    List<AgentTaskEntity> findAll();

    /**
     * 根据状态查询
     */
    List<AgentTaskEntity> findByStatus(TaskStatusEnum status);

    /**
     * 统计任务总数。
     */
    default long countAll() {
        List<AgentTaskEntity> tasks = findAll();
        return tasks == null ? 0L : tasks.size();
    }

    /**
     * 按状态统计任务数量。
     */
    default long countByStatus(TaskStatusEnum status) {
        if (status == null) {
            return 0L;
        }
        List<AgentTaskEntity> tasks = findByStatus(status);
        return tasks == null ? 0L : tasks.size();
    }

    /**
     * 查询最近更新任务。
     */
    default List<AgentTaskEntity> findRecent(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<AgentTaskEntity> tasks = findAll();
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentTaskEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 按状态查询最近更新任务。
     */
    default List<AgentTaskEntity> findRecentByStatus(TaskStatusEnum status, int limit) {
        if (status == null || limit <= 0) {
            return Collections.emptyList();
        }
        List<AgentTaskEntity> tasks = findByStatus(status);
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentTaskEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 按过滤条件统计任务数量。
     */
    default long countByFilters(TaskStatusEnum status, String keyword, Long planId, List<Long> planIds) {
        return applyTaskFilters(status, keyword, planId, planIds).size();
    }

    /**
     * 按过滤条件分页查询任务。
     */
    default List<AgentTaskEntity> findByFiltersPaged(TaskStatusEnum status,
                                                     String keyword,
                                                     Long planId,
                                                     List<Long> planIds,
                                                     int offset,
                                                     int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<AgentTaskEntity> filtered = applyTaskFilters(status, keyword, planId, planIds);
        int safeOffset = Math.max(0, offset);
        if (safeOffset >= filtered.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(safeOffset + limit, filtered.size());
        return filtered.subList(safeOffset, toIndex);
    }

    /**
     * 查询就绪的任务 (用于调度器)
     */
    List<AgentTaskEntity> findReadyTasks();

    /**
     * 原子 claim 可执行任务（READY/REFINING/过期RUNNING -> RUNNING）。
     */
    List<AgentTaskEntity> claimExecutableTasks(String claimOwner, int limit, int leaseSeconds);

    /**
     * 原子 claim READY + 过期 RUNNING 任务（READY 优先路径）。
     * 默认回退到 claimExecutableTasks，便于兼容旧实现与测试替身。
     */
    default List<AgentTaskEntity> claimReadyLikeTasks(String claimOwner, int limit, int leaseSeconds) {
        return claimExecutableTasks(claimOwner, limit, leaseSeconds);
    }

    /**
     * 原子 claim REFINING 任务。
     * 默认返回空，避免旧实现在无显式支持时破坏 READY 优先语义。
     */
    default List<AgentTaskEntity> claimRefiningTasks(String claimOwner, int limit, int leaseSeconds) {
        return Collections.emptyList();
    }

    /**
     * 续约 claim lease。
     */
    boolean renewClaimLease(Long taskId, String claimOwner, Integer executionAttempt, int leaseSeconds);

    /**
     * 按 claim_owner + execution_attempt 条件更新任务终态，防止旧执行者回写污染。
     */
    boolean updateClaimedTaskState(AgentTaskEntity entity);

    /**
     * 查询过期 RUNNING 任务数量（用于监控/报警）。
     */
    long countExpiredRunningTasks();

    /**
     * 批量保存任务
     */
    List<AgentTaskEntity> batchSave(List<AgentTaskEntity> entities);

    /**
     * 批量更新状态
     */
    boolean batchUpdateStatus(Long planId, TaskStatusEnum fromStatus, TaskStatusEnum toStatus);

    /**
     * 按 Plan IDs 聚合任务状态统计。
     */
    List<PlanTaskStatusStat> summarizeByPlanIds(List<Long> planIds);

    private List<AgentTaskEntity> applyTaskFilters(TaskStatusEnum status, String keyword, Long planId, List<Long> planIds) {
        List<AgentTaskEntity> source;
        if (planId != null) {
            source = findByPlanId(planId);
        } else if (status != null) {
            source = findByStatus(status);
        } else {
            source = findAll();
        }
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> scopedPlanIds = planIds == null ? Collections.emptySet() : planIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        return source.stream()
                .filter(Objects::nonNull)
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> planId == null || Objects.equals(item.getPlanId(), planId))
                .filter(item -> scopedPlanIds.isEmpty() || (item.getPlanId() != null && scopedPlanIds.contains(item.getPlanId())))
                .filter(item -> normalizedKeyword.isEmpty() || containsKeyword(item, normalizedKeyword))
                .sorted(Comparator
                        .comparing(AgentTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentTaskEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private static boolean containsKeyword(AgentTaskEntity task, String keyword) {
        String text = String.format("%s %s %s %s %s %s %s",
                        task.getId() == null ? "" : task.getId(),
                        safeText(task.getName()),
                        safeText(task.getNodeId()),
                        task.getTaskType() == null ? "" : task.getTaskType().name(),
                        safeText(task.getOutputResult()),
                        safeText(task.getClaimOwner()),
                        task.getStatus() == null ? "" : task.getStatus().name())
                .toLowerCase(Locale.ROOT);
        return text.contains(keyword);
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
