package com.getoffer.domain.planning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Graph DSL 统一规则：结构校验、节点签名与图哈希。
 */
public final class GraphDslPolicyService {

    public static final int GRAPH_DSL_VERSION = 2;

    private GraphDslPolicyService() {
    }

    public static void validateGraphDslV2(Map<String, Object> graphDefinition, String fieldName) {
        if (graphDefinition == null || graphDefinition.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        int version = parseInt(graphDefinition.get("version"), fieldName + ".version");
        if (version != GRAPH_DSL_VERSION) {
            throw new IllegalArgumentException(fieldName + ".version仅支持" + GRAPH_DSL_VERSION);
        }

        Object nodesObj = graphDefinition.get("nodes");
        if (!(nodesObj instanceof List<?> nodes) || nodes.isEmpty()) {
            throw new IllegalArgumentException(fieldName + ".nodes不能为空");
        }
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> node)) {
                throw new IllegalArgumentException(fieldName + ".nodes元素必须是对象");
            }
            String nodeId = trimToNull(node.get("id"));
            if (nodeId == null) {
                throw new IllegalArgumentException(fieldName + ".nodes.id不能为空");
            }
            String type = trimToNull(node.get("type"));
            if (type == null) {
                throw new IllegalArgumentException(fieldName + ".nodes.type不能为空");
            }
        }

        Object groupsObj = graphDefinition.get("groups");
        if (groupsObj != null) {
            if (!(groupsObj instanceof List<?> groups)) {
                throw new IllegalArgumentException(fieldName + ".groups必须是数组");
            }
            for (Object groupObj : groups) {
                if (!(groupObj instanceof Map<?, ?> group)) {
                    throw new IllegalArgumentException(fieldName + ".groups元素必须是对象");
                }
                String groupId = trimToNull(group.get("id"));
                if (groupId == null) {
                    throw new IllegalArgumentException(fieldName + ".groups.id不能为空");
                }
            }
        }

        Object edgesObj = graphDefinition.get("edges");
        if (edgesObj != null) {
            if (!(edgesObj instanceof List<?> edges)) {
                throw new IllegalArgumentException(fieldName + ".edges必须是数组");
            }
            for (Object edgeObj : edges) {
                if (!(edgeObj instanceof Map<?, ?> edge)) {
                    throw new IllegalArgumentException(fieldName + ".edges元素必须是对象");
                }
                String from = trimToNull(edge.get("from"));
                String to = trimToNull(edge.get("to"));
                if (from == null || to == null) {
                    throw new IllegalArgumentException(fieldName + ".edges.from/to不能为空");
                }
            }
        }
    }

    public static String computeNodeSignature(Map<String, Object> graphDefinition) {
        Integer parsedVersion = getInteger(graphDefinition, "version", "dslVersion", "graphVersion");
        int version = parsedVersion == null ? -1 : parsedVersion;

        List<Map<String, Object>> nodes = new ArrayList<>(getMapList(graphDefinition, "nodes"));
        nodes.sort(Comparator.comparing(item -> defaultString(getString(item, "id"))));
        List<String> nodeSignatures = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = getString(node, "id", "nodeId", "node_id");
            String type = getString(node, "type", "taskType", "task_type");
            Map<String, Object> config = getMap(node, "config", "configSnapshot", "config_snapshot", "options");
            String agentKey = getString(config, "agentKey", "agent_key");
            Long agentId = getLong(config, "agentId", "agent_id");
            String groupId = getString(node, "groupId", "group_id");
            String joinPolicy = getString(node, "joinPolicy", "join_policy", "dependencyJoinPolicy");
            String failurePolicy = getString(node, "failurePolicy", "failure_policy");
            Integer quorum = getInteger(node, "quorum", "joinQuorum");
            nodeSignatures.add(defaultString(nodeId) + ":" + defaultIfBlank(type, "WORKER") + ":"
                    + defaultIfBlank(agentKey, "-") + ":" + (agentId == null ? "-" : agentId)
                    + ":g=" + defaultIfBlank(groupId, "-")
                    + ":join=" + defaultIfBlank(joinPolicy, "-")
                    + ":fail=" + defaultIfBlank(failurePolicy, "-")
                    + ":q=" + (quorum == null ? "-" : quorum));
        }

        List<Map<String, Object>> groups = new ArrayList<>(getMapList(graphDefinition, "groups"));
        groups.sort(Comparator.comparing(item -> defaultString(getString(item, "id", "groupId", "group_id"))));
        List<String> groupSignatures = new ArrayList<>();
        for (Map<String, Object> group : groups) {
            String groupId = getString(group, "id", "groupId", "group_id");
            String joinPolicy = getString(group, "joinPolicy", "join_policy", "dependencyJoinPolicy");
            String failurePolicy = getString(group, "failurePolicy", "failure_policy");
            Integer quorum = getInteger(group, "quorum", "joinQuorum");
            List<String> members = readStringList(group, "nodes", "nodeIds", "members");
            members.sort(String::compareTo);
            groupSignatures.add(defaultIfBlank(groupId, "-")
                    + ":join=" + defaultIfBlank(joinPolicy, "-")
                    + ":fail=" + defaultIfBlank(failurePolicy, "-")
                    + ":q=" + (quorum == null ? "-" : quorum)
                    + ":members=" + String.join(";", members));
        }

        List<Map<String, Object>> edges = new ArrayList<>(getMapList(graphDefinition, "edges"));
        edges.sort(Comparator.comparing(item -> defaultString(getString(item, "from"))
                + "->" + defaultString(getString(item, "to"))));
        List<String> edgeSignatures = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            edgeSignatures.add(defaultString(getString(edge, "from"))
                    + "->" + defaultString(getString(edge, "to")));
        }
        return "v=" + version
                + "#nodes=" + String.join("|", nodeSignatures)
                + "#groups=" + String.join("|", groupSignatures)
                + "#edges=" + String.join(",", edgeSignatures);
    }

    public static String hashGraph(Map<String, Object> graphDefinition, ObjectMapper objectMapper) {
        if (graphDefinition == null || graphDefinition.isEmpty() || objectMapper == null) {
            return null;
        }
        Object canonical = canonicalize(graphDefinition);
        try {
            String json = objectMapper.writeValueAsString(canonical);
            return sha256Hex(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Graph DSL序列化失败: " + ex.getMessage(), ex);
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int parseInt(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "必须是整数");
        }
    }

    private static Integer getInteger(Map<String, Object> source, String... keys) {
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
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Long getLong(Map<String, Object> source, String... keys) {
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
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getMapList(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty() || isBlank(key)) {
            return Collections.emptyList();
        }
        Object value = source.get(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value instanceof Map<?, ?>) {
                return (Map<String, Object>) value;
            }
        }
        return null;
    }

    private static String getString(Map<String, Object> source, String... keys) {
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
            String text = String.valueOf(value);
            if (!text.trim().isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static List<String> readStringList(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            if (isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (!(value instanceof List<?> list)) {
                continue;
            }
            Set<String> result = new LinkedHashSet<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text;
                if (item instanceof Map<?, ?> itemMap) {
                    Object nodeId = itemMap.get("id");
                    if (nodeId == null) {
                        nodeId = itemMap.get("nodeId");
                    }
                    if (nodeId == null) {
                        nodeId = itemMap.get("node_id");
                    }
                    text = nodeId == null ? null : String.valueOf(nodeId).trim();
                } else {
                    text = String.valueOf(item).trim();
                }
                if (!isBlank(text)) {
                    result.add(text);
                }
            }
            return new ArrayList<>(result);
        }
        return Collections.emptyList();
    }

    private static Object canonicalize(Object source) {
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (source instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(canonicalize(item));
            }
            return normalized;
        }
        return source;
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
