/**
 * 仓储接口层
 * <p>
 * 定义领域层的仓储接口，由基础设施层负责实现。
 * 仓储封装了对象持久化和检索的逻辑，提供类似集合的接口访问领域对象。
 * <p>
 * 主要仓储接口：
 * <ul>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository} - Agent 注册表仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentSessionRepository} - 用户会话仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.ISopTemplateRepository} - SOP 模板仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentPlanRepository} - 执行计划仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentTaskRepository} - 任务仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.ITaskExecutionRepository} - 任务执行记录仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository} - 工具目录仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IAgentToolRelationRepository} - Agent-工具关联关系仓储</li>
 *   <li>{@link com.getoffer.domain.agent.adapter.repository.IVectorStoreRegistryRepository} - 向量存储注册表仓储</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-29
 */
package com.getoffer.domain.agent.adapter.repository;
