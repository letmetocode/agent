package com.getoffer.infrastructure.repository.session;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.infrastructure.dao.SessionMessageDao;
import com.getoffer.infrastructure.dao.po.SessionMessagePO;
import com.getoffer.infrastructure.util.JsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话消息仓储实现。
 */
@Repository
@Slf4j
public class SessionMessageRepositoryImpl implements ISessionMessageRepository {

    private final SessionMessageDao sessionMessageDao;
    private final JsonCodec jsonCodec;

    public SessionMessageRepositoryImpl(SessionMessageDao sessionMessageDao, JsonCodec jsonCodec) {
        this.sessionMessageDao = sessionMessageDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public SessionMessageEntity save(SessionMessageEntity entity) {
        entity.validate();
        SessionMessagePO po = toPO(entity);
        sessionMessageDao.insert(po);
        return toEntity(po);
    }

    @Override
    public SessionMessageEntity saveAssistantFinalMessageIfAbsent(SessionMessageEntity entity) {
        entity.validate();
        SessionMessagePO existed = sessionMessageDao.selectFinalAssistantByTurnId(entity.getTurnId());
        if (existed != null) {
            return toEntity(existed);
        }
        SessionMessagePO po = toPO(entity);
        try {
            sessionMessageDao.insert(po);
            return toEntity(po);
        } catch (RuntimeException ex) {
            SessionMessagePO latest = sessionMessageDao.selectLatestAssistantByTurnId(entity.getTurnId());
            if (latest != null) {
                log.info("Session assistant final message deduplicated by unique index. turnId={}, messageId={}",
                        entity.getTurnId(), latest.getId());
                return toEntity(latest);
            }
            throw ex;
        }
    }

    @Override
    public SessionMessageEntity findById(Long id) {
        SessionMessagePO po = sessionMessageDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    @Override
    public List<SessionMessageEntity> findByTurnId(Long turnId) {
        List<SessionMessagePO> list = sessionMessageDao.selectByTurnId(turnId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<SessionMessageEntity> findBySessionId(Long sessionId) {
        List<SessionMessagePO> list = sessionMessageDao.selectBySessionId(sessionId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private SessionMessageEntity toEntity(SessionMessagePO po) {
        if (po == null) {
            return null;
        }
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setId(po.getId());
        entity.setSessionId(po.getSessionId());
        entity.setTurnId(po.getTurnId());
        entity.setRole(po.getRole());
        entity.setContent(po.getContent());
        if (po.getMetadata() != null) {
            entity.setMetadata(jsonCodec.readMap(po.getMetadata()));
        }
        entity.setCreatedAt(po.getCreatedAt());
        return entity;
    }

    private SessionMessagePO toPO(SessionMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        SessionMessagePO po = SessionMessagePO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .turnId(entity.getTurnId())
                .role(entity.getRole())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
        if (entity.getMetadata() != null) {
            po.setMetadata(jsonCodec.writeValue(entity.getMetadata()));
        }
        return po;
    }
}
