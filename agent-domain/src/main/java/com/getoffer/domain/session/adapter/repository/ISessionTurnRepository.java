package com.getoffer.domain.session.adapter.repository;

import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.types.enums.TurnStatusEnum;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 会话回合仓储接口。
 */
public interface ISessionTurnRepository {

    SessionTurnEntity save(SessionTurnEntity entity);

    SessionTurnEntity update(SessionTurnEntity entity);

    SessionTurnEntity findById(Long id);

    SessionTurnEntity findByPlanId(Long planId);

    SessionTurnEntity findBySessionIdAndClientMessageId(Long sessionId, String clientMessageId);

    boolean markTerminalIfNotTerminal(Long turnId,
                                      TurnStatusEnum status,
                                      String assistantSummary,
                                      LocalDateTime completedAt);

    boolean bindFinalResponseMessage(Long turnId, Long messageId);

    List<SessionTurnEntity> findBySessionId(Long sessionId);

    SessionTurnEntity findLatestBySessionIdAndStatus(Long sessionId, TurnStatusEnum status);
}
