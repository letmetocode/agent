package com.getoffer.trigger.http;

import com.getoffer.api.dto.ChatRequestDTO;
import com.getoffer.api.dto.ChatResponseDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.model.entity.AgentSessionEntity;
import com.getoffer.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话聊天 API
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    private final PlannerService plannerService;
    private final IAgentSessionRepository agentSessionRepository;

    public ChatController(PlannerService plannerService, IAgentSessionRepository agentSessionRepository) {
        this.plannerService = plannerService;
        this.agentSessionRepository = agentSessionRepository;
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
        try {
            AgentPlanEntity plan = plannerService.createPlan(sessionId, request.getMessage());
            ChatResponseDTO data = new ChatResponseDTO();
            data.setSessionId(sessionId);
            data.setPlanId(plan.getId());
            data.setPlanGoal(plan.getPlanGoal());

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception ex) {
            log.warn("触发规划失败: {}", ex.getMessage());
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ex.getMessage())
                    .build();
        }
    }
}
