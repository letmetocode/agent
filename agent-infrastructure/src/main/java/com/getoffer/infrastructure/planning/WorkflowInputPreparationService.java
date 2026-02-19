package com.getoffer.infrastructure.planning;

import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow 输入预处理服务：负责 userInput 解析、补全与校验。
 */
public class WorkflowInputPreparationService {

    private static final Pattern DESCRIPTION_FIXED_VALUE_PATTERN =
            Pattern.compile("(?:固定(?:值)?为|默认(?:值)?为|默认为)\\s*[\"'“”]?([^\"'“”，。；;\\n]+)");
    private final JsonCodec jsonCodec;

    public WorkflowInputPreparationService(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public Map<String, Object> parseUserInput(String userQuery) {
        if (StringUtils.isBlank(userQuery)) {
            return Collections.emptyMap();
        }
        String trimmed = userQuery.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> parsed = jsonCodec.readMap(trimmed);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> input = new HashMap<>();
        input.put("userQuery", userQuery);
        input.put("query", userQuery);
        return input;
    }

    public void enrichRequiredInputFromQuery(Map<String, Object> inputSchema,
                                             Map<String, Object> userInput,
                                             String userQuery) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }
        if (userInput == null) {
            return;
        }
        Object required = inputSchema.get("required");
        if (!(required instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) required) {
            String key = item == null ? null : String.valueOf(item);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (hasInputValue(userInput.get(key))) {
                continue;
            }
            Object inferred = inferRequiredFieldValue(key, inputSchema, userQuery);
            if (!hasInputValue(inferred)) {
                continue;
            }
            userInput.put(key, inferred);
        }
    }

    public void enrichRequiredInputFromContext(Map<String, Object> inputSchema,
                                               Map<String, Object> userInput,
                                               Map<String, Object> globalContext) {
        if (inputSchema == null || inputSchema.isEmpty() || userInput == null || globalContext == null || globalContext.isEmpty()) {
            return;
        }
        Object required = inputSchema.get("required");
        if (!(required instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) required) {
            String key = item == null ? null : String.valueOf(item);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (hasInputValue(userInput.get(key))) {
                continue;
            }
            Object contextValue = globalContext.get(key);
            if (!hasInputValue(contextValue)) {
                contextValue = findContextValueByIgnoreCase(globalContext, key);
            }
            if (!hasInputValue(contextValue)) {
                continue;
            }
            userInput.put(key, contextValue);
        }
    }

    public void validateInput(Map<String, Object> inputSchema, Map<String, Object> userInput) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return;
        }
        Object required = inputSchema.get("required");
        if (!(required instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) required) {
            String key = item == null ? null : String.valueOf(item);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = userInput == null ? null : userInput.get(key);
            if (!hasInputValue(value)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Missing required input: " + key);
            }
        }
    }

    private Object findContextValueByIgnoreCase(Map<String, Object> globalContext, String key) {
        if (globalContext == null || globalContext.isEmpty() || StringUtils.isBlank(key)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : globalContext.entrySet()) {
            if (StringUtils.equalsIgnoreCase(entry.getKey(), key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasInputValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.isNotBlank((String) value);
        }
        if (value instanceof List<?>) {
            return !((List<?>) value).isEmpty();
        }
        if (value instanceof Map<?, ?>) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private Object inferRequiredFieldValue(String key,
                                           Map<String, Object> inputSchema,
                                           String userQuery) {
        if (StringUtils.isBlank(userQuery)) {
            return null;
        }
        String raw = extractFieldValueFromText(key, userQuery);
        if (StringUtils.isBlank(raw)) {
            raw = extractFieldValueFromDescription(key, inputSchema);
        }
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return convertBySchemaType(inputSchema, key, raw);
    }

    private String extractFieldValueFromText(String key, String userQuery) {
        List<String> aliases = buildFieldAliases(key);
        for (String alias : aliases) {
            String value = extractByAlias(alias, userQuery);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> buildFieldAliases(String key) {
        List<String> aliases = new ArrayList<>();
        aliases.add(key);

        String snake = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (!StringUtils.equalsIgnoreCase(snake, key)) {
            aliases.add(snake);
            aliases.add(snake.replace("_", " "));
        }

        String compact = key.toLowerCase(Locale.ROOT);
        if (!aliases.contains(compact)) {
            aliases.add(compact);
        }

        if ("productName".equalsIgnoreCase(key)) {
            aliases.add("商品名");
            aliases.add("商品名称");
            aliases.add("产品名");
            aliases.add("产品名称");
        }
        return aliases;
    }

    private String extractByAlias(String alias, String text) {
        if (StringUtils.isBlank(alias) || StringUtils.isBlank(text)) {
            return null;
        }
        String escapedAlias = Pattern.quote(alias);
        Pattern naturalPattern = Pattern.compile("(?:^|[\\s,，。;；])" + escapedAlias
                + "\\s*(?:是|为|=|:|：)\\s*[\"'“”]?([^,，。;；\\n\"'“”]+)");
        Matcher naturalMatcher = naturalPattern.matcher(text);
        if (naturalMatcher.find()) {
            return cleanCapturedValue(naturalMatcher.group(1));
        }

        Pattern strictKvPattern = Pattern.compile("[\"']?" + escapedAlias
                + "[\"']?\\s*[:=]\\s*[\"'“”]?([^,，。;；}\\]\\n\"'“”]+)");
        Matcher strictKvMatcher = strictKvPattern.matcher(text);
        if (strictKvMatcher.find()) {
            return cleanCapturedValue(strictKvMatcher.group(1));
        }
        return null;
    }

    private String cleanCapturedValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("^[\"'“”]+|[\"'“”]+$", "");
        return StringUtils.trimToNull(cleaned);
    }

    @SuppressWarnings("unchecked")
    private String extractFieldValueFromDescription(String key, Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty() || StringUtils.isBlank(key)) {
            return null;
        }
        Map<String, Object> properties = getMap(inputSchema, "properties");
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        Object fieldSchemaObj = properties.get(key);
        if (!(fieldSchemaObj instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaObj;
        String description = getString(fieldSchema, "description", "desc");
        if (StringUtils.isBlank(description)) {
            return null;
        }
        Matcher matcher = DESCRIPTION_FIXED_VALUE_PATTERN.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        return cleanCapturedValue(matcher.group(1));
    }

    @SuppressWarnings("unchecked")
    private Object convertBySchemaType(Map<String, Object> inputSchema, String key, String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        if (inputSchema == null || inputSchema.isEmpty()) {
            return rawValue;
        }
        Map<String, Object> properties = getMap(inputSchema, "properties");
        if (properties == null || properties.isEmpty()) {
            return rawValue;
        }
        Object fieldSchemaObj = properties.get(key);
        if (!(fieldSchemaObj instanceof Map<?, ?>)) {
            return rawValue;
        }
        Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaObj;
        String type = getString(fieldSchema, "type");
        if (StringUtils.isBlank(type)) {
            return rawValue;
        }
        String normalizedType = type.trim().toLowerCase(Locale.ROOT);
        try {
            if ("array".equals(normalizedType)) {
                String[] parts = rawValue.split("[,，/、;；]");
                List<String> values = new ArrayList<>();
                for (String part : parts) {
                    String value = StringUtils.trimToNull(part);
                    if (value != null) {
                        values.add(value);
                    }
                }
                return values;
            }
            if ("integer".equals(normalizedType)) {
                return Integer.parseInt(rawValue.trim());
            }
            if ("number".equals(normalizedType)) {
                return Double.parseDouble(rawValue.trim());
            }
            if ("boolean".equals(normalizedType)) {
                String lower = rawValue.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "y".equals(lower)
                        || "是".equals(rawValue.trim())) {
                    return true;
                }
                if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower) || "n".equals(lower)
                        || "否".equals(rawValue.trim())) {
                    return false;
                }
            }
        } catch (Exception ignore) {
            return rawValue;
        }
        return rawValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value instanceof Map<?, ?>) {
                return (Map<String, Object>) value;
            }
        }
        return null;
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }
}
