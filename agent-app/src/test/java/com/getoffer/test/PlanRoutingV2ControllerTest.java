package com.getoffer.test;

import com.getoffer.trigger.http.PlanRoutingV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PlanRoutingV2ControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new PlanRoutingV2Controller()).build();
    }

    @Test
    public void shouldRejectV2RoutingEntry() throws Exception {
        mockMvc.perform(get("/api/v2/plans/{id}/routing", 22L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("V2 路由入口已下线，请使用 /api/v3/chat/plans/{id}/routing"));
    }
}
