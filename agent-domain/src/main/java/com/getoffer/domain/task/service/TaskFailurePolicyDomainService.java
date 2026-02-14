package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Task 失败策略领域服务：判定 FAILED 任务是否可被计划层容忍（Fail-Safe）。
 */
@Service
public class TaskFailurePolicyDomainService {

    private static final String FAILURE_POLICY_FAIL_SAFE = "failSafe";

    public boolean isFailSafeFailure(AgentTaskEntity task) {
        if (task == null || task.getStatus() != TaskStatusEnum.FAILED) {
            return false;
        }
        Map<String, Object> graphPolicy = extractGraphPolicy(task.getConfigSnapshot());
        String failurePolicy = readText(graphPolicy, "failurePolicy", "failure_policy");
        if (isBlank(failurePolicy)) {
            failurePolicy = readText(task.getConfigSnapshot(), "failurePolicy", "failure_policy");
        }
        return equalsIgnoreCase(failurePolicy, FAILURE_POLICY_FAIL_SAFE)
                || equalsIgnoreCase(failurePolicy, "fail_safe");
    }

    private Map<String, Object> extractGraphPolicy(Map<String, Object> configSnapshot) {
        if (configSnapshot == null || configSnapshot.isEmpty()) {
            return new HashMap<>();
        }
        Object graphPolicy = configSnapshot.get("graphPolicy");
        if (graphPolicy instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        graphPolicy = configSnapshot.get("graph_policy");
        if (graphPolicy instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return new HashMap<>();
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copied = new HashMap<>();
        if (source == null || source.isEmpty()) {
            return copied;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
    }

    private String readText(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!isBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }
}
