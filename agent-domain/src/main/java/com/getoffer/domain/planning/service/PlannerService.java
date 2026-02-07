package com.getoffer.domain.planning.service;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.SopTemplateEntity;

/**
 * Planner service interface.
 * <p>
 * Responsible for SOP matching and plan creation.
 * </p>
 *
 * @author getoffer
 * @since 2026-02-02
 */
public interface PlannerService {

    /**
     * Match a SOP template.
     *
     * @param userQuery user input
     * @return matched SOP template, or null if not found
     */
    SopTemplateEntity matchSop(String userQuery);

    /**
     * Create a plan and unfold tasks.
     *
     * @param sessionId session id
     * @param userQuery user input
     * @return created execution plan
     */
    AgentPlanEntity createPlan(Long sessionId, String userQuery);
}
