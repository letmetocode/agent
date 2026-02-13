package com.getoffer.domain.task.model.entity;

import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Plan/Task 增量事件。
 */
@Data
public class PlanTaskEventEntity {

    private Long id;
    private Long planId;
    private Long taskId;
    private PlanTaskEventTypeEnum eventType;
    private Map<String, Object> eventData;
    private LocalDateTime createdAt;

    public void validate() {
        if (planId == null) {
            throw new IllegalStateException("Plan ID cannot be null");
        }
        if (eventType == null) {
            throw new IllegalStateException("Event type cannot be null");
        }
    }

    public boolean isPlanFinishedEvent() {
        return eventType == PlanTaskEventTypeEnum.PLAN_FINISHED;
    }

    public static PlanTaskEventEntity create(Long planId,
                                             Long taskId,
                                             PlanTaskEventTypeEnum eventType,
                                             Map<String, Object> eventData) {
        PlanTaskEventEntity entity = new PlanTaskEventEntity();
        entity.setPlanId(planId);
        entity.setTaskId(taskId);
        entity.setEventType(eventType);
        entity.setEventData(eventData);
        entity.validate();
        return entity;
    }
}
