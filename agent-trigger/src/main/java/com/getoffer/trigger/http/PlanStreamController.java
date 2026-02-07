package com.getoffer.trigger.http;

import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.TaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 计划 SSE 流式输出
 */
@Slf4j
@RestController
@RequestMapping("/api/plans")
public class PlanStreamController {

    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;

    public PlanStreamController(IAgentPlanRepository agentPlanRepository,
                                IAgentTaskRepository agentTaskRepository) {
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPlan(@PathVariable("id") Long planId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);

        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> running.set(false));
        emitter.onError(ex -> running.set(false));

        Thread worker = new Thread(() -> emitLoop(planId, emitter, running), "plan-stream-" + planId);
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    private void emitLoop(Long planId, SseEmitter emitter, AtomicBoolean running) {
        Map<Long, TaskSnapshot> snapshots = new HashMap<>();
        boolean finished = false;
        while (running.get()) {
            AgentPlanEntity plan = agentPlanRepository.findById(planId);
            if (plan == null) {
                sendEvent(emitter, "PlanFinished", Map.of("planId", planId, "status", "NOT_FOUND"));
                break;
            }

            List<AgentTaskEntity> tasks = agentTaskRepository.findByPlanId(planId);
            if (tasks != null) {
                for (AgentTaskEntity task : tasks) {
                    if (task == null) {
                        continue;
                    }
                    TaskSnapshot previous = snapshots.get(task.getId());
                    TaskSnapshot current = TaskSnapshot.from(task);

                    if (previous == null) {
                        if (current.status == TaskStatusEnum.RUNNING) {
                            sendEvent(emitter, "TaskStarted", buildTaskData(planId, task));
                        }
                        if (isTerminal(current.status)) {
                            sendEvent(emitter, "TaskCompleted", buildTaskData(planId, task));
                        }
                        if (current.outputChanged(null)) {
                            sendEvent(emitter, "TaskLog", buildTaskLog(planId, task));
                        }
                        snapshots.put(task.getId(), current);
                        continue;
                    }

                    if (previous.status != current.status) {
                        if (current.status == TaskStatusEnum.RUNNING) {
                            sendEvent(emitter, "TaskStarted", buildTaskData(planId, task));
                        }
                        if (isTerminal(current.status)) {
                            sendEvent(emitter, "TaskCompleted", buildTaskData(planId, task));
                        }
                    }
                    if (current.outputChanged(previous.outputResult)) {
                        sendEvent(emitter, "TaskLog", buildTaskLog(planId, task));
                    }
                    snapshots.put(task.getId(), current);
                }
            }

            finished = isPlanFinished(plan, tasks);
            if (finished) {
                sendEvent(emitter, "PlanFinished", Map.of(
                        "planId", planId,
                        "status", plan.getStatus() == null ? "FINISHED" : plan.getStatus().name()
                ));
                break;
            }

            sleep(1000L);
        }
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE completed with error: {}", ex.getMessage());
        }
    }

    private boolean isPlanFinished(AgentPlanEntity plan, List<AgentTaskEntity> tasks) {
        if (plan != null && plan.getStatus() != null) {
            PlanStatusEnum status = plan.getStatus();
            if (status == PlanStatusEnum.COMPLETED || status == PlanStatusEnum.FAILED || status == PlanStatusEnum.CANCELLED) {
                return true;
            }
        }
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        for (AgentTaskEntity task : tasks) {
            if (task == null || !isTerminal(task.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean isTerminal(TaskStatusEnum status) {
        return status == TaskStatusEnum.COMPLETED
                || status == TaskStatusEnum.FAILED
                || status == TaskStatusEnum.SKIPPED;
    }

    private Map<String, Object> buildTaskData(Long planId, AgentTaskEntity task) {
        Map<String, Object> data = new HashMap<>();
        data.put("planId", planId);
        data.put("taskId", task.getId());
        data.put("nodeId", task.getNodeId());
        data.put("status", task.getStatus() == null ? null : task.getStatus().name());
        data.put("taskType", task.getTaskType());
        return data;
    }

    private Map<String, Object> buildTaskLog(Long planId, AgentTaskEntity task) {
        Map<String, Object> data = buildTaskData(planId, task);
        data.put("output", task.getOutputResult());
        return data;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ex) {
            log.debug("SSE send failed: {}", ex.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class TaskSnapshot {
        private final TaskStatusEnum status;
        private final String outputResult;

        private TaskSnapshot(TaskStatusEnum status, String outputResult) {
            this.status = status;
            this.outputResult = outputResult;
        }

        private static TaskSnapshot from(AgentTaskEntity task) {
            return new TaskSnapshot(task.getStatus(), task.getOutputResult());
        }

        private boolean outputChanged(String previous) {
            if (outputResult == null) {
                return previous != null;
            }
            return !outputResult.equals(previous);
        }
    }
}
