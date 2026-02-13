package com.getoffer.test;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.trigger.application.query.ChatRoutingQueryService;
import com.getoffer.trigger.http.ChatRoutingV3Controller;
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

public class ChatRoutingV3ControllerTest {

    private MockMvc mockMvc;
    private ChatRoutingQueryService chatRoutingQueryService;

    @BeforeEach
    public void setUp() {
        this.chatRoutingQueryService = mock(ChatRoutingQueryService.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatRoutingV3Controller(chatRoutingQueryService)
        ).build();
    }

    @Test
    public void shouldReturnNullDataWhenRouteDecisionNotBound() throws Exception {
        when(chatRoutingQueryService.getRoutingDecision(21L)).thenReturn(null);

        mockMvc.perform(get("/api/v3/chat/plans/{id}/routing", 21L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    public void shouldReturnRoutingDecision() throws Exception {
        RoutingDecisionDTO decision = new RoutingDecisionDTO();
        decision.setRoutingDecisionId(88L);
        decision.setDecisionType("FALLBACK");
        decision.setSourceType("auto_miss_fallback");
        decision.setFallbackFlag(true);
        decision.setPlannerAttempts(3);

        when(chatRoutingQueryService.getRoutingDecision(22L)).thenReturn(decision);

        mockMvc.perform(get("/api/v3/chat/plans/{id}/routing", 22L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.routingDecisionId").value(88L))
                .andExpect(jsonPath("$.data.decisionType").value("FALLBACK"))
                .andExpect(jsonPath("$.data.sourceType").value("auto_miss_fallback"))
                .andExpect(jsonPath("$.data.fallbackFlag").value(true))
                .andExpect(jsonPath("$.data.plannerAttempts").value(3));
    }
}
