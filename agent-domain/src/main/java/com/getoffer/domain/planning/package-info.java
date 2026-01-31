/**
 * Planning 领域 - 规划与编排域
 *
 * <p>职责：SOP 模板管理、执行计划生成、任务编排</p>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>SOP 模板：标准作业程序模板，定义可复用的工作流</li>
 *   <li>执行计划：将用户请求转化为可执行的 DAG 工作流</li>
 *   <li>任务编排：管理任务依赖关系和执行顺序</li>
 * </ul>
 *
 * <h3>聚合根</h3>
 * <ul>
 *   <li>{@link com.getoffer.domain.planning.model.entity.SopTemplateEntity}</li>
 *   <li>{@link com.getoffer.domain.planning.model.entity.AgentPlanEntity}</li>
 * </ul>
 *
 * <h3>核心实体</h3>
 * <ul>
 *   <li>SopTemplate - SOP 模板（主表）</li>
 *   <li>AgentPlan - 执行计划（主表）</li>
 *   <li>DAGNode - DAG 节点</li>
 *   <li>DAGEdge - DAG 边</li>
 * </ul>
 *
 * <h3>领域服务</h3>
 * <ul>
 *   <li>PlannerDomainService - 规划器服务（生成执行计划）</li>
 *   <li>PlanOrchestrationService - 计划编排服务</li>
 *   <li>SopTemplateService - SOP 模板管理服务</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-30
 */
package com.getoffer.domain.planning;
