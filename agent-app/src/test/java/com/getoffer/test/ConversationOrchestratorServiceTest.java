package com.getoffer.test;

import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.trigger.application.command.ChatConversationCommandService;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConversationOrchestratorServiceTest {

    private PlannerService plannerService;
    private IAgentSessionRepository agentSessionRepository;
    private ISessionTurnRepository sessionTurnRepository;
    private ISessionMessageRepository sessionMessageRepository;
    private IAgentPlanRepository agentPlanRepository;
    private IRoutingDecisionRepository routingDecisionRepository;
    private IAgentRegistryRepository agentRegistryRepository;
    private Executor directExecutor;

    private ChatConversationCommandService conversationOrchestratorService;

    @BeforeEach
    public void setUp() {
        this.plannerService = mock(PlannerService.class);
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.routingDecisionRepository = mock(IRoutingDecisionRepository.class);
        this.agentRegistryRepository = mock(IAgentRegistryRepository.class);
        this.directExecutor = Runnable::run;

        this.conversationOrchestratorService = new ChatConversationCommandService(
                plannerService,
                agentSessionRepository,
                sessionTurnRepository,
                sessionMessageRepository,
                agentPlanRepository,
                routingDecisionRepository,
                agentRegistryRepository,
                new SessionConversationDomainService(),
                directExecutor
        );

        when(sessionTurnRepository.update(any(SessionTurnEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionTurnRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            SessionTurnEntity turn = new SessionTurnEntity();
            turn.setId(id);
            turn.setSessionId(101L);
            turn.setUserMessage("mock");
            turn.setStatus(TurnStatusEnum.PLANNING);
            return turn;
        });
        when(sessionMessageRepository.save(any(SessionMessageEntity.class))).thenAnswer(invocation -> {
            SessionMessageEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(900L);
            }
            return entity;
        });
    }

    @Test
    public void shouldCreateSessionByDefaultAssistantAndSubmitPlan() {
        AgentRegistryEntity assistant = new AgentRegistryEntity();
        assistant.setId(1L);
        assistant.setKey("assistant");
        assistant.setIsActive(true);
        when(agentRegistryRepository.findByActive(true)).thenReturn(List.of(assistant));

        when(agentSessionRepository.save(any(AgentSessionEntity.class))).thenAnswer(invocation -> {
            AgentSessionEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        when(sessionTurnRepository.save(any(SessionTurnEntity.class))).thenAnswer(invocation -> {
            SessionTurnEntity turn = invocation.getArgument(0);
            turn.setId(201L);
            return turn;
        });

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(301L);
        plan.setPlanGoal("生成推荐文案");
        when(plannerService.createPlan(eq(101L), eq("生成推荐文案"), any(Map.class))).thenReturn(plan);

        ChatMessageSubmitRequestV3DTO request = new ChatMessageSubmitRequestV3DTO();
        request.setClientMessageId("client-msg-1");
        request.setUserId("dev-user");
        request.setMessage("生成推荐文案");

        ChatConversationCommandService.ConversationSubmitResult result = conversationOrchestratorService.submitMessage(request);

        assertEquals(101L, result.getSessionId());
        assertEquals(201L, result.getTurnId());
        assertNull(result.getPlanId());
        assertEquals("PLANNING", result.getTurnStatus());
        assertTrue(Boolean.TRUE.equals(result.getAccepted()));
        assertEquals("ACCEPTED", result.getSubmissionState());

        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(plannerService).createPlan(eq(101L), eq("生成推荐文案"), contextCaptor.capture());
        Map<String, Object> ctx = contextCaptor.getValue();
        assertEquals("assistant", ctx.get("agentKey"));
        assertEquals("CHAT_DEFAULT", ctx.get("scenario"));
        assertEquals("chat-v3", ctx.get("entry"));
        assertEquals(201L, ctx.get("turnId"));
        assertEquals("client-msg-1", ctx.get("clientMessageId"));
    }

    @Test
    public void shouldMarkTurnFailedWhenPlannerThrows() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(102L);
        session.setUserId("dev-user");
        session.setAgentKey("assistant");
        session.setScenario("CHAT_DEFAULT");
        when(agentSessionRepository.findById(102L)).thenReturn(session);

        when(sessionTurnRepository.save(any(SessionTurnEntity.class))).thenAnswer(invocation -> {
            SessionTurnEntity turn = invocation.getArgument(0);
            turn.setId(202L);
            return turn;
        });
        when(sessionTurnRepository.findById(202L)).thenAnswer(invocation -> {
            SessionTurnEntity turn = new SessionTurnEntity();
            turn.setId(202L);
            turn.setSessionId(102L);
            turn.setUserMessage("触发失败");
            turn.setStatus(TurnStatusEnum.PLANNING);
            return turn;
        });

        when(plannerService.createPlan(eq(102L), eq("触发失败"), any(Map.class)))
                .thenThrow(new AppException("0001", "路由失败"));

        ChatMessageSubmitRequestV3DTO request = new ChatMessageSubmitRequestV3DTO();
        request.setClientMessageId("client-msg-2");
        request.setUserId("dev-user");
        request.setSessionId(102L);
        request.setMessage("触发失败");

        ChatConversationCommandService.ConversationSubmitResult result = conversationOrchestratorService.submitMessage(request);
        assertNotNull(result);
        assertEquals(202L, result.getTurnId());
        assertEquals("PLANNING", result.getTurnStatus());

        ArgumentCaptor<SessionTurnEntity> turnCaptor = ArgumentCaptor.forClass(SessionTurnEntity.class);
        verify(sessionTurnRepository, atLeast(1)).update(turnCaptor.capture());
        assertTrue(turnCaptor.getAllValues().stream().anyMatch(turn -> turn.getStatus() == TurnStatusEnum.FAILED));
    }

    @Test
    public void shouldReuseExistingTurnWhenClientMessageDuplicated() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(103L);
        session.setUserId("dev-user");
        session.setAgentKey("assistant");
        session.setScenario("CHAT_DEFAULT");
        when(agentSessionRepository.findById(103L)).thenReturn(session);

        SessionTurnEntity existingTurn = new SessionTurnEntity();
        existingTurn.setId(203L);
        existingTurn.setSessionId(103L);
        existingTurn.setPlanId(303L);
        existingTurn.setStatus(TurnStatusEnum.EXECUTING);
        when(sessionTurnRepository.findBySessionIdAndClientMessageId(103L, "client-msg-dup")).thenReturn(existingTurn);

        AgentPlanEntity existingPlan = new AgentPlanEntity();
        existingPlan.setId(303L);
        existingPlan.setPlanGoal("复用计划");
        when(agentPlanRepository.findById(303L)).thenReturn(existingPlan);

        ChatMessageSubmitRequestV3DTO request = new ChatMessageSubmitRequestV3DTO();
        request.setClientMessageId("client-msg-dup");
        request.setUserId("dev-user");
        request.setSessionId(103L);
        request.setMessage("复用请求");

        ChatConversationCommandService.ConversationSubmitResult result = conversationOrchestratorService.submitMessage(request);
        assertEquals(203L, result.getTurnId());
        assertEquals(303L, result.getPlanId());
        assertEquals("DUPLICATE", result.getSubmissionState());
        assertEquals("复用计划", result.getPlanGoal());
    }
}
