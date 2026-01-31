package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.infrastructure.dao.AgentTaskDao;
import com.getoffer.infrastructure.dao.po.AgentTaskPO;
import com.getoffer.types.enums.TaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentTaskRepositoryImpl implements IAgentTaskRepository {

    private final AgentTaskDao agentTaskDao;

    public AgentTaskRepositoryImpl(AgentTaskDao agentTaskDao) {
        this.agentTaskDao = agentTaskDao;
    }

    @Override
    public AgentTaskEntity save(AgentTaskEntity entity) {
        entity.validate();
        AgentTaskPO po = toPO(entity);
        agentTaskDao.insert(po);
        return toEntity(po);
    }

    @Override
    public AgentTaskEntity update(AgentTaskEntity entity) {
        entity.validate();
        entity.incrementVersion();
        AgentTaskPO po = toPO(entity);
        int affected = agentTaskDao.updateWithVersion(po);
        if (affected == 0) {
            throw new RuntimeException("Optimistic lock failed for AgentTask: " + entity.getId());
        }
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return agentTaskDao.deleteById(id) > 0;
    }

    @Override
    public AgentTaskEntity findById(Long id) {
        AgentTaskPO po = agentTaskDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentTaskEntity> findByPlanId(Long planId) {
        return agentTaskDao.selectByPlanId(planId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTaskEntity> findByPlanIdAndStatus(Long planId, TaskStatusEnum status) {
        return agentTaskDao.selectByPlanIdAndStatus(planId, status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AgentTaskEntity findByPlanIdAndNodeId(Long planId, String nodeId) {
        AgentTaskPO po = agentTaskDao.selectByPlanIdAndNodeId(planId, nodeId);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentTaskEntity> findAll() {
        return agentTaskDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTaskEntity> findByStatus(TaskStatusEnum status) {
        return agentTaskDao.selectByStatus(status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTaskEntity> findReadyTasks() {
        return agentTaskDao.selectReadyTasks().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTaskEntity> batchSave(List<AgentTaskEntity> entities) {
        List<AgentTaskPO> pos = entities.stream()
                .map(this::toPO)
                .collect(Collectors.toList());
        agentTaskDao.batchInsert(pos);
        return entities; // IDs will be populated by MyBatis
    }

    @Override
    public boolean batchUpdateStatus(Long planId, TaskStatusEnum fromStatus, TaskStatusEnum toStatus) {
        return agentTaskDao.batchUpdateStatus(planId, fromStatus, toStatus) > 0;
    }

    /**
     * PO 转换为 Entity
     */
    private AgentTaskEntity toEntity(AgentTaskPO po) {
        if (po == null) {
            return null;
        }
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(po.getId());
        entity.setPlanId(po.getPlanId());
        entity.setNodeId(po.getNodeId());
        entity.setName(po.getName());
        entity.setTaskType(po.getTaskType());
        entity.setStatus(po.getStatus());

        // JSONB 字段转换
        if (po.getDependencyNodeIds() != null) {
            entity.setDependencyNodeIds(com.alibaba.fastjson2.JSON.parseArray(po.getDependencyNodeIds(), String.class));
        }
        if (po.getInputContext() != null) {
            entity.setInputContext(com.alibaba.fastjson2.JSON.parseObject(po.getInputContext()));
        }
        if (po.getConfigSnapshot() != null) {
            entity.setConfigSnapshot(com.alibaba.fastjson2.JSON.parseObject(po.getConfigSnapshot()));
        }

        entity.setOutputResult(po.getOutputResult());
        entity.setMaxRetries(po.getMaxRetries());
        entity.setCurrentRetry(po.getCurrentRetry());
        entity.setVersion(po.getVersion());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentTaskPO toPO(AgentTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentTaskPO po = AgentTaskPO.builder()
                .id(entity.getId())
                .planId(entity.getPlanId())
                .nodeId(entity.getNodeId())
                .name(entity.getName())
                .taskType(entity.getTaskType())
                .status(entity.getStatus())
                .outputResult(entity.getOutputResult())
                .maxRetries(entity.getMaxRetries())
                .currentRetry(entity.getCurrentRetry())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Map/List 转换为 JSON 字符串
        if (entity.getDependencyNodeIds() != null) {
            po.setDependencyNodeIds(com.alibaba.fastjson2.JSON.toJSONString(entity.getDependencyNodeIds()));
        }
        if (entity.getInputContext() != null) {
            po.setInputContext(com.alibaba.fastjson2.JSON.toJSONString(entity.getInputContext()));
        }
        if (entity.getConfigSnapshot() != null) {
            po.setConfigSnapshot(com.alibaba.fastjson2.JSON.toJSONString(entity.getConfigSnapshot()));
        }

        return po;
    }
}
