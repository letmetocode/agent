package com.getoffer.test;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.trigger.http.PlanRoutingV2Controller;
import com.getoffer.types.enums.RoutingDecisionTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PlanRoutingV2ControllerTest {

    private MockMvc mockMvc;
    private IAgentPlanRepository agentPlanRepository;
    private IRoutingDecisionRepository routingDecisionRepository;

    @BeforeEach
    public void setUp() {
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.routingDecisionRepository = mock(IRoutingDecisionRepository.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new PlanRoutingV2Controller(agentPlanRepository, routingDecisionRepository)
        ).build();
    }

    @Test
    public void shouldRejectWhenPlanNotFound() throws Exception {
        when(agentPlanRepository.findById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v2/plans/{id}/routing", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("计划不存在"));
    }

    @Test
    public void shouldReturnNullDataWhenRouteDecisionNotBound() throws Exception {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(21L);
        plan.setRouteDecisionId(null);
        when(agentPlanRepository.findById(21L)).thenReturn(plan);

        mockMvc.perform(get("/api/v2/plans/{id}/routing", 21L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    public void shouldReturnRoutingDecision() throws Exception {
        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setId(22L);
        plan.setRouteDecisionId(88L);
        when(agentPlanRepository.findById(22L)).thenReturn(plan);

        RoutingDecisionEntity decision = new RoutingDecisionEntity();
        decision.setId(88L);
        decision.setSessionId(100L);
        decision.setTurnId(200L);
        decision.setDecisionType(RoutingDecisionTypeEnum.FALLBACK);
        decision.setSourceType("auto_miss_fallback");
        decision.setFallbackFlag(true);
        decision.setFallbackReason("AUTO_MISS_FALLBACK");
        decision.setPlannerAttempts(3);
        when(routingDecisionRepository.findById(88L)).thenReturn(decision);

        mockMvc.perform(get("/api/v2/plans/{id}/routing", 22L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.routingDecisionId").value(88L))
                .andExpect(jsonPath("$.data.decisionType").value("FALLBACK"))
                .andExpect(jsonPath("$.data.sourceType").value("auto_miss_fallback"))
                .andExpect(jsonPath("$.data.fallbackFlag").value(true))
                .andExpect(jsonPath("$.data.plannerAttempts").value(3));
    }
}
