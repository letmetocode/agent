/**
 * Task 领域：承载任务调度、执行、评估、重试与依赖判定规则。
 *
 * <p>核心聚合根：{@link com.getoffer.domain.task.model.entity.AgentTaskEntity}</p>
 *
 * <p>关键实体：TaskExecution / PlanTaskEvent / TaskShareLink。</p>
 *
 * <p>关键领域服务：
 * <ul>
 *   <li>{@link com.getoffer.domain.task.service.TaskDependencyPolicyDomainService}</li>
 *   <li>{@link com.getoffer.domain.task.service.TaskExecutionDomainService}</li>
 *   <li>{@link com.getoffer.domain.task.service.TaskEvaluationDomainService}</li>
 *   <li>{@link com.getoffer.domain.task.service.TaskRecoveryDomainService}</li>
 * </ul>
 *
 * <p>运行时编排基于 Runtime Graph（有向无环图）依赖语义推进节点状态。</p>
 */
package com.getoffer.domain.task;
