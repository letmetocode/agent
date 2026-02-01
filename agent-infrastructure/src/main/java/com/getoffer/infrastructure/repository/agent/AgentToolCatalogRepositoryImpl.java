package com.getoffer.infrastructure.repository.agent;

import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.infrastructure.dao.AgentToolCatalogDao;
import com.getoffer.infrastructure.dao.po.AgentToolCatalogPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ToolTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具目录仓储实现类。
 * <p>
 * 负责工具目录的持久化操作，包括：
 * <ul>
 *   <li>工具配置的增删改查</li>
 *   <li>按类型、名称等条件查询</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（toolConfig、inputSchema、outputSchema）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentToolCatalogRepositoryImpl implements IAgentToolCatalogRepository {

    private final AgentToolCatalogDao agentToolCatalogDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 AgentToolCatalogRepositoryImpl。
     */
    public AgentToolCatalogRepositoryImpl(AgentToolCatalogDao agentToolCatalogDao, JsonCodec jsonCodec) {
        this.agentToolCatalogDao = agentToolCatalogDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public AgentToolCatalogEntity save(AgentToolCatalogEntity entity) {
        entity.validate();
        AgentToolCatalogPO po = toPO(entity);
        agentToolCatalogDao.insert(po);
        return toEntity(po);
    }

    /**
     * 更新实体。
     */
    @Override
    public AgentToolCatalogEntity update(AgentToolCatalogEntity entity) {
        entity.validate();
        AgentToolCatalogPO po = toPO(entity);
        agentToolCatalogDao.update(po);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return agentToolCatalogDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public AgentToolCatalogEntity findById(Long id) {
        AgentToolCatalogPO po = agentToolCatalogDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按名称查询。
     */
    @Override
    public AgentToolCatalogEntity findByName(String name) {
        AgentToolCatalogPO po = agentToolCatalogDao.selectByName(name);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 查询全部。
     */
    @Override
    public List<AgentToolCatalogEntity> findAll() {
        return agentToolCatalogDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按类型查询。
     */
    @Override
    public List<AgentToolCatalogEntity> findByType(ToolTypeEnum type) {
        return agentToolCatalogDao.selectByType(type).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 检查名称是否存在。
     */
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
            entity.setToolConfig(jsonCodec.readMap(po.getToolConfig()));
        }
        if (po.getInputSchema() != null) {
            entity.setInputSchema(jsonCodec.readMap(po.getInputSchema()));
        }
        if (po.getOutputSchema() != null) {
            entity.setOutputSchema(jsonCodec.readMap(po.getOutputSchema()));
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
            po.setToolConfig(jsonCodec.writeValue(entity.getToolConfig()));
        }
        if (entity.getInputSchema() != null) {
            po.setInputSchema(jsonCodec.writeValue(entity.getInputSchema()));
        }
        if (entity.getOutputSchema() != null) {
            po.setOutputSchema(jsonCodec.writeValue(entity.getOutputSchema()));
        }

        return po;
    }
}
