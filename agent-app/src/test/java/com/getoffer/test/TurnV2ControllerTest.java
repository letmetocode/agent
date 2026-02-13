package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.trigger.http.TurnV2Controller;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TurnV2ControllerTest {

    private MockMvc mockMvc;
    private PlannerService plannerService;
    private IAgentSessionRepository agentSessionRepository;
    private ISessionTurnRepository sessionTurnRepository;
    private ISessionMessageRepository sessionMessageRepository;
    private IRoutingDecisionRepository routingDecisionRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.plannerService = mock(PlannerService.class);
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.routingDecisionRepository = mock(IRoutingDecisionRepository.class);

        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new TurnV2Controller(
                        plannerService,
                        agentSessionRepository,
                        sessionTurnRepository,
                        sessionMessageRepository,
                        routingDecisionRepository
                )
        ).build();
        this.objectMapper = new ObjectMapper();

        when(sessionMessageRepository.save(any(SessionMessageEntity.class))).thenAnswer(invocation -> {
            SessionMessageEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(entity.getRole() == MessageRoleEnum.USER ? 901L : 902L);
            }
            return entity;
        });
        when(sessionTurnRepository.update(any(SessionTurnEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void shouldCreateTurnAndReturnRoutingDecision() throws Exception {
        Long sessionId = 1L;
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(sessionId);
        session.setAgentKey("assistant");
        session.setScenario("CONSOLE_LAUNCH");
        when(agentSessionRepository.findById(sessionId)).thenReturn(session);

        SessionTurnEntity savedTurn = new SessionTurnEntity();
        savedTurn.setId(11L);
        savedTurn.setSessionId(sessionId);
        savedTurn.setStatus(TurnStatusEnum.PLANNING);
        when(sessionTurnRepository.save(any(SessionTurnEntity.class))).thenReturn(savedTurn);

        SessionTurnEntity previous = new SessionTurnEntity();
        previous.setId(10L);
        previous.setAssistantSummary("上轮摘要");
        when(sessionTurnRepository.findLatestBySessionIdAndStatus(sessionId, TurnStatusEnum.COMPLETED)).thenReturn(previous);

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(21L);
        plan.setPlanGoal("制定发布计划");
        plan.setRouteDecisionId(31L);
        when(plannerService.createPlan(eq(sessionId), eq("制定发布计划"), any(Map.class))).thenReturn(plan);

        RoutingDecisionEntity decision = new RoutingDecisionEntity();
        decision.setId(31L);
        decision.setSessionId(sessionId);
        decision.setTurnId(11L);
        decision.setDecisionType(RoutingDecisionTypeEnum.CANDIDATE);
        decision.setSourceType("root_candidate");
        decision.setFallbackFlag(false);
        decision.setPlannerAttempts(1);
        when(routingDecisionRepository.findById(31L)).thenReturn(decision);

        String payload = objectMapper.writeValueAsString(Map.of(
                "message", "制定发布计划",
                "contextOverrides", Map.of("tenantId", "t-001")
        ));

        mockMvc.perform(post("/api/v2/sessions/{id}/turns", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.turnId").value(11L))
                .andExpect(jsonPath("$.data.planId").value(21L))
                .andExpect(jsonPath("$.data.turnStatus").value("EXECUTING"))
                .andExpect(jsonPath("$.data.routingDecision.sourceType").value("root_candidate"))
                .andExpect(jsonPath("$.data.routingDecision.decisionType").value("CANDIDATE"));

        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(plannerService).createPlan(eq(sessionId), eq("制定发布计划"), contextCaptor.capture());
        Map<String, Object> context = contextCaptor.getValue();

        assertEquals("assistant", context.get("agentKey"));
        assertEquals("CONSOLE_LAUNCH", context.get("scenario"));
        assertEquals(11L, context.get("turnId"));
        assertEquals("t-001", context.get("tenantId"));
        assertEquals("上轮摘要", context.get("lastAssistantSummary"));
    }

    @Test
    public void shouldReturnErrorAndMarkTurnFailedWhenPlannerThrows() throws Exception {
        Long sessionId = 2L;
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(sessionId);
        session.setAgentKey("assistant");
        when(agentSessionRepository.findById(sessionId)).thenReturn(session);

        SessionTurnEntity savedTurn = new SessionTurnEntity();
        savedTurn.setId(66L);
        savedTurn.setSessionId(sessionId);
        savedTurn.setStatus(TurnStatusEnum.PLANNING);
        when(sessionTurnRepository.save(any(SessionTurnEntity.class))).thenReturn(savedTurn);
        when(plannerService.createPlan(eq(sessionId), eq("执行失败路径"), any(Map.class)))
                .thenThrow(new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "路由失败"));

        String payload = objectMapper.writeValueAsString(Map.of("message", "执行失败路径"));

        mockMvc.perform(post("/api/v2/sessions/{id}/turns", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0001"))
                .andExpect(jsonPath("$.info").value("路由失败"));

        ArgumentCaptor<SessionTurnEntity> turnCaptor = ArgumentCaptor.forClass(SessionTurnEntity.class);
        verify(sessionTurnRepository, atLeast(1)).update(turnCaptor.capture());
        assertTrue(turnCaptor.getAllValues().stream().anyMatch(t -> t.getStatus() == TurnStatusEnum.FAILED));

        verify(sessionMessageRepository, atLeast(2)).save(any(SessionMessageEntity.class));
    }
}
