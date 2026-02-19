package com.getoffer.domain.planning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.types.enums.TaskTypeEnum;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Workflow 图策略内核：统一图归一化、校验、签名与图策略解析。
 */
@Slf4j
public final class WorkflowGraphPolicyKernel {

    public static final int GRAPH_DSL_VERSION = GraphDslPolicyService.GRAPH_DSL_VERSION;
    public static final String JOIN_POLICY_ALL = "all";
    public static final String JOIN_POLICY_ANY = "any";
    public static final String JOIN_POLICY_QUORUM = "quorum";
    public static final String FAILURE_POLICY_FAIL_FAST = "failFast";
    public static final String FAILURE_POLICY_FAIL_SAFE = "failSafe";

    private static final Set<String> VIRTUAL_ENTRY_NODE_IDS = Set.of(
            "START", "BEGIN", "ENTRY", "ROOT_START", "SOURCE"
    );
    private static final Set<String> VIRTUAL_EXIT_NODE_IDS = Set.of(
            "END", "FINISH", "EXIT", "ROOT_END", "SINK"
    );

    private WorkflowGraphPolicyKernel() {
    }

    @FunctionalInterface
    public interface NodeConfigNormalizer {
        void normalize(String nodeId, Map<String, Object> config);
    }

    public static void validateGraphDslV2(Map<String, Object> graphDefinition, String fieldName) {
        GraphDslPolicyService.validateGraphDslV2(graphDefinition, fieldName);
    }

    public static String computeNodeSignature(Map<String, Object> graphDefinition) {
        return GraphDslPolicyService.computeNodeSignature(graphDefinition);
    }

    public static String hashGraph(Map<String, Object> graphDefinition, ObjectMapper objectMapper) {
        return GraphDslPolicyService.hashGraph(graphDefinition, objectMapper);
    }

    public static Map<String, Object> normalizeAndValidateGraphDefinition(Map<String, Object> rawGraph,
                                                                           String scene,
                                                                           boolean allowCandidateVersionUpgrade,
                                                                           NodeConfigNormalizer nodeConfigNormalizer) {
        Map<String, Object> graph = rawGraph == null ? new HashMap<>() : new HashMap<>(rawGraph);
        if (graph.isEmpty()) {
            throw new IllegalArgumentException(scene + "图定义为空");
        }

        Integer version = getInteger(graph, "version", "dslVersion", "graphVersion");
        if ((version == null || version != GRAPH_DSL_VERSION)
                && allowCandidateVersionUpgrade
                && tryUpgradeCandidateGraphDslVersion(graph, version, scene)) {
            version = GRAPH_DSL_VERSION;
        }
        if (version == null || version != GRAPH_DSL_VERSION) {
            throw new IllegalArgumentException(scene + "图DSL版本非法，仅支持version=" + GRAPH_DSL_VERSION);
        }

        List<Map<String, Object>> nodes = getMapList(graph, "nodes");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(scene + "节点为空");
        }

        Map<String, Map<String, Object>> normalizedNodesById = normalizeAndValidateNodes(nodes, scene, nodeConfigNormalizer);
        Map<String, Map<String, Object>> normalizedGroupsById = normalizeAndValidateGroups(graph, scene);
        Map<String, Set<String>> groupMembersById = buildGroupMembers(normalizedNodesById, normalizedGroupsById, scene);
        List<Map<String, Object>> normalizedEdges = normalizeAndValidateEdges(
                getMapList(graph, "edges"),
                normalizedNodesById.keySet(),
                groupMembersById,
                scene
        );

