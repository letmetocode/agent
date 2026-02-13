package com.getoffer.domain.task.service;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Task 提示词领域服务：负责 worker/critic/refine/retry 提示词策略。
 */
@Service
public class TaskPromptDomainService {

    public String buildWorkerPrompt(AgentTaskEntity task,
                                    AgentPlanEntity plan,
                                    Function<Object, String> serializer) {
        Map<String, Object> config = task == null ? Collections.emptyMap() : task.getConfigSnapshot();
        Map<String, Object> context = new HashMap<>();
        if (plan != null && plan.getGlobalContext() != null) {
            context.putAll(plan.getGlobalContext());
        }
        if (task != null && task.getInputContext() != null) {
            context.putAll(task.getInputContext());
        }

        List<String> contextKeys = getStringList(config,
                "contextKeys", "context_keys", "inputKeys", "input_keys", "inputs");
        Map<String, Object> filteredContext = filterContext(context, contextKeys);

        Map<String, Object> variables = new HashMap<>(filteredContext);
        variables.put("taskName", safeText(task == null ? null : task.getName()));
        variables.put("planGoal", safeText(plan == null ? null : plan.getPlanGoal()));
        variables.put("context", serialize(serializer, filteredContext));

        String template = getString(config, "prompt", "promptTemplate", "prompt_template", "template");
        if (isBlank(template)) {
            return "任务：" + safeText(task == null ? null : task.getName())
                    + "\n目标：" + safeText(plan == null ? null : plan.getPlanGoal())
                    + "\n上下文：" + serialize(serializer, filteredContext);
        }
        return applyTemplate(template, variables);
    }

    public String buildCriticPrompt(AgentTaskEntity task,
                                    AgentPlanEntity plan,
                                    String targetNodeId,
                                    String targetOutput,
                                    Function<Object, String> serializer) {
        Map<String, Object> context = new HashMap<>();
        if (plan != null && plan.getGlobalContext() != null) {
            context.putAll(plan.getGlobalContext());
        }
        if (task != null && task.getInputContext() != null) {
            context.putAll(task.getInputContext());
        }
        context.put("targetNodeId", targetNodeId);
        context.put("targetOutput", defaultString(targetOutput));
        context.put("planGoal", plan == null ? "" : plan.getPlanGoal());

        Map<String, Object> config = task == null ? Collections.emptyMap() : task.getConfigSnapshot();
        String template = getString(config, "criticPrompt", "prompt", "promptTemplate", "prompt_template", "template");
        if (isNotBlank(template)) {
            return applyTemplate(template, context);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("你是审查员，不需要生成内容。请审查目标输出是否满足要求。");
        builder.append("仅输出 JSON：{\"pass\": true/false, \"feedback\": \"...\"}。");
        builder.append("\n目标任务：").append(defaultIfBlank(targetNodeId, "未知"));
        builder.append("\n计划目标：").append(safeText(plan == null ? null : plan.getPlanGoal()));
        builder.append("\n目标输出：").append(defaultString(targetOutput));
        builder.append("\n上下文：").append(serialize(serializer, context));
        return builder.toString();
    }

    public String buildRefinePrompt(String basePrompt,
                                    String lastResponse,
                                    String validationFeedback) {
        String reason = isBlank(validationFeedback) ? "未通过验证" : validationFeedback;
        StringBuilder builder = new StringBuilder();
        builder.append("你上次写错了，报错是").append(reason).append("，请重写。");
        if (isNotBlank(lastResponse)) {
            builder.append("\n上次输出：").append(lastResponse);
        }
        builder.append("\n\n").append(defaultString(basePrompt));
        return builder.toString();
    }

    public String buildRetrySystemPrompt(AgentTaskEntity task) {
        if (task == null) {
            return null;
        }
        Integer retryCount = task.getCurrentRetry();
        if (retryCount == null || retryCount <= 0) {
            return null;
        }
        String feedback = extractFeedback(task.getInputContext());
        if (isBlank(feedback)) {
            feedback = "无";
        }
        return "注意：这是你的第 " + retryCount + " 次尝试。上一次你失败了，反馈意见是："
                + feedback + "。请根据反馈修正你的输出。";
    }

    public String resolveTargetNodeId(AgentTaskEntity task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> config = task.getConfigSnapshot();
        String target = getString(config, "targetNodeId", "target_node_id", "target", "criticTarget", "critic_target");
        if (isNotBlank(target)) {
            return target;
        }
        List<String> deps = task.getDependencyNodeIds();
        if (deps != null && deps.size() == 1) {
            return deps.get(0);
        }
        return null;
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

    private Map<String, Object> filterContext(Map<String, Object> context, List<String> keys) {
        if (context == null || context.isEmpty()) {
            return Collections.emptyMap();
        }
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>(context);
        }
        Map<String, Object> filtered = new HashMap<>();
        for (String key : keys) {
            if (isBlank(key)) {
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
                    if (isNotBlank(part)) {
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
            if (isNotBlank(text)) {
                return text;
            }
        }
        return null;
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String serialize(Function<Object, String> serializer, Object value) {
        if (serializer == null) {
            return String.valueOf(value);
        }
        String serialized = serializer.apply(value);
        return serialized == null ? String.valueOf(value) : serialized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
