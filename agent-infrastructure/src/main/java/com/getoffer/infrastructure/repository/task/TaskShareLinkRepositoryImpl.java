package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.adapter.repository.ITaskShareLinkRepository;
import com.getoffer.domain.task.model.entity.TaskShareLinkEntity;
import com.getoffer.infrastructure.dao.TaskShareLinkDao;
import com.getoffer.infrastructure.dao.po.TaskShareLinkPO;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务分享链接仓储实现。
 */
@Repository
public class TaskShareLinkRepositoryImpl implements ITaskShareLinkRepository {

    private final TaskShareLinkDao taskShareLinkDao;

    public TaskShareLinkRepositoryImpl(TaskShareLinkDao taskShareLinkDao) {
        this.taskShareLinkDao = taskShareLinkDao;
    }

    @Override
    public TaskShareLinkEntity save(TaskShareLinkEntity entity) {
        TaskShareLinkPO po = toPO(entity);
        taskShareLinkDao.insert(po);
        return toEntity(po);
    }

    @Override
    public TaskShareLinkEntity findById(Long id) {
        TaskShareLinkPO po = taskShareLinkDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    @Override
    public TaskShareLinkEntity findByTaskIdAndShareCode(Long taskId, String shareCode) {
        TaskShareLinkPO po = taskShareLinkDao.selectByTaskIdAndShareCode(taskId, shareCode);
        return po == null ? null : toEntity(po);
    }

    @Override
    public List<TaskShareLinkEntity> findByTaskId(Long taskId) {
        List<TaskShareLinkPO> rows = taskShareLinkDao.selectByTaskId(taskId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public boolean revokeById(Long taskId, Long shareId, String revokedReason) {
        return taskShareLinkDao.revokeById(taskId, shareId, revokedReason) > 0;
    }

    @Override
    public int revokeAllByTaskId(Long taskId, String revokedReason) {
        return taskShareLinkDao.revokeAllByTaskId(taskId, revokedReason);
    }

    private TaskShareLinkEntity toEntity(TaskShareLinkPO po) {
        if (po == null) {
            return null;
        }
        TaskShareLinkEntity entity = new TaskShareLinkEntity();
        entity.setId(po.getId());
        entity.setTaskId(po.getTaskId());
        entity.setShareCode(po.getShareCode());
        entity.setTokenHash(po.getTokenHash());
        entity.setScope(po.getScope());
        entity.setExpiresAt(po.getExpiresAt());
        entity.setRevoked(po.getRevoked());
        entity.setRevokedAt(po.getRevokedAt());
        entity.setRevokedReason(po.getRevokedReason());
        entity.setCreatedBy(po.getCreatedBy());
        entity.setVersion(po.getVersion());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    private TaskShareLinkPO toPO(TaskShareLinkEntity entity) {
        if (entity == null) {
            return null;
        }
        return TaskShareLinkPO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .shareCode(entity.getShareCode())
                .tokenHash(entity.getTokenHash())
                .scope(entity.getScope())
                .expiresAt(entity.getExpiresAt())
                .revoked(entity.getRevoked())
                .revokedAt(entity.getRevokedAt())
                .revokedReason(entity.getRevokedReason())
                .createdBy(entity.getCreatedBy())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
