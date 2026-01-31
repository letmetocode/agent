/**
 * Task 领域 - 任务执行域
 *
 * <p>职责：任务调度、执行、验证、重试</p>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>任务执行：单个任务的执行生命周期管理</li>
 *   <li>任务调度：根据 DAG 依赖关系调度任务执行</li>
 *   <li>结果验证：验证任务输出是否符合预期</li>
 *   <li>失败重试：自动重试失败的任务</li>
 * </ul>
 *
 * <h3>聚合根</h3>
 * <ul>
 *   <li>{@link com.getoffer.domain.task.model.entity.AgentTaskEntity}</li>
 * </ul>
 *
 * <h3>核心实体</h3>
 * <ul>
 *   <li>AgentTask - 任务（主表）</li>
 *   <li>TaskExecution - 任务执行记录（主表）</li>
 * </ul>
 *
 * <h3>领域服务</h3>
 * <ul>
 *   <li>TaskSchedulerService - 任务调度服务</li>
 *   <li>TaskExecutionService - 任务执行服务</li>
 *   <li>TaskValidationService - 任务验证服务</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-30
 */
package com.getoffer.domain.task;
