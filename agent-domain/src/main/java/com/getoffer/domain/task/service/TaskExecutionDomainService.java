package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

/**
 * Task 执行规则领域服务：负责执行尝试次数与超时重试策略。
 */
@Service
public class TaskExecutionDomainService {

    public Integer resolveAttemptNumber(Integer maxPersistedAttempt, AgentTaskEntity task) {
        if (maxPersistedAttempt != null && maxPersistedAttempt > 0) {
            return maxPersistedAttempt + 1;
        }
        if (task == null) {
            return 1;
        }
        int currentRetry = task.normalizedCurrentRetry();
        return currentRetry > 0 ? currentRetry + 1 : 1;
    }

    public boolean canTimeoutRetry(AgentTaskEntity task, int timeoutRetryCount, int timeoutRetryMax) {
        return task != null && task.canTimeoutRetry(timeoutRetryCount, timeoutRetryMax);
    }

    public void applyTimeoutRetry(AgentTaskEntity task, String timeoutMessage) {
        if (task == null) {
            return;
        }
        task.applyTimeoutRetry(timeoutMessage);
    }
}
