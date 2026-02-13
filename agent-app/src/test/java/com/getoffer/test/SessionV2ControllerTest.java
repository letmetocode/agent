package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.trigger.http.SessionV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SessionV2ControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new SessionV2Controller()).build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldRejectV2SessionEntry() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", "u-1",
                "agentKey", "assistant"
        ));

        mockMvc.perform(post("/api/v2/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("V2 会话入口已下线，请使用 /api/v3/chat/messages"));
    }
}
