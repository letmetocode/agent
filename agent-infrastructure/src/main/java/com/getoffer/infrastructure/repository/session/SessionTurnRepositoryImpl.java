package com.getoffer.infrastructure.repository.session;

import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.infrastructure.dao.SessionTurnDao;
import com.getoffer.infrastructure.dao.po.SessionTurnPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.TurnStatusEnum;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话回合仓储实现。
 */
@Repository
public class SessionTurnRepositoryImpl implements ISessionTurnRepository {

    private final SessionTurnDao sessionTurnDao;
    private final JsonCodec jsonCodec;

    public SessionTurnRepositoryImpl(SessionTurnDao sessionTurnDao, JsonCodec jsonCodec) {
        this.sessionTurnDao = sessionTurnDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public SessionTurnEntity save(SessionTurnEntity entity) {
        entity.validate();
        SessionTurnPO po = toPO(entity);
        sessionTurnDao.insert(po);
        return toEntity(po);
    }

    @Override
    public SessionTurnEntity update(SessionTurnEntity entity) {
        entity.validate();
        SessionTurnPO po = toPO(entity);
        sessionTurnDao.update(po);
        return toEntity(po);
    }

    @Override
    public SessionTurnEntity findById(Long id) {
        SessionTurnPO po = sessionTurnDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    @Override
    public SessionTurnEntity findByPlanId(Long planId) {
        SessionTurnPO po = sessionTurnDao.selectByPlanId(planId);
        return po == null ? null : toEntity(po);
    }

    @Override
    public List<SessionTurnEntity> findBySessionId(Long sessionId) {
        List<SessionTurnPO> list = sessionTurnDao.selectBySessionId(sessionId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public SessionTurnEntity findLatestBySessionIdAndStatus(Long sessionId, TurnStatusEnum status) {
        SessionTurnPO po = sessionTurnDao.selectLatestBySessionIdAndStatus(sessionId, status);
        return po == null ? null : toEntity(po);
    }

    private SessionTurnEntity toEntity(SessionTurnPO po) {
        if (po == null) {
            return null;
        }
        SessionTurnEntity entity = new SessionTurnEntity();
        entity.setId(po.getId());
        entity.setSessionId(po.getSessionId());
        entity.setPlanId(po.getPlanId());
        entity.setUserMessage(po.getUserMessage());
        entity.setStatus(po.getStatus());
        entity.setFinalResponseMessageId(po.getFinalResponseMessageId());
        entity.setAssistantSummary(po.getAssistantSummary());
        if (po.getMetadata() != null) {
            entity.setMetadata(jsonCodec.readMap(po.getMetadata()));
        }
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        entity.setCompletedAt(po.getCompletedAt());
        return entity;
    }

    private SessionTurnPO toPO(SessionTurnEntity entity) {
        if (entity == null) {
            return null;
        }
        SessionTurnPO po = SessionTurnPO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .planId(entity.getPlanId())
                .userMessage(entity.getUserMessage())
                .status(entity.getStatus())
                .finalResponseMessageId(entity.getFinalResponseMessageId())
                .assistantSummary(entity.getAssistantSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
        if (entity.getMetadata() != null) {
            po.setMetadata(jsonCodec.writeValue(entity.getMetadata()));
        }
        return po;
    }
}
