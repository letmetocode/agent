package com.getoffer.types.enums;

/**
 * Plan/Task 事件类型（SSE 增量流）。
 */
public enum PlanTaskEventTypeEnum {

    TASK_STARTED("TaskStarted"),
    TASK_COMPLETED("TaskCompleted"),
    TASK_LOG("TaskLog"),
    PLAN_FINISHED("PlanFinished");

    private final String eventName;

    PlanTaskEventTypeEnum(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
