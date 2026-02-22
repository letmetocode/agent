package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.ChatHistoryResponseV3DTO;
import com.getoffer.api.dto.SessionMessageDTO;
import com.getoffer.api.dto.SessionTurnDTO;
import com.getoffer.trigger.application.command.ChatConversationCommandService;
import com.getoffer.trigger.application.query.ChatHistoryQueryService;
import com.getoffer.trigger.http.ChatV3Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    private ChatConversationCommandService chatConversationCommandService;
    private ChatHistoryQueryService chatHistoryQueryService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.chatConversationCommandService = mock(ChatConversationCommandService.class);
        this.chatHistoryQueryService = mock(ChatHistoryQueryService.class);
        this.objectMapper = new ObjectMapper();

        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatV3Controller(chatConversationCommandService, chatHistoryQueryService)
        ).build();
    }

    @Test
    public void shouldSubmitMessageAndReturnPaths() throws Exception {
        ChatConversationCommandService.ConversationSubmitResult result = new ChatConversationCommandService.ConversationSubmitResult();
        result.setSessionId(11L);
        result.setSessionTitle("新聊天");
        result.setTurnId(21L);
        result.setPlanId(31L);
        result.setTurnStatus("EXECUTING");
        result.setAssistantMessage("收到，本轮任务已开始执行。");

        when(chatConversationCommandService.submitMessage(any())).thenReturn(result);

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
        ChatHistoryResponseV3DTO history = new ChatHistoryResponseV3DTO();
        history.setSessionId(12L);
        history.setUserId("dev-user");
        history.setTitle("会话标题");
        history.setLatestPlanId(32L);
        history.setHasMore(true);
        history.setNextCursor(88L);
        history.setLimit(20);
        history.setOrder("desc");

        SessionTurnDTO turn = new SessionTurnDTO();
        turn.setTurnId(22L);
        turn.setSessionId(12L);
        history.setTurns(List.of(turn));

        SessionMessageDTO message = new SessionMessageDTO();
        message.setMessageId(41L);
        message.setSessionId(12L);
        message.setTurnId(22L);
        message.setRole("USER");
        history.setMessages(List.of(message));

        when(chatHistoryQueryService.getHistory(12L, null, null, null)).thenReturn(history);

        mockMvc.perform(get("/api/v3/chat/sessions/{id}/history", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.sessionId").value(12L))
                .andExpect(jsonPath("$.data.latestPlanId").value(32L))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(88L))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.order").value("desc"))
                .andExpect(jsonPath("$.data.turns[0].turnId").value(22L))
                .andExpect(jsonPath("$.data.messages[0].messageId").value(41L));
    }

    @Test
    public void shouldPassPagingParamsWhenGetHistory() throws Exception {
        ChatHistoryResponseV3DTO history = new ChatHistoryResponseV3DTO();
        history.setSessionId(12L);
        history.setUserId("dev-user");
        history.setTurns(List.of());
        history.setMessages(List.of());
        history.setHasMore(false);
        history.setOrder("asc");
        history.setLimit(50);

        when(chatHistoryQueryService.getHistory(12L, 100L, 30, "desc")).thenReturn(history);

        mockMvc.perform(get("/api/v3/chat/sessions/{id}/history", 12L)
                        .param("cursor", "100")
                        .param("limit", "30")
                        .param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.sessionId").value(12L));
    }
}
