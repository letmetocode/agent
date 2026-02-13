package com.getoffer.domain.task.service;

import org.springframework.stereotype.Service;

/**
 * Task 持久化策略领域服务：负责持久化异常语义与重试策略。
 */
@Service
public class TaskPersistencePolicyDomainService {

    public boolean isOptimisticLockConflict(String errorMessage) {
        return errorMessage != null && errorMessage.contains("Optimistic lock");
    }

    public boolean shouldRetryOptimisticLock(int attempt, int maxAttempts) {
        return attempt < Math.max(maxAttempts, 1);
    }

    public String normalizeErrorMessage(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
