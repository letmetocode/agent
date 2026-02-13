package com.getoffer.test.domain;

import com.getoffer.domain.task.service.TaskPersistencePolicyDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TaskPersistencePolicyDomainServiceTest {

    private final TaskPersistencePolicyDomainService service = new TaskPersistencePolicyDomainService();

    @Test
    public void shouldDetectOptimisticLockConflictByMessage() {
        Assertions.assertTrue(service.isOptimisticLockConflict("Optimistic lock conflict for plan"));
        Assertions.assertFalse(service.isOptimisticLockConflict("duplicate key"));
    }

    @Test
    public void shouldRetryOnlyBeforeMaxAttempts() {
        Assertions.assertTrue(service.shouldRetryOptimisticLock(1, 3));
        Assertions.assertFalse(service.shouldRetryOptimisticLock(3, 3));
        Assertions.assertFalse(service.shouldRetryOptimisticLock(1, 0));
    }

    @Test
    public void shouldNormalizeErrorMessage() {
        RuntimeException exceptionWithMessage = new RuntimeException("boom");
        Assertions.assertEquals("boom", service.normalizeErrorMessage(exceptionWithMessage));

        RuntimeException exceptionWithoutMessage = new RuntimeException("");
        Assertions.assertEquals("RuntimeException", service.normalizeErrorMessage(exceptionWithoutMessage));

        Assertions.assertEquals("unknown", service.normalizeErrorMessage(null));
    }
}
