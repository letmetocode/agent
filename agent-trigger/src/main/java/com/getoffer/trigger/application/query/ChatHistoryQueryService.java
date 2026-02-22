package com.getoffer.trigger.application.query;

import com.getoffer.api.dto.ChatHistoryResponseV3DTO;
import com.getoffer.api.dto.SessionMessageDTO;
import com.getoffer.api.dto.SessionTurnDTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 聊天历史读用例。
 */
@Service
public class ChatHistoryQueryService {

    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String ORDER_ASC = "asc";
    private static final String ORDER_DESC = "desc";

    public ChatHistoryQueryService(IAgentSessionRepository agentSessionRepository,
                                   ISessionTurnRepository sessionTurnRepository,
                                   ISessionMessageRepository sessionMessageRepository,
                                   IAgentPlanRepository agentPlanRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentPlanRepository = agentPlanRepository;
    }

    public ChatHistoryResponseV3DTO getHistory(Long sessionId, Long cursor, Integer limit, String order) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId不能为空");
        }
        if (cursor != null && cursor <= 0L) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "cursor 必须大于 0");
        }
        int normalizedLimit = normalizeLimit(limit);
        boolean ascending = resolveAscending(order);
        String normalizedOrder = ascending ? ORDER_ASC : ORDER_DESC;

        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
        }

        List<SessionTurnEntity> queriedTurns = sessionTurnRepository.findBySessionIdWithCursor(
                sessionId,
                cursor,
                normalizedLimit + 1,
                ascending
        );
        boolean hasMore = queriedTurns != null && queriedTurns.size() > normalizedLimit;
        List<SessionTurnEntity> turns = trimTurns(queriedTurns, normalizedLimit);
        Long nextCursor = resolveNextCursor(turns, hasMore);

        List<Long> turnIds = turns.stream()
                .map(SessionTurnEntity::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        List<SessionMessageEntity> messages = sessionMessageRepository.findByTurnIds(turnIds);
        List<AgentPlanEntity> plans = agentPlanRepository.findBySessionId(sessionId);

        ChatHistoryResponseV3DTO data = new ChatHistoryResponseV3DTO();
        data.setSessionId(session.getId());
        data.setUserId(session.getUserId());
        data.setTitle(session.getTitle());
        data.setAgentKey(session.getAgentKey());
        data.setScenario(session.getScenario());
        data.setLatestPlanId(resolveLatestPlanId(plans));
        data.setHasMore(hasMore);
        data.setNextCursor(nextCursor);
        data.setLimit(normalizedLimit);
        data.setOrder(normalizedOrder);
        data.setTurns(toTurnDTOList(turns, ascending));
        data.setMessages(toMessageDTOList(messages, ascending));
        return data;
    }

    private Long resolveLatestPlanId(List<AgentPlanEntity> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream()
                .filter(item -> item != null && item.getId() != null)
                .sorted(Comparator.comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentPlanEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AgentPlanEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private List<SessionTurnEntity> trimTurns(List<SessionTurnEntity> turns, int limit) {
        if (turns == null || turns.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        if (turns.size() <= limit) {
            return turns;
        }
        return turns.subList(0, limit);
    }

    private Long resolveNextCursor(List<SessionTurnEntity> turns, boolean hasMore) {
        if (!hasMore || turns == null || turns.isEmpty()) {
            return null;
        }
        SessionTurnEntity last = turns.get(turns.size() - 1);
        return last == null ? null : last.getId();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "limit 必须大于 0");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean resolveAscending(String order) {
        if (order == null || order.trim().isEmpty()) {
            return true;
        }
        String normalized = order.trim().toLowerCase(Locale.ROOT);
        if (ORDER_ASC.equals(normalized)) {
            return true;
        }
        if (ORDER_DESC.equals(normalized)) {
            return false;
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "order 仅支持 asc 或 desc");
    }

    private List<SessionTurnDTO> toTurnDTOList(List<SessionTurnEntity> turns, boolean ascending) {
        if (turns == null || turns.isEmpty()) {
            return Collections.emptyList();
        }
        Comparator<SessionTurnEntity> comparator = Comparator
                .comparing(SessionTurnEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        Stream<SessionTurnEntity> stream = turns.stream().sorted(ascending ? comparator : comparator.reversed());
        return stream
                .map(this::toTurnDTO)
                .collect(Collectors.toList());
    }

    private List<SessionMessageDTO> toMessageDTOList(List<SessionMessageEntity> messages, boolean ascending) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        Comparator<SessionMessageEntity> comparator = Comparator
                .comparing(SessionMessageEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        Stream<SessionMessageEntity> stream = messages.stream().sorted(ascending ? comparator : comparator.reversed());
        return stream
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
    }

    private SessionTurnDTO toTurnDTO(SessionTurnEntity entity) {
        SessionTurnDTO dto = new SessionTurnDTO();
        dto.setTurnId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setPlanId(entity.getPlanId());
        dto.setUserMessage(entity.getUserMessage());
        dto.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        dto.setFinalResponseMessageId(entity.getFinalResponseMessageId());
        dto.setAssistantSummary(entity.getAssistantSummary());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    private SessionMessageDTO toMessageDTO(SessionMessageEntity entity) {
        SessionMessageDTO dto = new SessionMessageDTO();
        dto.setMessageId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTurnId(entity.getTurnId());
        dto.setRole(entity.getRole() == null ? null : entity.getRole().name());
        dto.setContent(entity.getContent());
        dto.setMetadata(entity.getMetadata());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
