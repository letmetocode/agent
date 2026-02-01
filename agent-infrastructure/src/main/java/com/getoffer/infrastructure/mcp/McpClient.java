package com.getoffer.infrastructure.mcp;

import java.util.Map;

/**
 * MCP（Model Context Protocol）客户端接口。
 * <p>
 * 定义与MCP Server交互的基本操作，包括工具调用、状态检查和资源释放。
 * MCP协议允许LLM应用与外部工具/服务进行标准化通信。
 * </p>
 *
 * @author getoffer
 * @since 2026-01-31
 */
public interface McpClient {

    /**
     * 调用MCP工具。
     *
     * @param toolName 工具名称
     * @param input 输入参数（JSON字符串）
     * @param toolContext 工具上下文信息
     * @return 工具执行结果（JSON字符串）
     */
    String call(String toolName, String input, Map<String, Object> toolContext);

    /**
     * 检查客户端连接是否存活。
     *
     * @return 如果客户端正在运行返回true，否则返回false
     */
    boolean isRunning();

    /**
     * 关闭客户端并释放资源。
     */
    void shutdown();
}
