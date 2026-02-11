package com.getoffer.trigger.http;

import com.getoffer.api.dto.SessionMessageDTO;
import com.getoffer.api.dto.SessionTurnDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.domain.session.model.entity.SessionMessageEntity;
import com.getoffer.domain.session.model.entity.SessionTurnEntity;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话回合与消息查询 API。
 */
@RestController
@RequestMapping("/api/sessions")
public class ConversationController {

    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;

    public ConversationController(IAgentSessionRepository agentSessionRepository,
                                  ISessionTurnRepository sessionTurnRepository,
                                  ISessionMessageRepository sessionMessageRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
    }

    @GetMapping("/{id}/turns")
    public Response<List<SessionTurnDTO>> listTurns(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return Response.<List<SessionTurnDTO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("SessionId不能为空")
                    .build();
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return Response.<List<SessionTurnDTO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
        }

        List<SessionTurnEntity> turns = sessionTurnRepository.findBySessionId(sessionId);
        List<SessionTurnDTO> data = turns == null ? Collections.emptyList() : turns.stream()
                .map(this::toTurnDTO)
                .collect(Collectors.toList());
        return Response.<List<SessionTurnDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    @GetMapping("/{id}/messages")
    public Response<List<SessionMessageDTO>> listMessages(@PathVariable("id") Long sessionId) {
        if (sessionId == null) {
            return Response.<List<SessionMessageDTO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("SessionId不能为空")
                    .build();
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return Response.<List<SessionMessageDTO>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
        }

        List<SessionMessageEntity> messages = sessionMessageRepository.findBySessionId(sessionId);
        List<SessionMessageDTO> data = messages == null ? Collections.emptyList() : messages.stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
        return Response.<List<SessionMessageDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
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
