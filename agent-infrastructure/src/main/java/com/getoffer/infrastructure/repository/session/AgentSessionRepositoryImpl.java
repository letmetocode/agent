package com.getoffer.infrastructure.repository.session;

import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.infrastructure.dao.AgentSessionDao;
import com.getoffer.infrastructure.dao.po.AgentSessionPO;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户会话仓储实现类。
 * <p>
 * 负责用户会话的持久化操作，包括：
 * <ul>
 *   <li>会话的增删改查</li>
 *   <li>按用户ID查询（全部或仅活跃）</li>
 *   <li>批量关闭活跃会话</li>
 *   <li>Entity与PO之间的相互转换</li>
 *   <li>JSONB字段（metaInfo）的序列化/反序列化</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentSessionRepositoryImpl implements IAgentSessionRepository {

    private final AgentSessionDao agentSessionDao;
    private final JsonCodec jsonCodec;

    /**
     * 创建 AgentSessionRepositoryImpl。
     */
    public AgentSessionRepositoryImpl(AgentSessionDao agentSessionDao, JsonCodec jsonCodec) {
        this.agentSessionDao = agentSessionDao;
        this.jsonCodec = jsonCodec;
    }

    /**
     * 保存实体。
     */
    @Override
    public AgentSessionEntity save(AgentSessionEntity entity) {
        entity.validate();
        AgentSessionPO po = toPO(entity);
        agentSessionDao.insert(po);
        return toEntity(po);
    }

    /**
     * 更新实体。
     */
    @Override
    public AgentSessionEntity update(AgentSessionEntity entity) {
        entity.validate();
        AgentSessionPO po = toPO(entity);
        agentSessionDao.update(po);
        return toEntity(po);
    }

    /**
     * 按 ID 删除。
     */
    @Override
    public boolean deleteById(Long id) {
        return agentSessionDao.deleteById(id) > 0;
    }

    /**
     * 按 ID 查询。
     */
    @Override
    public AgentSessionEntity findById(Long id) {
        AgentSessionPO po = agentSessionDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    /**
     * 按用户 ID 查询。
     */
    @Override
    public List<AgentSessionEntity> findByUserId(String userId) {
        return agentSessionDao.selectByUserId(userId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按用户 ID 查询活跃记录。
     */
    @Override
    public List<AgentSessionEntity> findActiveByUserId(String userId) {
        return agentSessionDao.selectActiveByUserId(userId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询全部。
     */
    @Override
    public List<AgentSessionEntity> findAll() {
        return agentSessionDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按启用状态查询。
     */
    @Override
    public List<AgentSessionEntity> findByActive(Boolean isActive) {
        return agentSessionDao.selectByActive(isActive).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    /**
     * 按用户 ID 关闭活跃会话。
     */
    @Override
    public boolean closeActiveSessionsByUserId(String userId) {
        return agentSessionDao.closeActiveSessionsByUserId(userId) > 0;
    }

    /**
     * PO 转换为 Entity
     */
    private AgentSessionEntity toEntity(AgentSessionPO po) {
        if (po == null) {
            return null;
        }
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setId(po.getId());
        entity.setUserId(po.getUserId());
        entity.setTitle(po.getTitle());
        entity.setIsActive(po.getIsActive());

        // JSONB 字段转换
        if (po.getMetaInfo() != null) {
            entity.setMetaInfo(jsonCodec.readMap(po.getMetaInfo()));
        }

        entity.setCreatedAt(po.getCreatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private AgentSessionPO toPO(AgentSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        AgentSessionPO po = AgentSessionPO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getMetaInfo() != null) {
            po.setMetaInfo(jsonCodec.writeValue(entity.getMetaInfo()));
        }

        return po;
    }
}
