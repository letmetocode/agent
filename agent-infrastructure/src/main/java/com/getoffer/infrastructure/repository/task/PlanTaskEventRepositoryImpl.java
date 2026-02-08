package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.infrastructure.dao.PlanTaskEventDao;
import com.getoffer.infrastructure.dao.po.PlanTaskEventPO;
import com.getoffer.infrastructure.util.JsonCodec;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plan/Task 事件仓储实现。
 */
@Repository
public class PlanTaskEventRepositoryImpl implements IPlanTaskEventRepository {

    private final PlanTaskEventDao planTaskEventDao;
    private final JsonCodec jsonCodec;

    public PlanTaskEventRepositoryImpl(PlanTaskEventDao planTaskEventDao, JsonCodec jsonCodec) {
        this.planTaskEventDao = planTaskEventDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public PlanTaskEventEntity save(PlanTaskEventEntity entity) {
        entity.validate();
        PlanTaskEventPO po = toPO(entity);
        planTaskEventDao.insert(po);
        return toEntity(po);
    }

    @Override
    public List<PlanTaskEventEntity> findByPlanIdAfterEventId(Long planId, Long afterEventId, int limit) {
        if (planId == null || limit <= 0) {
            return Collections.emptyList();
        }
        long cursor = afterEventId == null ? 0L : Math.max(afterEventId, 0L);
        List<PlanTaskEventPO> events = planTaskEventDao.selectByPlanIdAfterEventId(planId, cursor, limit);
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return events.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private PlanTaskEventEntity toEntity(PlanTaskEventPO po) {
        if (po == null) {
            return null;
        }
        PlanTaskEventEntity entity = new PlanTaskEventEntity();
        entity.setId(po.getId());
        entity.setPlanId(po.getPlanId());
        entity.setTaskId(po.getTaskId());
        entity.setEventType(po.getEventType());
        entity.setCreatedAt(po.getCreatedAt());
        if (po.getEventData() != null) {
            entity.setEventData(jsonCodec.readMap(po.getEventData()));
        }
        return entity;
    }

    private PlanTaskEventPO toPO(PlanTaskEventEntity entity) {
        if (entity == null) {
            return null;
        }
        PlanTaskEventPO po = PlanTaskEventPO.builder()
                .id(entity.getId())
                .planId(entity.getPlanId())
                .taskId(entity.getTaskId())
                .eventType(entity.getEventType())
                .createdAt(entity.getCreatedAt())
                .build();
        if (entity.getEventData() != null) {
            po.setEventData(jsonCodec.writeValue(entity.getEventData()));
        }
        return po;
    }
}
