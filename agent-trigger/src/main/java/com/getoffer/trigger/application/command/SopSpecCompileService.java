package com.getoffer.trigger.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * SOP Spec -> Runtime Graph 编译服务。
 */
@Service
public class SopSpecCompileService {

    private static final int GRAPH_DSL_VERSION = 2;
    private static final String TASK_TYPE_WORKER = "WORKER";
    private static final String TASK_TYPE_CRITIC = "CRITIC";
    private static final String JOIN_POLICY_ALL = "all";
    private static final String JOIN_POLICY_ANY = "any";
    private static final String JOIN_POLICY_QUORUM = "quorum";
    private static final String FAILURE_POLICY_FAIL_FAST = "failFast";
    private static final String FAILURE_POLICY_FAIL_SAFE = "failSafe";

    private final ObjectMapper objectMapper;

    public SopSpecCompileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompileResult compile(Map<String, Object> sopSpec) {
        if (sopSpec == null || sopSpec.isEmpty()) {
            throw new IllegalArgumentException("sopSpec不能为空");
        }

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> rawSteps = readObjectList(sopSpec.get("steps"), "sopSpec.steps", true);
        Map<String, StepSpec> stepsById = parseSteps(rawSteps, warnings);
        validateDependencies(stepsById);

        Map<String, GroupSpec> groupsById = parseGroups(readObjectList(sopSpec.get("groups"), "sopSpec.groups", false), stepsById);
        injectImplicitGroupsFromSteps(stepsById, groupsById, warnings);

        List<Map<String, Object>> runtimeNodes = new ArrayList<>();
        List<Map<String, Object>> runtimeEdges = new ArrayList<>();
        Set<String> edgeDedup = new LinkedHashSet<>();

        for (StepSpec step : stepsById.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", step.id());
            node.put("name", step.name());
            node.put("type", step.type());
            node.put("config", step.config());
            if (StringUtils.isNotBlank(step.groupId())) {
                node.put("groupId", step.groupId());
            }
            if (step.joinPolicy() != null) {
                node.put("joinPolicy", step.joinPolicy());
            }
            if (step.failurePolicy() != null) {
                node.put("failurePolicy", step.failurePolicy());
            }
            if (step.quorum() != null) {
                node.put("quorum", step.quorum());
            }
            if (step.runPolicy() != null) {
                node.put("runPolicy", step.runPolicy());
            }
            runtimeNodes.add(node);

            for (String dep : step.dependsOn()) {
                String edgeKey = dep + "->" + step.id();
                if (!edgeDedup.add(edgeKey)) {
                    continue;
                }
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from", dep);
                edge.put("to", step.id());
                runtimeEdges.add(edge);
            }
        }

        List<Map<String, Object>> runtimeGroups = new ArrayList<>();
        for (GroupSpec group : groupsById.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", group.id());
            if (StringUtils.isNotBlank(group.name())) {
                item.put("name", group.name());
            }
            item.put("nodes", new ArrayList<>(group.nodeIds()));
            if (group.joinPolicy() != null) {
                item.put("joinPolicy", group.joinPolicy());
            }
            if (group.failurePolicy() != null) {
                item.put("failurePolicy", group.failurePolicy());
            }
            if (group.quorum() != null) {
                item.put("quorum", group.quorum());
            }
            if (group.runPolicy() != null) {
                item.put("runPolicy", group.runPolicy());
            }
            runtimeGroups.add(item);
        }

        Map<String, Object> runtimeGraph = new LinkedHashMap<>();
        runtimeGraph.put("version", GRAPH_DSL_VERSION);
        runtimeGraph.put("nodes", runtimeNodes);
        runtimeGraph.put("edges", runtimeEdges);
        runtimeGraph.put("groups", runtimeGroups);

        String compileHash = hashRuntimeGraph(runtimeGraph);
        String nodeSignature = buildNodeSignature(runtimeNodes, runtimeGroups, runtimeEdges);
        return new CompileResult(runtimeGraph, compileHash, nodeSignature, warnings);
    }

    public ValidationResult validate(Map<String, Object> sopSpec) {
        try {
            CompileResult compileResult = compile(sopSpec);
            return new ValidationResult(true, Collections.emptyList(), compileResult.warnings());
        } catch (IllegalArgumentException ex) {
            return new ValidationResult(false, List.of(ex.getMessage()), Collections.emptyList());
        }
    }

