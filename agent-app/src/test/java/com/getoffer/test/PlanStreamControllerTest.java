package com.getoffer.test;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.trigger.http.PlanStreamController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlanStreamControllerTest {

    private IAgentPlanRepository agentPlanRepository;
    private IAgentTaskRepository agentTaskRepository;
    private PlanTaskEventPublisher planTaskEventPublisher;
    private MeterRegistry meterRegistry;
    private ObjectProvider<MeterRegistry> meterRegistryProvider;
    private PlanStreamController controller;

    @BeforeEach
    public void setUp() {
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.agentTaskRepository = mock(IAgentTaskRepository.class);
        this.planTaskEventPublisher = mock(PlanTaskEventPublisher.class);
        this.meterRegistry = new SimpleMeterRegistry();

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("meterRegistry", meterRegistry);
        this.meterRegistryProvider = beanFactory.getBeanProvider(MeterRegistry.class);

        this.controller = new PlanStreamController(
                agentPlanRepository,
                agentTaskRepository,
                planTaskEventPublisher,
                meterRegistryProvider,
                200,
                1
        );
    }

    @AfterEach
    public void tearDown() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    @Test
    public void shouldPreferLastEventIdHeaderOverQueryParameter() throws Exception {
        Method method = PlanStreamController.class.getDeclaredMethod("resolveCursor", Long.class, String.class);
        method.setAccessible(true);

        long resolved = (long) method.invoke(controller, 7L, "19");

        Assertions.assertEquals(19L, resolved);
    }

    @Test
    public void shouldFallbackToQueryCursorWhenHeaderInvalid() throws Exception {
        Method method = PlanStreamController.class.getDeclaredMethod("resolveCursor", Long.class, String.class);
        method.setAccessible(true);

        long resolved = (long) method.invoke(controller, 9L, "not-a-number");

        Assertions.assertEquals(9L, resolved);
    }

    @Test
    public void shouldRecordReplayBatchAndEmptyMetricsWhenNoEvents() throws Exception {
        Long planId = 101L;
        String subscriberId = "subscriber-1";
        registerSubscriber(planId, subscriberId, 0L);
        when(planTaskEventPublisher.replay(eq(planId), eq(0L), eq(200)))
                .thenReturn(Collections.emptyList());

        Method replayMethod = PlanStreamController.class.getDeclaredMethod(
                "replayMissedEvents", Long.class, String.class, int.class);
        replayMethod.setAccessible(true);
        replayMethod.invoke(controller, planId, subscriberId, 1);

        verify(planTaskEventPublisher).replay(eq(planId), eq(0L), eq(200));
        Assertions.assertEquals(1.0D, meterRegistry.counter("agent.sse.replay.batch.total").count(), 0.0001D);
        Assertions.assertEquals(1.0D, meterRegistry.counter("agent.sse.replay.empty.total").count(), 0.0001D);
        Assertions.assertEquals(0.0D, meterRegistry.counter("agent.sse.replay.hit.total").count(), 0.0001D);
        Assertions.assertEquals(1L, meterRegistry.find("agent.sse.replay.duration").timer().count());
    }

    @Test
    public void shouldUseConfiguredReplayBatchSize() throws Exception {
        PlanStreamController configuredController = new PlanStreamController(
                agentPlanRepository,
                agentTaskRepository,
                planTaskEventPublisher,
                meterRegistryProvider,
                64,
                1
        );
        try {
            Long planId = 202L;
            String subscriberId = "subscriber-2";
            registerSubscriber(configuredController, planId, subscriberId, 0L);
            when(planTaskEventPublisher.replay(eq(planId), eq(0L), eq(64)))
                    .thenReturn(Collections.emptyList());

            Method replayMethod = PlanStreamController.class.getDeclaredMethod(
                    "replayMissedEvents", Long.class, String.class, int.class);
            replayMethod.setAccessible(true);
            replayMethod.invoke(configuredController, planId, subscriberId, 1);

            verify(planTaskEventPublisher).replay(eq(planId), eq(0L), eq(64));
        } finally {
            configuredController.shutdown();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerSubscriber(Long planId, String subscriberId, long lastEventId) throws Exception {
        registerSubscriber(controller, planId, subscriberId, lastEventId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerSubscriber(PlanStreamController target,
                                    Long planId,
                                    String subscriberId,
                                    long lastEventId) throws Exception {
        Field field = PlanStreamController.class.getDeclaredField("subscribersByPlan");
        field.setAccessible(true);

        ConcurrentMap subscribersByPlan = (ConcurrentMap) field.get(target);
        ConcurrentMap subscribers = (ConcurrentMap) subscribersByPlan.computeIfAbsent(planId, key -> new ConcurrentHashMap<>());

        Class<?> stateClass = Class.forName("com.getoffer.trigger.http.PlanStreamController$SubscriberState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(SseEmitter.class, long.class);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(new SseEmitter(), lastEventId);
        subscribers.put(subscriberId, state);
    }
}
