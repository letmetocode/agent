package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.infrastructure.dao.AgentRegistryDao;
import com.getoffer.infrastructure.dao.po.AgentRegistryPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 注册表仓储实现类。
 * <p>
 * 负责Agent配置的持久化操作，包括：
 * <ul>
 *   <li>Agent配置的增删改查</li>
 *   <li>按激活状态、模型提供商等条件查询</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（modelOptions、advisorConfig）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentRegistryRepositoryImpl implements IAgentRegistryRepository {

    private final AgentRegistryDao agentRegistryDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 AgentRegistryRepositoryImpl。
     */
    public AgentRegistryRepositoryImpl(AgentRegistryDao agentRegistryDao, JsonCodec jsonCodec) {
        this.agentRegistryDao = agentRegistryDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public AgentRegistryEntity save(AgentRegistryEntity entity) {
        entity.validate();
        AgentRegistryPO po = toPO(entity);
        agentRegistryDao.insert(po);
        return toEntity(po);
    }

    /**
     * 更新实体。
     */
    @Override
    public AgentRegistryEntity update(AgentRegistryEntity entity) {
        entity.validate();
        AgentRegistryPO po = toPO(entity);
        agentRegistryDao.update(po);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return agentRegistryDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public AgentRegistryEntity findById(Long id) {
        AgentRegistryPO po = agentRegistryDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按 Key 查询。
     */
    @Override
    public AgentRegistryEntity findByKey(String key) {
        AgentRegistryPO po = agentRegistryDao.selectByKey(key);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 查询全部。
     */
    @Override
    public List<AgentRegistryEntity> findAll() {
        return agentRegistryDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按启用状态查询。
     */
    @Override
    public List<AgentRegistryEntity> findByActive(Boolean isActive) {
        return agentRegistryDao.selectByActive(isActive).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按模型提供方查询。
     */
    @Override
    public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
        return agentRegistryDao.selectByModelProvider(modelProvider).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 检查 Key 是否存在。
     */
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
            entity.setModelOptions(jsonCodec.readMap(po.getModelOptions()));
        }
        entity.setBaseSystemPrompt(po.getBaseSystemPrompt());
        if (po.getAdvisorConfig() != null) {
            entity.setAdvisorConfig(jsonCodec.readMap(po.getAdvisorConfig()));
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
            po.setModelOptions(jsonCodec.writeValue(entity.getModelOptions()));
        }
        po.setBaseSystemPrompt(entity.getBaseSystemPrompt());
        if (entity.getAdvisorConfig() != null) {
            po.setAdvisorConfig(jsonCodec.writeValue(entity.getAdvisorConfig()));
        }

        po.setIsActive(entity.getIsActive());
        po.setCreatedAt(entity.getCreatedAt());
        po.setUpdatedAt(entity.getUpdatedAt());
        return po;
    }
}
