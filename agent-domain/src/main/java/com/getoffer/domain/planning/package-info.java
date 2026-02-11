/**
 * Planning 领域 - Workflow 规划与编排。
 *
 * <p>职责：Workflow Definition/Draft 管理、路由决策、Plan 生成与任务编排。</p>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>Workflow Definition：生产流程定义（版本不可变）。</li>
 *   <li>Workflow Draft：候选草案（运行时产物与治理对象）。</li>
 *   <li>Routing Decision：路由命中/兜底决策审计。</li>
 *   <li>Plan：执行实例，运行时以 execution graph 为事实源。</li>
 * </ul>
 */
package com.getoffer.domain.planning;
