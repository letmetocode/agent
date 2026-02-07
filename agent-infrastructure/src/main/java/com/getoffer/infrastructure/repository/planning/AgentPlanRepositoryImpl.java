package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.infrastructure.dao.AgentPlanDao;
import com.getoffer.infrastructure.dao.po.AgentPlanPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.PlanStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行计划仓储实现类。
 * <p>
 * 负责执行计划的持久化操作，包括：
 * <ul>
 *   <li>执行计划的增删改查（带乐观锁）</li>
 *   <li>按会话ID、状态、SOP模板ID等条件查询</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（executionGraph、globalContext）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentPlanRepositoryImpl implements IAgentPlanRepository {

    private final AgentPlanDao agentPlanDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 AgentPlanRepositoryImpl。
     */
    public AgentPlanRepositoryImpl(AgentPlanDao agentPlanDao, JsonCodec jsonCodec) {
        this.agentPlanDao = agentPlanDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public AgentPlanEntity save(AgentPlanEntity entity) {
        entity.validate();
        AgentPlanPO po = toPO(entity);
        agentPlanDao.insert(po);
        return toEntity(po);
    }

    /**
     * 更新实体。
     */
    @Override
    public AgentPlanEntity update(AgentPlanEntity entity) {
        entity.validate();
        Integer oldVersion = entity.getVersion();
        if (oldVersion == null) {
            throw new IllegalStateException("Version cannot be null for AgentPlan update: " + entity.getId());
        }
        AgentPlanPO po = toPO(entity);
        int affected = agentPlanDao.updateWithVersion(po);
        if (affected == 0) {
            throw new RuntimeException("Optimistic lock failed for AgentPlan: " + entity.getId());
        }
        Integer newVersion = oldVersion + 1;
        entity.setVersion(newVersion);
        po.setVersion(newVersion);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return agentPlanDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public AgentPlanEntity findById(Long id) {
        AgentPlanPO po = agentPlanDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按会话 ID 查询。
     */
    @Override
    public List<AgentPlanEntity> findBySessionId(Long sessionId) {
        return agentPlanDao.selectBySessionId(sessionId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按状态查询。
     */
    @Override
    public List<AgentPlanEntity> findByStatus(PlanStatusEnum status) {
        return agentPlanDao.selectByStatus(status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按状态和优先级查询。
     */
    @Override
    public List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status) {
        return agentPlanDao.selectByStatusAndPriority(status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentPlanEntity> findByStatusPaged(PlanStatusEnum status, int offset, int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        int safeOffset = Math.max(0, offset);
        return agentPlanDao.selectByStatusPaged(status, safeOffset, limit).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询全部。
     */
    @Override
    public List<AgentPlanEntity> findAll() {
        return agentPlanDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按 SOP 模板 ID 查询。
     */
    @Override
    public List<AgentPlanEntity> findBySopTemplateId(Long sopTemplateId) {
        return agentPlanDao.selectBySopTemplateId(sopTemplateId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询 executable plans。
     */
    @Override
    public List<AgentPlanEntity> findExecutablePlans() {
        return agentPlanDao.selectByStatusAndPriority(PlanStatusEnum.READY).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * PO 转换为 Entity
     */
    private AgentPlanEntity toEntity(AgentPlanPO po) {
        if (po == null) {
            return null;
        }
        AgentPlanEntity entity = new AgentPlanEntity();
        entity.setId(po.getId());
        entity.setSessionId(po.getSessionId());
        entity.setSopTemplateId(po.getSopTemplateId());
        entity.setPlanGoal(po.getPlanGoal());

        // JSONB 字段转换
        if (po.getExecutionGraph() != null) {
            entity.setExecutionGraph(jsonCodec.readMap(po.getExecutionGraph()));
        }
        if (po.getGlobalContext() != null) {
            entity.setGlobalContext(jsonCodec.readMap(po.getGlobalContext()));
        }

        entity.setStatus(po.getStatus());
        entity.setPriority(po.getPriority());
        entity.setErrorSummary(po.getErrorSummary());
        entity.setVersion(po.getVersion());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentPlanPO toPO(AgentPlanEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentPlanPO po = AgentPlanPO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .sopTemplateId(entity.getSopTemplateId())
                .planGoal(entity.getPlanGoal())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .errorSummary(entity.getErrorSummary())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getExecutionGraph() != null) {
            po.setExecutionGraph(jsonCodec.writeValue(entity.getExecutionGraph()));
        }
        if (entity.getGlobalContext() != null) {
            po.setGlobalContext(jsonCodec.writeValue(entity.getGlobalContext()));
        }

        return po;
    }
}
