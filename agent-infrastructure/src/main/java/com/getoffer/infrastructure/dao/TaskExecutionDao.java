package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.TaskExecutionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行记录 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface TaskExecutionDao {

    /**
     * 插入执行记录
     */
    int insert(TaskExecutionPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    TaskExecutionPO selectById(@Param("id") Long id);

    /**
     * 根据任务 ID 查询
     */
    List<TaskExecutionPO> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务 ID 查询，按尝试次数降序
     */
    List<TaskExecutionPO> selectByTaskIdOrderByAttempt(@Param("taskId") Long taskId);

    /**
     * 根据任务 ID 和尝试次数查询
     */
    TaskExecutionPO selectByTaskIdAndAttempt(@Param("taskId") Long taskId,
                                              @Param("attemptNumber") Integer attemptNumber);

    /**
     * 查询所有执行记录
     */
    List<TaskExecutionPO> selectAll();

    /**
     * 获取任务的最大尝试次数
     */
    Integer getMaxAttemptNumber(@Param("taskId") Long taskId);

    /**
     * 批量查询任务最新执行耗时（按最大 attempt_number）。
     */
    List<TaskExecutionPO> selectLatestExecutionByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 批量插入执行记录
     */
    int batchInsert(@Param("list") List<TaskExecutionPO> list);
}
