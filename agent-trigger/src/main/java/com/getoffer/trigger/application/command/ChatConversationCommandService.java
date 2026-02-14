package com.getoffer.trigger.application.command;

import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.domain.session.service.SessionConversationDomainService;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.exception.AppException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 聊天会话写用例：统一处理会话创建/复用、回合创建、规划触发与失败兜底。
 */
@Slf4j
@Service
public class ChatConversationCommandService {

    private static final String SUBMISSION_STATE_ACCEPTED = "ACCEPTED";
    private static final String SUBMISSION_STATE_DUPLICATE = "DUPLICATE";

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final SessionConversationDomainService sessionConversationDomainService;
    private final Executor commonThreadPoolExecutor;

    public ChatConversationCommandService(PlannerService plannerService,
                                          IAgentSessionRepository agentSessionRepository,
                                          ISessionTurnRepository sessionTurnRepository,
                                          ISessionMessageRepository sessionMessageRepository,
                                          IAgentPlanRepository agentPlanRepository,
                                          IRoutingDecisionRepository routingDecisionRepository,
                                          IAgentRegistryRepository agentRegistryRepository,
                                          SessionConversationDomainService sessionConversationDomainService,
                                          @Qualifier("commonThreadPoolExecutor") Executor commonThreadPoolExecutor) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.agentRegistryRepository = agentRegistryRepository;
        this.sessionConversationDomainService = sessionConversationDomainService;
        this.commonThreadPoolExecutor = commonThreadPoolExecutor;
    }

    public ConversationSubmitResult submitMessage(ChatMessageSubmitRequestV3DTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "message 不能为空");
        }

        String clientMessageId = normalizeClientMessageId(request.getClientMessageId());
        request.setClientMessageId(clientMessageId);

        SessionConversationDomainService.SessionPreparationResult sessionResult = resolveOrCreateSession(request);
        AgentSessionEntity session = sessionResult.session();
        SessionTurnEntity savedTurn = null;
        String userMessage = sessionResult.normalizedUserMessage();

        try {
            SessionTurnEntity duplicatedTurn = sessionTurnRepository.findBySessionIdAndClientMessageId(session.getId(), clientMessageId);
            if (duplicatedTurn != null) {
                return buildDuplicateResult(session, duplicatedTurn);
            }

            savedTurn = createTurn(session, userMessage, clientMessageId);
            SessionTurnEntity latestCompletedTurn = sessionTurnRepository.findLatestBySessionIdAndStatus(session.getId(), TurnStatusEnum.COMPLETED);
            Map<String, Object> extraContext = sessionConversationDomainService.buildPlanExtraContext(
                    new SessionConversationDomainService.PlanContextCommand(
                            session,
                            savedTurn.getId(),
                            request.getContextOverrides(),
                            latestCompletedTurn
                    )
            );
            extraContext.put("clientMessageId", clientMessageId);
            dispatchAsyncPlanning(session, savedTurn, userMessage, extraContext);

            ConversationSubmitResult result = new ConversationSubmitResult();
            result.setSessionId(session.getId());
            result.setSessionTitle(session.getTitle());
            result.setTurnId(savedTurn.getId());
            result.setTurnStatus(TurnStatusEnum.PLANNING.name());
            result.setAccepted(true);
            result.setSubmissionState(SUBMISSION_STATE_ACCEPTED);
            result.setAcceptedAt(savedTurn.getCreatedAt() == null ? LocalDateTime.now() : savedTurn.getCreatedAt());
            result.setAssistantMessage("收到，本轮任务已开始执行。");

            log.info("CHAT_V3_ACCEPTED sessionId={}, turnId={}, clientMessageId={}, submissionState={}",
                    session.getId(),
                    savedTurn.getId(),
                    clientMessageId,
                    SUBMISSION_STATE_ACCEPTED);
            return result;
        } catch (Exception ex) {
            String errorMessage = sessionConversationDomainService.resolveErrorMessage(ex);
            markTurnAsFailed(savedTurn, session.getId(), errorMessage);
            log.error("CHAT_V3_SUBMIT_FAILED sessionId={}, turnId={}, reason={}",
                    session.getId(),
                    savedTurn == null ? null : savedTurn.getId(),
                    errorMessage,
                    ex);
            if (ex instanceof AppException appException) {
                throw new AppException(appException.getCode(), errorMessage, ex);
            }
            if (ex instanceof IllegalStateException) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), errorMessage, ex);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), errorMessage, ex);
        }
    }

    private ConversationSubmitResult buildDuplicateResult(AgentSessionEntity session, SessionTurnEntity existingTurn) {
        ConversationSubmitResult result = new ConversationSubmitResult();
        result.setSessionId(session.getId());
        result.setSessionTitle(session.getTitle());
        result.setTurnId(existingTurn.getId());
        result.setPlanId(existingTurn.getPlanId());
        result.setTurnStatus(existingTurn.getStatus() == null ? null : existingTurn.getStatus().name());
        result.setAccepted(true);
        result.setSubmissionState(SUBMISSION_STATE_DUPLICATE);
        result.setAcceptedAt(existingTurn.getCreatedAt());
        result.setAssistantMessage("检测到重复提交，已复用已有回合。");

        if (existingTurn.getPlanId() != null) {
            AgentPlanEntity existingPlan = agentPlanRepository.findById(existingTurn.getPlanId());
            if (existingPlan != null) {
                result.setPlanGoal(existingPlan.getPlanGoal());
                if (existingPlan.getRouteDecisionId() != null) {
                    result.setRoutingDecision(toRoutingDecisionDTO(routingDecisionRepository.findById(existingPlan.getRouteDecisionId())));
                }
            }
        }

        log.info("CHAT_V3_DUPLICATE_ACCEPTED sessionId={}, turnId={}, planId={}",
                result.getSessionId(),
                result.getTurnId(),
                result.getPlanId());
        return result;
    }

    private SessionConversationDomainService.SessionPreparationResult resolveOrCreateSession(ChatMessageSubmitRequestV3DTO request) {
        AgentSessionEntity existingSession = null;
        AgentRegistryEntity explicitAgent = null;
        List<AgentRegistryEntity> activeAgents = null;

        if (request.getSessionId() != null) {
            existingSession = agentSessionRepository.findById(request.getSessionId());
            if (existingSession == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
            }
        } else if (StringUtils.isNotBlank(request.getAgentKey())) {
            explicitAgent = agentRegistryRepository.findByKey(request.getAgentKey().trim());
        } else {
            activeAgents = agentRegistryRepository.findByActive(true);
        }

        SessionConversationDomainService.SessionPreparationCommand command =
                new SessionConversationDomainService.SessionPreparationCommand(
                        request.getUserId(),
                        request.getMessage(),
                        request.getTitle(),
                        request.getAgentKey(),
                        request.getScenario(),
                        request.getMetaInfo(),
                        existingSession,
                        explicitAgent,
                        activeAgents
                );

        SessionConversationDomainService.SessionPreparationResult result;
        try {
            result = sessionConversationDomainService.prepareSession(command);
        } catch (IllegalStateException ex) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ex.getMessage(), ex);
        }

        if (result.newSession()) {
            AgentSessionEntity saved = agentSessionRepository.save(result.session());
            return SessionConversationDomainService.SessionPreparationResult.newSession(saved, result.normalizedUserMessage());
        }
        return result;
    }

    private SessionTurnEntity createTurn(AgentSessionEntity session, String message, String clientMessageId) {
        SessionTurnEntity turn = sessionConversationDomainService.createPlanningTurn(session, message);
        Map<String, Object> turnMetadata = new HashMap<>();
        turnMetadata.put("clientMessageId", clientMessageId);
        turnMetadata.put("entry", "chat-v3");
        turn.setMetadata(turnMetadata);
        SessionTurnEntity savedTurn = sessionTurnRepository.save(turn);

        SessionMessageEntity userMessage = sessionConversationDomainService.createUserMessage(
                session.getId(),
                savedTurn.getId(),
                message,
                Map.of("clientMessageId", clientMessageId)
        );
        sessionMessageRepository.save(userMessage);
        return savedTurn;
    }

    private void dispatchAsyncPlanning(AgentSessionEntity session,
                                       SessionTurnEntity savedTurn,
                                       String userMessage,
                                       Map<String, Object> extraContext) {
        try {
            commonThreadPoolExecutor.execute(() -> runPlanningAsync(session, savedTurn, userMessage, extraContext));
        } catch (Exception ex) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "规划任务派发失败，请稍后重试", ex);
        }
    }

    private void runPlanningAsync(AgentSessionEntity session,
                                  SessionTurnEntity savedTurn,
                                  String userMessage,
                                  Map<String, Object> extraContext) {
        if (session == null || savedTurn == null || savedTurn.getId() == null) {
            return;
        }
        try {
            AgentPlanEntity plan = plannerService.createPlan(session.getId(), userMessage, extraContext);

            SessionTurnEntity latestTurn = sessionTurnRepository.findById(savedTurn.getId());
            if (latestTurn == null || latestTurn.isTerminal()) {
                return;
            }
            latestTurn.markExecuting(plan.getId());
            sessionTurnRepository.update(latestTurn);

            log.info("CHAT_V3_PLAN_BOUND sessionId={}, turnId={}, planId={}, routeDecisionId={}",
                    session.getId(),
                    latestTurn.getId(),
                    plan.getId(),
                    plan.getRouteDecisionId());
        } catch (Exception ex) {
            String errorMessage = sessionConversationDomainService.resolveErrorMessage(ex);
            markTurnAsFailed(savedTurn, session.getId(), errorMessage);
            log.error("CHAT_V3_PLAN_ASYNC_FAILED sessionId={}, turnId={}, reason={}",
                    session.getId(),
                    savedTurn.getId(),
                    errorMessage,
                    ex);
        }
    }

    private String normalizeClientMessageId(String raw) {
        if (StringUtils.isNotBlank(raw)) {
            return StringUtils.abbreviate(raw.trim(), 128);
        }
        return "cm-" + UUID.randomUUID();
    }

    private RoutingDecisionDTO toRoutingDecisionDTO(RoutingDecisionEntity entity) {
        if (entity == null) {
            return null;
        }
        RoutingDecisionDTO dto = new RoutingDecisionDTO();
        dto.setRoutingDecisionId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTurnId(entity.getTurnId());
        dto.setDecisionType(entity.getDecisionType() == null ? null : entity.getDecisionType().name());
        dto.setStrategy(entity.getStrategy());
        dto.setScore(entity.getScore());
        dto.setReason(entity.getReason());
        dto.setSourceType(entity.getSourceType());
        dto.setFallbackFlag(entity.getFallbackFlag());
        dto.setFallbackReason(entity.getFallbackReason());
        dto.setPlannerAttempts(entity.getPlannerAttempts());
        dto.setDefinitionId(entity.getDefinitionId());
        dto.setDefinitionKey(entity.getDefinitionKey());
        dto.setDefinitionVersion(entity.getDefinitionVersion());
        dto.setDraftId(entity.getDraftId());
        dto.setDraftKey(entity.getDraftKey());
        dto.setMetadata(entity.getMetadata());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private void markTurnAsFailed(SessionTurnEntity turn, Long sessionId, String errorMessage) {
        if (turn == null || turn.getId() == null) {
            return;
        }
        String finalErrorMessage = sessionConversationDomainService.resolveFailureMessage(errorMessage);
        try {
            turn.markFailed(finalErrorMessage, LocalDateTime.now());
            sessionTurnRepository.update(turn);
        } catch (Exception updateEx) {
            log.warn("更新回合失败状态失败: turnId={}, error={}", turn.getId(), updateEx.getMessage());
        }
        try {
            SessionMessageEntity assistantMessage = sessionConversationDomainService.createFailureAssistantMessage(sessionId, turn.getId(), finalErrorMessage);
            SessionMessageEntity saved = sessionMessageRepository.save(assistantMessage);
            turn.bindFinalResponseMessage(saved.getId());
            sessionTurnRepository.update(turn);
        } catch (Exception messageEx) {
            log.warn("写入失败消息失败: turnId={}, error={}", turn.getId(), messageEx.getMessage());
        }
    }

    @Data
    public static class ConversationSubmitResult {
        private Long sessionId;
        private String sessionTitle;
        private Long turnId;
        private Long planId;
        private String planGoal;
        private String turnStatus;
        private Boolean accepted;
        private String submissionState;
        private LocalDateTime acceptedAt;
        private String assistantMessage;
        private RoutingDecisionDTO routingDecision;
    }
}
