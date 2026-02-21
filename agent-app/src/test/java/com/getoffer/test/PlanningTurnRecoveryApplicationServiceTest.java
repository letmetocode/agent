package com.getoffer.test;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.trigger.application.command.PlanningTurnRecoveryApplicationService;
import com.getoffer.types.enums.TurnStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlanningTurnRecoveryApplicationServiceTest {

    @Test
    public void shouldRecoverStalePlanningTurnAndBindFinalMessage() {
        ISessionTurnRepository turnRepository = mock(ISessionTurnRepository.class);
        ISessionMessageRepository messageRepository = mock(ISessionMessageRepository.class);

        SessionTurnEntity staleTurn = new SessionTurnEntity();
        staleTurn.setId(11L);
        staleTurn.setSessionId(21L);
        staleTurn.setStatus(TurnStatusEnum.PLANNING);
        when(turnRepository.findPlanningTurnsOlderThan(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(staleTurn));
        when(turnRepository.markTerminalIfNotTerminal(eq(11L), eq(TurnStatusEnum.FAILED), any(String.class), any(LocalDateTime.class)))
                .thenReturn(true);

        SessionMessageEntity savedMessage = new SessionMessageEntity();
        savedMessage.setId(31L);
        when(messageRepository.saveAssistantFinalMessageIfAbsent(any(SessionMessageEntity.class))).thenReturn(savedMessage);

        PlanningTurnRecoveryApplicationService service = new PlanningTurnRecoveryApplicationService(
                turnRepository,
                messageRepository,
                new SessionConversationDomainService()
        );

        PlanningTurnRecoveryApplicationService.RecoveryResult result = service.recoverStalePlanningTurns(100, 2);

        Assertions.assertEquals(1, result.processedCount());
        Assertions.assertEquals(1, result.recoveredCount());
        Assertions.assertEquals(0, result.dedupCount());
        Assertions.assertEquals(0, result.errorCount());
        verify(turnRepository, times(1)).bindFinalResponseMessage(11L, 31L);
    }

    @Test
    public void shouldCountDedupWhenTurnAlreadyTerminal() {
        ISessionTurnRepository turnRepository = mock(ISessionTurnRepository.class);
        ISessionMessageRepository messageRepository = mock(ISessionMessageRepository.class);

        SessionTurnEntity staleTurn = new SessionTurnEntity();
        staleTurn.setId(12L);
        staleTurn.setSessionId(22L);
        staleTurn.setStatus(TurnStatusEnum.PLANNING);
        when(turnRepository.findPlanningTurnsOlderThan(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(staleTurn));
        when(turnRepository.markTerminalIfNotTerminal(eq(12L), eq(TurnStatusEnum.FAILED), any(String.class), any(LocalDateTime.class)))
                .thenReturn(false);

        PlanningTurnRecoveryApplicationService service = new PlanningTurnRecoveryApplicationService(
                turnRepository,
                messageRepository,
                new SessionConversationDomainService()
        );

        PlanningTurnRecoveryApplicationService.RecoveryResult result = service.recoverStalePlanningTurns(100, 2);

        Assertions.assertEquals(1, result.processedCount());
        Assertions.assertEquals(0, result.recoveredCount());
        Assertions.assertEquals(1, result.dedupCount());
        Assertions.assertEquals(0, result.errorCount());
        verify(messageRepository, never()).saveAssistantFinalMessageIfAbsent(any(SessionMessageEntity.class));
    }
}
