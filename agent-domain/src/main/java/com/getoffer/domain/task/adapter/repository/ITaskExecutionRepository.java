package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.TaskExecutionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 任务执行记录仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface ITaskExecutionRepository {

    /**
     * 保存执行记录
     */
    TaskExecutionEntity save(TaskExecutionEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    TaskExecutionEntity findById(Long id);

    /**
     * 根据任务 ID 查询
     */
    List<TaskExecutionEntity> findByTaskId(Long taskId);

    /**
     * 根据任务 ID 查询，按尝试次数降序
     */
    List<TaskExecutionEntity> findByTaskIdOrderByAttempt(Long taskId);

    /**
     * 根据任务 ID 和尝试次数查询
     */
    TaskExecutionEntity findByTaskIdAndAttempt(Long taskId, Integer attemptNumber);

    /**
     * 查询所有执行记录
     */
    List<TaskExecutionEntity> findAll();

    /**
     * 统计执行耗时大于等于阈值的记录数。
     */
    default long countByExecutionTimeAbove(long thresholdMs) {
        if (thresholdMs < 0) {
            return 0L;
        }
        List<TaskExecutionEntity> executions = findAll();
        if (executions == null || executions.isEmpty()) {
            return 0L;
        }
        return executions.stream()
                .map(TaskExecutionEntity::getExecutionTimeMs)
                .filter(Objects::nonNull)
                .filter(item -> item >= thresholdMs)
                .count();
    }

    /**
     * 汇总执行耗时分位数（p50/p95/p99）。
     */
    default Map<String, Long> summarizeLatencyQuantiles() {
        List<TaskExecutionEntity> executions = findAll();
        if (executions == null || executions.isEmpty()) {
            return Map.of("p50", 0L, "p95", 0L, "p99", 0L);
        }
        List<Long> times = executions.stream()
                .map(TaskExecutionEntity::getExecutionTimeMs)
                .filter(Objects::nonNull)
                .filter(item -> item > 0)
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (times.isEmpty()) {
            return Map.of("p50", 0L, "p95", 0L, "p99", 0L);
        }
        return Map.of(
                "p50", percentile(times, 0.50),
                "p95", percentile(times, 0.95),
                "p99", percentile(times, 0.99)
        );
    }

    /**
     * 获取任务的最大尝试次数
     */
    Integer getMaxAttemptNumber(Long taskId);

    /**
     * 批量查询任务最新执行耗时（按最大 attempt_number）。
     */
    Map<Long, Long> findLatestExecutionTimeByTaskIds(List<Long> taskIds);

    /**
     * 批量保存执行记录
     */
    List<TaskExecutionEntity> batchSave(List<TaskExecutionEntity> entities);

    private static long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0L;
        }
        int size = sortedValues.size();
        int index = (int) Math.ceil(percentile * size) - 1;
        int normalizedIndex = Math.max(0, Math.min(size - 1, index));
        return sortedValues.get(normalizedIndex);
    }
}
