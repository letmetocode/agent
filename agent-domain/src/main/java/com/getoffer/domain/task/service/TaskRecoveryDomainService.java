package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

/**
 * Task 回滚领域服务：负责 Critic 失败后的目标任务回滚与重试预算决策。
 */
@Service
public class TaskRecoveryDomainService {

    public RecoveryDecision applyCriticFeedback(AgentTaskEntity targetTask, String feedback) {
        if (targetTask == null) {
            return RecoveryDecision.NOT_FOUND;
        }
        if (targetTask.isFailed()) {
            return RecoveryDecision.ALREADY_FAILED;
        }

        targetTask.applyCriticFeedback(feedback);
        targetTask.incrementRetry();

        if (targetTask.exceedsRetryLimit()) {
            targetTask.fail("Validation failed: " + defaultString(feedback));
            return RecoveryDecision.FAILED;
        }

        targetTask.rollbackToRefining();
        return RecoveryDecision.REFINING;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public enum RecoveryDecision {
        NOT_FOUND,
        ALREADY_FAILED,
        FAILED,
        REFINING;

        public boolean requiresUpdate() {
            return this == FAILED || this == REFINING;
        }
    }
}
