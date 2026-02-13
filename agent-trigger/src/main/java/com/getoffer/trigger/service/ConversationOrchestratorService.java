package com.getoffer.trigger.service;

import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.session.adapter.repository.IAgentSessionRepository;
import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.trigger.application.command.ChatConversationCommandService;

/**
 * @deprecated 请使用 {@link ChatConversationCommandService}
 */
@Deprecated
public class ConversationOrchestratorService extends ChatConversationCommandService {

    public ConversationOrchestratorService(PlannerService plannerService,
                                           IAgentSessionRepository agentSessionRepository,
                                           ISessionTurnRepository sessionTurnRepository,
                                           ISessionMessageRepository sessionMessageRepository,
                                           IRoutingDecisionRepository routingDecisionRepository,
                                           IAgentRegistryRepository agentRegistryRepository) {
        super(plannerService,
                agentSessionRepository,
                sessionTurnRepository,
                sessionMessageRepository,
                routingDecisionRepository,
                agentRegistryRepository);
    }
}
