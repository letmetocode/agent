package com.getoffer.infrastructure.planning;

import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.model.valobj.RootWorkflowDraft;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 root Agent 的候选 Workflow Draft 规划实现。
 */
@Slf4j
@Component
public class RootWorkflowDraftPlannerImpl implements IRootWorkflowDraftPlanner {

    private final IAgentFactory agentFactory;
    private final JsonCodec jsonCodec;
    private final String rootAgentKey;

    public RootWorkflowDraftPlannerImpl(IAgentFactory agentFactory,
                                        JsonCodec jsonCodec,
                                        @Value("${planner.root.agent-key:root}") String rootAgentKey) {
        this.agentFactory = agentFactory;
        this.jsonCodec = jsonCodec;
        this.rootAgentKey = StringUtils.defaultIfBlank(rootAgentKey, "root");
    }

    @Override
    public RootWorkflowDraft planDraft(Long sessionId, String userQuery, Map<String, Object> context) {
        if (StringUtils.isBlank(userQuery)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户输入为空，无法生成候选Workflow草案");
        }
        String conversationId = "root-plan-" + (sessionId == null ? "unknown" : sessionId);
        ChatClient rootClient = agentFactory.createAgent(rootAgentKey, conversationId);
        String prompt = buildPrompt(userQuery, context);
        ChatClient.CallResponseSpec response = rootClient.prompt(prompt).call();
        String content = response == null ? null : response.content();
        Map<String, Object> payload = parsePayload(content);
        return toDraft(payload, userQuery);
    }

    private String buildPrompt(String userQuery, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是Workflow规划器。请根据用户请求生成可执行的DAG草案，并只返回JSON。");
        prompt.append("JSON必须包含字段：category,name,routeDescription,graphDefinition,inputSchema,defaultConfig,toolPolicy,constraints,inputSchemaVersion,nodeSignature。");
        prompt.append("graphDefinition.version 必须固定为2，并显式输出 groups（可为空数组）。");
        prompt.append("其中 graphDefinition.nodes 每个节点至少包含 id,name,type,config；type 仅允许 WORKER/CRITIC。");
        prompt.append("graphDefinition.edges 必须是有向无环图，禁止自环与回路。");
        prompt.append("edges 的 from/to 必须引用 nodes 中真实存在的节点 id，不允许输出 START/END 等伪节点。");
        prompt.append("若无法判断复杂流程，至少给出一个可执行 WORKER 节点。");
        prompt.append("\n用户请求：").append(userQuery);
        if (context != null && !context.isEmpty()) {
            prompt.append("\n上下文：").append(jsonCodec.writeValue(context));
        }
        return prompt.toString();
    }

    private Map<String, Object> parsePayload(String content) {
        if (StringUtils.isBlank(content)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "root 规划返回为空");
        }
        Map<String, Object> payload = tryReadMap(content.trim());
        if (payload != null) {
            return payload;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            payload = tryReadMap(content.substring(start, end + 1));
            if (payload != null) {
                return payload;
            }
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "root 规划结果不是有效 JSON");
    }

    private Map<String, Object> tryReadMap(String text) {
        try {
            return jsonCodec.readMap(text);
        } catch (Exception ex) {
            log.debug("Failed to parse root workflow draft json: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private RootWorkflowDraft toDraft(Map<String, Object> payload, String userQuery) {
        Map<String, Object> normalized = payload == null ? Collections.emptyMap() : payload;
        Object draftObj = normalized.get("workflowDraft");
        if (draftObj instanceof Map<?, ?>) {
            normalized = (Map<String, Object>) draftObj;
        }

        RootWorkflowDraft draft = new RootWorkflowDraft();
        draft.setCategory(getString(normalized, "category"));
        draft.setName(getString(normalized, "name"));
        draft.setRouteDescription(StringUtils.defaultIfBlank(getString(normalized, "routeDescription"), userQuery));
        draft.setGraphDefinition(getMap(normalized, "graphDefinition", "graph"));
        draft.setInputSchema(defaultMap(getMap(normalized, "inputSchema")));
        draft.setDefaultConfig(defaultMap(getMap(normalized, "defaultConfig")));
        draft.setToolPolicy(defaultMap(getMap(normalized, "toolPolicy")));
        draft.setConstraints(defaultMap(getMap(normalized, "constraints")));
        draft.setInputSchemaVersion(StringUtils.defaultIfBlank(getString(normalized, "inputSchemaVersion"), "v1"));
        draft.setNodeSignature(getString(normalized, "nodeSignature"));
        return draft;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?>) {
                return new HashMap<>((Map<String, Object>) value);
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> defaultMap(Map<String, Object> source) {
        return source == null ? new HashMap<>() : source;
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }
}
