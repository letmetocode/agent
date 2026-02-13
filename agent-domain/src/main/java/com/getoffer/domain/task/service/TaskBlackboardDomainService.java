package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Task 黑板写回领域服务：负责输出合并策略、输出键策略与上下文增量合并。
 */
@Service
public class TaskBlackboardDomainService {

    public Map<String, Object> buildContextDelta(AgentTaskEntity task,
                                                  String output,
                                                  Function<String, Map<String, Object>> jsonParser) {
        Map<String, Object> config = task == null ? null : task.getConfigSnapshot();
        Map<String, Object> delta = new HashMap<>();

        boolean merge = getBoolean(config, "mergeOutput", "merge_output", "outputMerge");
        if (merge && jsonParser != null) {
            Map<String, Object> parsed = jsonParser.apply(output);
            if (parsed != null && !parsed.isEmpty()) {
                delta.putAll(parsed);
            }
        }

        if (delta.isEmpty()) {
            String outputKey = getString(config, "outputKey", "output_key", "resultKey", "result_key");
            if (isBlank(outputKey)) {
                outputKey = defaultIfBlank(task == null ? null : task.getNodeId(), "output");
            }
            delta.put(outputKey, output);
        }
        return delta;
    }

    public Map<String, Object> mergeContext(Map<String, Object> currentContext,
                                            Map<String, Object> delta) {
        Map<String, Object> mergedContext = currentContext == null
                ? new HashMap<>()
                : new HashMap<>(currentContext);
        if (delta != null && !delta.isEmpty()) {
            mergedContext.putAll(delta);
        }
        return mergedContext;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
