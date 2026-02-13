package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.http.TurnV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TurnV2ControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TurnV2Controller()).build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldRejectV2TurnEntry() throws Exception {
        Long sessionId = 1L;
        String payload = objectMapper.writeValueAsString(Map.of("message", "制定发布计划"));

        mockMvc.perform(post("/api/v2/sessions/{id}/turns", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("V2 回合入口已下线，请使用 /api/v3/chat/messages"));
    }
}
