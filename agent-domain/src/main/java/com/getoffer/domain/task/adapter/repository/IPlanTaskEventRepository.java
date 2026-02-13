package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;

import java.util.Collections;
import java.util.List;

/**
 * Plan/Task 事件仓储接口。
 */
public interface IPlanTaskEventRepository {

    PlanTaskEventEntity save(PlanTaskEventEntity entity);

    List<PlanTaskEventEntity> findByPlanIdAfterEventId(Long planId, Long afterEventId, int limit);

    /**
     * 日志分页查询（DB 侧过滤 + 排序 + 分页）。
     */
    default List<PlanTaskEventEntity> findLogsPaged(List<Long> planIds,
                                                     Long taskId,
                                                     String level,
                                                     String traceId,
                                                     String keyword,
                                                     int offset,
                                                     int limit) {
        return Collections.emptyList();
    }

    /**
     * 日志分页计数（与 findLogsPaged 同条件）。
     */
    default long countLogs(List<Long> planIds,
                           Long taskId,
                           String level,
                           String traceId,
                           String keyword) {
        return 0L;
    }
}
