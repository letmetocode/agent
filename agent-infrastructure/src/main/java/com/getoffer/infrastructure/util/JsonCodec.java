package com.getoffer.infrastructure.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JSON 编解码工具。
 *
 * @author getoffer
 * @since 2026-02-01
 */
@Component
public class JsonCodec {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<Map<String, Object>>() {};
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<List<String>>() {};

    private final ObjectMapper objectMapper;

    /**
     * 创建 JsonCodec。
     */
    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 读取 JSON 为 Map。
     */
    public Map<String, Object> readMap(String json) {
        return readValue(json, MAP_REF);
    }

    /**
     * 读取 JSON 为 String List。
     */
    public List<String> readStringList(String json) {
        return readValue(json, STRING_LIST_REF);
    }

    /**
     * 读取 JSON 为指定类型。
     */
    public <T> T readValue(String json, TypeReference<T> type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException ex) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Failed to parse json", ex);
        }
    }

    /**
     * 写出为 JSON 字符串。
     */
    public String writeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Failed to write json", ex);
        }
    }

    /**
     * 对象转换。
     */
    public <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, targetType);
    }

    /**
     * 获取 ObjectMapper。
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
