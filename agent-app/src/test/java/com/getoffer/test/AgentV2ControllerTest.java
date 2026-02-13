package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.trigger.http.AgentV2Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentV2ControllerTest {

    private MockMvc mockMvc;
    private IAgentRegistryRepository agentRegistryRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.agentRegistryRepository = mock(IAgentRegistryRepository.class);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new AgentV2Controller(agentRegistryRepository)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldListActiveAgents() throws Exception {
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(11L);
        entity.setKey("assistant");
        entity.setName("Assistant");
        entity.setModelProvider("openai");
        entity.setModelName("gpt-4o-mini");
        entity.setIsActive(true);
        when(agentRegistryRepository.findByActive(true)).thenReturn(Collections.singletonList(entity));

        mockMvc.perform(get("/api/v2/agents/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].agentId").value(11L))
                .andExpect(jsonPath("$.data[0].agentKey").value("assistant"))
                .andExpect(jsonPath("$.data[0].modelProvider").value("openai"));
    }

    @Test
    public void shouldRejectWhenCreateAgentWithDuplicateKey() throws Exception {
        AgentRegistryEntity existing = new AgentRegistryEntity();
        existing.setId(1L);
        existing.setKey("assistant");
        when(agentRegistryRepository.findByKey("assistant")).thenReturn(existing);

        String payload = objectMapper.writeValueAsString(Map.of(
                "agentKey", "assistant",
                "name", "Assistant",
                "modelProvider", "openai",
                "modelName", "gpt-4o-mini"
        ));

        mockMvc.perform(post("/api/v2/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0002"))
                .andExpect(jsonPath("$.info").value("agentKey 已存在: assistant"));
    }

    @Test
    public void shouldCreateAgentWithNormalizedKey() throws Exception {
        when(agentRegistryRepository.findByKey("planner-agent")).thenReturn(null);
        when(agentRegistryRepository.save(any(AgentRegistryEntity.class))).thenAnswer(invocation -> {
            AgentRegistryEntity entity = invocation.getArgument(0);
            entity.setId(21L);
            entity.setCreatedAt(LocalDateTime.of(2026, 2, 12, 12, 0, 0));
            return entity;
        });

        String payload = objectMapper.writeValueAsString(Map.of(
                "name", "Planner Agent",
                "modelProvider", "openai",
                "modelName", "gpt-4o-mini"
        ));

        mockMvc.perform(post("/api/v2/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.agentId").value(21L))
                .andExpect(jsonPath("$.data.agentKey").value("planner-agent"))
                .andExpect(jsonPath("$.data.modelName").value("gpt-4o-mini"));

        ArgumentCaptor<AgentRegistryEntity> captor = ArgumentCaptor.forClass(AgentRegistryEntity.class);
        verify(agentRegistryRepository).save(captor.capture());
        AgentRegistryEntity saved = captor.getValue();
        assertEquals("planner-agent", saved.getKey());
        assertTrue(saved.getBaseSystemPrompt().contains("Agent"));
    }
}
