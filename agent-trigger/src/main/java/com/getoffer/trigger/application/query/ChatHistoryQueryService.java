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
import java.util.stream.Collectors;

/**
 * 聊天历史读用例。
 */
@Service
public class ChatHistoryQueryService {

    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentPlanRepository agentPlanRepository;

    public ChatHistoryQueryService(IAgentSessionRepository agentSessionRepository,
                                   ISessionTurnRepository sessionTurnRepository,
                                   ISessionMessageRepository sessionMessageRepository,
                                   IAgentPlanRepository agentPlanRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentPlanRepository = agentPlanRepository;
    }

    public ChatHistoryResponseV3DTO getHistory(Long sessionId) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
        }

        List<SessionTurnEntity> turns = sessionTurnRepository.findBySessionId(sessionId);
        List<SessionMessageEntity> messages = sessionMessageRepository.findBySessionId(sessionId);
        List<AgentPlanEntity> plans = agentPlanRepository.findBySessionId(sessionId);

        ChatHistoryResponseV3DTO data = new ChatHistoryResponseV3DTO();
        data.setSessionId(session.getId());
        data.setUserId(session.getUserId());
        data.setTitle(session.getTitle());
        data.setAgentKey(session.getAgentKey());
        data.setScenario(session.getScenario());
        data.setLatestPlanId(resolveLatestPlanId(plans));
        data.setTurns(toTurnDTOList(turns));
        data.setMessages(toMessageDTOList(messages));
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

    private List<SessionTurnDTO> toTurnDTOList(List<SessionTurnEntity> turns) {
        if (turns == null || turns.isEmpty()) {
            return Collections.emptyList();
        }
        return turns.stream()
                .sorted(Comparator.comparing(SessionTurnEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SessionTurnEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTurnDTO)
                .collect(Collectors.toList());
    }

    private List<SessionMessageDTO> toMessageDTOList(List<SessionMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .sorted(Comparator.comparing(SessionMessageEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SessionMessageEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
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
