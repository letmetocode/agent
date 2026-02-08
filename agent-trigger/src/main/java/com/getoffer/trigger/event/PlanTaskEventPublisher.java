package com.getoffer.trigger.event;

import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Plan/Task 事件发布器：持久化 + 进程内实时分发。
 */
@Slf4j
@Component
public class PlanTaskEventPublisher {

    private final IPlanTaskEventRepository planTaskEventRepository;
    private final ConcurrentMap<Long, ConcurrentMap<String, Consumer<PlanTaskEventEntity>>> subscribersByPlan;

    public PlanTaskEventPublisher(IPlanTaskEventRepository planTaskEventRepository) {
        this.planTaskEventRepository = planTaskEventRepository;
        this.subscribersByPlan = new ConcurrentHashMap<>();
    }

    public PlanTaskEventEntity publish(PlanTaskEventTypeEnum eventType,
                                       Long planId,
                                       Long taskId,
                                       Map<String, Object> eventData) {
        if (eventType == null || planId == null) {
            return null;
        }
        PlanTaskEventEntity event = new PlanTaskEventEntity();
        event.setPlanId(planId);
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setEventData(eventData == null ? Collections.emptyMap() : eventData);
        PlanTaskEventEntity saved = planTaskEventRepository.save(event);
        dispatch(saved);
        return saved;
    }

    public List<PlanTaskEventEntity> replay(Long planId, Long afterEventId, int limit) {
        return planTaskEventRepository.findByPlanIdAfterEventId(planId, afterEventId, limit);
    }

    public void subscribe(Long planId, String subscriberId, Consumer<PlanTaskEventEntity> consumer) {
        if (planId == null || subscriberId == null || consumer == null) {
            return;
        }
        subscribersByPlan.computeIfAbsent(planId, key -> new ConcurrentHashMap<>()).put(subscriberId, consumer);
    }

    public void unsubscribe(Long planId, String subscriberId) {
        if (planId == null || subscriberId == null) {
            return;
        }
        ConcurrentMap<String, Consumer<PlanTaskEventEntity>> subscribers = subscribersByPlan.get(planId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(subscriberId);
        if (subscribers.isEmpty()) {
            subscribersByPlan.remove(planId, subscribers);
        }
    }

    private void dispatch(PlanTaskEventEntity event) {
        if (event == null || event.getPlanId() == null) {
            return;
        }
        ConcurrentMap<String, Consumer<PlanTaskEventEntity>> subscribers = subscribersByPlan.get(event.getPlanId());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Consumer<PlanTaskEventEntity>> entry : subscribers.entrySet()) {
            try {
                entry.getValue().accept(event);
            } catch (Exception ex) {
                log.debug("Plan event dispatch failed. planId={}, subscriberId={}, eventId={}, error={}",
                        event.getPlanId(), entry.getKey(), event.getId(), ex.getMessage());
            }
        }
    }
}
