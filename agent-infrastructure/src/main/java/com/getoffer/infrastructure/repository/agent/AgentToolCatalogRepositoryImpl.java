package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.infrastructure.dao.AgentToolCatalogDao;
import com.getoffer.infrastructure.dao.po.AgentToolCatalogPO;
import com.getoffer.types.enums.ToolTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具目录仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentToolCatalogRepositoryImpl implements IAgentToolCatalogRepository {

    private final AgentToolCatalogDao agentToolCatalogDao;

    public AgentToolCatalogRepositoryImpl(AgentToolCatalogDao agentToolCatalogDao) {
        this.agentToolCatalogDao = agentToolCatalogDao;
    }

    @Override
    public AgentToolCatalogEntity save(AgentToolCatalogEntity entity) {
        entity.validate();
        AgentToolCatalogPO po = toPO(entity);
        agentToolCatalogDao.insert(po);
        return toEntity(po);
    }

    @Override
    public AgentToolCatalogEntity update(AgentToolCatalogEntity entity) {
        entity.validate();
        AgentToolCatalogPO po = toPO(entity);
        agentToolCatalogDao.update(po);
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return agentToolCatalogDao.deleteById(id) > 0;
    }

    @Override
    public AgentToolCatalogEntity findById(Long id) {
        AgentToolCatalogPO po = agentToolCatalogDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public AgentToolCatalogEntity findByName(String name) {
        AgentToolCatalogPO po = agentToolCatalogDao.selectByName(name);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentToolCatalogEntity> findAll() {
        return agentToolCatalogDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentToolCatalogEntity> findByType(ToolTypeEnum type) {
        return agentToolCatalogDao.selectByType(type).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByName(String name) {
        return agentToolCatalogDao.selectByName(name) != null;
    }

    /**
     * PO 转换为 Entity
     */
    private AgentToolCatalogEntity toEntity(AgentToolCatalogPO po) {
        if (po == null) {
            return null;
        }
        AgentToolCatalogEntity entity = new AgentToolCatalogEntity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setType(po.getType());
        entity.setDescription(po.getDescription());
        entity.setIsActive(po.getIsActive());

        // JSONB 字段转换
        if (po.getToolConfig() != null) {
            entity.setToolConfig(com.alibaba.fastjson2.JSON.parseObject(po.getToolConfig()));
        }
        if (po.getInputSchema() != null) {
            entity.setInputSchema(com.alibaba.fastjson2.JSON.parseObject(po.getInputSchema()));
        }
        if (po.getOutputSchema() != null) {
            entity.setOutputSchema(com.alibaba.fastjson2.JSON.parseObject(po.getOutputSchema()));
        }

        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentToolCatalogPO toPO(AgentToolCatalogEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentToolCatalogPO po = AgentToolCatalogPO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getToolConfig() != null) {
            po.setToolConfig(com.alibaba.fastjson2.JSON.toJSONString(entity.getToolConfig()));
        }
        if (entity.getInputSchema() != null) {
            po.setInputSchema(com.alibaba.fastjson2.JSON.toJSONString(entity.getInputSchema()));
        }
        if (entity.getOutputSchema() != null) {
            po.setOutputSchema(com.alibaba.fastjson2.JSON.toJSONString(entity.getOutputSchema()));
        }

        return po;
    }
}
