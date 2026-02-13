package com.getoffer.trigger.job;

import com.getoffer.trigger.application.command.TaskScheduleApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler daemon: promote pending tasks to READY when dependencies are satisfied.
 */
@Slf4j
@Component
public class TaskSchedulerDaemon {

    private final TaskScheduleApplicationService taskScheduleApplicationService;

    public TaskSchedulerDaemon(TaskScheduleApplicationService taskScheduleApplicationService) {
        this.taskScheduleApplicationService = taskScheduleApplicationService;
    }

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:1000}", scheduler = "daemonScheduler")
    public void promotePendingTasks() {
        TaskScheduleApplicationService.ScheduleResult result =
                taskScheduleApplicationService.schedulePendingTasks();
        if (result.pendingCount() <= 0) {
            return;
        }
        log.debug("Task schedule round finished. pending={}, promoted={}, skipped={}, waiting={}, errors={}",
                result.pendingCount(),
                result.promotedCount(),
                result.skippedCount(),
                result.waitingCount(),
                result.errorCount());
    }
}
