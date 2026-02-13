package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.http.AgentV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentV2ControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2Controller()).build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldRejectV2ActiveEntry() throws Exception {
        mockMvc.perform(get("/api/v2/agents/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("V2 Agent 入口已下线，请使用 /api/v3/chat/messages"));
    }

    @Test
    public void shouldRejectV2CreateEntry() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "name", "assistant",
                "modelProvider", "openai",
                "modelName", "gpt-4o-mini"
        ));

        mockMvc.perform(post("/api/v2/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("V2 Agent 入口已下线，请使用 /api/v3/chat/messages"));
    }
}
