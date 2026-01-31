package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.infrastructure.dao.AgentPlanDao;
import com.getoffer.infrastructure.dao.po.AgentPlanPO;
import com.getoffer.types.enums.PlanStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行计划仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentPlanRepositoryImpl implements IAgentPlanRepository {

    private final AgentPlanDao agentPlanDao;

    public AgentPlanRepositoryImpl(AgentPlanDao agentPlanDao) {
        this.agentPlanDao = agentPlanDao;
    }

    @Override
    public AgentPlanEntity save(AgentPlanEntity entity) {
        entity.validate();
        AgentPlanPO po = toPO(entity);
        agentPlanDao.insert(po);
        return toEntity(po);
    }

    @Override
    public AgentPlanEntity update(AgentPlanEntity entity) {
        entity.validate();
        entity.incrementVersion();
        AgentPlanPO po = toPO(entity);
        int affected = agentPlanDao.updateWithVersion(po);
        if (affected == 0) {
            throw new RuntimeException("Optimistic lock failed for AgentPlan: " + entity.getId());
        }
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return agentPlanDao.deleteById(id) > 0;
    }

    @Override
    public AgentPlanEntity findById(Long id) {
        AgentPlanPO po = agentPlanDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentPlanEntity> findBySessionId(Long sessionId) {
        return agentPlanDao.selectBySessionId(sessionId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentPlanEntity> findByStatus(PlanStatusEnum status) {
        return agentPlanDao.selectByStatus(status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status) {
        return agentPlanDao.selectByStatusAndPriority(status).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentPlanEntity> findAll() {
        return agentPlanDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentPlanEntity> findBySopTemplateId(Long sopTemplateId) {
        return agentPlanDao.selectBySopTemplateId(sopTemplateId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

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
            entity.setExecutionGraph(com.alibaba.fastjson2.JSON.parseObject(po.getExecutionGraph()));
        }
        if (po.getGlobalContext() != null) {
            entity.setGlobalContext(com.alibaba.fastjson2.JSON.parseObject(po.getGlobalContext()));
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
            po.setExecutionGraph(com.alibaba.fastjson2.JSON.toJSONString(entity.getExecutionGraph()));
        }
        if (entity.getGlobalContext() != null) {
            po.setGlobalContext(com.alibaba.fastjson2.JSON.toJSONString(entity.getGlobalContext()));
        }

        return po;
    }
}
