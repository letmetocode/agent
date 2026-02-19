package com.getoffer.domain.planning.service;

import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow 路由匹配策略领域服务。
 */
@Service
public class WorkflowRoutingPolicyDomainService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final WorkflowRoutingPolicyDomainService DEFAULT = new WorkflowRoutingPolicyDomainService();

    public static WorkflowRoutingPolicyDomainService defaultInstance() {
        return DEFAULT;
    }

    public WorkflowDefinitionEntity matchDefinition(String userQuery,
                                                    List<WorkflowDefinitionEntity> definitions) {
        if (StringUtils.isBlank(userQuery)) {
            return null;
        }
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }

        String query = userQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> tokens = tokenize(query);

        WorkflowDefinitionEntity best = null;
        double bestScore = 0D;
        for (WorkflowDefinitionEntity definition : definitions) {
            if (definition == null || !Boolean.TRUE.equals(definition.getIsActive())) {
                continue;
            }
            String trigger = StringUtils.defaultString(definition.getRouteDescription()).toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(trigger)) {
                continue;
            }
            double score = computeScore(query, tokens, trigger);
            if (score > bestScore) {
                best = definition;
                bestScore = score;
                continue;
            }
            if (score == bestScore && score > 0 && best != null) {
                Integer currentVersion = definition.getVersion();
                Integer bestVersion = best.getVersion();
                if (currentVersion != null && bestVersion != null && currentVersion > bestVersion) {
                    best = definition;
                }
            }
        }
        return bestScore > 0 ? best : null;
    }

    private double computeScore(String query, Set<String> tokens, String trigger) {
        if (trigger.contains(query)) {
            return 100D;
        }
        if (query.contains(trigger)) {
            return 100D;
        }
        if (tokens == null || tokens.isEmpty()) {
            return 0D;
        }
        int hits = 0;
        for (String token : tokens) {
            if (StringUtils.isBlank(token)) {
                continue;
            }
            if (trigger.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    private Set<String> tokenize(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.isNotBlank(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
