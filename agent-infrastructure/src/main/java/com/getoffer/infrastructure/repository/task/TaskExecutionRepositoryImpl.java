package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.infrastructure.dao.TaskExecutionDao;
import com.getoffer.infrastructure.dao.po.TaskExecutionPO;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务执行记录仓储实现类。
 * <p>
 * 负责任务执行记录的持久化操作，包括：
 * <ul>
 *   <li>执行记录的增删查</li>
 *   <li>按任务ID查询（支持按尝试次数排序）</li>
 *   <li>获取最大尝试次数</li>
 *   <li>批量保存</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（tokenUsage）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class TaskExecutionRepositoryImpl implements ITaskExecutionRepository {

    private final TaskExecutionDao taskExecutionDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 TaskExecutionRepositoryImpl。
     */
    public TaskExecutionRepositoryImpl(TaskExecutionDao taskExecutionDao, JsonCodec jsonCodec) {
        this.taskExecutionDao = taskExecutionDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public TaskExecutionEntity save(TaskExecutionEntity entity) {
        entity.validate();
        TaskExecutionPO po = toPO(entity);
        taskExecutionDao.insert(po);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return taskExecutionDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public TaskExecutionEntity findById(Long id) {
        TaskExecutionPO po = taskExecutionDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按任务 ID 查询。
     */
    @Override
    public List<TaskExecutionEntity> findByTaskId(Long taskId) {
        return taskExecutionDao.selectByTaskId(taskId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按任务 ID 查询并按重试次数排序。
     */
    @Override
    public List<TaskExecutionEntity> findByTaskIdOrderByAttempt(Long taskId) {
        return taskExecutionDao.selectByTaskIdOrderByAttempt(taskId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按任务 ID 和重试次数查询。
     */
    @Override
    public TaskExecutionEntity findByTaskIdAndAttempt(Long taskId, Integer attemptNumber) {
        TaskExecutionPO po = taskExecutionDao.selectByTaskIdAndAttempt(taskId, attemptNumber);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 查询全部。
     */
    @Override
    public List<TaskExecutionEntity> findAll() {
        return taskExecutionDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取 max attempt number。
     */
    @Override
    public Integer getMaxAttemptNumber(Long taskId) {
        return taskExecutionDao.getMaxAttemptNumber(taskId);
    }

    /**
     * 执行 batch save。
     */
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
            entity.setTokenUsage(jsonCodec.readMap(po.getTokenUsage()));
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
            po.setTokenUsage(jsonCodec.writeValue(entity.getTokenUsage()));
        }

        return po;
    }
}
