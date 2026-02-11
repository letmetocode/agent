package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatRequestDTO;
import com.getoffer.api.dto.ChatResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话聊天 API
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;

    public ChatController(PlannerService plannerService,
                          IAgentSessionRepository agentSessionRepository,
                          ISessionTurnRepository sessionTurnRepository,
                          ISessionMessageRepository sessionMessageRepository) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
    }

    @PostMapping("/{id}/chat")
    public Response<ChatResponseDTO> chat(@PathVariable("id") Long sessionId,
                                          @RequestBody ChatRequestDTO request) {
        if (sessionId == null) {
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("SessionId不能为空")
                    .build();
        }
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("消息不能为空")
                    .build();
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
        }
        SessionTurnEntity savedTurn = null;
        try {
            SessionTurnEntity turn = new SessionTurnEntity();
            turn.setSessionId(sessionId);
            turn.setUserMessage(request.getMessage());
            turn.setStatus(TurnStatusEnum.PLANNING);
            savedTurn = sessionTurnRepository.save(turn);

            SessionMessageEntity userMessage = new SessionMessageEntity();
            userMessage.setSessionId(sessionId);
            userMessage.setTurnId(savedTurn.getId());
            userMessage.setRole(MessageRoleEnum.USER);
            userMessage.setContent(request.getMessage());
            sessionMessageRepository.save(userMessage);

            AgentPlanEntity plan = plannerService.createPlan(
                    sessionId,
                    request.getMessage(),
                    buildPlanExtraContext(sessionId, request, savedTurn.getId())
            );

            savedTurn.setPlanId(plan.getId());
            savedTurn.setStatus(TurnStatusEnum.EXECUTING);
            sessionTurnRepository.update(savedTurn);

            log.info("CHAT_ACCEPTED sessionId={}, turnId={}, planId={}, messageLength={}",
                    sessionId,
                    savedTurn.getId(),
                    plan.getId(),
                    request.getMessage().length());

            ChatResponseDTO data = new ChatResponseDTO();
            data.setSessionId(sessionId);
            data.setTurnId(savedTurn.getId());
            data.setPlanId(plan.getId());
            data.setPlanGoal(plan.getPlanGoal());
            data.setTurnStatus(TurnStatusEnum.EXECUTING.name());
            data.setAssistantMessage("收到，本轮任务已开始执行。");

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception ex) {
            String errorMessage = resolveErrorMessage(ex);
            markTurnAsFailed(savedTurn, sessionId, errorMessage);
            log.error("触发规划失败: sessionId={}, turnId={}, reason={}",
                    sessionId,
                    savedTurn == null ? null : savedTurn.getId(),
                    errorMessage,
                    ex);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(errorMessage)
                    .build();
        }
    }

    private Map<String, Object> buildPlanExtraContext(Long sessionId, ChatRequestDTO request, Long turnId) {
        Map<String, Object> extra = new HashMap<>();
        if (request != null && request.getExtraContext() != null && !request.getExtraContext().isEmpty()) {
            extra.putAll(request.getExtraContext());
        }
        extra.put("turnId", turnId);
        SessionTurnEntity latestCompleted = sessionTurnRepository.findLatestBySessionIdAndStatus(sessionId, TurnStatusEnum.COMPLETED);
        if (latestCompleted != null && StringUtils.isNotBlank(latestCompleted.getAssistantSummary())) {
            extra.put("lastAssistantSummary", latestCompleted.getAssistantSummary());
            extra.put("lastTurnId", latestCompleted.getId());
        }
        return extra;
    }

    private void markTurnAsFailed(SessionTurnEntity turn, Long sessionId, String errorMessage) {
        if (turn == null || turn.getId() == null) {
            return;
        }
        String finalErrorMessage = StringUtils.defaultIfBlank(errorMessage, "本轮规划失败，请稍后重试。");
        try {
            turn.setStatus(TurnStatusEnum.FAILED);
            turn.setAssistantSummary(finalErrorMessage);
            turn.setCompletedAt(LocalDateTime.now());
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
            turn.setFinalResponseMessageId(saved.getId());
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
}
