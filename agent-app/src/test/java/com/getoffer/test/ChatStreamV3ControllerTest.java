package com.getoffer.test;

import com.getoffer.api.dto.ChatStreamEventV3DTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.task.model.entity.PlanTaskEventEntity;
import com.getoffer.trigger.application.sse.ChatSseEventMapper;
import com.getoffer.trigger.event.PlanTaskEventPublisher;
import com.getoffer.trigger.http.ChatStreamV3Controller;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChatStreamV3ControllerTest {

    private IAgentSessionRepository agentSessionRepository;
    private IAgentPlanRepository agentPlanRepository;
    private ISessionTurnRepository sessionTurnRepository;
    private ISessionMessageRepository sessionMessageRepository;
    private PlanTaskEventPublisher planTaskEventPublisher;
    private ChatStreamV3Controller controller;

    @BeforeEach
    public void setUp() {
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.planTaskEventPublisher = mock(PlanTaskEventPublisher.class);
        ChatSseEventMapper mapper = new ChatSseEventMapper(sessionMessageRepository, sessionTurnRepository);
        this.controller = new ChatStreamV3Controller(
                agentSessionRepository,
                agentPlanRepository,
                sessionTurnRepository,
                sessionMessageRepository,
                planTaskEventPublisher,
                mapper
        );
    }

    @Test
    public void shouldMapTaskLogToTaskProgressEvent() throws Exception {
        Object subscriber = createSubscriber("sid-1", 1L, 2L, 3L, 0L);
        PlanTaskEventEntity event = new PlanTaskEventEntity();
        event.setId(88L);
        event.setPlanId(2L);
        event.setTaskId(9L);
        event.setEventType(PlanTaskEventTypeEnum.TASK_LOG);
        event.setEventData(Map.of("taskId", 9L, "status", "RUNNING", "output", "阶段处理中", "taskNodeId", "collect_data"));

        Method method = ChatStreamV3Controller.class.getDeclaredMethod("mapTaskEvent", Class.forName("com.getoffer.trigger.http.ChatStreamV3Controller$StreamSubscriber"), PlanTaskEventEntity.class);
        method.setAccessible(true);

        ChatStreamEventV3DTO payload = (ChatStreamEventV3DTO) method.invoke(controller, subscriber, event);
        assertEquals("task.progress", payload.getType());
        assertEquals("阶段处理中", payload.getMessage());
        assertEquals(9L, payload.getTaskId());
        assertEquals("RUNNING", payload.getTaskStatus());
        assertEquals("collect_data", payload.getMetadata().get("nodeId"));
        assertEquals("collect_data", payload.getMetadata().get("taskName"));
    }

    @Test
    public void shouldResolveFinalAnswerFromAssistantMessage() throws Exception {
        SessionMessageEntity assistantMessage = new SessionMessageEntity();
        assistantMessage.setId(901L);
        assistantMessage.setRole(MessageRoleEnum.ASSISTANT);
        assistantMessage.setContent("这是最终答案");
        when(sessionMessageRepository.findById(901L)).thenReturn(assistantMessage);

        Method method = ChatStreamV3Controller.class.getDeclaredMethod("resolveFinalAnswer", Map.class, Long.class);
        method.setAccessible(true);

        String answer = (String) method.invoke(controller, Map.of("assistantMessageId", 901L), 3L);
        assertEquals("这是最终答案", answer);
    }

    @Test
    public void shouldKeepExplicitTaskNameWhenMetadataContainsNodeIdAndTaskName() throws Exception {
        Object subscriber = createSubscriber("sid-2", 1L, 2L, 3L, 0L);
        PlanTaskEventEntity event = new PlanTaskEventEntity();
        event.setId(89L);
        event.setPlanId(2L);
        event.setTaskId(10L);
        event.setEventType(PlanTaskEventTypeEnum.TASK_STARTED);
        event.setEventData(Map.of(
                "taskId", 10L,
                "status", "RUNNING",
                "nodeId", "collect_data",
                "taskName", "抓取数据"
        ));

        Method method = ChatStreamV3Controller.class.getDeclaredMethod("mapTaskEvent", Class.forName("com.getoffer.trigger.http.ChatStreamV3Controller$StreamSubscriber"), PlanTaskEventEntity.class);
        method.setAccessible(true);

        ChatStreamEventV3DTO payload = (ChatStreamEventV3DTO) method.invoke(controller, subscriber, event);
        assertEquals("任务开始：collect_data", payload.getMessage());
        assertEquals("collect_data", payload.getMetadata().get("nodeId"));
        assertEquals("抓取数据", payload.getMetadata().get("taskName"));
    }

    private Object createSubscriber(String subscriberId,
                                    Long sessionId,
                                    Long planId,
                                    Long turnId,
                                    long lastEventId) throws Exception {
        Class<?> stateClass = Class.forName("com.getoffer.trigger.http.ChatStreamV3Controller$StreamSubscriber");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(String.class, Long.class, Long.class, Long.class, SseEmitter.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(subscriberId, sessionId, planId, turnId, new SseEmitter(), lastEventId);
    }
}
