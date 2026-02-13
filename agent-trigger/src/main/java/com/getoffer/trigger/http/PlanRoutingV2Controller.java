package com.getoffer.trigger.http;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan 路由决策查询 API（V2）。
 */
@RestController
@RequestMapping("/api/v2/plans")
public class PlanRoutingV2Controller {

    private final IAgentPlanRepository agentPlanRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;

    public PlanRoutingV2Controller(IAgentPlanRepository agentPlanRepository,
                                   IRoutingDecisionRepository routingDecisionRepository) {
        this.agentPlanRepository = agentPlanRepository;
        this.routingDecisionRepository = routingDecisionRepository;
    }

    @GetMapping("/{id}/routing")
    public Response<RoutingDecisionDTO> getPlanRouting(@PathVariable("id") Long planId) {
        if (planId == null) {
            return illegal("planId 不能为空");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            return illegal("计划不存在");
        }
        if (plan.getRouteDecisionId() == null) {
            return success(null);
        }
        RoutingDecisionEntity entity = routingDecisionRepository.findById(plan.getRouteDecisionId());
        return success(toDTO(entity));
    }

    private RoutingDecisionDTO toDTO(RoutingDecisionEntity entity) {
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
