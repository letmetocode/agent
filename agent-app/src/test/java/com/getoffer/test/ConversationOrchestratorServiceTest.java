package com.getoffer.test;

import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private IRoutingDecisionRepository routingDecisionRepository;
    private IAgentRegistryRepository agentRegistryRepository;

    private ChatConversationCommandService conversationOrchestratorService;

    @BeforeEach
    public void setUp() {
        this.plannerService = mock(PlannerService.class);
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.routingDecisionRepository = mock(IRoutingDecisionRepository.class);
        this.agentRegistryRepository = mock(IAgentRegistryRepository.class);

        this.conversationOrchestratorService = new ChatConversationCommandService(
                plannerService,
                agentSessionRepository,
                sessionTurnRepository,
                sessionMessageRepository,
                routingDecisionRepository,
                agentRegistryRepository,
                new SessionConversationDomainService()
        );

        when(sessionTurnRepository.update(any(SessionTurnEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        request.setUserId("dev-user");
        request.setMessage("生成推荐文案");

        ChatConversationCommandService.ConversationSubmitResult result = conversationOrchestratorService.submitMessage(request);

        assertEquals(101L, result.getSessionId());
        assertEquals(201L, result.getTurnId());
        assertEquals(301L, result.getPlanId());
        assertEquals("EXECUTING", result.getTurnStatus());

        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(plannerService).createPlan(eq(101L), eq("生成推荐文案"), contextCaptor.capture());
        Map<String, Object> ctx = contextCaptor.getValue();
        assertEquals("assistant", ctx.get("agentKey"));
        assertEquals("CHAT_DEFAULT", ctx.get("scenario"));
        assertEquals("chat-v3", ctx.get("entry"));
        assertEquals(201L, ctx.get("turnId"));
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

        when(plannerService.createPlan(eq(102L), eq("触发失败"), any(Map.class)))
                .thenThrow(new AppException("0001", "路由失败"));

        ChatMessageSubmitRequestV3DTO request = new ChatMessageSubmitRequestV3DTO();
        request.setUserId("dev-user");
        request.setSessionId(102L);
        request.setMessage("触发失败");

        AppException ex = assertThrows(AppException.class, () -> conversationOrchestratorService.submitMessage(request));
        assertEquals("路由失败", ex.getInfo());

        ArgumentCaptor<SessionTurnEntity> turnCaptor = ArgumentCaptor.forClass(SessionTurnEntity.class);
        verify(sessionTurnRepository, atLeast(1)).update(turnCaptor.capture());
        assertTrue(turnCaptor.getAllValues().stream().anyMatch(turn -> turn.getStatus() == TurnStatusEnum.FAILED));
    }
}
