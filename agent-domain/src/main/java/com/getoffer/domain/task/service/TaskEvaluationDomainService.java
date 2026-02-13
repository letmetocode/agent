package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Task 结果判定领域服务：负责校验判定与 Critic 输出语义。
 */
@Service
public class TaskEvaluationDomainService {

    public boolean needsValidation(AgentTaskEntity task) {
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
        return config.containsKey("validator")
                || config.containsKey("validate")
                || config.containsKey("validation");
    }

    public ValidationResult evaluateValidation(AgentTaskEntity task, String response) {
        String feedback = defaultString(response);
        Map<String, Object> config = task == null ? Collections.emptyMap() : task.getConfigSnapshot();
        List<String> passKeywords = getStringList(config,
                "passKeywords", "pass_keywords", "validKeywords", "valid_keywords");
        List<String> failKeywords = getStringList(config,
                "failKeywords", "fail_keywords", "invalidKeywords", "invalid_keywords");

        String lower = feedback.toLowerCase();
        if (containsKeyword(lower, failKeywords,
                "fail", "failed", "error", "incorrect", "wrong", "不通过", "失败", "错误", "有问题")) {
            return new ValidationResult(false, feedback);
        }
        if (containsKeyword(lower, passKeywords,
                "pass", "passed", "ok", "valid", "通过", "正确", "符合", "无问题")) {
            return new ValidationResult(true, feedback);
        }
        return new ValidationResult(true, feedback);
    }

    public CriticDecision parseCriticDecision(String response, Map<String, Object> payload) {
        if (isBlank(response)) {
            return new CriticDecision(false, "Critic输出为空");
        }
        String trimmed = response.trim();
        if (payload == null || payload.isEmpty()) {
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

    private boolean containsKeyword(String text, List<String> keywords, String... defaults) {
        if (isBlank(text)) {
            return false;
        }
        if (keywords != null && !keywords.isEmpty()) {
            for (String keyword : keywords) {
                if (isNotBlank(keyword) && text.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
        for (String keyword : defaults) {
            if (isNotBlank(keyword) && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record ValidationResult(boolean valid, String feedback) {
    }

    public record CriticDecision(boolean pass, String feedback) {
    }
}
