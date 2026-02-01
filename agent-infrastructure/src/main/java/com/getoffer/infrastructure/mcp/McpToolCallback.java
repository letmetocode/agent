package com.getoffer.infrastructure.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Collections;
import java.util.Map;

/**
 * MCP 工具回调实现类。
 * <p>
 * 实现Spring AI的ToolCallback接口，将MCP工具封装为可被ChatClient调用的形式。
 * 通过McpClientManager与MCP Server通信，执行工具调用。
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
public class McpToolCallback implements ToolCallback {

    private final ToolDefinition toolDefinition;
    private final Map<String, Object> serverConfig;
    private final McpClientManager mcpClientManager;

    /**
     * 创建 MCP 工具回调。
     */
    public McpToolCallback(ToolDefinition toolDefinition,
                           Map<String, Object> serverConfig,
                           McpClientManager mcpClientManager) {
        this.toolDefinition = toolDefinition;
        this.serverConfig = serverConfig;
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 获取工具定义。
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    /**
     * 调用 MCP 工具。
     */
    @Override
    public String call(String input) {
        return mcpClientManager.callTool(serverConfig, toolDefinition.name(), input, Collections.emptyMap());
    }

    /**
     * 调用 MCP 工具（携带上下文）。
     */
    @Override
    public String call(String input, ToolContext toolContext) {
        Map<String, Object> context = toolContext == null ? null : toolContext.getContext();
        return mcpClientManager.callTool(serverConfig, toolDefinition.name(), input, context);
    }
}
