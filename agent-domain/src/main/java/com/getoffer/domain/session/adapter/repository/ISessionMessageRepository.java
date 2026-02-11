package com.getoffer.domain.session.adapter.repository;

import com.getoffer.domain.session.model.entity.SessionMessageEntity;

import java.util.List;

/**
 * 会话消息仓储接口。
 */
public interface ISessionMessageRepository {

    SessionMessageEntity save(SessionMessageEntity entity);

    SessionMessageEntity findById(Long id);

    List<SessionMessageEntity> findByTurnId(Long turnId);

    List<SessionMessageEntity> findBySessionId(Long sessionId);
}
