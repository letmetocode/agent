package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        return evaluateValidation(task, response, null);
    }

    public ValidationResult evaluateValidation(AgentTaskEntity task,
                                               String response,
                                               Map<String, Object> payload) {
        String feedback = defaultString(response);
        Map<String, Object> config = task == null ? Collections.emptyMap() : task.getConfigSnapshot();
        ValidationSchema schema = resolveValidationSchema(config);
        List<String> passKeywords = getStringList(config,
                "passKeywords", "pass_keywords", "validKeywords", "valid_keywords");
        List<String> failKeywords = getStringList(config,
                "failKeywords", "fail_keywords", "invalidKeywords", "invalid_keywords");

        if (payload != null && !payload.isEmpty()) {
            ValidationResult structured = evaluateStructuredValidation(payload, feedback, schema);
            if (structured != null) {
                return structured;
            }
        } else if (schema.strict()) {
            return new ValidationResult(false, "验证结果缺少结构化字段: " + String.join(",", schema.requiredFields()));
        }

        String lower = feedback.toLowerCase();
        if (containsKeyword(lower, failKeywords,
                "fail", "failed", "error", "incorrect", "wrong", "不通过", "失败", "错误", "有问题")) {
            return new ValidationResult(false, feedback);
        }
        if (containsKeyword(lower, passKeywords,
                "pass", "passed", "ok", "valid", "通过", "正确", "符合", "无问题")) {
            return new ValidationResult(true, feedback);
        }
        return new ValidationResult(schema.passOnUnknown(), feedback);
    }

    public CriticDecision parseCriticDecision(String response, Map<String, Object> payload) {
        if (isBlank(response)) {
            return new CriticDecision(false, "Critic输出为空");
        }
        String trimmed = response.trim();
        if (payload == null || payload.isEmpty()) {
            return new CriticDecision(false, "Critic输出格式错误");
        }

        Boolean pass = getBoolean(payload, "pass", "valid", "approved", "isValid", "is_valid");
        if (pass == null) {
            return new CriticDecision(false, "Critic输出缺少pass字段");
        }
        String text = getString(payload, "feedback", "reason", "message", "comment");
        if (isBlank(text)) {
            text = trimmed;
        }
        return new CriticDecision(pass, text);
    }

    private ValidationResult evaluateStructuredValidation(Map<String, Object> payload,
                                                          String rawFeedback,
                                                          ValidationSchema schema) {
        for (String requiredField : schema.requiredFields()) {
            if (!payload.containsKey(requiredField)) {
                if (schema.strict()) {
                    return new ValidationResult(false, "验证结果缺少结构化字段: " + requiredField);
                }
                return null;
            }
        }

        String structuredFeedback = getString(payload, schema.feedbackField(), "feedback", "reason", "message");
        String feedback = isNotBlank(structuredFeedback) ? structuredFeedback : rawFeedback;

        Double score = getDouble(payload, schema.scoreField(), "score", "qualityScore", "quality_score");
        if (score != null && schema.passThreshold() != null) {
            boolean pass = score >= schema.passThreshold();
            return new ValidationResult(pass, appendScoreFeedback(feedback, score, schema.passThreshold()));
        }

        Boolean pass = getBoolean(payload, schema.passField(), "pass", "valid", "isValid", "is_valid");
        if (pass != null) {
            return new ValidationResult(pass, feedback);
        }

        if (schema.strict()) {
            return new ValidationResult(false, "验证结果缺少判定字段: " + schema.passField());
        }
        return null;
    }

    private ValidationSchema resolveValidationSchema(Map<String, Object> config) {
        Map<String, Object> schemaMap = getMap(config, "validationSchema", "validation_schema");
        Set<String> requiredFields = new LinkedHashSet<>();
        requiredFields.addAll(getStringList(schemaMap, "requiredFields", "required_fields", "required"));
        requiredFields.addAll(getStringList(config, "validationRequiredFields", "validation_required_fields"));

        Double passThreshold = getDouble(schemaMap, "passThreshold", "pass_threshold", "threshold", "scoreThreshold", "score_threshold");
        if (passThreshold == null) {
            passThreshold = getDouble(config, "validationPassThreshold", "validation_pass_threshold", "passThreshold", "pass_threshold");
        }

        boolean strict = getBoolean(schemaMap, "strict", "requireStructured", "require_structured") != null
                ? Boolean.TRUE.equals(getBoolean(schemaMap, "strict", "requireStructured", "require_structured"))
                : Boolean.TRUE.equals(getBoolean(config, "validationStrict", "validation_strict", "validationRequireStructured", "validation_require_structured"));
        if (!requiredFields.isEmpty()) {
            strict = true;
        }

        Boolean passOnUnknown = getBoolean(schemaMap, "passOnUnknown", "pass_on_unknown");
        if (passOnUnknown == null) {
            passOnUnknown = getBoolean(config, "validationPassOnUnknown", "validation_pass_on_unknown");
        }

        String passField = firstNonBlank(
                getString(schemaMap, "passField", "pass_field", "resultField", "result_field"),
                "pass"
        );
        String scoreField = firstNonBlank(
                getString(schemaMap, "scoreField", "score_field", "qualityField", "quality_field"),
                "score"
        );
        String feedbackField = firstNonBlank(
                getString(schemaMap, "feedbackField", "feedback_field", "reasonField", "reason_field"),
                "feedback"
        );

        return new ValidationSchema(
                new ArrayList<>(requiredFields),
                passThreshold,
                passField,
                scoreField,
                feedbackField,
                strict,
                passOnUnknown == null || passOnUnknown
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        for (String key : keys) {
            if (isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value instanceof Map<?, ?> mapValue) {
                return (Map<String, Object>) mapValue;
            }
        }
        return Collections.emptyMap();
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
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
            if (isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
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
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text) || "通过".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "0".equals(text) || "no".equals(text) || "n".equals(text) || "失败".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private Double getDouble(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
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
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private String appendScoreFeedback(String feedback, double score, double threshold) {
        String prefix = isBlank(feedback) ? "" : feedback + " | ";
        return prefix + "score=" + score + ", threshold=" + threshold;
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

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    public record ValidationResult(boolean valid, String feedback) {
    }

    public record CriticDecision(boolean pass, String feedback) {
    }

    private record ValidationSchema(List<String> requiredFields,
                                    Double passThreshold,
                                    String passField,
                                    String scoreField,
                                    String feedbackField,
                                    boolean strict,
                                    boolean passOnUnknown) {
    }
}
