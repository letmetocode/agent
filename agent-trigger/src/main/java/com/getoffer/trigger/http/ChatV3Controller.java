package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatHistoryResponseV3DTO;
import com.getoffer.api.dto.ChatMessageSubmitRequestV3DTO;
import com.getoffer.api.dto.ChatMessageSubmitResponseV3DTO;
import com.getoffer.api.dto.SessionMessageDTO;
import com.getoffer.api.dto.SessionTurnDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.trigger.service.ConversationOrchestratorService;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * V3 Chat API：提供会话编排聚合能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/v3/chat")
public class ChatV3Controller {

    private final ConversationOrchestratorService conversationOrchestratorService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IAgentPlanRepository agentPlanRepository;

    public ChatV3Controller(ConversationOrchestratorService conversationOrchestratorService,
                            IAgentSessionRepository agentSessionRepository,
                            ISessionTurnRepository sessionTurnRepository,
                            ISessionMessageRepository sessionMessageRepository,
                            IAgentPlanRepository agentPlanRepository) {
        this.conversationOrchestratorService = conversationOrchestratorService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.agentPlanRepository = agentPlanRepository;
    }

    @PostMapping("/messages")
    public Response<ChatMessageSubmitResponseV3DTO> submitMessage(@RequestBody ChatMessageSubmitRequestV3DTO request) {
        ConversationOrchestratorService.ConversationSubmitResult result = conversationOrchestratorService.submitMessage(request);

        ChatMessageSubmitResponseV3DTO data = new ChatMessageSubmitResponseV3DTO();
        data.setSessionId(result.getSessionId());
        data.setTurnId(result.getTurnId());
        data.setPlanId(result.getPlanId());
        data.setTurnStatus(result.getTurnStatus());
        data.setSessionTitle(result.getSessionTitle());
        data.setAssistantMessage(result.getAssistantMessage());
        data.setRoutingDecision(result.getRoutingDecision());
        data.setStreamPath(String.format("/api/v3/chat/sessions/%d/stream?planId=%d", result.getSessionId(), result.getPlanId()));
        data.setHistoryPath(String.format("/api/v3/chat/sessions/%d/history", result.getSessionId()));

        return Response.<ChatMessageSubmitResponseV3DTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    @GetMapping("/sessions/{id}/history")
    public Response<ChatHistoryResponseV3DTO> getHistory(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return Response.<ChatHistoryResponseV3DTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("SessionId不能为空")
                    .build();
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return Response.<ChatHistoryResponseV3DTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
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

        return Response.<ChatHistoryResponseV3DTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
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
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
