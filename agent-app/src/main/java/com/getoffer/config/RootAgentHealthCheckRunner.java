package com.getoffer.config;

import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时校验 root Agent 可用性。
 */
@Slf4j
@Component
public class RootAgentHealthCheckRunner implements ApplicationRunner {

    private final IAgentRegistryRepository agentRegistryRepository;
    private final boolean rootPlannerEnabled;
    private final String rootAgentKey;

    public RootAgentHealthCheckRunner(IAgentRegistryRepository agentRegistryRepository,
                                      @Value("${planner.root.enabled:true}") boolean rootPlannerEnabled,
                                      @Value("${planner.root.agent-key:root}") String rootAgentKey) {
        this.agentRegistryRepository = agentRegistryRepository;
        this.rootPlannerEnabled = rootPlannerEnabled;
        this.rootAgentKey = StringUtils.defaultIfBlank(rootAgentKey, "root");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!rootPlannerEnabled) {
            log.info("Skip root agent health check because planner.root.enabled=false");
            return;
        }
        AgentRegistryEntity rootAgent = agentRegistryRepository.findByKey(rootAgentKey);
        if (rootAgent == null) {
            throw new IllegalStateException("Root agent is required but not found: key=" + rootAgentKey);
        }
        if (!Boolean.TRUE.equals(rootAgent.getIsActive())) {
            throw new IllegalStateException("Root agent is required but inactive: key=" + rootAgentKey);
        }
        log.info("Root agent health check passed. key={}", rootAgentKey);
    }
}
