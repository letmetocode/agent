package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.infrastructure.dao.RoutingDecisionDao;
import com.getoffer.infrastructure.dao.po.RoutingDecisionPO;
import com.getoffer.infrastructure.util.JsonCodec;
import org.springframework.stereotype.Repository;

/**
 * 路由决策仓储实现。
 */
@Repository
public class RoutingDecisionRepositoryImpl implements IRoutingDecisionRepository {

    private final RoutingDecisionDao routingDecisionDao;
    private final JsonCodec jsonCodec;

    public RoutingDecisionRepositoryImpl(RoutingDecisionDao routingDecisionDao,
                                         JsonCodec jsonCodec) {
        this.routingDecisionDao = routingDecisionDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public RoutingDecisionEntity save(RoutingDecisionEntity entity) {
        entity.validate();
        RoutingDecisionPO po = toPO(entity);
        routingDecisionDao.insert(po);
        return toEntity(po);
    }

    @Override
    public RoutingDecisionEntity findById(Long id) {
        RoutingDecisionPO po = routingDecisionDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    private RoutingDecisionEntity toEntity(RoutingDecisionPO po) {
        RoutingDecisionEntity entity = new RoutingDecisionEntity();
        entity.setId(po.getId());
        entity.setSessionId(po.getSessionId());
        entity.setTurnId(po.getTurnId());
        entity.setDecisionType(po.getDecisionType());
        entity.setStrategy(po.getStrategy());
        entity.setScore(po.getScore());
        entity.setReason(po.getReason());
        entity.setDefinitionId(po.getDefinitionId());
        entity.setDefinitionKey(po.getDefinitionKey());
        entity.setDefinitionVersion(po.getDefinitionVersion());
        entity.setDraftId(po.getDraftId());
        entity.setDraftKey(po.getDraftKey());
        entity.setMetadata(jsonCodec.readMap(po.getMetadata()));
        entity.setCreatedAt(po.getCreatedAt());
        return entity;
    }

    private RoutingDecisionPO toPO(RoutingDecisionEntity entity) {
        RoutingDecisionPO po = RoutingDecisionPO.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .turnId(entity.getTurnId())
                .decisionType(entity.getDecisionType())
                .strategy(entity.getStrategy())
                .score(entity.getScore())
                .reason(entity.getReason())
                .definitionId(entity.getDefinitionId())
                .definitionKey(entity.getDefinitionKey())
                .definitionVersion(entity.getDefinitionVersion())
                .draftId(entity.getDraftId())
                .draftKey(entity.getDraftKey())
                .createdAt(entity.getCreatedAt())
                .build();
        po.setMetadata(jsonCodec.writeValue(entity.getMetadata()));
        return po;
    }
}

