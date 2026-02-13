package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.TaskExecutionEntity;

import java.util.List;
import java.util.Map;

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
}
