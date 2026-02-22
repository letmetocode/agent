package com.getoffer.test;

import com.getoffer.api.dto.ChatHistoryResponseV3DTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.trigger.application.query.ChatHistoryQueryService;
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatHistoryQueryServiceTest {

    private IAgentSessionRepository agentSessionRepository;
    private ISessionTurnRepository sessionTurnRepository;
    private ISessionMessageRepository sessionMessageRepository;
    private IAgentPlanRepository agentPlanRepository;
    private ChatHistoryQueryService service;

    @BeforeEach
    public void setUp() {
        this.agentSessionRepository = mock(IAgentSessionRepository.class);
        this.sessionTurnRepository = mock(ISessionTurnRepository.class);
        this.sessionMessageRepository = mock(ISessionMessageRepository.class);
        this.agentPlanRepository = mock(IAgentPlanRepository.class);
        this.service = new ChatHistoryQueryService(
                agentSessionRepository,
                sessionTurnRepository,
                sessionMessageRepository,
                agentPlanRepository
        );
    }

    @Test
    public void shouldReturnCursorPagedHistoryDesc() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(100L);
        session.setUserId("u-1");
        session.setTitle("t");
        session.setAgentKey("assistant");
        session.setScenario("CHAT_DEFAULT");
        when(agentSessionRepository.findById(100L)).thenReturn(session);

        SessionTurnEntity t120 = buildTurn(120L, 100L);
        SessionTurnEntity t110 = buildTurn(110L, 100L);
        SessionTurnEntity t100 = buildTurn(100L, 100L);
        when(sessionTurnRepository.findBySessionIdWithCursor(100L, null, 3, false))
                .thenReturn(List.of(t120, t110, t100));

        SessionMessageEntity m1005 = buildMessage(1005L, 100L, 120L, "m120");
        SessionMessageEntity m1001 = buildMessage(1001L, 100L, 110L, "m110");
        when(sessionMessageRepository.findByTurnIds(List.of(120L, 110L)))
                .thenReturn(List.of(m1001, m1005));

        AgentPlanEntity p1 = new AgentPlanEntity();
        p1.setId(900L);
        p1.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        AgentPlanEntity p2 = new AgentPlanEntity();
        p2.setId(901L);
        p2.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        when(agentPlanRepository.findBySessionId(100L)).thenReturn(List.of(p1, p2));

        ChatHistoryResponseV3DTO data = service.getHistory(100L, null, 2, "desc");

        assertEquals(100L, data.getSessionId());
        assertEquals("desc", data.getOrder());
        assertEquals(2, data.getLimit());
        assertTrue(Boolean.TRUE.equals(data.getHasMore()));
        assertEquals(110L, data.getNextCursor());
        assertEquals(901L, data.getLatestPlanId());
        assertEquals(List.of(120L, 110L), data.getTurns().stream().map(item -> item.getTurnId()).toList());
        assertEquals(List.of(1005L, 1001L), data.getMessages().stream().map(item -> item.getMessageId()).toList());
    }

    @Test
    public void shouldRejectIllegalOrder() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(100L);
        when(agentSessionRepository.findById(100L)).thenReturn(session);

        AppException ex = assertThrows(AppException.class, () -> service.getHistory(100L, null, 20, "bad-order"));
        assertEquals("order 仅支持 asc 或 desc", ex.getInfo());
    }

    @Test
    public void shouldClampLimitToMax() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(100L);
        when(agentSessionRepository.findById(100L)).thenReturn(session);
        when(sessionTurnRepository.findBySessionIdWithCursor(eq(100L), eq(null), eq(201), anyBoolean()))
                .thenReturn(List.of());
        when(agentPlanRepository.findBySessionId(100L)).thenReturn(List.of());

        ChatHistoryResponseV3DTO data = service.getHistory(100L, null, 999, "asc");

        assertEquals(200, data.getLimit());
        verify(sessionTurnRepository).findBySessionIdWithCursor(100L, null, 201, true);
    }

    private SessionTurnEntity buildTurn(Long turnId, Long sessionId) {
        SessionTurnEntity entity = new SessionTurnEntity();
        entity.setId(turnId);
        entity.setSessionId(sessionId);
        entity.setStatus(TurnStatusEnum.EXECUTING);
        entity.setUserMessage("message-" + turnId);
        return entity;
    }

    private SessionMessageEntity buildMessage(Long id, Long sessionId, Long turnId, String content) {
        SessionMessageEntity entity = new SessionMessageEntity();
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setTurnId(turnId);
        entity.setRole(MessageRoleEnum.USER);
        entity.setContent(content);
        return entity;
    }
}
