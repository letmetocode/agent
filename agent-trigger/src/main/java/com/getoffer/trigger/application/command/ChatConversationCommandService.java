package com.getoffer.trigger.application.command;

import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话写用例：统一处理会话创建/复用、回合创建、规划触发与失败兜底。
 */
@Slf4j
@Service
public class ChatConversationCommandService {

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;
    private final IAgentRegistryRepository agentRegistryRepository;
    private final SessionConversationDomainService sessionConversationDomainService;

    public ChatConversationCommandService(PlannerService plannerService,
                                          IAgentSessionRepository agentSessionRepository,
                                          ISessionTurnRepository sessionTurnRepository,
                                          ISessionMessageRepository sessionMessageRepository,
                                          IRoutingDecisionRepository routingDecisionRepository,
                                          IAgentRegistryRepository agentRegistryRepository,
                                          SessionConversationDomainService sessionConversationDomainService) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.agentRegistryRepository = agentRegistryRepository;
        this.sessionConversationDomainService = sessionConversationDomainService;
    }

    public ConversationSubmitResult submitMessage(ChatMessageSubmitRequestV3DTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "message 不能为空");
        }

        SessionConversationDomainService.SessionPreparationResult sessionResult = resolveOrCreateSession(request);
        AgentSessionEntity session = sessionResult.session();
        SessionTurnEntity savedTurn = null;
        String userMessage = sessionResult.normalizedUserMessage();

        try {
            savedTurn = createTurn(session, userMessage);
            SessionTurnEntity latestCompletedTurn = sessionTurnRepository.findLatestBySessionIdAndStatus(session.getId(), TurnStatusEnum.COMPLETED);
            Map<String, Object> extraContext = sessionConversationDomainService.buildPlanExtraContext(
                    new SessionConversationDomainService.PlanContextCommand(
                            session,
                            savedTurn.getId(),
                            request.getContextOverrides(),
                            latestCompletedTurn
                    )
            );

            AgentPlanEntity plan = plannerService.createPlan(session.getId(), userMessage, extraContext);
            savedTurn.markExecuting(plan.getId());
            sessionTurnRepository.update(savedTurn);

            RoutingDecisionDTO routingDecision = null;
            if (plan.getRouteDecisionId() != null) {
                routingDecision = toRoutingDecisionDTO(routingDecisionRepository.findById(plan.getRouteDecisionId()));
            }

            ConversationSubmitResult result = new ConversationSubmitResult();
            result.setSessionId(session.getId());
            result.setSessionTitle(session.getTitle());
            result.setTurnId(savedTurn.getId());
            result.setPlanId(plan.getId());
            result.setPlanGoal(plan.getPlanGoal());
            result.setTurnStatus(TurnStatusEnum.EXECUTING.name());
            result.setRoutingDecision(routingDecision);
            result.setAssistantMessage("收到，本轮任务已开始执行。");

            log.info("CHAT_V3_ACCEPTED sessionId={}, turnId={}, planId={}, routeDecisionId={}",
                    session.getId(),
                    savedTurn.getId(),
                    plan.getId(),
                    plan.getRouteDecisionId());
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

    private SessionTurnEntity createTurn(AgentSessionEntity session, String message) {
        SessionTurnEntity turn = sessionConversationDomainService.createPlanningTurn(session, message);
        SessionTurnEntity savedTurn = sessionTurnRepository.save(turn);

        SessionMessageEntity userMessage = sessionConversationDomainService.createUserMessage(session.getId(), savedTurn.getId(), message);
        sessionMessageRepository.save(userMessage);
        return savedTurn;
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
        private String assistantMessage;
        private RoutingDecisionDTO routingDecision;
    }
}
