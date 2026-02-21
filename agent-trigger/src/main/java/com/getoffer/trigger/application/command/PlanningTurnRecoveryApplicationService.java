package com.getoffer.trigger.application.command;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.types.enums.TurnStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规划超时回合恢复写用例：扫描长期停留 PLANNING 的回合并收敛为失败终态。
 */
@Slf4j
@Service
public class PlanningTurnRecoveryApplicationService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_TIMEOUT_MINUTES = 2;
    private static final String PLANNING_TIMEOUT_FAILURE_MESSAGE = "系统检测到本轮规划超时，已自动结束，请重试。";

    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final SessionConversationDomainService sessionConversationDomainService;

    public PlanningTurnRecoveryApplicationService(ISessionTurnRepository sessionTurnRepository,
                                                  ISessionMessageRepository sessionMessageRepository,
                                                  SessionConversationDomainService sessionConversationDomainService) {
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.sessionConversationDomainService = sessionConversationDomainService;
    }

    public RecoveryResult recoverStalePlanningTurns(int batchSize, int timeoutMinutes) {
        int normalizedBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        int normalizedTimeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : DEFAULT_TIMEOUT_MINUTES;
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(normalizedTimeoutMinutes);

        List<SessionTurnEntity> staleTurns = sessionTurnRepository.findPlanningTurnsOlderThan(cutoff, normalizedBatchSize);
        if (staleTurns == null || staleTurns.isEmpty()) {
            return RecoveryResult.empty();
        }

        int processedCount = 0;
        int recoveredCount = 0;
        int dedupCount = 0;
        int errorCount = 0;
        String failureMessage = sessionConversationDomainService.resolveFailureMessage(PLANNING_TIMEOUT_FAILURE_MESSAGE);
        for (SessionTurnEntity turn : staleTurns) {
            if (turn == null || turn.getId() == null || turn.getSessionId() == null) {
                continue;
            }
            processedCount++;
            boolean marked = sessionTurnRepository.markTerminalIfNotTerminal(
                    turn.getId(),
                    TurnStatusEnum.FAILED,
                    failureMessage,
                    LocalDateTime.now()
            );
            if (!marked) {
                dedupCount++;
                continue;
            }
            recoveredCount++;
            try {
                SessionMessageEntity assistantMessage = sessionConversationDomainService.createFailureAssistantMessage(
                        turn.getSessionId(),
                        turn.getId(),
                        failureMessage
                );
                SessionMessageEntity savedMessage = sessionMessageRepository.saveAssistantFinalMessageIfAbsent(assistantMessage);
                if (savedMessage != null && savedMessage.getId() != null) {
                    sessionTurnRepository.bindFinalResponseMessage(turn.getId(), savedMessage.getId());
                }
            } catch (RuntimeException ex) {
                errorCount++;
                log.warn("Stale planning turn recovered but final message binding failed. turnId={}, error={}",
                        turn.getId(),
                        ex.getMessage());
            }
        }
        return new RecoveryResult(processedCount, recoveredCount, dedupCount, errorCount);
    }

    public record RecoveryResult(int processedCount,
                                 int recoveredCount,
                                 int dedupCount,
                                 int errorCount) {
        public static RecoveryResult empty() {
            return new RecoveryResult(0, 0, 0, 0);
        }
    }
}
