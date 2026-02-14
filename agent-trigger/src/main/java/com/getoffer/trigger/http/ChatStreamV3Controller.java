package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatStreamEventV3DTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.trigger.application.sse.ChatSseEventMapper;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V3 Chat SSE：将底层 PlanTask 事件映射为聊天语义事件。
 */
@Slf4j
@RestController
@RequestMapping("/api/v3/chat/sessions")
public class ChatStreamV3Controller {

    private static final long SSE_RECONNECT_TIME_MS = 1500L;

    private final IAgentSessionRepository agentSessionRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final PlanTaskEventPublisher planTaskEventPublisher;
    private final ChatSseEventMapper chatSseEventMapper;

    private final ConcurrentMap<String, StreamSubscriber> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<String, StreamSubscriber>> subscribersByPlan = new ConcurrentHashMap<>();

    @Value("${sse.replay.batch-size:200}")
    private int replayBatchSize;

    @Value("${sse.replay.max-batches-per-sweep:1}")
    private int replayMaxBatchesPerSweep;

    public ChatStreamV3Controller(IAgentSessionRepository agentSessionRepository,
                                  IAgentPlanRepository agentPlanRepository,
                                  ISessionTurnRepository sessionTurnRepository,
                                  ISessionMessageRepository sessionMessageRepository,
                                  PlanTaskEventPublisher planTaskEventPublisher,
                                  ChatSseEventMapper chatSseEventMapper) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.planTaskEventPublisher = planTaskEventPublisher;
        this.chatSseEventMapper = chatSseEventMapper;
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") Long sessionId,
                             @RequestParam(value = "planId", required = false) Long planIdParam,
                             @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
                             HttpServletResponse response) {
        AgentSessionEntity session = validateSession(sessionId);
        Long planId = resolvePlanId(session.getId(), planIdParam);
        if (planId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话暂无可订阅计划，请先发送一条消息");
        }

        Long turnId = resolveTurnId(planId);
        long cursor = resolveCursor(lastEventIdParam, lastEventIdHeader);

        applySseResponseHeaders(response);

        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        String subscriberId = UUID.randomUUID().toString();
        StreamSubscriber subscriber = new StreamSubscriber(subscriberId, sessionId, planId, turnId, emitter, cursor);
        subscribers.put(subscriberId, subscriber);
        subscribersByPlan.computeIfAbsent(planId, key -> new ConcurrentHashMap<>()).put(subscriberId, subscriber);
        log.info("CHAT_V3_STREAM_SUBSCRIBED sessionId={}, planId={}, subscriberId={}, cursor={}",
                sessionId, planId, subscriberId, cursor);

        emitter.onCompletion(() -> removeSubscriber(subscriber));
        emitter.onTimeout(() -> removeSubscriber(subscriber));
        emitter.onError(ex -> {
            log.debug("CHAT_V3_STREAM_EMITTER_ERROR sessionId={}, planId={}, subscriberId={}, error={}",
                    sessionId, planId, subscriberId, ex == null ? "unknown" : ex.getMessage());
            removeSubscriber(subscriber);
        });

        if (cursor <= 0L) {
            sendSystemEvent(subscriber,
                    "message.accepted",
                    "消息已接收，正在执行中",
                    Map.of("sessionId", sessionId, "planId", planId));
            sendSystemEvent(subscriber,
                    "planning.started",
                    "已进入规划与任务编排阶段",
                    Collections.emptyMap());
        }

        replayMissedEvents(subscriber);
        subscribeRealtime(subscriber);

        return emitter;
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat-interval-ms:10000}", scheduler = "daemonScheduler")
    public void emitHeartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (StreamSubscriber subscriber : subscribers.values()) {
            sendSystemEvent(subscriber,
                    "stream.heartbeat",
                    "heartbeat",
                    Map.of("planId", subscriber.planId, "sessionId", subscriber.sessionId));
        }
    }

    @Scheduled(fixedDelayString = "${sse.replay-interval-ms:3000}", scheduler = "daemonScheduler")
    public void sweepReplayMissedEvents() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (StreamSubscriber subscriber : subscribers.values()) {
            replayMissedEvents(subscriber);
        }
    }

    private void applySseResponseHeaders(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }

    private AgentSessionEntity validateSession(Long sessionId) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
        }
        return session;
    }

    private Long resolvePlanId(Long sessionId, Long planIdParam) {
        if (planIdParam != null) {
            AgentPlanEntity plan = agentPlanRepository.findById(planIdParam);
            if (plan == null || !sessionId.equals(plan.getSessionId())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "planId 不存在或不属于当前会话");
            }
            return plan.getId();
        }

        List<AgentPlanEntity> plans = agentPlanRepository.findBySessionId(sessionId);
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(item -> item != null && item.getId() != null)
                .sorted(Comparator.comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentPlanEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AgentPlanEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveTurnId(Long planId) {
        SessionTurnEntity turn = sessionTurnRepository.findByPlanId(planId);
        return turn == null ? null : turn.getId();
    }

    private void replayMissedEvents(StreamSubscriber subscriber) {
        if (subscriber == null || !subscribers.containsKey(subscriber.subscriberId)) {
            return;
        }
        int batchSize = Math.max(1, replayBatchSize);
        int maxBatches = Math.max(1, replayMaxBatchesPerSweep);
        long cursor = Math.max(subscriber.lastEventId.get(), 0L);

        for (int batchIndex = 0; batchIndex < maxBatches; batchIndex++) {
            List<PlanTaskEventEntity> events = planTaskEventPublisher.replay(subscriber.planId, cursor, batchSize);
            if (events == null || events.isEmpty()) {
                return;
            }
            for (PlanTaskEventEntity event : events) {
                deliverPlanEvent(subscriber, event);
                if (!subscribers.containsKey(subscriber.subscriberId)) {
                    return;
                }
            }

            PlanTaskEventEntity latest = events.get(events.size() - 1);
            if (latest == null || latest.getId() == null) {
                return;
            }
            cursor = Math.max(cursor, latest.getId());
            if (events.size() < batchSize) {
                return;
            }
        }
    }

    private void subscribeRealtime(StreamSubscriber subscriber) {
        planTaskEventPublisher.subscribe(subscriber.planId, subscriber.subscriberId, event -> deliverPlanEvent(subscriber, event));
    }

    private void deliverPlanEvent(StreamSubscriber subscriber, PlanTaskEventEntity event) {
        if (subscriber == null || event == null || event.getId() == null) {
            return;
        }
        synchronized (subscriber) {
            if (event.getId() <= subscriber.lastEventId.get()) {
                return;
            }
            try {
                if (event.getEventType() == PlanTaskEventTypeEnum.PLAN_FINISHED) {
                    sendPlanFinishedEvents(subscriber, event);
                } else {
                    ChatStreamEventV3DTO payload = mapTaskEvent(subscriber, event);
                    if (!sendEvent(subscriber, payload, event.getId())) {
                        removeSubscriber(subscriber);
                        return;
                    }
                }
                subscriber.lastEventId.updateAndGet(previous -> Math.max(previous, event.getId()));
            } catch (Exception ex) {
                log.warn("CHAT_V3_STREAM_EVENT_FAILED sessionId={}, planId={}, subscriberId={}, eventId={}, error={}",
                        subscriber.sessionId,
                        subscriber.planId,
                        subscriber.subscriberId,
                        event.getId(),
                        ex.getMessage());
                sendSystemEvent(subscriber, "stream.error", ex.getMessage(), Map.of("eventId", event.getId()));
            }
        }
    }

    private void sendPlanFinishedEvents(StreamSubscriber subscriber, PlanTaskEventEntity event) {
        Map<String, Object> rawEventData = event.getEventData() == null ? Collections.emptyMap() : event.getEventData();
        Map<String, Object> eventData = chatSseEventMapper.normalizeMetadata(rawEventData);

        ChatStreamEventV3DTO finalizing = new ChatStreamEventV3DTO();
        finalizing.setType("answer.finalizing");
        finalizing.setSessionId(subscriber.sessionId);
        finalizing.setPlanId(subscriber.planId);
        finalizing.setTurnId(resolveTurnIdFromEvent(eventData, subscriber.turnId));
        finalizing.setMessage(String.format("计划执行结束，状态=%s，正在汇总最终答案", String.valueOf(eventData.getOrDefault("status", "UNKNOWN"))));
        finalizing.setMetadata(eventData);
        if (!sendEvent(subscriber, finalizing, null)) {
            removeSubscriber(subscriber);
            return;
        }

        Long turnId = resolveTurnIdFromEvent(eventData, subscriber.turnId);

        ChatStreamEventV3DTO answer = new ChatStreamEventV3DTO();
        answer.setType("answer.final");
        answer.setEventId(event.getId());
        answer.setSessionId(subscriber.sessionId);
        answer.setPlanId(subscriber.planId);
        answer.setTurnId(turnId);
        answer.setFinalAnswer(resolveFinalAnswer(eventData, subscriber.turnId));
        answer.setMetadata(eventData);
        if (!sendEvent(subscriber, answer, event.getId())) {
            removeSubscriber(subscriber);
            return;
        }

        ChatStreamEventV3DTO completed = new ChatStreamEventV3DTO();
        completed.setType("stream.completed");
        completed.setSessionId(subscriber.sessionId);
        completed.setPlanId(subscriber.planId);
        completed.setTurnId(turnId);
        completed.setMessage("流式输出已完成");
        completed.setMetadata(Map.of("reason", "PLAN_FINISHED", "eventId", event.getId()));
        if (!sendEvent(subscriber, completed, event.getId())) {
            removeSubscriber(subscriber);
            return;
        }

        try {
            subscriber.emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
        removeSubscriber(subscriber);
    }

    private ChatStreamEventV3DTO mapTaskEvent(StreamSubscriber subscriber, PlanTaskEventEntity event) {
        return chatSseEventMapper.mapTaskEvent(subscriber.sessionId, subscriber.planId, subscriber.turnId, event);
    }

    private Long resolveTurnIdFromEvent(Map<String, Object> eventData, Long fallbackTurnId) {
        return chatSseEventMapper.resolveTurnIdFromEvent(eventData, fallbackTurnId);
    }

    private String resolveFinalAnswer(Map<String, Object> eventData, Long fallbackTurnId) {
        return chatSseEventMapper.resolveFinalAnswer(eventData, fallbackTurnId);
    }

    private boolean sendEvent(StreamSubscriber subscriber, ChatStreamEventV3DTO payload, Long eventId) {
        if (subscriber == null || payload == null) {
            return false;
        }
        payload.setMetadata(enrichMetadata(payload.getMetadata()));
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(payload.getType())
                    .data(payload)
                    .reconnectTime(SSE_RECONNECT_TIME_MS);
            if (eventId != null) {
                builder.id(String.valueOf(eventId));
            }
            subscriber.emitter.send(builder);
            return true;
        } catch (IOException | RuntimeException ex) {
            log.debug("CHAT_V3_STREAM_SEND_FAILED sessionId={}, planId={}, subscriberId={}, eventType={}, eventId={}, error={}",
                    subscriber.sessionId,
                    subscriber.planId,
                    subscriber.subscriberId,
                    payload.getType(),
                    eventId,
                    ex.getMessage());
            return false;
        }
    }

    private void sendSystemEvent(StreamSubscriber subscriber,
                                 String type,
                                 String message,
                                 Map<String, Object> metadata) {
        ChatStreamEventV3DTO event = new ChatStreamEventV3DTO();
        event.setType(type);
        event.setSessionId(subscriber.sessionId);
        event.setPlanId(subscriber.planId);
        event.setTurnId(subscriber.turnId);
        event.setMessage(message);
        event.setMetadata(metadata == null ? Collections.emptyMap() : metadata);
        if (!sendEvent(subscriber, event, null)) {
            removeSubscriber(subscriber);
        }
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> metadata) {
        Map<String, Object> result = new HashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            result.putAll(metadata);
        }
        result.put("emittedAt", LocalDateTime.now().toString());
        return result;
    }

    private void removeSubscriber(StreamSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        StreamSubscriber removed = subscribers.remove(subscriber.subscriberId);
        ConcurrentMap<String, StreamSubscriber> planSubscribers = subscribersByPlan.get(subscriber.planId);
        if (planSubscribers != null) {
            planSubscribers.remove(subscriber.subscriberId);
            if (planSubscribers.isEmpty()) {
                subscribersByPlan.remove(subscriber.planId, planSubscribers);
            }
        }
        planTaskEventPublisher.unsubscribe(subscriber.planId, subscriber.subscriberId);
        if (removed != null) {
            log.info("CHAT_V3_STREAM_UNSUBSCRIBED sessionId={}, planId={}, subscriberId={}",
                    subscriber.sessionId,
                    subscriber.planId,
                    subscriber.subscriberId);
        }
    }

    private long resolveCursor(Long lastEventIdParam, String lastEventIdHeader) {
        long headerCursor = parseCursor(lastEventIdHeader);
        if (headerCursor > 0L) {
            return headerCursor;
        }
        if (lastEventIdParam == null) {
            return 0L;
        }
        return Math.max(lastEventIdParam, 0L);
    }

    private long parseCursor(String cursorText) {
        if (cursorText == null || cursorText.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(Long.parseLong(cursorText.trim()), 0L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static final class StreamSubscriber {
        private final String subscriberId;
        private final Long sessionId;
        private final Long planId;
        private final Long turnId;
        private final SseEmitter emitter;
        private final AtomicLong lastEventId;

        private StreamSubscriber(String subscriberId,
                                 Long sessionId,
                                 Long planId,
                                 Long turnId,
                                 SseEmitter emitter,
                                 long lastEventId) {
            this.subscriberId = subscriberId;
            this.sessionId = sessionId;
            this.planId = planId;
            this.turnId = turnId;
            this.emitter = emitter;
            this.lastEventId = new AtomicLong(Math.max(lastEventId, 0L));
        }
    }
}
