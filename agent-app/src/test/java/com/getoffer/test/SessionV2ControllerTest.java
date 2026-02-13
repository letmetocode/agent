package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.trigger.http.SessionV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SessionV2ControllerTest {

    private MockMvc mockMvc;
    private IAgentSessionRepository agentSessionRepository;
    private IAgentRegistryRepository agentRegistryRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.agentRegistryRepository = mock(IAgentRegistryRepository.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new SessionV2Controller(agentSessionRepository, agentRegistryRepository)
        ).build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldRejectWhenAgentNotActive() throws Exception {
        AgentRegistryEntity inactive = new AgentRegistryEntity();
        inactive.setKey("assistant");
        inactive.setIsActive(false);
        when(agentRegistryRepository.findByKey("assistant")).thenReturn(inactive);

        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", "u-1",
                "agentKey", "assistant"
        ));

        mockMvc.perform(post("/api/v2/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("agentKey 不存在或未激活: assistant"));
    }

    @Test
    public void shouldCreateSessionAndMergeMetaInfo() throws Exception {
        AgentRegistryEntity agent = new AgentRegistryEntity();
        agent.setId(7L);
        agent.setKey("assistant");
        agent.setIsActive(true);
        when(agentRegistryRepository.findByKey("assistant")).thenReturn(agent);
        when(agentSessionRepository.save(any(AgentSessionEntity.class))).thenAnswer(invocation -> {
            AgentSessionEntity entity = invocation.getArgument(0);
            entity.setId(1001L);
            entity.setCreatedAt(LocalDateTime.of(2026, 2, 12, 12, 30, 0));
            return entity;
        });

        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", "u-1",
                "title", "制定发布计划",
                "agentKey", "assistant",
                "scenario", "CONSOLE_LAUNCH",
                "metaInfo", Map.of("source", "session-list")
        ));

        mockMvc.perform(post("/api/v2/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.sessionId").value(1001L))
                .andExpect(jsonPath("$.data.userId").value("u-1"))
                .andExpect(jsonPath("$.data.agentKey").value("assistant"))
                .andExpect(jsonPath("$.data.scenario").value("CONSOLE_LAUNCH"));

        ArgumentCaptor<AgentSessionEntity> captor = ArgumentCaptor.forClass(AgentSessionEntity.class);
        verify(agentSessionRepository).save(captor.capture());
        AgentSessionEntity saved = captor.getValue();

        assertNotNull(saved.getMetaInfo());
        assertEquals("session-list", saved.getMetaInfo().get("source"));
        assertEquals("assistant", saved.getMetaInfo().get("agentKey"));
        assertEquals("CONSOLE_LAUNCH", saved.getMetaInfo().get("scenario"));
    }
}
