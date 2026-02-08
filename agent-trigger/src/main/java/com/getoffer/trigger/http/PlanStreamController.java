package com.getoffer.trigger.http;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 计划 SSE 增量流（事件驱动 + 断线回放）。
 */
@Slf4j
@RestController
@RequestMapping("/api/plans")
public class PlanStreamController {

    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final ConcurrentMap<Long, ConcurrentMap<String, SubscriberState>> subscribersByPlan;
    private final ExecutorService pushExecutor;
    private final int replayBatchSize;
    private final AtomicLong heartbeatTick;

    public PlanStreamController(IAgentPlanRepository agentPlanRepository,
                                IAgentTaskRepository agentTaskRepository,
                                PlanTaskEventPublisher planTaskEventPublisher) {
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.subscribersByPlan = new ConcurrentHashMap<>();
        this.pushExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "plan-stream-push");
            thread.setDaemon(true);
            return thread;
        });
        this.replayBatchSize = 200;
        this.heartbeatTick = new AtomicLong(0L);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPlan(@PathVariable("id") Long planId,
                                 @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam,
                                 @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        String subscriberId = UUID.randomUUID().toString();
        long cursor = resolveCursor(lastEventIdParam, lastEventIdHeader);
        SubscriberState state = new SubscriberState(emitter, cursor);
        subscribersByPlan.computeIfAbsent(planId, key -> new ConcurrentHashMap<>()).put(subscriberId, state);

        emitter.onCompletion(() -> removeSubscriber(planId, subscriberId));
        emitter.onTimeout(() -> removeSubscriber(planId, subscriberId));
        emitter.onError(ex -> removeSubscriber(planId, subscriberId));

        sendEvent(emitter, "StreamReady", Map.of("planId", planId, "lastEventId", cursor), null);
        sendPlanSnapshot(planId, emitter);
        subscribeRealtime(planId, subscriberId);
        replayMissedEvents(planId, subscriberId);
        return emitter;
    }

    private void subscribeRealtime(Long planId, String subscriberId) {
        planTaskEventPublisher.subscribe(planId, subscriberId, event -> {
            if (event == null) {
                return;
            }
            pushExecutor.execute(() -> deliverEvent(planId, subscriberId, event));
        });
    }

    private void replayMissedEvents(Long planId, String subscriberId) {
        SubscriberState subscriber = getSubscriber(planId, subscriberId);
        if (subscriber == null) {
            return;
        }
        while (true) {
            List<PlanTaskEventEntity> events;
            try {
                events = planTaskEventPublisher.replay(planId, subscriber.lastEventId.get(), replayBatchSize);
            } catch (Exception ex) {
                log.warn("Replay plan events failed. planId={}, subscriberId={}, error={}",
                        planId, subscriberId, ex.getMessage());
                return;
            }
            if (events == null || events.isEmpty()) {
                return;
            }
            for (PlanTaskEventEntity event : events) {
                deliverEvent(planId, subscriberId, event);
            }
            if (events.size() < replayBatchSize) {
                return;
            }
        }
    }

    private void deliverEvent(Long planId, String subscriberId, PlanTaskEventEntity event) {
        SubscriberState subscriber = getSubscriber(planId, subscriberId);
        if (subscriber == null || event == null || event.getId() == null) {
            return;
        }
        long eventId = event.getId();
        if (eventId <= subscriber.lastEventId.get()) {
            return;
        }
        if (!sendEvent(subscriber.emitter,
                event.getEventType() == null ? "PlanEvent" : event.getEventType().getEventName(),
                event.getEventData(),
                eventId)) {
            removeSubscriber(planId, subscriberId);
            return;
        }
        subscriber.lastEventId.updateAndGet(previous -> Math.max(previous, eventId));
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval-ms:10000}", scheduler = "daemonScheduler")
    public void emitHeartbeat() {
        long tick = heartbeatTick.incrementAndGet();
        if (tick % 1 != 0 || subscribersByPlan.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, ConcurrentMap<String, SubscriberState>> entry : subscribersByPlan.entrySet()) {
            Long planId = entry.getKey();
            ConcurrentMap<String, SubscriberState> subscribers = entry.getValue();
            if (subscribers == null || subscribers.isEmpty()) {
                subscribersByPlan.remove(planId, subscribers);
                continue;
            }
            for (Map.Entry<String, SubscriberState> subscriberEntry : subscribers.entrySet()) {
                if (!sendEvent(subscriberEntry.getValue().emitter, "Heartbeat", Map.of("planId", planId), null)) {
                    removeSubscriber(planId, subscriberEntry.getKey());
                }
            }
        }
    }

    private void sendPlanSnapshot(Long planId, SseEmitter emitter) {
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            sendEvent(emitter, "PlanFinished", Map.of("planId", planId, "status", "NOT_FOUND"), null);
            return;
        }
        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(planId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", planId);
        payload.put("planStatus", plan.getStatus() == null ? null : plan.getStatus().name());
        payload.put("taskCount", tasks == null ? 0 : tasks.size());
        sendEvent(emitter, "PlanSnapshot", payload, null);
    }

    private long resolveCursor(Long lastEventIdParam, String lastEventIdHeader) {
        if (lastEventIdParam != null && lastEventIdParam > 0) {
            return lastEventIdParam;
        }
        if (lastEventIdHeader == null) {
            return 0L;
        }
        try {
            long cursor = Long.parseLong(lastEventIdHeader.trim());
            return Math.max(cursor, 0L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void removeSubscriber(Long planId, String subscriberId) {
        ConcurrentMap<String, SubscriberState> subscribers = subscribersByPlan.get(planId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(subscriberId);
        if (subscribers.isEmpty()) {
            subscribersByPlan.remove(planId, subscribers);
        }
        planTaskEventPublisher.unsubscribe(planId, subscriberId);
    }

    private SubscriberState getSubscriber(Long planId, String subscriberId) {
        ConcurrentMap<String, SubscriberState> subscribers = subscribersByPlan.get(planId);
        if (subscribers == null) {
            return null;
        }
        return subscribers.get(subscriberId);
    }

    private boolean sendEvent(SseEmitter emitter, String name, Object data, Long eventId) {
        if (emitter == null) {
            return false;
        }
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(name).data(data);
            if (eventId != null) {
                builder.id(String.valueOf(eventId));
            }
            emitter.send(builder);
            return true;
        } catch (IOException ex) {
            log.debug("SSE send failed: {}", ex.getMessage());
            return false;
        }
    }

    private static final class SubscriberState {
        private final SseEmitter emitter;
        private final AtomicLong lastEventId;

        private SubscriberState(SseEmitter emitter, long lastEventId) {
            this.emitter = emitter;
            this.lastEventId = new AtomicLong(Math.max(lastEventId, 0L));
        }
    }

    @PreDestroy
    public void shutdown() {
        pushExecutor.shutdownNow();
    }
}
