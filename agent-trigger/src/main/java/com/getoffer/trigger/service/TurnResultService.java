package com.getoffer.trigger.service;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.trigger.application.command.TurnFinalizeApplicationService;

/**
 * @deprecated 请使用 {@link TurnFinalizeApplicationService}
 */
@Deprecated
public class TurnResultService extends TurnFinalizeApplicationService {

    public TurnResultService(ISessionTurnRepository sessionTurnRepository,
                             ISessionMessageRepository sessionMessageRepository,
                             IAgentTaskRepository agentTaskRepository) {
        super(sessionTurnRepository, sessionMessageRepository, agentTaskRepository);
    }
}
