/**
 * Agent 领域 - 代理管理域
 *
 * <p>职责：Agent 的定义、配置、能力管理</p>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>Agent 是什么：定义 AI Agent 的身份和基础人设</li>
 *   <li>能做什么：Agent 的工具能力和知识库配置</li>
 *   <li>使用什么模型：LLM 模型配置和参数</li>
 * </ul>
 *
 * <h3>聚合根</h3>
 * <ul>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentRegistryEntity}</li>
 * </ul>
 *
 * <h3>核心实体</h3>
 * <ul>
 *   <li>AgentRegistry - Agent 注册表（主表）</li>
 *   <li>VectorStoreRegistry - 向量存储配置</li>
 *   <li>AgentToolCatalog - 工具能力目录</li>
 * </ul>
 *
 * <h3>领域服务</h3>
 * <ul>
 *   <li>AgentConfigurationService - Agent 配置管理</li>
 *   <li>ToolCapabilityService - 工具能力管理</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-30
 */
package com.getoffer.domain.agent;
