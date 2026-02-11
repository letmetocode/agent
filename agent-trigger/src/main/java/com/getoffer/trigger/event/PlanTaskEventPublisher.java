package com.getoffer.trigger.event;

import com.getoffer.domain.task.adapter.repository.IPlanTaskEventRepository;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Plan/Task 事件发布器：持久化 + 跨实例通知 + 进程内实时分发。
 */
@Slf4j
@Component
public class PlanTaskEventPublisher {

    private static final int LISTEN_TIMEOUT_MILLIS = 3000;
    private static final int RECONNECT_BACKOFF_MILLIS = 1000;
    private static final int LOAD_RETRY_TIMES = 3;
    private static final int LOAD_RETRY_BACKOFF_MILLIS = 60;

    private final IPlanTaskEventRepository planTaskEventRepository;
    private final DataSource dataSource;
    private final ConcurrentMap<Long, ConcurrentMap<String, Consumer<PlanTaskEventEntity>>> subscribersByPlan;
    private final ExecutorService notifyListenExecutor;
    private final String notifyChannel;
    private final String publisherInstanceId;
    private volatile boolean running;

    public PlanTaskEventPublisher(IPlanTaskEventRepository planTaskEventRepository) {
        this(planTaskEventRepository, null, "plan_task_events_channel", null);
    }

    @Autowired
    public PlanTaskEventPublisher(IPlanTaskEventRepository planTaskEventRepository,
                                  ObjectProvider<DataSource> dataSourceProvider,
                                  @Value("${event.notify.channel:plan_task_events_channel}") String notifyChannel,
                                  @Value("${event.publisher.instance-id:}") String configuredInstanceId) {
        this.planTaskEventRepository = planTaskEventRepository;
        this.dataSource = dataSourceProvider == null ? null : dataSourceProvider.getIfAvailable();
        this.subscribersByPlan = new ConcurrentHashMap<>();
        this.notifyListenExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "plan-event-notify-listener");
            thread.setDaemon(true);
            return thread;
        });
        this.notifyChannel = (notifyChannel == null || notifyChannel.isBlank())
                ? "plan_task_events_channel"
                : notifyChannel;
        this.publisherInstanceId = resolvePublisherId(configuredInstanceId);
        this.running = false;
    }

    @PostConstruct
    public void startNotifyListener() {
        if (dataSource == null) {
            log.info("PlanTaskEventPublisher notify listener disabled because DataSource is unavailable.");
            return;
        }
        running = true;
        notifyListenExecutor.execute(this::listenLoop);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        notifyListenExecutor.shutdownNow();
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
        notifyCrossInstance(saved);
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

    private void notifyCrossInstance(PlanTaskEventEntity event) {
        if (dataSource == null || event == null || event.getPlanId() == null || event.getId() == null) {
            return;
        }
        String payload = event.getPlanId() + ":" + event.getId() + ":" + publisherInstanceId;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            statement.setString(1, notifyChannel);
            statement.setString(2, payload);
            statement.execute();
        } catch (Exception ex) {
            log.debug("Plan event notify failed. planId={}, eventId={}, error={}",
                    event.getPlanId(), event.getId(), ex.getMessage());
        }
    }

    private void listenLoop() {
        while (running) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("LISTEN " + notifyChannel);
                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                while (running && !connection.isClosed()) {
                    PGNotification[] notifications = pgConnection.getNotifications(LISTEN_TIMEOUT_MILLIS);
                    if (notifications == null || notifications.length == 0) {
                        continue;
                    }
                    for (PGNotification notification : notifications) {
                        handleNotification(notification == null ? null : notification.getParameter());
                    }
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                if (!running) {
                    return;
                }
                log.warn("Plan event notify listener failed, retrying. channel={}, error={}",
                        notifyChannel, ex.getMessage());
                try {
                    sleepSilently(RECONNECT_BACKOFF_MILLIS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleNotification(String payload) throws InterruptedException {
        NotifyPayload notifyPayload = NotifyPayload.parse(payload);
        if (notifyPayload == null || notifyPayload.planId == null || notifyPayload.eventId == null) {
            return;
        }
        if (publisherInstanceId.equals(notifyPayload.publisherId)) {
            return;
        }
        PlanTaskEventEntity event = loadEventById(notifyPayload.planId, notifyPayload.eventId);
        if (event != null) {
            dispatch(event);
        }
    }

    private PlanTaskEventEntity loadEventById(Long planId, Long eventId) throws InterruptedException {
        if (planId == null || eventId == null || eventId <= 0) {
            return null;
        }
        long cursor = eventId - 1;
        for (int i = 0; i < LOAD_RETRY_TIMES; i++) {
            List<PlanTaskEventEntity> events = replay(planId, cursor, 1);
            if (events != null && !events.isEmpty()) {
                PlanTaskEventEntity event = events.get(0);
                if (event != null && eventId.equals(event.getId())) {
                    return event;
                }
            }
            if (i < LOAD_RETRY_TIMES - 1) {
                sleepSilently(LOAD_RETRY_BACKOFF_MILLIS);
            }
        }
        return null;
    }

    private void sleepSilently(int millis) throws InterruptedException {
        if (millis <= 0) {
            return;
        }
        Thread.sleep(millis);
    }

    private String resolvePublisherId(String configuredInstanceId) {
        if (configuredInstanceId != null && !configuredInstanceId.isBlank()) {
            return configuredInstanceId;
        }
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            return host + "-" + pid;
        } catch (Exception ex) {
            return "instance-" + System.nanoTime();
        }
    }

    private static final class NotifyPayload {
        private final Long planId;
        private final Long eventId;
        private final String publisherId;

        private NotifyPayload(Long planId, Long eventId, String publisherId) {
            this.planId = planId;
            this.eventId = eventId;
            this.publisherId = publisherId;
        }

        private static NotifyPayload parse(String payload) {
            if (payload == null || payload.isBlank()) {
                return null;
            }
            String[] parts = payload.split(":", 3);
            if (parts.length < 3) {
                return null;
            }
            try {
                Long planId = Long.parseLong(parts[0].trim());
                Long eventId = Long.parseLong(parts[1].trim());
                String publisherId = parts[2];
                return new NotifyPayload(planId, eventId, publisherId);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
