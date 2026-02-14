package com.getoffer.domain.task.service;

import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.types.enums.TaskStatusEnum;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 依赖判定领域服务：封装依赖节点状态到任务可执行决策的规则。
 */
@Service
public class TaskDependencyPolicyDomainService implements TaskDependencyPolicy {

    private static final String JOIN_POLICY_ALL = "all";
    private static final String JOIN_POLICY_ANY = "any";
    private static final String JOIN_POLICY_QUORUM = "quorum";
    private static final String FAILURE_POLICY_FAIL_FAST = "failFast";
    private static final String FAILURE_POLICY_FAIL_SAFE = "failSafe";

    @Override
    public DependencyDecision resolveDependencyDecision(AgentTaskEntity task,
                                                        Map<String, TaskStatusEnum> statusByNode) {
        if (task == null || task.getStatus() != TaskStatusEnum.PENDING) {
            return DependencyDecision.WAITING;
        }

        List<String> dependencies = task.getDependencyNodeIds();
        if (dependencies == null || dependencies.isEmpty()) {
            return DependencyDecision.SATISFIED;
        }

        if (statusByNode == null || statusByNode.isEmpty()) {
            return DependencyDecision.WAITING;
        }

        GraphPolicy policy = resolveGraphPolicy(task);
        DependencyStatusSummary summary = summarizeDependencies(dependencies, statusByNode);

        if (JOIN_POLICY_ANY.equals(policy.joinPolicy)) {
            return resolveAnyPolicy(summary, policy);
        }
        if (JOIN_POLICY_QUORUM.equals(policy.joinPolicy)) {
            return resolveQuorumPolicy(summary, policy);
        }
        return resolveAllPolicy(summary, policy);
    }

    private DependencyDecision resolveAllPolicy(DependencyStatusSummary summary,
                                                GraphPolicy policy) {
        if (summary.total <= 0) {
            return DependencyDecision.SATISFIED;
        }
        if (summary.completedCount == summary.total) {
            return DependencyDecision.SATISFIED;
        }

        if (policy.failFast && summary.failedOrSkippedCount > 0) {
            return DependencyDecision.BLOCKED;
        }

        if (!policy.failFast && summary.terminalCount == summary.total) {
            return DependencyDecision.SATISFIED;
        }

        return DependencyDecision.WAITING;
    }

    private DependencyDecision resolveAnyPolicy(DependencyStatusSummary summary,
                                                GraphPolicy policy) {
        if (summary.completedCount > 0) {
            return DependencyDecision.SATISFIED;
        }
        if (summary.terminalCount == summary.total) {
            return DependencyDecision.BLOCKED;
        }
        if (policy.failFast && summary.failedOrSkippedCount > 0) {
            return DependencyDecision.BLOCKED;
        }
        return DependencyDecision.WAITING;
    }

    private DependencyDecision resolveQuorumPolicy(DependencyStatusSummary summary,
                                                   GraphPolicy policy) {
        if (summary.total <= 0) {
            return DependencyDecision.SATISFIED;
        }

        int quorum = normalizeQuorum(policy.quorum, summary.total);
        if (summary.completedCount >= quorum) {
            return DependencyDecision.SATISFIED;
        }

        int maxPossibleCompleted = summary.total - summary.failedOrSkippedCount;
        if (summary.terminalCount == summary.total && summary.completedCount < quorum) {
            return DependencyDecision.BLOCKED;
        }
        if (policy.failFast && maxPossibleCompleted < quorum) {
            return DependencyDecision.BLOCKED;
        }
        return DependencyDecision.WAITING;
    }

    private int normalizeQuorum(Integer rawQuorum, int dependencyCount) {
        if (dependencyCount <= 0) {
            return 0;
        }
        if (rawQuorum == null || rawQuorum <= 0) {
            return Math.min(1, dependencyCount);
        }
        return Math.min(rawQuorum, dependencyCount);
    }

    private DependencyStatusSummary summarizeDependencies(List<String> dependencies,
                                                          Map<String, TaskStatusEnum> statusByNode) {
        int completed = 0;
        int failedOrSkipped = 0;
        int unresolved = 0;

        for (String dependency : dependencies) {
            if (isBlank(dependency)) {
                continue;
            }
            TaskStatusEnum status = statusByNode.get(dependency);
            if (status == TaskStatusEnum.COMPLETED) {
                completed++;
                continue;
            }
            if (status == TaskStatusEnum.FAILED || status == TaskStatusEnum.SKIPPED) {
                failedOrSkipped++;
                continue;
            }
            unresolved++;
        }

        return new DependencyStatusSummary(
                dependencies.size(),
                completed,
                failedOrSkipped,
                unresolved,
                completed + failedOrSkipped
        );
    }

    private GraphPolicy resolveGraphPolicy(AgentTaskEntity task) {
        Map<String, Object> configSnapshot = task == null ? null : task.getConfigSnapshot();
        Map<String, Object> graphPolicy = extractGraphPolicy(configSnapshot);
        String joinPolicy = normalizeJoinPolicy(readText(graphPolicy, "joinPolicy", "join_policy", "dependencyJoinPolicy"));
        String failurePolicy = normalizeFailurePolicy(readText(graphPolicy, "failurePolicy", "failure_policy"));
        Integer quorum = readInteger(graphPolicy, "quorum", "joinQuorum");
        return new GraphPolicy(joinPolicy,
                FAILURE_POLICY_FAIL_FAST.equals(failurePolicy),
                quorum);
    }

    private Map<String, Object> extractGraphPolicy(Map<String, Object> configSnapshot) {
        if (configSnapshot == null || configSnapshot.isEmpty()) {
            return new HashMap<>();
        }
        Object value = configSnapshot.get("graphPolicy");
        if (value instanceof Map<?, ?> map) {
            return copyObjectMap(map);
        }
        value = configSnapshot.get("graph_policy");
        if (value instanceof Map<?, ?> map) {
            return copyObjectMap(map);
        }
        return new HashMap<>();
    }

    private String normalizeJoinPolicy(String raw) {
        if (isBlank(raw)) {
            return JOIN_POLICY_ALL;
        }
        String normalized = raw.trim().toLowerCase();
        if (JOIN_POLICY_ANY.equals(normalized)) {
            return JOIN_POLICY_ANY;
        }
        if (JOIN_POLICY_QUORUM.equals(normalized)) {
            return JOIN_POLICY_QUORUM;
        }
        return JOIN_POLICY_ALL;
    }

    private String normalizeFailurePolicy(String raw) {
        if (isBlank(raw)) {
            return FAILURE_POLICY_FAIL_FAST;
        }
        String normalized = raw.trim();
        if (equalsIgnoreCase(normalized, FAILURE_POLICY_FAIL_SAFE)
                || equalsIgnoreCase(normalized, "fail_safe")) {
            return FAILURE_POLICY_FAIL_SAFE;
        }
        return FAILURE_POLICY_FAIL_FAST;
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
            if (isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> source, String... keys) {
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

    private Map<String, Object> copyObjectMap(Map<?, ?> source) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private record GraphPolicy(String joinPolicy,
                               boolean failFast,
                               Integer quorum) {
    }

    private record DependencyStatusSummary(int total,
                                           int completedCount,
                                           int failedOrSkippedCount,
                                           int unresolvedCount,
                                           int terminalCount) {
    }
}
