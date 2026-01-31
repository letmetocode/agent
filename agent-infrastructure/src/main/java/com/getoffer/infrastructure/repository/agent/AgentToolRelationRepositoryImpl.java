package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.AgentToolRelationEntity;
import com.getoffer.domain.agent.adapter.repository.IAgentToolRelationRepository;
import com.getoffer.infrastructure.dao.AgentToolRelationDao;
import com.getoffer.infrastructure.dao.po.AgentToolRelationPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent-工具关联关系仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentToolRelationRepositoryImpl implements IAgentToolRelationRepository {

    private final AgentToolRelationDao agentToolRelationDao;

    public AgentToolRelationRepositoryImpl(AgentToolRelationDao agentToolRelationDao) {
        this.agentToolRelationDao = agentToolRelationDao;
    }

    @Override
    public AgentToolRelationEntity save(AgentToolRelationEntity entity) {
        entity.validate();
        AgentToolRelationPO po = toPO(entity);
        agentToolRelationDao.insert(po);
        return toEntity(po);
    }

    @Override
    public boolean delete(Long agentId, Long toolId) {
        return agentToolRelationDao.delete(agentId, toolId) > 0;
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        return agentToolRelationDao.deleteByAgentId(agentId) > 0;
    }

    @Override
    public boolean deleteByToolId(Long toolId) {
        return agentToolRelationDao.deleteByToolId(toolId) > 0;
    }

    @Override
    public List<AgentToolRelationEntity> findByAgentId(Long agentId) {
        return agentToolRelationDao.selectByAgentId(agentId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentToolRelationEntity> findByToolId(Long toolId) {
        return agentToolRelationDao.selectByToolId(toolId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentToolRelationEntity> findAll() {
        return agentToolRelationDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(Long agentId, Long toolId) {
        return agentToolRelationDao.exists(agentId, toolId);
    }

    @Override
    public boolean batchSave(Long agentId, List<Long> toolIds) {
        return agentToolRelationDao.batchInsert(agentId, toolIds) > 0;
    }

    /**
     * PO 转换为 Entity
     */
    private AgentToolRelationEntity toEntity(AgentToolRelationPO po) {
        if (po == null) {
            return null;
        }
        AgentToolRelationEntity entity = new AgentToolRelationEntity();
        entity.setId(po.getId());
        entity.setAgentId(po.getAgentId());
        entity.setToolId(po.getToolId());
        entity.setIsEnabled(po.getIsEnabled());
        entity.setPriority(po.getPriority());
        entity.setCreatedAt(po.getCreatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentToolRelationPO toPO(AgentToolRelationEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentToolRelationPO po = AgentToolRelationPO.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .toolId(entity.getToolId())
                .isEnabled(entity.getIsEnabled())
                .priority(entity.getPriority())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now())
                .build();
        return po;
    }
}