    public String hashRuntimeGraph(Map<String, Object> runtimeGraph) {
        if (runtimeGraph == null || runtimeGraph.isEmpty()) {
            return null;
        }
        Object canonical = canonicalize(runtimeGraph);
        try {
            String json = objectMapper.writeValueAsString(canonical);
            return sha256Hex(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Runtime Graph序列化失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, StepSpec> parseSteps(List<Map<String, Object>> rawSteps,
                                             List<String> warnings) {
        Map<String, StepSpec> stepsById = new LinkedHashMap<>();
        for (int i = 0; i < rawSteps.size(); i++) {
            Map<String, Object> raw = rawSteps.get(i);
            String path = "sopSpec.steps[" + i + "]";
            String id = readRequiredText(raw, path + ".id", "id", "stepId");
            if (stepsById.containsKey(id)) {
                throw new IllegalArgumentException(path + ".id重复: " + id);
            }
            String name = readOptionalText(raw, "name", "title", "label");
            if (StringUtils.isBlank(name)) {
                name = id;
                warnings.add(path + ".name为空，已回退为节点id");
            }

            String typeRaw = readOptionalText(raw, "roleType", "type", "taskType");
            String type = normalizeTaskType(typeRaw);
            if (type == null) {
                throw new IllegalArgumentException(path + ".roleType/type仅支持WORKER或CRITIC");
            }

            Map<String, Object> config = readObjectMap(raw.get("config"), path + ".config", false);
            List<String> dependsOn = readStringList(raw, path, "dependsOn", "dependencies", "deps");
            String groupId = readOptionalText(raw, "groupId", "group_id");
            String joinPolicy = normalizeJoinPolicy(readOptionalText(raw, "joinPolicy", "join_policy", "dependencyJoinPolicy"));
            String failurePolicy = normalizeFailurePolicy(readOptionalText(raw, "failurePolicy", "failure_policy"));
            Integer quorum = readInteger(raw, "quorum", "joinQuorum");
            String runPolicy = normalizeRunPolicy(readOptionalText(raw, "runPolicy", "run_policy"));

            stepsById.put(id, new StepSpec(
                    id,
                    name,
                    type,
                    config == null ? new LinkedHashMap<>() : config,
                    dependsOn,
                    groupId,
                    joinPolicy,
                    failurePolicy,
                    quorum,
                    runPolicy
            ));
        }
        return stepsById;
    }

    private void validateDependencies(Map<String, StepSpec> stepsById) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (String id : stepsById.keySet()) {
            indegree.put(id, 0);
            adjacency.put(id, new LinkedHashSet<>());
        }

        for (StepSpec step : stepsById.values()) {
            for (String dep : step.dependsOn()) {
                if (StringUtils.equals(dep, step.id())) {
                    throw new IllegalArgumentException("sopSpec.steps依赖非法: 节点" + step.id() + "不能依赖自身");
                }
                if (!stepsById.containsKey(dep)) {
                    throw new IllegalArgumentException("sopSpec.steps依赖非法: 节点" + step.id() + "引用了不存在依赖" + dep);
                }
                if (adjacency.get(dep).add(step.id())) {
                    indegree.put(step.id(), indegree.get(step.id()) + 1);
                }
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            visited++;
            for (String next : adjacency.getOrDefault(node, Collections.emptySet())) {
                int in = indegree.get(next) - 1;
                indegree.put(next, in);
                if (in == 0) {
                    queue.add(next);
                }
            }
        }

        if (visited != stepsById.size()) {
            throw new IllegalArgumentException("sopSpec.steps存在循环依赖");
        }
    }

    private Map<String, GroupSpec> parseGroups(List<Map<String, Object>> rawGroups,
                                                Map<String, StepSpec> stepsById) {
        Map<String, GroupSpec> groupsById = new LinkedHashMap<>();
        for (int i = 0; i < rawGroups.size(); i++) {
            Map<String, Object> raw = rawGroups.get(i);
            String path = "sopSpec.groups[" + i + "]";
            String id = readRequiredText(raw, path + ".id", "id", "groupId", "group_id");
            if (groupsById.containsKey(id)) {
                throw new IllegalArgumentException(path + ".id重复: " + id);
            }

            List<String> nodeIds = readStringList(raw, path, "nodes", "nodeIds", "members");
            for (String nodeId : nodeIds) {
                if (!stepsById.containsKey(nodeId)) {
                    throw new IllegalArgumentException(path + ".nodes包含不存在节点: " + nodeId);
                }
            }

            groupsById.put(id, new GroupSpec(
                    id,
                    readOptionalText(raw, "name", "title", "label"),
                    new LinkedHashSet<>(nodeIds),
                    normalizeJoinPolicy(readOptionalText(raw, "joinPolicy", "join_policy", "dependencyJoinPolicy")),
                    normalizeFailurePolicy(readOptionalText(raw, "failurePolicy", "failure_policy")),
                    readInteger(raw, "quorum", "joinQuorum"),
                    normalizeRunPolicy(readOptionalText(raw, "runPolicy", "run_policy"))
            ));
        }
        return groupsById;
    }

    private void injectImplicitGroupsFromSteps(Map<String, StepSpec> stepsById,
                                               Map<String, GroupSpec> groupsById,
                                               List<String> warnings) {
        for (StepSpec step : stepsById.values()) {
            if (StringUtils.isBlank(step.groupId())) {
                continue;
            }
            GroupSpec group = groupsById.get(step.groupId());
            if (group == null) {
                group = new GroupSpec(step.groupId(), step.groupId(), new LinkedHashSet<>(), null, null, null, null);
                groupsById.put(step.groupId(), group);
                warnings.add("sopSpec.steps.groupId=" + step.groupId() + "未声明分组，已自动补齐默认分组");
            }
            group.nodeIds().add(step.id());
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(canonicalize(item));
            }
            return normalized;
        }
        return value;
    }

    private String buildNodeSignature(List<Map<String, Object>> nodes,
                                      List<Map<String, Object>> groups,
                                      List<Map<String, Object>> edges) {
        List<String> nodeSignatures = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String id = Objects.toString(node.get("id"), "");
            String type = Objects.toString(node.get("type"), "WORKER");
            String join = Objects.toString(node.get("joinPolicy"), "-");
            String failure = Objects.toString(node.get("failurePolicy"), "-");
            String quorum = Objects.toString(node.get("quorum"), "-");
            nodeSignatures.add(id + ":" + type + ":join=" + join + ":fail=" + failure + ":q=" + quorum);
        }
        Collections.sort(nodeSignatures);

        List<String> groupSignatures = new ArrayList<>();
        for (Map<String, Object> group : groups) {
            String id = Objects.toString(group.get("id"), "");
            String join = Objects.toString(group.get("joinPolicy"), "-");
            String failure = Objects.toString(group.get("failurePolicy"), "-");
            String quorum = Objects.toString(group.get("quorum"), "-");
            groupSignatures.add(id + ":join=" + join + ":fail=" + failure + ":q=" + quorum);
        }
        Collections.sort(groupSignatures);

        List<String> edgeSignatures = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            edgeSignatures.add(Objects.toString(edge.get("from"), "") + "->" + Objects.toString(edge.get("to"), ""));
        }
        Collections.sort(edgeSignatures);

        String raw = "v2#nodes=" + String.join("|", nodeSignatures)
                + "#groups=" + String.join("|", groupSignatures)
                + "#edges=" + String.join("|", edgeSignatures);
        return "sig_" + sha256Hex(raw).substring(0, 24);
    }

