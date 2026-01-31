package com.getoffer.infrastructure.repository.session;

import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.infrastructure.dao.AgentSessionDao;
import com.getoffer.infrastructure.dao.po.AgentSessionPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户会话仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class AgentSessionRepositoryImpl implements IAgentSessionRepository {

    private final AgentSessionDao agentSessionDao;

    public AgentSessionRepositoryImpl(AgentSessionDao agentSessionDao) {
        this.agentSessionDao = agentSessionDao;
    }

    @Override
    public AgentSessionEntity save(AgentSessionEntity entity) {
        entity.validate();
        AgentSessionPO po = toPO(entity);
        agentSessionDao.insert(po);
        return toEntity(po);
    }

    @Override
    public AgentSessionEntity update(AgentSessionEntity entity) {
        entity.validate();
        AgentSessionPO po = toPO(entity);
        agentSessionDao.update(po);
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return agentSessionDao.deleteById(id) > 0;
    }

    @Override
    public AgentSessionEntity findById(Long id) {
        AgentSessionPO po = agentSessionDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<AgentSessionEntity> findByUserId(String userId) {
        return agentSessionDao.selectByUserId(userId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentSessionEntity> findActiveByUserId(String userId) {
        return agentSessionDao.selectActiveByUserId(userId).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentSessionEntity> findAll() {
        return agentSessionDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentSessionEntity> findByActive(Boolean isActive) {
        return agentSessionDao.selectByActive(isActive).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

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
            entity.setMetaInfo(com.alibaba.fastjson2.JSON.parseObject(po.getMetaInfo()));
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
            po.setMetaInfo(com.alibaba.fastjson2.JSON.toJSONString(entity.getMetaInfo()));
        }

        return po;
    }
}
