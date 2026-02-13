package com.getoffer.trigger.application.command;

import com.getoffer.domain.planning.service.PlanFinalizationDomainService;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TurnStatusEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回合结果写用例：将 Plan 终态聚合为 Assistant 可读消息并落库。
 */
@Slf4j
@Service
public class TurnFinalizeApplicationService {

    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final PlanFinalizationDomainService planFinalizationDomainService;

    public TurnFinalizeApplicationService(ISessionTurnRepository sessionTurnRepository,
                                          ISessionMessageRepository sessionMessageRepository,
                                          IAgentTaskRepository agentTaskRepository,
                                          PlanFinalizationDomainService planFinalizationDomainService) {
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.planFinalizationDomainService = planFinalizationDomainService;
    }

    public TurnFinalizeResult finalizeByPlan(Long planId, PlanStatusEnum planStatus) {
        if (planId == null || planStatus == null) {
            return TurnFinalizeResult.empty();
        }
        SessionTurnEntity turn = sessionTurnRepository.findByPlanId(planId);
        if (turn == null) {
            return TurnFinalizeResult.empty();
        }

        List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(planId);
        PlanFinalizationDomainService.FinalizationDecision decision =
                planFinalizationDomainService.resolveFinalization(planStatus, tasks);
        if (!decision.finalizable()) {
            return TurnFinalizeResult.empty();
        }

        if (turn.isTerminal()) {
            return finalizeForTerminalTurn(planId, turn, decision);
        }

        boolean terminalMarked = sessionTurnRepository.markTerminalIfNotTerminal(
                turn.getId(),
                decision.turnStatus(),
                decision.assistantSummary(),
                LocalDateTime.now()
        );
        if (!terminalMarked) {
            SessionTurnEntity latestTurn = sessionTurnRepository.findById(turn.getId());
            if (latestTurn == null || !latestTurn.isTerminal()) {
                return TurnFinalizeResult.empty();
            }
            if (latestTurn.getFinalResponseMessageId() == null) {
                return saveAndBindFinalMessage(planId, latestTurn, decision, FinalizeOutcome.FINALIZED);
            }
            return TurnFinalizeResult.of(latestTurn.getId(),
                    latestTurn.getFinalResponseMessageId(),
                    latestTurn.getAssistantSummary(),
                    latestTurn.getStatus(),
                    FinalizeOutcome.ALREADY_FINALIZED);
        }

        return saveAndBindFinalMessage(planId, turn, decision, FinalizeOutcome.FINALIZED);
    }

    private TurnFinalizeResult finalizeForTerminalTurn(Long planId,
                                                       SessionTurnEntity turn,
                                                       PlanFinalizationDomainService.FinalizationDecision decision) {
        if (turn.getFinalResponseMessageId() != null) {
            return TurnFinalizeResult.of(turn.getId(),
                    turn.getFinalResponseMessageId(),
                    turn.getAssistantSummary(),
                    turn.getStatus(),
                    FinalizeOutcome.ALREADY_FINALIZED);
        }
        return saveAndBindFinalMessage(planId, turn, decision, FinalizeOutcome.FINALIZED);
    }

    private TurnFinalizeResult saveAndBindFinalMessage(Long planId,
                                                       SessionTurnEntity turn,
                                                       PlanFinalizationDomainService.FinalizationDecision decision,
                                                       FinalizeOutcome outcome) {
        if (turn == null || turn.getId() == null || turn.getSessionId() == null) {
            return TurnFinalizeResult.empty();
        }
        SessionMessageEntity assistantMessage = SessionMessageEntity.assistantMessage(
                turn.getSessionId(),
                turn.getId(),
                decision.assistantContent()
        );
        SessionMessageEntity savedMessage = sessionMessageRepository.saveAssistantFinalMessageIfAbsent(assistantMessage);

        boolean bound = sessionTurnRepository.bindFinalResponseMessage(turn.getId(), savedMessage.getId());
        SessionTurnEntity latestTurn = sessionTurnRepository.findById(turn.getId());
        if (latestTurn == null) {
            return TurnFinalizeResult.empty();
        }
        Long finalMessageId = latestTurn.getFinalResponseMessageId();
        if (finalMessageId == null && bound) {
            finalMessageId = savedMessage.getId();
            latestTurn.bindFinalResponseMessage(savedMessage.getId());
        }
        if (latestTurn.getAssistantSummary() == null && decision.assistantSummary() != null) {
            latestTurn.setAssistantSummary(decision.assistantSummary());
        }

        log.info("TURN_FINALIZED planId={}, turnId={}, turnStatus={}, assistantMessageId={}",
                planId,
                latestTurn.getId(),
                latestTurn.getStatus() == null ? null : latestTurn.getStatus().name(),
                finalMessageId);

        return TurnFinalizeResult.of(latestTurn.getId(),
                finalMessageId,
                latestTurn.getAssistantSummary(),
                latestTurn.getStatus(),
                outcome == null ? FinalizeOutcome.FINALIZED : outcome);
    }

    @Data
    public static class TurnFinalizeResult {
        private Long turnId;
        private Long assistantMessageId;
        private String assistantSummary;
        private TurnStatusEnum turnStatus;
        private FinalizeOutcome outcome;

        public static TurnFinalizeResult empty() {
            TurnFinalizeResult result = new TurnFinalizeResult();
            result.setOutcome(FinalizeOutcome.SKIPPED_NOT_TERMINAL);
            return result;
        }

        public static TurnFinalizeResult of(Long turnId,
                                            Long assistantMessageId,
                                            String assistantSummary,
                                            TurnStatusEnum turnStatus,
                                            FinalizeOutcome outcome) {
            TurnFinalizeResult result = new TurnFinalizeResult();
            result.setTurnId(turnId);
            result.setAssistantMessageId(assistantMessageId);
            result.setAssistantSummary(assistantSummary);
            result.setTurnStatus(turnStatus);
            result.setOutcome(outcome == null ? FinalizeOutcome.SKIPPED_NOT_TERMINAL : outcome);
            return result;
        }
    }

    public enum FinalizeOutcome {
        FINALIZED,
        ALREADY_FINALIZED,
        SKIPPED_NOT_TERMINAL
    }
}