    private String sha256Hex(String content) {
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

    private List<Map<String, Object>> readObjectList(Object value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + "不能为空");
            }
            return new ArrayList<>();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(field + "必须是数组");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(field + "元素必须是对象");
            }
            result.add(copyObjectMap(map));
        }
        if (required && result.isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return result;
    }

    private Map<String, Object> readObjectMap(Object value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + "不能为空");
            }
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + "必须是对象");
        }
        return copyObjectMap(map);
    }

    private List<String> readStringList(Map<String, Object> source, String path, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return new ArrayList<>();
        }
        Object value = null;
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            value = source.get(key);
            if (value != null) {
                break;
            }
        }
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(path + "字段必须是数组");
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (Object item : list) {
            String text = item == null ? null : String.valueOf(item).trim();
            if (StringUtils.isBlank(text)) {
                continue;
            }
            dedup.add(text);
        }
        return new ArrayList<>(dedup);
    }

    private String normalizeTaskType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TASK_TYPE_WORKER;
        }
        if (TASK_TYPE_WORKER.equalsIgnoreCase(raw)) {
            return TASK_TYPE_WORKER;
        }
        if (TASK_TYPE_CRITIC.equalsIgnoreCase(raw)) {
            return TASK_TYPE_CRITIC;
        }
        return null;
    }

    private String normalizeJoinPolicy(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (JOIN_POLICY_ANY.equalsIgnoreCase(raw)) {
            return JOIN_POLICY_ANY;
        }
        if (JOIN_POLICY_QUORUM.equalsIgnoreCase(raw)) {
            return JOIN_POLICY_QUORUM;
        }
        return JOIN_POLICY_ALL;
    }

    private String normalizeFailurePolicy(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (FAILURE_POLICY_FAIL_SAFE.equalsIgnoreCase(raw)
                || "fail_safe".equalsIgnoreCase(raw)) {
            return FAILURE_POLICY_FAIL_SAFE;
        }
        return FAILURE_POLICY_FAIL_FAST;
    }

    private String normalizeRunPolicy(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return raw.trim();
    }

    private Integer readInteger(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
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

    private String readRequiredText(Map<String, Object> source,
                                    String path,
                                    String... keys) {
        String text = readOptionalText(source, keys);
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException(path + "不能为空");
        }
        return text;
    }

    private String readOptionalText(Map<String, Object> source,
                                    String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return target;
    }

    private record StepSpec(String id,
                            String name,
                            String type,
                            Map<String, Object> config,
                            List<String> dependsOn,
                            String groupId,
                            String joinPolicy,
                            String failurePolicy,
                            Integer quorum,
                            String runPolicy) {
    }

    private record GroupSpec(String id,
                             String name,
                             LinkedHashSet<String> nodeIds,
                             String joinPolicy,
                             String failurePolicy,
                             Integer quorum,
                             String runPolicy) {
    }

    public record CompileResult(Map<String, Object> sopRuntimeGraph,
                                String compileHash,
                                String nodeSignature,
                                List<String> warnings) {
    }

    public record ValidationResult(boolean pass,
                                   List<String> issues,
                                   List<String> warnings) {
    }
}
