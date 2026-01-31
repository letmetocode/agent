package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.infrastructure.dao.AgentRegistryDao;
import com.getoffer.infrastructure.dao.po.AgentRegistryPO;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 注册表仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentRegistryRepositoryImpl implements IAgentRegistryRepository {

    private final AgentRegistryDao agentRegistryDao;

    public AgentRegistryRepositoryImpl(AgentRegistryDao agentRegistryDao) {
        this.agentRegistryDao = agentRegistryDao;
    }

    @Override
    public AgentRegistryEntity save(AgentRegistryEntity entity) {
        entity.validate();
        AgentRegistryPO po = toPO(entity);
        agentRegistryDao.insert(po);
        return toEntity(po);
    }

    @Override
    public AgentRegistryEntity update(AgentRegistryEntity entity) {
        entity.validate();
        AgentRegistryPO po = toPO(entity);
        agentRegistryDao.update(po);
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return agentRegistryDao.deleteById(id) > 0;
    }

    @Override
    public AgentRegistryEntity findById(Long id) {
        AgentRegistryPO po = agentRegistryDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public AgentRegistryEntity findByKey(String key) {
        AgentRegistryPO po = agentRegistryDao.selectByKey(key);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentRegistryEntity> findAll() {
        return agentRegistryDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentRegistryEntity> findByActive(Boolean isActive) {
        return agentRegistryDao.selectByActive(isActive).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
        return agentRegistryDao.selectByModelProvider(modelProvider).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByKey(String key) {
        return agentRegistryDao.selectByKey(key) != null;
    }

    /**
     * PO 转换为 Entity
     */
    private AgentRegistryEntity toEntity(AgentRegistryPO po) {
        if (po == null) {
            return null;
        }
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(po.getId());
        entity.setKey(po.getKey());
        entity.setName(po.getName());
        entity.setModelProvider(po.getModelProvider());
        entity.setModelName(po.getModelName());

        // JSONB 字段转换
        if (po.getModelOptions() != null) {
            entity.setModelOptions(com.alibaba.fastjson2.JSON.parseObject(po.getModelOptions()));
        }
        entity.setBaseSystemPrompt(po.getBaseSystemPrompt());
        if (po.getAdvisorConfig() != null) {
            entity.setAdvisorConfig(com.alibaba.fastjson2.JSON.parseObject(po.getAdvisorConfig()));
        }

        entity.setIsActive(po.getIsActive());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentRegistryPO toPO(AgentRegistryEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentRegistryPO po = AgentRegistryPO.builder()
                .id(entity.getId())
                .key(entity.getKey())
                .name(entity.getName())
                .modelProvider(entity.getModelProvider())
                .modelName(entity.getModelName())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getModelOptions() != null) {
            po.setModelOptions(com.alibaba.fastjson2.JSON.toJSONString(entity.getModelOptions()));
        }
        po.setBaseSystemPrompt(entity.getBaseSystemPrompt());
        if (entity.getAdvisorConfig() != null) {
            po.setAdvisorConfig(com.alibaba.fastjson2.JSON.toJSONString(entity.getAdvisorConfig()));
        }

        po.setIsActive(entity.getIsActive());
        po.setCreatedAt(entity.getCreatedAt());
        po.setUpdatedAt(entity.getUpdatedAt());
        return po;
    }
}
