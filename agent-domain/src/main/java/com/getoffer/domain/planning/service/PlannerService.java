package com.getoffer.domain.planning.service;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.valobj.RoutingDecisionResult;

import java.util.Map;

/**
 * Planner service interface.
 * <p>
 * Responsible for workflow routing and plan creation.
 * </p>
 *
 * @author getoffer
 * @since 2026-02-02
 */
public interface PlannerService {

    /**
     * Route workflow for current user query.
     *
     * @param userQuery user input
     * @return routing decision result
     */
    RoutingDecisionResult route(String userQuery);

    /**
     * Create a plan and unfold tasks.
     *
     * @param sessionId session id
     * @param userQuery user input
     * @return created execution plan
     */
    AgentPlanEntity createPlan(Long sessionId, String userQuery);

    /**
     * Create a plan with extra context.
     *
     * @param sessionId session id
     * @param userQuery user input
     * @param extraContext extra context appended to plan global context
     * @return created execution plan
     */
    default AgentPlanEntity createPlan(Long sessionId, String userQuery, Map<String, Object> extraContext) {
        return createPlan(sessionId, userQuery);
    }
}
