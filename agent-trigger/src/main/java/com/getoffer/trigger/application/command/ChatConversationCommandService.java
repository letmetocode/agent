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
import com.getoffer.types.enums.MessageRoleEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.TurnStatusEnum;
import com.getoffer.types.exception.AppException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话写用例：统一处理会话创建/复用、回合创建、规划触发与失败兜底。
 */
@Slf4j
@Service
public class ChatConversationCommandService {

    private static final int TITLE_MAX_LENGTH = 60;

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;
    private final IAgentRegistryRepository agentRegistryRepository;

    public ChatConversationCommandService(PlannerService plannerService,
                                          IAgentSessionRepository agentSessionRepository,
                                          ISessionTurnRepository sessionTurnRepository,
                                          ISessionMessageRepository sessionMessageRepository,
                                          IRoutingDecisionRepository routingDecisionRepository,
                                          IAgentRegistryRepository agentRegistryRepository) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.agentRegistryRepository = agentRegistryRepository;
    }

    public ConversationSubmitResult submitMessage(ChatMessageSubmitRequestV3DTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId 不能为空");
        }
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "message 不能为空");
        }

        AgentSessionEntity session = resolveOrCreateSession(request);
        SessionTurnEntity savedTurn = null;
        String userMessage = request.getMessage().trim();

        try {
            savedTurn = createTurn(session, userMessage);
            AgentPlanEntity plan = plannerService.createPlan(
                    session.getId(),
                    userMessage,
                    buildPlanExtraContext(session, request, savedTurn.getId())
            );

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
            String errorMessage = resolveErrorMessage(ex);
            markTurnAsFailed(savedTurn, session.getId(), errorMessage);
            log.error("CHAT_V3_SUBMIT_FAILED sessionId={}, turnId={}, reason={}",
                    session.getId(),
                    savedTurn == null ? null : savedTurn.getId(),
                    errorMessage,
                    ex);
            if (ex instanceof AppException appException) {
                throw new AppException(appException.getCode(), errorMessage, ex);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), errorMessage, ex);
        }
    }

    private AgentSessionEntity resolveOrCreateSession(ChatMessageSubmitRequestV3DTO request) {
        Long sessionId = request.getSessionId();
        String userId = request.getUserId().trim();
        if (sessionId != null) {
            AgentSessionEntity session = agentSessionRepository.findById(sessionId);
            if (session == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
            }
            if (!StringUtils.equals(StringUtils.trimToEmpty(session.getUserId()), userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不属于当前 userId");
            }
            return session;
        }

        String resolvedAgentKey = resolveAgentKey(request.getAgentKey());
        String scenario = StringUtils.defaultIfBlank(StringUtils.trimToNull(request.getScenario()), "CHAT_DEFAULT");
        String title = resolveTitle(request.getTitle(), request.getMessage());

        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setUserId(userId);
        entity.updateTitle(title);
        entity.assignAgentProfile(resolvedAgentKey, scenario);
        entity.updateMetaInfo(mergeMetaInfo(request.getMetaInfo(), resolvedAgentKey, scenario));
        entity.activate();
        return agentSessionRepository.save(entity);
    }

    private String resolveAgentKey(String agentKey) {
        if (StringUtils.isNotBlank(agentKey)) {
            AgentRegistryEntity agent = agentRegistryRepository.findByKey(agentKey.trim());
            if (agent == null || !Boolean.TRUE.equals(agent.getIsActive())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentKey 不存在或未激活: " + agentKey);
            }
            return agent.getKey();
        }

        List<AgentRegistryEntity> activeAgents = agentRegistryRepository.findByActive(true);
        if (activeAgents == null || activeAgents.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "暂无可用 Agent，请先激活 assistant 或其他 Agent");
        }

        List<AgentRegistryEntity> candidates = new ArrayList<>(activeAgents);
        candidates.sort(Comparator.comparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()));
        for (AgentRegistryEntity item : candidates) {
            if (StringUtils.equalsIgnoreCase(item.getKey(), "assistant")) {
                return item.getKey();
            }
        }
        return candidates.get(0).getKey();
    }

    private String resolveTitle(String requestTitle, String message) {
        if (StringUtils.isNotBlank(requestTitle)) {
            String normalized = requestTitle.trim();
            return normalized.length() <= TITLE_MAX_LENGTH
                    ? normalized
                    : normalized.substring(0, TITLE_MAX_LENGTH);
        }
        String normalizedMessage = StringUtils.defaultString(message).replace('\n', ' ').trim();
        if (normalizedMessage.isEmpty()) {
            return "新聊天";
        }
        return normalizedMessage.length() <= TITLE_MAX_LENGTH
                ? normalizedMessage
                : normalizedMessage.substring(0, TITLE_MAX_LENGTH);
    }

    private Map<String, Object> mergeMetaInfo(Map<String, Object> requestMeta, String agentKey, String scenario) {
        Map<String, Object> merged = new HashMap<>();
        if (requestMeta != null && !requestMeta.isEmpty()) {
            merged.putAll(requestMeta);
        }
        merged.put("entry", "chat-v3");
        merged.put("agentKey", agentKey);
        merged.put("scenario", scenario);
        return merged;
    }

    private SessionTurnEntity createTurn(AgentSessionEntity session, String message) {
        SessionTurnEntity turn = session.createTurn(message);
        SessionTurnEntity savedTurn = sessionTurnRepository.save(turn);

        SessionMessageEntity userMessage = new SessionMessageEntity();
        userMessage.setSessionId(session.getId());
        userMessage.setTurnId(savedTurn.getId());
        userMessage.setRole(MessageRoleEnum.USER);
        userMessage.setContent(message);
        sessionMessageRepository.save(userMessage);
        return savedTurn;
    }

    private Map<String, Object> buildPlanExtraContext(AgentSessionEntity session,
                                                      ChatMessageSubmitRequestV3DTO request,
                                                      Long turnId) {
        Map<String, Object> extra = new HashMap<>();
        if (request != null && request.getContextOverrides() != null && !request.getContextOverrides().isEmpty()) {
            extra.putAll(request.getContextOverrides());
        }
        extra.put("turnId", turnId);
        extra.put("entry", "chat-v3");
        if (session != null) {
            if (StringUtils.isNotBlank(session.getAgentKey())) {
                extra.put("agentKey", session.getAgentKey());
            }
            if (StringUtils.isNotBlank(session.getScenario())) {
                extra.put("scenario", session.getScenario());
            }
        }
        SessionTurnEntity latestCompleted = sessionTurnRepository.findLatestBySessionIdAndStatus(session.getId(), TurnStatusEnum.COMPLETED);
        if (latestCompleted != null && StringUtils.isNotBlank(latestCompleted.getAssistantSummary())) {
            extra.put("lastAssistantSummary", latestCompleted.getAssistantSummary());
            extra.put("lastTurnId", latestCompleted.getId());
        }
        return extra;
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
        String finalErrorMessage = StringUtils.defaultIfBlank(errorMessage, "本轮规划失败，请稍后重试。");
        try {
            turn.markFailed(finalErrorMessage, LocalDateTime.now());
            sessionTurnRepository.update(turn);
        } catch (Exception updateEx) {
            log.warn("更新回合失败状态失败: turnId={}, error={}", turn.getId(), updateEx.getMessage());
        }
        try {
            SessionMessageEntity assistantMessage = new SessionMessageEntity();
            assistantMessage.setSessionId(sessionId);
            assistantMessage.setTurnId(turn.getId());
            assistantMessage.setRole(MessageRoleEnum.ASSISTANT);
            assistantMessage.setContent(finalErrorMessage);
            SessionMessageEntity saved = sessionMessageRepository.save(assistantMessage);
            turn.bindFinalResponseMessage(saved.getId());
            sessionTurnRepository.update(turn);
        } catch (Exception messageEx) {
            log.warn("写入失败消息失败: turnId={}, error={}", turn.getId(), messageEx.getMessage());
        }
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex == null) {
            return "触发规划失败：未知异常";
        }
        if (ex instanceof AppException appException && StringUtils.isNotBlank(appException.getInfo())) {
            return appException.getInfo();
        }
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        if (cursor instanceof AppException appException && StringUtils.isNotBlank(appException.getInfo())) {
            return appException.getInfo();
        }
        String message = StringUtils.defaultIfBlank(cursor.getMessage(), ex.getMessage());
        if (StringUtils.isBlank(message)) {
            return "触发规划失败：" + ex.getClass().getSimpleName();
        }
        return message;
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
