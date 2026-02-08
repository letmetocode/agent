package com.getoffer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度器隔离配置：
 * 1) task-executor-scheduler 专门承载 TaskExecutor，避免长耗时执行阻塞其它守护任务；
 * 2) daemon-scheduler 承载状态推进与任务调度守护任务。
 */
@Slf4j
@Configuration
public class SchedulingConfig {

    @Bean(name = "taskExecutorScheduler")
    public ThreadPoolTaskScheduler taskExecutorScheduler(
            @Value("${scheduling.task-executor.pool-size:1}") int poolSize,
            @Value("${scheduling.task-executor.thread-name-prefix:task-executor-scheduler-}") String threadNamePrefix,
            @Value("${scheduling.task-executor.await-termination-seconds:30}") int awaitTerminationSeconds) {
        return buildScheduler(poolSize, threadNamePrefix, awaitTerminationSeconds);
    }

    @Bean(name = "daemonScheduler")
    public ThreadPoolTaskScheduler daemonScheduler(
            @Value("${scheduling.daemon.pool-size:2}") int poolSize,
            @Value("${scheduling.daemon.thread-name-prefix:daemon-scheduler-}") String threadNamePrefix,
            @Value("${scheduling.daemon.await-termination-seconds:30}") int awaitTerminationSeconds) {
        return buildScheduler(poolSize, threadNamePrefix, awaitTerminationSeconds);
    }

    private ThreadPoolTaskScheduler buildScheduler(int poolSize, String threadNamePrefix, int awaitTerminationSeconds) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(poolSize, 1));
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(Math.max(awaitTerminationSeconds, 0));
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(throwable ->
                log.error("Scheduled task execution failed. scheduler={}, error={}",
                        threadNamePrefix, throwable.getMessage(), throwable));
        return scheduler;
    }
}
