package com.getoffer.trigger.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.adapter.repository.ITaskExecutionRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.domain.task.model.entity.TaskExecutionEntity;
import com.getoffer.types.enums.TaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task executor: run READY tasks, write results, and sync blackboard.
 */
@Slf4j
@Component
public class TaskExecutor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final IAgentTaskRepository agentTaskRepository;
    private final IAgentPlanRepository agentPlanRepository;
    private final ITaskExecutionRepository taskExecutionRepository;
    private final IAgentFactory agentFactory;
    private final ObjectMapper objectMapper;

    public TaskExecutor(IAgentTaskRepository agentTaskRepository,
                        IAgentPlanRepository agentPlanRepository,
                        ITaskExecutionRepository taskExecutionRepository,
                        IAgentFactory agentFactory,
                        ObjectMapper objectMapper) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentPlanRepository = agentPlanRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.agentFactory = agentFactory;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${executor.poll-interval-ms:1000}")
    public void executeReadyTasks() {
        List<AgentTaskEntity> readyTasks = agentTaskRepository.findReadyTasks();
        List<AgentTaskEntity> refiningTasks = agentTaskRepository.findByStatus(TaskStatusEnum.REFINING);
        if ((readyTasks == null || readyTasks.isEmpty()) && (refiningTasks == null || refiningTasks.isEmpty())) {
            return;
        }
        List<AgentTaskEntity> tasks = new ArrayList<>();
        if (readyTasks != null) {
            tasks.addAll(readyTasks);
        }
        if (refiningTasks != null) {
            tasks.addAll(refiningTasks);
        }
        for (AgentTaskEntity task : tasks) {
            if (task == null || task.getStatus() != TaskStatusEnum.READY) {
                if (task == null || task.getStatus() != TaskStatusEnum.REFINING) {
                    continue;
                }
            }
            executeTask(task);
        }
    }

    private void executeTask(AgentTaskEntity task) {
        AgentPlanEntity plan = agentPlanRepository.findById(task.getPlanId());
        if (plan == null) {
            log.warn("Skip task execution because plan not found. taskId={}, planId={}", task.getId(), task.getPlanId());
            return;
        }
        if (!plan.isExecutable()) {
            log.debug("Skip task execution because plan is not executable. planId={}, status={}", plan.getId(), plan.getStatus());
            return;
        }

        boolean criticTask = isCriticTask(task);
        boolean refining = task.getStatus() == TaskStatusEnum.REFINING;
        try {
            task.start();
            agentTaskRepository.update(task);
        } catch (Exception ex) {
            log.warn("Failed to mark task RUNNING. taskId={}, nodeId={}, error={}",
                    task.getId(), task.getNodeId(), ex.getMessage());
            return;
        }

        long startTime = System.currentTimeMillis();
        String prompt = criticTask ? buildCriticPrompt(task, plan)
                : (refining ? buildRefinePrompt(task, plan) : buildPrompt(task, plan));
        TaskExecutionEntity execution = new TaskExecutionEntity();
        execution.setTaskId(task.getId());
        execution.setAttemptNumber(resolveAttemptNumber(task));
        execution.setPromptSnapshot(prompt);

        try {
            String systemPromptSuffix = buildRetrySystemPrompt(task);
            ChatClient client = resolveAgent(task, plan, systemPromptSuffix);
            String response = client.prompt(prompt).call().content();

            execution.setLlmResponseRaw(response);
            execution.setExecutionTime(startTime);
            if (criticTask) {
                CriticDecision decision = parseCriticDecision(response);
                if (decision.pass) {
                    execution.markAsValid(decision.feedback);
                } else {
                    execution.markAsInvalid(decision.feedback);
                }
                taskExecutionRepository.save(execution);

                task.startValidation();
                if (decision.pass) {
                    task.complete(response);
                    safeUpdateTask(task);
                } else {
                    task.setOutputResult(response);
                    task.resetToPending();
                    safeUpdateTask(task);
                    rollbackTarget(plan, task, decision.feedback);
                }
                return;
            }
            if (needsValidation(task)) {
                ValidationResult validation = evaluateValidation(task, response);
                if (validation.valid) {
                    execution.markAsValid(validation.feedback);
                } else {
                    execution.markAsInvalid(validation.feedback);
                }
                taskExecutionRepository.save(execution);

                task.startValidation();
                if (!validation.valid) {
                    handleValidationFailure(task, validation.feedback);
                    return;
                }
                task.complete(response);
                if (safeUpdateTask(task)) {
                    syncBlackboard(plan, task, response);
                }
            } else {
                execution.markAsValid("no validator");
                taskExecutionRepository.save(execution);

                task.startValidation();
                task.complete(response);
                if (safeUpdateTask(task)) {
                    syncBlackboard(plan, task, response);
                }
            }
        } catch (Exception ex) {
            execution.recordError(ex.getMessage());
            execution.setExecutionTime(startTime);
            safeSaveExecution(execution);

            task.fail(ex.getMessage());
            safeUpdateTask(task);
            log.warn("Task execution failed. taskId={}, nodeId={}, error={}",
                    task.getId(), task.getNodeId(), ex.getMessage());
        }
    }

    private void handleValidationFailure(AgentTaskEntity task, String feedback) {
        try {
            task.startRefining();
            safeUpdateTask(task);
        } catch (Exception ex) {
            String reason = StringUtils.isBlank(feedback) ? ex.getMessage() : feedback;
            task.fail("Validation failed: " + reason);
            safeUpdateTask(task);
        }
    }

    private boolean needsValidation(AgentTaskEntity task) {
        if (task == null) {
            return false;
        }
        Map<String, Object> config = task.getConfigSnapshot();
        if (config == null || config.isEmpty()) {
            return false;
        }
        Object validator = config.get("validator");
        if (validator instanceof Boolean) {
            return (Boolean) validator;
        }
        return config.containsKey("validator") || config.containsKey("validate") || config.containsKey("validation");
    }

    private ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
        String feedback = StringUtils.defaultString(response);
        Map<String, Object> config = task.getConfigSnapshot();
        List<String> passKeywords = getStringList(config, "passKeywords", "pass_keywords", "validKeywords", "valid_keywords");
        List<String> failKeywords = getStringList(config, "failKeywords", "fail_keywords", "invalidKeywords", "invalid_keywords");

        String lower = feedback.toLowerCase();
        if (containsKeyword(lower, failKeywords, "fail", "failed", "error", "incorrect", "wrong", "不通过", "失败", "错误", "有问题")) {
            return new ValidationResult(false, feedback);
        }
        if (containsKeyword(lower, passKeywords, "pass", "passed", "ok", "valid", "通过", "正确", "符合", "无问题")) {
            return new ValidationResult(true, feedback);
        }
        return new ValidationResult(true, feedback);
    }

    private boolean containsKeyword(String text, List<String> keywords, String... defaults) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        if (keywords != null && !keywords.isEmpty()) {
            for (String keyword : keywords) {
                if (StringUtils.isNotBlank(keyword) && text.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
        for (String keyword : defaults) {
            if (StringUtils.isNotBlank(keyword) && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private ChatClient resolveAgent(AgentTaskEntity task, AgentPlanEntity plan) {
        return resolveAgent(task, plan, null);
    }

    private ChatClient resolveAgent(AgentTaskEntity task, AgentPlanEntity plan, String systemPromptSuffix) {
        Map<String, Object> config = task.getConfigSnapshot();
        Long agentId = getLong(config, "agentId", "agent_id");
        String agentKey = getString(config, "agentKey", "agent_key");
        String conversationId = buildConversationId(plan, task);
        if (agentId != null) {
            return agentFactory.createAgent(agentId, conversationId, systemPromptSuffix);
        }
        if (StringUtils.isNotBlank(agentKey)) {
            return agentFactory.createAgent(agentKey, conversationId, systemPromptSuffix);
        }
        String fallbackKey = "CRITIC".equalsIgnoreCase(task.getTaskType()) ? "critic" : "worker";
        return agentFactory.createAgent(fallbackKey, conversationId, systemPromptSuffix);
    }

    private String buildConversationId(AgentPlanEntity plan, AgentTaskEntity task) {
        String planPart = plan.getId() == null ? "plan" : "plan-" + plan.getId();
        String nodePart = StringUtils.defaultIfBlank(task.getNodeId(), "node");
        return planPart + ":" + nodePart;
    }

    private String buildPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        Map<String, Object> config = task.getConfigSnapshot();
        Map<String, Object> context = new HashMap<>();
        if (plan.getGlobalContext() != null) {
            context.putAll(plan.getGlobalContext());
        }
        if (task.getInputContext() != null) {
            context.putAll(task.getInputContext());
        }

        List<String> contextKeys = getStringList(config, "contextKeys", "context_keys", "inputKeys", "input_keys", "inputs");
        Map<String, Object> filteredContext = filterContext(context, contextKeys);

        Map<String, Object> variables = new HashMap<>(filteredContext);
        variables.put("taskName", task.getName());
        variables.put("taskNodeId", task.getNodeId());
        variables.put("taskType", task.getTaskType());
        variables.put("planGoal", plan.getPlanGoal());
        variables.put("planId", plan.getId());
        variables.put("sessionId", plan.getSessionId());

        String template = getString(config, "prompt", "promptTemplate", "prompt_template", "template");
        if (StringUtils.isBlank(template)) {
            return defaultPrompt(task, plan, filteredContext);
        }
        return applyTemplate(template, variables);
    }

    private String buildCriticPrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        Map<String, Object> config = task.getConfigSnapshot();
        String targetNodeId = resolveTargetNodeId(task);
        AgentTaskEntity targetTask = targetNodeId == null ? null
                : agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        String targetOutput = targetTask == null ? "" : StringUtils.defaultString(targetTask.getOutputResult());

        Map<String, Object> context = new HashMap<>();
        if (plan.getGlobalContext() != null) {
            context.putAll(plan.getGlobalContext());
        }
        if (task.getInputContext() != null) {
            context.putAll(task.getInputContext());
        }
        context.put("targetNodeId", targetNodeId);
        context.put("targetOutput", targetOutput);
        context.put("planGoal", plan.getPlanGoal());

        String template = getString(config, "criticPrompt", "prompt", "promptTemplate", "prompt_template", "template");
        if (StringUtils.isNotBlank(template)) {
            return applyTemplate(template, context);
        }
        return defaultCriticPrompt(task, plan, targetNodeId, targetOutput, context);
    }

    private String defaultCriticPrompt(AgentTaskEntity task,
                                       AgentPlanEntity plan,
                                       String targetNodeId,
                                       String targetOutput,
                                       Map<String, Object> context) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是审查员，不需要生成内容。请审查目标输出是否满足要求。");
        builder.append("仅输出 JSON：{\"pass\": true/false, \"feedback\": \"...\"}。");
        builder.append("\n目标任务：").append(StringUtils.defaultIfBlank(targetNodeId, "未知"));
        builder.append("\n计划目标：").append(safeText(plan.getPlanGoal()));
        builder.append("\n目标输出：").append(StringUtils.defaultString(targetOutput));
        builder.append("\n上下文：").append(toJson(context));
        return builder.toString();
    }

    private String buildRefinePrompt(AgentTaskEntity task, AgentPlanEntity plan) {
        TaskExecutionEntity lastExecution = loadLastExecution(task.getId());
        String lastResponse = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getLlmResponseRaw());
        String feedback = lastExecution == null ? "" : StringUtils.defaultString(lastExecution.getValidationFeedback());
        String basePrompt = buildPrompt(task, plan);
        String reason = StringUtils.isBlank(feedback) ? "未通过验证" : feedback;
        StringBuilder builder = new StringBuilder();
        builder.append("你上次写错了，报错是").append(reason).append("，请重写。");
        if (StringUtils.isNotBlank(lastResponse)) {
            builder.append("\n上次输出：").append(lastResponse);
        }
        builder.append("\n\n").append(basePrompt);
        return builder.toString();
    }

    private TaskExecutionEntity loadLastExecution(Long taskId) {
        if (taskId == null) {
            return null;
        }
        List<TaskExecutionEntity> executions = taskExecutionRepository.findByTaskIdOrderByAttempt(taskId);
        if (executions == null || executions.isEmpty()) {
            return null;
        }
        return executions.get(0);
    }

    private String buildRetrySystemPrompt(AgentTaskEntity task) {
        Integer retryCount = task.getCurrentRetry();
        if (retryCount == null || retryCount <= 0) {
            return null;
        }
        String feedback = extractFeedback(task.getInputContext());
        if (StringUtils.isBlank(feedback)) {
            feedback = "无";
        }
        return "注意：这是你的第 " + retryCount + " 次尝试。上一次你失败了，反馈意见是：" + feedback + "。请根据反馈修正你的输出。";
    }

    private String extractFeedback(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Object value = context.get("feedback");
        if (value == null) {
            value = context.get("criticFeedback");
        }
        if (value == null) {
            value = context.get("validationFeedback");
        }
        return value == null ? null : String.valueOf(value);
    }

    private CriticDecision parseCriticDecision(String response) {
        if (StringUtils.isBlank(response)) {
            return new CriticDecision(false, "Critic输出为空");
        }
        String trimmed = response.trim();
        Map<String, Object> payload = parseJsonPayload(trimmed);
        if (payload == null) {
            return new CriticDecision(false, "Critic输出格式错误");
        }
        Object passValue = payload.get("pass");
        boolean pass = false;
        if (passValue instanceof Boolean) {
            pass = (Boolean) passValue;
        } else if (passValue != null) {
            pass = Boolean.parseBoolean(String.valueOf(passValue));
        }
        Object feedback = payload.get("feedback");
        String text = feedback == null ? trimmed : String.valueOf(feedback);
        return new CriticDecision(pass, text);
    }

    private Map<String, Object> parseJsonPayload(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            String snippet = text.substring(start, end + 1);
            try {
                return objectMapper.readValue(snippet, MAP_TYPE);
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
    }

    private void rollbackTarget(AgentPlanEntity plan, AgentTaskEntity criticTask, String feedback) {
        String targetNodeId = resolveTargetNodeId(criticTask);
        if (StringUtils.isBlank(targetNodeId)) {
            log.warn("Critic rollback skipped: target node not found. planId={}, taskId={}", plan.getId(), criticTask.getId());
            return;
        }
        AgentTaskEntity target = agentTaskRepository.findByPlanIdAndNodeId(plan.getId(), targetNodeId);
        if (target == null) {
            log.warn("Critic rollback skipped: target task not found. planId={}, nodeId={}", plan.getId(), targetNodeId);
            return;
        }
        if (target.getStatus() == TaskStatusEnum.FAILED) {
            return;
        }
        Map<String, Object> context = target.getInputContext();
        if (context == null) {
            context = new HashMap<>();
        }
        context.put("feedback", feedback);
        context.put("criticFeedback", feedback);
        target.setInputContext(context);

        int retry = target.getCurrentRetry() == null ? 0 : target.getCurrentRetry();
        retry += 1;
        target.setCurrentRetry(retry);

        Integer maxRetries = target.getMaxRetries();
        if (maxRetries != null && retry > maxRetries) {
            target.fail("Validation failed: " + feedback);
            safeUpdateTask(target);
            return;
        }

        target.rollbackToRefining();
        safeUpdateTask(target);
    }

    private String resolveTargetNodeId(AgentTaskEntity task) {
        Map<String, Object> config = task.getConfigSnapshot();
        String target = getString(config, "targetNodeId", "target_node_id", "target", "criticTarget", "critic_target");
        if (StringUtils.isNotBlank(target)) {
            return target;
        }
        List<String> deps = task.getDependencyNodeIds();
        if (deps != null && deps.size() == 1) {
            return deps.get(0);
        }
        return null;
    }

    private boolean isCriticTask(AgentTaskEntity task) {
        return task != null && "CRITIC".equalsIgnoreCase(task.getTaskType());
    }

    private String defaultPrompt(AgentTaskEntity task, AgentPlanEntity plan, Map<String, Object> context) {
        return "任务：" + safeText(task.getName()) + "\n目标：" + safeText(plan.getPlanGoal())
                + "\n上下文：" + toJson(context);
    }

    private String applyTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            result = result.replace("{{" + key + "}}", value)
                    .replace("${" + key + "}", value)
                    .replace("{" + key + "}", value);
        }
        return result;
    }

    private void syncBlackboard(AgentPlanEntity plan, AgentTaskEntity task, String output) {
        Map<String, Object> context = plan.getGlobalContext();
        if (context == null) {
            context = new HashMap<>();
        }
        Map<String, Object> config = task.getConfigSnapshot();

        boolean merge = getBoolean(config, "mergeOutput", "merge_output", "outputMerge");
        if (merge) {
            Map<String, Object> parsed = parseJsonMap(output);
            if (parsed != null && !parsed.isEmpty()) {
                context.putAll(parsed);
                plan.setGlobalContext(context);
                safeUpdatePlan(plan);
                return;
            }
        }

        String outputKey = getString(config, "outputKey", "output_key", "resultKey", "result_key");
        if (StringUtils.isBlank(outputKey)) {
            outputKey = StringUtils.defaultIfBlank(task.getNodeId(), "output");
        }
        context.put(outputKey, output);
        plan.setGlobalContext(context);
        safeUpdatePlan(plan);
    }

    private Map<String, Object> filterContext(Map<String, Object> context, List<String> keys) {
        if (context == null || context.isEmpty()) {
            return Collections.emptyMap();
        }
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>(context);
        }
        Map<String, Object> filtered = new HashMap<>();
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (context.containsKey(key)) {
                filtered.put(key, context.get(key));
            }
        }
        return filtered;
    }

    private List<String> getStringList(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        result.add(String.valueOf(item));
                    }
                }
                return result;
            }
            if (value instanceof String) {
                String text = ((String) value).trim();
                if (text.isEmpty()) {
                    continue;
                }
                String[] parts = text.split(",");
                List<String> result = new ArrayList<>();
                for (String part : parts) {
                    if (StringUtils.isNotBlank(part)) {
                        result.add(part.trim());
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    private String getString(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean getBoolean(Map<String, Object> config, String... keys) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return false;
    }

    private Map<String, Object> parseJsonMap(String output) {
        if (StringUtils.isBlank(output)) {
            return null;
        }
        String trimmed = output.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Integer resolveAttemptNumber(AgentTaskEntity task) {
        Integer maxAttempt = taskExecutionRepository.getMaxAttemptNumber(task.getId());
        if (maxAttempt != null && maxAttempt > 0) {
            return maxAttempt + 1;
        }
        if (task.getCurrentRetry() != null && task.getCurrentRetry() > 0) {
            return task.getCurrentRetry() + 1;
        }
        return 1;
    }

    private static final class ValidationResult {
        private final boolean valid;
        private final String feedback;

        private ValidationResult(boolean valid, String feedback) {
            this.valid = valid;
            this.feedback = feedback;
        }
    }

    private static final class CriticDecision {
        private final boolean pass;
        private final String feedback;

        private CriticDecision(boolean pass, String feedback) {
            this.pass = pass;
            this.feedback = feedback;
        }
    }

    private boolean safeUpdateTask(AgentTaskEntity task) {
        try {
            agentTaskRepository.update(task);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to update task status. taskId={}, error={}", task.getId(), ex.getMessage());
            return false;
        }
    }

    private void safeSaveExecution(TaskExecutionEntity execution) {
        try {
            taskExecutionRepository.save(execution);
        } catch (Exception ex) {
            log.warn("Failed to save task execution. taskId={}, error={}", execution.getTaskId(), ex.getMessage());
        }
    }

    private void safeUpdatePlan(AgentPlanEntity plan) {
        try {
            agentPlanRepository.update(plan);
        } catch (Exception ex) {
            log.warn("Failed to update plan context. planId={}, error={}", plan.getId(), ex.getMessage());
        }
    }
}
