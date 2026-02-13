package com.getoffer.trigger.application.query;

import com.getoffer.api.dto.RoutingDecisionDTO;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.springframework.stereotype.Service;

/**
 * 路由决策读用例。
 */
@Service
public class ChatRoutingQueryService {

    private final IAgentPlanRepository agentPlanRepository;
    private final IRoutingDecisionRepository routingDecisionRepository;

    public ChatRoutingQueryService(IAgentPlanRepository agentPlanRepository,
                                   IRoutingDecisionRepository routingDecisionRepository) {
        this.agentPlanRepository = agentPlanRepository;
        this.routingDecisionRepository = routingDecisionRepository;
    }

    public RoutingDecisionDTO getRoutingDecision(Long planId) {
        if (planId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "planId 不能为空");
        }
        AgentPlanEntity plan = agentPlanRepository.findById(planId);
        if (plan == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "计划不存在");
        }
        if (plan.getRouteDecisionId() == null) {
            return null;
        }
        RoutingDecisionEntity entity = routingDecisionRepository.findById(plan.getRouteDecisionId());
        return toDTO(entity);
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
}
