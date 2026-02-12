package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.TaskShareLinkEntity;

import java.util.List;

/**
 * 任务分享链接仓储接口。
 */
public interface ITaskShareLinkRepository {

    TaskShareLinkEntity save(TaskShareLinkEntity entity);

    TaskShareLinkEntity findById(Long id);

    TaskShareLinkEntity findByTaskIdAndShareCode(Long taskId, String shareCode);

    List<TaskShareLinkEntity> findByTaskId(Long taskId);

    boolean revokeById(Long taskId, Long shareId, String revokedReason);

    int revokeAllByTaskId(Long taskId, String revokedReason);
}
