package com.getoffer.trigger.http;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.api.dto.TurnCreateRequestDTO;
import com.getoffer.api.dto.TurnCreateResponseDTO;
import com.getoffer.api.response.Response;
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
 * 回合创建 API（V2）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/sessions")
public class TurnV2Controller {

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;
    private final ISessionTurnRepository sessionTurnRepository;
    private final ISessionMessageRepository sessionMessageRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;

    public TurnV2Controller(PlannerService plannerService,
                            IAgentSessionRepository agentSessionRepository,
                            ISessionTurnRepository sessionTurnRepository,
                            ISessionMessageRepository sessionMessageRepository,
                            IRoutingDecisionRepository routingDecisionRepository) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
        this.sessionTurnRepository = sessionTurnRepository;
        this.sessionMessageRepository = sessionMessageRepository;
        this.routingDecisionRepository = routingDecisionRepository;
    }

    @PostMapping("/{id}/turns")
    public Response<TurnCreateResponseDTO> createTurn(@PathVariable("id") Long sessionId,
                                                      @RequestBody TurnCreateRequestDTO request) {
        if (sessionId == null) {
            return illegal("SessionId不能为空");
        }
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            return illegal("消息不能为空");
        }
        AgentSessionEntity session = agentSessionRepository.findById(sessionId);
        if (session == null) {
            return illegal("会话不存在");
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
                    buildPlanExtraContext(session, request, savedTurn.getId())
            );

            savedTurn.setPlanId(plan.getId());
            savedTurn.setStatus(TurnStatusEnum.EXECUTING);
            sessionTurnRepository.update(savedTurn);

            RoutingDecisionDTO routingDecision = null;
            if (plan.getRouteDecisionId() != null) {
                routingDecision = toRoutingDecisionDTO(routingDecisionRepository.findById(plan.getRouteDecisionId()));
            }

            log.info("TURN_PLANNING_TRIGGERED_V2 sessionId={}, turnId={}, planId={}, routeDecisionId={}",
                    sessionId,
                    savedTurn.getId(),
                    plan.getId(),
                    plan.getRouteDecisionId());

            TurnCreateResponseDTO data = new TurnCreateResponseDTO();
            data.setSessionId(sessionId);
            data.setTurnId(savedTurn.getId());
            data.setPlanId(plan.getId());
            data.setPlanGoal(plan.getPlanGoal());
            data.setTurnStatus(TurnStatusEnum.EXECUTING.name());
            data.setAssistantMessage("收到，本轮任务已开始执行。");
            data.setRoutingDecision(routingDecision);

            return success(data);
        } catch (Exception ex) {
            String errorMessage = resolveErrorMessage(ex);
            markTurnAsFailed(savedTurn, sessionId, errorMessage);
            log.error("触发规划失败(V2): sessionId={}, turnId={}, reason={}",
                    sessionId,
                    savedTurn == null ? null : savedTurn.getId(),
                    errorMessage,
                    ex);
            return Response.<TurnCreateResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(errorMessage)
                    .build();
        }
    }

    private Map<String, Object> buildPlanExtraContext(AgentSessionEntity session,
                                                      TurnCreateRequestDTO request,
                                                      Long turnId) {
        Map<String, Object> extra = new HashMap<>();
        if (request != null && request.getContextOverrides() != null && !request.getContextOverrides().isEmpty()) {
            extra.putAll(request.getContextOverrides());
        }
        extra.put("turnId", turnId);
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

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> illegal(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .build();
    }
}