        Map<String, Object> normalizedGraph = new HashMap<>(graph);
        normalizedGraph.put("version", GRAPH_DSL_VERSION);
        normalizedGraph.put("nodes", new ArrayList<>(normalizedNodesById.values()));
        normalizedGraph.put("groups", new ArrayList<>(normalizedGroupsById.values()));
        normalizedGraph.put("edges", normalizedEdges);
        return normalizedGraph;
    }

    public static Map<String, Map<String, Object>> buildGroupPolicyById(List<Map<String, Object>> groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> groupPolicyById = new HashMap<>();
        for (Map<String, Object> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            String groupId = getString(group, "id", "groupId", "group_id");
            if (StringUtils.isBlank(groupId)) {
                continue;
            }
            Map<String, Object> policy = new HashMap<>();
            policy.put("joinPolicy", normalizeJoinPolicy(getString(group,
                    "joinPolicy", "join_policy", "dependencyJoinPolicy")));
            policy.put("failurePolicy", normalizeFailurePolicy(getString(group,
                    "failurePolicy", "failure_policy")));
            Integer quorum = getInteger(group, "quorum", "joinQuorum");
            if (quorum != null) {
                policy.put("quorum", quorum);
            }
            String runPolicy = normalizeRunPolicy(getString(group, "runPolicy", "run_policy"));
            if (StringUtils.isNotBlank(runPolicy)) {
                policy.put("runPolicy", runPolicy);
            }
            groupPolicyById.put(groupId, policy);
        }
        return groupPolicyById;
    }

    public static Map<String, Object> resolveGraphPolicyForNode(Map<String, Object> node,
                                                                 Map<String, Map<String, Object>> groupPolicyById) {
        Map<String, Object> graphPolicy = new HashMap<>();
        graphPolicy.put("joinPolicy", JOIN_POLICY_ALL);
        graphPolicy.put("failurePolicy", FAILURE_POLICY_FAIL_SAFE);

        String groupId = getString(node, "groupId", "group_id");
        if (StringUtils.isNotBlank(groupId)) {
            graphPolicy.put("groupId", groupId);
            if (groupPolicyById != null && groupPolicyById.containsKey(groupId)) {
                graphPolicy.putAll(groupPolicyById.get(groupId));
            }
        }

        if (containsAnyKey(node, "joinPolicy", "join_policy", "dependencyJoinPolicy")) {
            graphPolicy.put("joinPolicy", normalizeJoinPolicy(getString(node,
                    "joinPolicy", "join_policy", "dependencyJoinPolicy")));
        }
        if (containsAnyKey(node, "failurePolicy", "failure_policy")) {
            graphPolicy.put("failurePolicy", normalizeFailurePolicy(getString(node,
                    "failurePolicy", "failure_policy")));
        }
        if (containsAnyKey(node, "quorum", "joinQuorum")) {
            Integer nodeQuorum = getInteger(node, "quorum", "joinQuorum");
            if (nodeQuorum != null) {
                graphPolicy.put("quorum", nodeQuorum);
            }
        }
        if (containsAnyKey(node, "runPolicy", "run_policy")) {
            String normalizedRunPolicy = normalizeRunPolicy(getString(node, "runPolicy", "run_policy"));
            if (StringUtils.isNotBlank(normalizedRunPolicy)) {
                graphPolicy.put("runPolicy", normalizedRunPolicy);
            }
        }
        return graphPolicy;
    }

    public static String normalizeJoinPolicy(String joinPolicy) {
        if (StringUtils.equalsIgnoreCase(joinPolicy, JOIN_POLICY_ANY)) {
            return JOIN_POLICY_ANY;
        }
        if (StringUtils.equalsIgnoreCase(joinPolicy, JOIN_POLICY_QUORUM)) {
            return JOIN_POLICY_QUORUM;
        }
        return JOIN_POLICY_ALL;
    }

    public static String normalizeFailurePolicy(String failurePolicy) {
        if (StringUtils.equalsIgnoreCase(failurePolicy, FAILURE_POLICY_FAIL_FAST)
                || StringUtils.equalsIgnoreCase(failurePolicy, "fail_fast")) {
            return FAILURE_POLICY_FAIL_FAST;
        }
        return FAILURE_POLICY_FAIL_SAFE;
    }

    public static String normalizeRunPolicy(String runPolicy) {
        if (StringUtils.isBlank(runPolicy)) {
            return null;
        }
        return runPolicy.trim();
    }

    public static String normalizeJoinPolicyStrict(String raw, String fieldName) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (StringUtils.equalsIgnoreCase(raw, JOIN_POLICY_ALL)) {
            return JOIN_POLICY_ALL;
        }
        if (StringUtils.equalsIgnoreCase(raw, JOIN_POLICY_ANY)) {
            return JOIN_POLICY_ANY;
        }
        if (StringUtils.equalsIgnoreCase(raw, JOIN_POLICY_QUORUM)) {
            return JOIN_POLICY_QUORUM;
        }
        throw new IllegalArgumentException(fieldName + "仅支持all/any/quorum");
    }

    public static String normalizeFailurePolicyStrict(String raw, String fieldName) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        if (StringUtils.equalsIgnoreCase(raw, FAILURE_POLICY_FAIL_FAST)
                || StringUtils.equalsIgnoreCase(raw, "fail_fast")) {
            return FAILURE_POLICY_FAIL_FAST;
        }
        if (StringUtils.equalsIgnoreCase(raw, FAILURE_POLICY_FAIL_SAFE)
                || StringUtils.equalsIgnoreCase(raw, "fail_safe")) {
            return FAILURE_POLICY_FAIL_SAFE;
        }
        throw new IllegalArgumentException(fieldName + "仅支持failFast/failSafe");
    }

    public static String normalizeRunPolicyStrict(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return raw.trim();
    }

    private static boolean tryUpgradeCandidateGraphDslVersion(Map<String, Object> graph,
                                                              Integer version,
                                                              String scene) {
        List<Map<String, Object>> nodes = getMapList(graph, "nodes");
        if (nodes.isEmpty()) {
            return false;
        }

        if (!graph.containsKey("edges")) {
            graph.put("edges", Collections.emptyList());
        }
        if (!graph.containsKey("groups")) {
            graph.put("groups", Collections.emptyList());
        }
        graph.put("version", GRAPH_DSL_VERSION);
        log.warn("{}图DSL版本不兼容(version={})，已自动升级为version={}", scene, version, GRAPH_DSL_VERSION);
        return true;
    }

    private static Map<String, Map<String, Object>> normalizeAndValidateNodes(List<Map<String, Object>> nodes,
                                                                               String scene,
                                                                               NodeConfigNormalizer nodeConfigNormalizer) {
        Map<String, Map<String, Object>> normalizedNodesById = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || node.isEmpty()) {
                continue;
            }

            String nodeId = getString(node, "id", "nodeId", "node_id");
            if (StringUtils.isBlank(nodeId)) {
                throw new IllegalArgumentException(scene + "节点缺少id");
            }
            if (normalizedNodesById.containsKey(nodeId)) {
                throw new IllegalArgumentException(scene + "节点id重复: " + nodeId);
            }

            String rawTaskType = StringUtils.defaultIfBlank(getString(node, "type", "taskType", "task_type"), "WORKER");
            TaskTypeEnum taskType;
            try {
                taskType = TaskTypeEnum.fromText(rawTaskType);
            } catch (Exception ex) {
                throw new IllegalArgumentException(scene + "节点类型非法: " + rawTaskType);
            }

            Map<String, Object> config = getMap(node, "config", "configSnapshot", "config_snapshot", "options");
            Map<String, Object> normalizedConfig = config == null ? new HashMap<>() : new HashMap<>(config);
            if (nodeConfigNormalizer != null) {
                nodeConfigNormalizer.normalize(nodeId, normalizedConfig);
            }

            Map<String, Object> normalizedNode = new LinkedHashMap<>(node);
            normalizedNode.put("id", nodeId);
            normalizedNode.put("type", taskType.name());
            normalizedNode.put("config", normalizedConfig);

            String groupId = getString(node, "groupId", "group_id");
            if (StringUtils.isNotBlank(groupId)) {
                normalizedNode.put("groupId", groupId);
            }

            if (containsAnyKey(node, "joinPolicy", "join_policy", "dependencyJoinPolicy")) {
                normalizedNode.put("joinPolicy", normalizeJoinPolicy(getString(node,
                        "joinPolicy", "join_policy", "dependencyJoinPolicy")));
            }
            if (containsAnyKey(node, "failurePolicy", "failure_policy")) {
                normalizedNode.put("failurePolicy", normalizeFailurePolicy(getString(node,
                        "failurePolicy", "failure_policy")));
            }
            if (containsAnyKey(node, "quorum", "joinQuorum")) {
                Integer quorum = getInteger(node, "quorum", "joinQuorum");
                if (quorum != null) {
                    normalizedNode.put("quorum", quorum);
                }
            }
            if (containsAnyKey(node, "runPolicy", "run_policy")) {
                String runPolicy = normalizeRunPolicy(getString(node, "runPolicy", "run_policy"));
                if (StringUtils.isNotBlank(runPolicy)) {
                    normalizedNode.put("runPolicy", runPolicy);
                }
            }

            normalizedNodesById.put(nodeId, normalizedNode);
        }

        if (normalizedNodesById.isEmpty()) {
            throw new IllegalArgumentException(scene + "没有可用节点");
        }
        return normalizedNodesById;
    }

    private static Map<String, Map<String, Object>> normalizeAndValidateGroups(Map<String, Object> graph,
                                                                                String scene) {
        List<Map<String, Object>> groups = getMapList(graph, "groups");
        if (groups.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> normalizedGroupsById = new LinkedHashMap<>();
        for (Map<String, Object> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            String groupId = getString(group, "id", "groupId", "group_id");
            if (StringUtils.isBlank(groupId)) {
                throw new IllegalArgumentException(scene + "分组缺少id");
            }
            if (normalizedGroupsById.containsKey(groupId)) {
                throw new IllegalArgumentException(scene + "分组id重复: " + groupId);
            }

            Map<String, Object> normalizedGroup = new LinkedHashMap<>(group);
            normalizedGroup.put("id", groupId);
            normalizedGroup.put("joinPolicy", normalizeJoinPolicy(getString(group,
                    "joinPolicy", "join_policy", "dependencyJoinPolicy")));
            normalizedGroup.put("failurePolicy", normalizeFailurePolicy(getString(group,
                    "failurePolicy", "failure_policy")));
            Integer quorum = getInteger(group, "quorum", "joinQuorum");
            if (quorum != null) {
                normalizedGroup.put("quorum", quorum);
            }
            String runPolicy = normalizeRunPolicy(getString(group, "runPolicy", "run_policy"));
            if (StringUtils.isNotBlank(runPolicy)) {
                normalizedGroup.put("runPolicy", runPolicy);
            }

            List<String> memberNodeIds = readStringList(group, "nodes", "nodeIds", "members");
            if (!memberNodeIds.isEmpty()) {
                normalizedGroup.put("nodes", memberNodeIds);
            }
            normalizedGroupsById.put(groupId, normalizedGroup);
        }

        return normalizedGroupsById;
    }

    private static Map<String, Set<String>> buildGroupMembers(Map<String, Map<String, Object>> normalizedNodesById,
                                                               Map<String, Map<String, Object>> normalizedGroupsById,
                                                               String scene) {
        if (normalizedGroupsById == null || normalizedGroupsById.isEmpty()) {
            for (Map.Entry<String, Map<String, Object>> entry : normalizedNodesById.entrySet()) {
                String groupId = getString(entry.getValue(), "groupId", "group_id");
                if (StringUtils.isNotBlank(groupId)) {
                    throw new IllegalArgumentException(scene + "节点引用了不存在分组: nodeId=" + entry.getKey() + ", groupId=" + groupId);
                }
            }
            return Collections.emptyMap();
        }

        Map<String, Set<String>> groupMembersById = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : normalizedGroupsById.entrySet()) {
            String groupId = entry.getKey();
            groupMembersById.put(groupId, new LinkedHashSet<>());
            List<String> staticMembers = readStringList(entry.getValue(), "nodes", "nodeIds", "members");
            if (staticMembers == null || staticMembers.isEmpty()) {
                continue;
            }
            for (String nodeId : staticMembers) {
                if (!normalizedNodesById.containsKey(nodeId)) {
                    throw new IllegalArgumentException(scene + "分组引用了不存在节点: groupId=" + groupId + ", nodeId=" + nodeId);
                }
                groupMembersById.get(groupId).add(nodeId);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : normalizedNodesById.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> node = entry.getValue();
            String groupId = getString(node, "groupId", "group_id");
            if (StringUtils.isBlank(groupId)) {
                continue;
            }
            if (!normalizedGroupsById.containsKey(groupId)) {
                throw new IllegalArgumentException(scene + "节点引用了不存在分组: nodeId=" + nodeId + ", groupId=" + groupId);
            }
            groupMembersById.computeIfAbsent(groupId, key -> new LinkedHashSet<>()).add(nodeId);
        }

        for (Map.Entry<String, Map<String, Object>> entry : normalizedGroupsById.entrySet()) {
            String groupId = entry.getKey();
            Set<String> members = groupMembersById.getOrDefault(groupId, Collections.emptySet());
            Map<String, Object> normalizedGroup = entry.getValue();
            normalizedGroup.put("nodes", new ArrayList<>(members));
        }

        return groupMembersById;
    }

    private static List<Map<String, Object>> normalizeAndValidateEdges(List<Map<String, Object>> edges,
                                                                        Set<String> nodeIdSet,
                                                                        Map<String, Set<String>> groupMembersById,
                                                                        String scene) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> dedupEdgeSet = new HashSet<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String nodeId : nodeIdSet) {
            inDegree.put(nodeId, 0);
            adjacency.put(nodeId, new ArrayList<>());
        }

        for (Map<String, Object> edge : edges) {
            if (edge == null || edge.isEmpty()) {
                continue;
            }
            String from = getString(edge, "from", "source", "src");
            String to = getString(edge, "to", "target", "dst");
            if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
                continue;
            }
            if (isVirtualBoundaryEdge(from, to, nodeIdSet, groupMembersById)) {
                log.debug("{}边界边已忽略: {} -> {}", scene, from, to);
                continue;
            }

            Set<String> fromNodes = resolveEdgeEndpoint(from, nodeIdSet, groupMembersById);
            Set<String> toNodes = resolveEdgeEndpoint(to, nodeIdSet, groupMembersById);
            if (fromNodes.isEmpty() || toNodes.isEmpty()) {
                throw new IllegalArgumentException(scene + "边引用了不存在节点或分组: " + from + " -> " + to);
            }

            for (String fromNode : fromNodes) {
                for (String toNode : toNodes) {
                    String key = fromNode + "->" + toNode;
                    if (!dedupEdgeSet.add(key)) {
                        continue;
                    }
                    Map<String, Object> normalizedEdge = new LinkedHashMap<>();
                    normalizedEdge.put("from", fromNode);
                    normalizedEdge.put("to", toNode);
                    normalized.add(normalizedEdge);

                    adjacency.computeIfAbsent(fromNode, item -> new ArrayList<>()).add(toNode);
                    inDegree.put(toNode, inDegree.getOrDefault(toNode, 0) + 1);
                }
            }
        }

        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int index = 0;
        int visited = 0;
        while (index < queue.size()) {
            String node = queue.get(index++);
            visited++;
            List<String> next = adjacency.getOrDefault(node, Collections.emptyList());
            for (String to : next) {
                int degree = inDegree.getOrDefault(to, 0) - 1;
                inDegree.put(to, degree);
                if (degree == 0) {
                    queue.add(to);
                }
            }
        }

        if (visited != nodeIdSet.size()) {
            throw new IllegalArgumentException(scene + "图存在环路");
        }
        return normalized;
    }

    private static Set<String> resolveEdgeEndpoint(String endpoint,
                                                   Set<String> nodeIdSet,
                                                   Map<String, Set<String>> groupMembersById) {
        if (nodeIdSet.contains(endpoint)) {
            return Collections.singleton(endpoint);
        }
        if (groupMembersById != null && groupMembersById.containsKey(endpoint)) {
            Set<String> members = groupMembersById.get(endpoint);
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            return members;
        }
        return Collections.emptySet();
    }

    private static boolean isVirtualBoundaryEdge(String from,
                                                 String to,
                                                 Set<String> nodeIdSet,
                                                 Map<String, Set<String>> groupMembersById) {
        if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
            return false;
        }

        boolean fromIsEndpoint = nodeIdSet.contains(from)
                || (groupMembersById != null && groupMembersById.containsKey(from));
        boolean toIsEndpoint = nodeIdSet.contains(to)
                || (groupMembersById != null && groupMembersById.containsKey(to));

        if (isVirtualEntryNodeId(from) && (toIsEndpoint || isVirtualExitNodeId(to))) {
            return true;
        }
        if (isVirtualExitNodeId(to) && (fromIsEndpoint || isVirtualEntryNodeId(from))) {
            return true;
        }
        if (!fromIsEndpoint && !toIsEndpoint) {
            return isVirtualEntryNodeId(from) || isVirtualExitNodeId(to);
        }
        return false;
    }

    private static boolean isVirtualEntryNodeId(String nodeId) {
        String normalized = normalizeBoundaryNodeId(nodeId);
        return normalized != null && VIRTUAL_ENTRY_NODE_IDS.contains(normalized);
    }

    private static boolean isVirtualExitNodeId(String nodeId) {
        String normalized = normalizeBoundaryNodeId(nodeId);
        return normalized != null && VIRTUAL_EXIT_NODE_IDS.contains(normalized);
    }

    private static String normalizeBoundaryNodeId(String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            return null;
        }
        return nodeId.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static boolean containsAnyKey(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (source.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getMapList(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty() || StringUtils.isBlank(key)) {
            return Collections.emptyList();
        }
        Object value = source.get(key);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> source, String... keys) {
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

    private static String getString(Map<String, Object> source, String... keys) {
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

    private static Integer getInteger(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static List<String> readStringList(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (!(value instanceof List<?> list)) {
                continue;
            }
            List<String> result = new ArrayList<>();
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
                if (StringUtils.isNotBlank(text)) {
                    result.add(text);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
