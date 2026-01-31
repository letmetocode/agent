package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.infrastructure.dao.TaskExecutionDao;
import com.getoffer.infrastructure.dao.po.TaskExecutionPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务执行记录仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class TaskExecutionRepositoryImpl implements ITaskExecutionRepository {

    private final TaskExecutionDao taskExecutionDao;

    public TaskExecutionRepositoryImpl(TaskExecutionDao taskExecutionDao) {
        this.taskExecutionDao = taskExecutionDao;
    }

    @Override
    public TaskExecutionEntity save(TaskExecutionEntity entity) {
        entity.validate();
        TaskExecutionPO po = toPO(entity);
        taskExecutionDao.insert(po);
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return taskExecutionDao.deleteById(id) > 0;
    }

    @Override
    public TaskExecutionEntity findById(Long id) {
        TaskExecutionPO po = taskExecutionDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<TaskExecutionEntity> findByTaskId(Long taskId) {
        return taskExecutionDao.selectByTaskId(taskId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskExecutionEntity> findByTaskIdOrderByAttempt(Long taskId) {
        return taskExecutionDao.selectByTaskIdOrderByAttempt(taskId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public TaskExecutionEntity findByTaskIdAndAttempt(Long taskId, Integer attemptNumber) {
        TaskExecutionPO po = taskExecutionDao.selectByTaskIdAndAttempt(taskId, attemptNumber);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<TaskExecutionEntity> findAll() {
        return taskExecutionDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getMaxAttemptNumber(Long taskId) {
        return taskExecutionDao.getMaxAttemptNumber(taskId);
    }

    @Override
    public List<TaskExecutionEntity> batchSave(List<TaskExecutionEntity> entities) {
        List<TaskExecutionPO> pos = entities.stream()
                .map(this::toPO)
                .collect(Collectors.toList());
        taskExecutionDao.batchInsert(pos);
        return entities; // IDs will be populated by MyBatis
    }

    /**
     * PO 转换为 Entity
     */
    private TaskExecutionEntity toEntity(TaskExecutionPO po) {
        if (po == null) {
            return null;
        }
        TaskExecutionEntity entity = new TaskExecutionEntity();
        entity.setId(po.getId());
        entity.setTaskId(po.getTaskId());
        entity.setAttemptNumber(po.getAttemptNumber());
        entity.setPromptSnapshot(po.getPromptSnapshot());
        entity.setLlmResponseRaw(po.getLlmResponseRaw());
        entity.setModelName(po.getModelName());
        entity.setExecutionTimeMs(po.getExecutionTimeMs());
        entity.setIsValid(po.getIsValid());
        entity.setValidationFeedback(po.getValidationFeedback());
        entity.setErrorMessage(po.getErrorMessage());
        entity.setCreatedAt(po.getCreatedAt());

        // JSONB 字段转换
        if (po.getTokenUsage() != null) {
            entity.setTokenUsage(com.alibaba.fastjson2.JSON.parseObject(po.getTokenUsage()));
        }

        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private TaskExecutionPO toPO(TaskExecutionEntity entity) {
        if (entity == null) {
            return null;
        }
        TaskExecutionPO po = TaskExecutionPO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .attemptNumber(entity.getAttemptNumber())
                .promptSnapshot(entity.getPromptSnapshot())
                .llmResponseRaw(entity.getLlmResponseRaw())
                .modelName(entity.getModelName())
                .executionTimeMs(entity.getExecutionTimeMs())
                .isValid(entity.getIsValid())
                .validationFeedback(entity.getValidationFeedback())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getTokenUsage() != null) {
            po.setTokenUsage(com.alibaba.fastjson2.JSON.toJSONString(entity.getTokenUsage()));
        }

        return po;
    }
}
