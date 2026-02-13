package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.trigger.http.ChatV3Controller;
import com.getoffer.trigger.service.ConversationOrchestratorService;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ChatV3ControllerTest {

    private ConversationOrchestratorService conversationOrchestratorService;
    private IAgentSessionRepository agentSessionRepository;
    private ISessionTurnRepository sessionTurnRepository;
    private ISessionMessageRepository sessionMessageRepository;
    private IAgentPlanRepository agentPlanRepository;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.conversationOrchestratorService = mock(ConversationOrchestratorService.class);
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.objectMapper = new ObjectMapper();

        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatV3Controller(
                        conversationOrchestratorService,
                        agentSessionRepository,
                        sessionTurnRepository,
                        sessionMessageRepository,
                        agentPlanRepository
                )
        ).build();
    }

    @Test
    public void shouldSubmitMessageAndReturnPaths() throws Exception {
        ConversationOrchestratorService.ConversationSubmitResult result = new ConversationOrchestratorService.ConversationSubmitResult();
        result.setSessionId(11L);
        result.setSessionTitle("新聊天");
        result.setTurnId(21L);
        result.setPlanId(31L);
        result.setTurnStatus("EXECUTING");
        result.setAssistantMessage("收到，本轮任务已开始执行。");

        when(conversationOrchestratorService.submitMessage(any())).thenReturn(result);

        mockMvc.perform(post("/api/v3/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", "dev-user", "message", "写一条文案"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.sessionId").value(11L))
                .andExpect(jsonPath("$.data.turnId").value(21L))
                .andExpect(jsonPath("$.data.planId").value(31L))
                .andExpect(jsonPath("$.data.streamPath").value("/api/v3/chat/sessions/11/stream?planId=31"))
                .andExpect(jsonPath("$.data.historyPath").value("/api/v3/chat/sessions/11/history"));
    }

    @Test
    public void shouldReturnHistoryWithLatestPlanId() throws Exception {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(12L);
        session.setUserId("dev-user");
        session.setTitle("会话标题");
        session.setAgentKey("assistant");
        session.setScenario("CHAT_DEFAULT");
        when(agentSessionRepository.findById(12L)).thenReturn(session);

        SessionTurnEntity turn = new SessionTurnEntity();
        turn.setId(22L);
        turn.setSessionId(12L);
        turn.setPlanId(32L);
        turn.setUserMessage("你好");
        turn.setStatus(TurnStatusEnum.EXECUTING);
        turn.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(sessionTurnRepository.findBySessionId(12L)).thenReturn(List.of(turn));

        SessionMessageEntity userMessage = new SessionMessageEntity();
        userMessage.setId(41L);
        userMessage.setSessionId(12L);
        userMessage.setTurnId(22L);
        userMessage.setRole(MessageRoleEnum.USER);
        userMessage.setContent("你好");
        userMessage.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        SessionMessageEntity assistantMessage = new SessionMessageEntity();
        assistantMessage.setId(42L);
        assistantMessage.setSessionId(12L);
        assistantMessage.setTurnId(22L);
        assistantMessage.setRole(MessageRoleEnum.ASSISTANT);
        assistantMessage.setContent("你好，我来处理");
        assistantMessage.setCreatedAt(LocalDateTime.now());

        when(sessionMessageRepository.findBySessionId(12L)).thenReturn(List.of(userMessage, assistantMessage));

        AgentPlanEntity planOld = new AgentPlanEntity();
        planOld.setId(31L);
        planOld.setSessionId(12L);
        planOld.setUpdatedAt(LocalDateTime.now().minusHours(1));

        AgentPlanEntity planNew = new AgentPlanEntity();
        planNew.setId(32L);
        planNew.setSessionId(12L);
        planNew.setUpdatedAt(LocalDateTime.now());

        when(agentPlanRepository.findBySessionId(12L)).thenReturn(List.of(planOld, planNew));

        mockMvc.perform(get("/api/v3/chat/sessions/{id}/history", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.sessionId").value(12L))
                .andExpect(jsonPath("$.data.latestPlanId").value(32L))
                .andExpect(jsonPath("$.data.turns[0].turnId").value(22L))
                .andExpect(jsonPath("$.data.messages[0].messageId").value(41L));
    }
}
