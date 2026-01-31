/**
 * 实体类层
 * <p>
 * 包含 Agent 系统的所有实体类。
 * 实体是具有唯一标识和生命周期的领域对象，封装了业务规则和行为。
 * <p>
 * 主要实体：
 * <ul>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentRegistryEntity} - Agent 注册表实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentSessionEntity} - 用户会话实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.SopTemplateEntity} - SOP 模板实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentPlanEntity} - 执行计划实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentTaskEntity} - 任务实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.TaskExecutionEntity} - 任务执行记录实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity} - 工具目录实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.AgentToolRelationEntity} - Agent-工具关联关系实体</li>
 *   <li>{@link com.getoffer.domain.agent.model.entity.VectorStoreRegistryEntity} - 向量存储注册表实体</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-29
 */
package com.getoffer.domain.agent.model.entity;
