package com.getoffer.domain.session.service;

import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.types.exception.AppException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话编排领域服务：封装会话创建、标题与 Agent 选择、上下文构建、失败语义等规则。
 */
@Service
public class SessionConversationDomainService {

    private static final int TITLE_MAX_LENGTH = 60;
    private static final String DEFAULT_SCENARIO = "CHAT_DEFAULT";
    private static final String CHAT_ENTRY = "chat-v3";
    private static final String DEFAULT_FAILURE_MESSAGE = "本轮规划失败，请稍后重试。";

    public SessionPreparationResult prepareSession(SessionPreparationCommand command) {
        if (command == null) {
            throw new IllegalStateException("Session preparation command cannot be null");
        }
        String userId = normalizeRequired(command.userId(), "userId 不能为空");
        String userMessage = normalizeRequired(command.message(), "message 不能为空");

        if (command.existingSession() != null) {
            AgentSessionEntity existing = command.existingSession();
            if (!trimToEmpty(existing.getUserId()).equals(userId)) {
                throw new IllegalStateException("会话不属于当前 userId");
            }
            return SessionPreparationResult.existing(existing, userMessage);
        }

        String resolvedAgentKey = resolveAgentKey(command.requestedAgentKey(), command.explicitAgent(), command.activeAgents());
        String scenario = defaultIfBlank(trimToNull(command.requestedScenario()), DEFAULT_SCENARIO);
        String title = resolveTitle(command.requestedTitle(), userMessage);

        AgentSessionEntity session = new AgentSessionEntity();
        session.setUserId(userId);
        session.updateTitle(title);
        session.assignAgentProfile(resolvedAgentKey, scenario);
        session.updateMetaInfo(mergeMetaInfo(command.requestMeta(), resolvedAgentKey, scenario));
        session.activate();

        return SessionPreparationResult.newSession(session, userMessage);
    }

    public SessionTurnEntity createPlanningTurn(AgentSessionEntity session, String userMessage) {
        if (session == null) {
            throw new IllegalStateException("Session cannot be null when creating turn");
        }
        return session.createTurn(normalizeRequired(userMessage, "message 不能为空"));
    }

    public SessionMessageEntity createUserMessage(Long sessionId, Long turnId, String content) {
        return SessionMessageEntity.userMessage(sessionId, turnId, normalizeRequired(content, "message 不能为空"));
    }

    public SessionMessageEntity createUserMessage(Long sessionId,
                                                  Long turnId,
                                                  String content,
                                                  Map<String, Object> metadata) {
        SessionMessageEntity entity = createUserMessage(sessionId, turnId, content);
        if (metadata != null && !metadata.isEmpty()) {
            entity.setMetadata(new HashMap<>(metadata));
        }
        return entity;
    }

    public SessionMessageEntity createFailureAssistantMessage(Long sessionId, Long turnId, String errorMessage) {
        return SessionMessageEntity.assistantMessage(sessionId, turnId, resolveFailureMessage(errorMessage));
    }

    public Map<String, Object> buildPlanExtraContext(PlanContextCommand command) {
        if (command == null) {
            return Map.of();
        }
        Map<String, Object> context = new HashMap<>();
        if (command.contextOverrides() != null && !command.contextOverrides().isEmpty()) {
            context.putAll(command.contextOverrides());
        }
        context.put("turnId", command.turnId());
        context.put("entry", CHAT_ENTRY);

        AgentSessionEntity session = command.session();
        if (session != null) {
            if (hasText(session.getAgentKey())) {
                context.put("agentKey", session.getAgentKey());
            }
            if (hasText(session.getScenario())) {
                context.put("scenario", session.getScenario());
            }
        }

        SessionTurnEntity latestCompletedTurn = command.latestCompletedTurn();
        if (latestCompletedTurn != null && hasText(latestCompletedTurn.getAssistantSummary())) {
            context.put("lastAssistantSummary", latestCompletedTurn.getAssistantSummary());
            context.put("lastTurnId", latestCompletedTurn.getId());
        }
        return context;
    }

    public String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "触发规划失败：未知异常";
        }
        if (throwable instanceof AppException appException && hasText(appException.getInfo())) {
            return appException.getInfo();
        }

        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        if (cursor instanceof AppException appException && hasText(appException.getInfo())) {
            return appException.getInfo();
        }

        String message = defaultIfBlank(cursor.getMessage(), throwable.getMessage());
        if (!hasText(message)) {
            return "触发规划失败：" + throwable.getClass().getSimpleName();
        }
        return message;
    }

    public String resolveFailureMessage(String rawMessage) {
        return defaultIfBlank(rawMessage, DEFAULT_FAILURE_MESSAGE);
    }

    private String resolveAgentKey(String requestedAgentKey,
                                   AgentRegistryEntity explicitAgent,
                                   List<AgentRegistryEntity> activeAgents) {
        if (hasText(requestedAgentKey)) {
            if (explicitAgent == null || !Boolean.TRUE.equals(explicitAgent.getIsActive())) {
                throw new IllegalStateException("agentKey 不存在或未激活: " + requestedAgentKey);
            }
            return explicitAgent.getKey();
        }

        if (activeAgents == null || activeAgents.isEmpty()) {
            throw new IllegalStateException("暂无可用 Agent，请先激活 assistant 或其他 Agent");
        }

        List<AgentRegistryEntity> candidates = new ArrayList<>(activeAgents);
        candidates.sort(Comparator.comparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()));
        for (AgentRegistryEntity item : candidates) {
            if (item != null && hasText(item.getKey()) && "assistant".equalsIgnoreCase(item.getKey()) && Boolean.TRUE.equals(item.getIsActive())) {
                return item.getKey();
            }
        }

        AgentRegistryEntity first = candidates.get(0);
        if (first == null || !hasText(first.getKey())) {
            throw new IllegalStateException("暂无可用 Agent，请先激活 assistant 或其他 Agent");
        }
        return first.getKey();
    }

    private String resolveTitle(String requestedTitle, String message) {
        if (hasText(requestedTitle)) {
            String normalized = requestedTitle.trim();
            return normalized.length() <= TITLE_MAX_LENGTH
                    ? normalized
                    : normalized.substring(0, TITLE_MAX_LENGTH);
        }
        String normalizedMessage = safe(message).replace('\n', ' ').trim();
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
        merged.put("entry", CHAT_ENTRY);
        merged.put("agentKey", agentKey);
        merged.put("scenario", scenario);
        return merged;
    }

    private String normalizeRequired(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record SessionPreparationCommand(String userId,
                                            String message,
                                            String requestedTitle,
                                            String requestedAgentKey,
                                            String requestedScenario,
                                            Map<String, Object> requestMeta,
                                            AgentSessionEntity existingSession,
                                            AgentRegistryEntity explicitAgent,
                                            List<AgentRegistryEntity> activeAgents) {
    }

    public record PlanContextCommand(AgentSessionEntity session,
                                     Long turnId,
                                     Map<String, Object> contextOverrides,
                                     SessionTurnEntity latestCompletedTurn) {
    }

    public record SessionPreparationResult(AgentSessionEntity session,
                                           String normalizedUserMessage,
                                           boolean newSession) {

        public static SessionPreparationResult existing(AgentSessionEntity session, String normalizedUserMessage) {
            return new SessionPreparationResult(session, normalizedUserMessage, false);
        }

        public static SessionPreparationResult newSession(AgentSessionEntity session, String normalizedUserMessage) {
            return new SessionPreparationResult(session, normalizedUserMessage, true);
        }
    }
}
