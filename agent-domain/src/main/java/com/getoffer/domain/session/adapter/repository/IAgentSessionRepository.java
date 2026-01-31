package com.getoffer.domain.session.adapter.repository;

import com.getoffer.domain.session.model.entity.AgentSessionEntity;

import java.util.List;

/**
 * 用户会话仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentSessionRepository {

    /**
     * 保存会话
     */
    AgentSessionEntity save(AgentSessionEntity entity);

    /**
     * 更新会话
     */
    AgentSessionEntity update(AgentSessionEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentSessionEntity findById(Long id);

    /**
     * 根据用户 ID 查询
     */
    List<AgentSessionEntity> findByUserId(String userId);

    /**
     * 根据用户 ID 查询活跃会话
     */
    List<AgentSessionEntity> findActiveByUserId(String userId);

    /**
     * 查询所有会话
     */
    List<AgentSessionEntity> findAll();

    /**
     * 根据激活状态查询
     */
    List<AgentSessionEntity> findByActive(Boolean isActive);

    /**
     * 关闭用户的所有活跃会话
     */
    boolean closeActiveSessionsByUserId(String userId);
}
