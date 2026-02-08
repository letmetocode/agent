package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;

import java.util.List;

/**
 * Plan/Task 事件仓储接口。
 */
public interface IPlanTaskEventRepository {

    PlanTaskEventEntity save(PlanTaskEventEntity entity);

    List<PlanTaskEventEntity> findByPlanIdAfterEventId(Long planId, Long afterEventId, int limit);
}
