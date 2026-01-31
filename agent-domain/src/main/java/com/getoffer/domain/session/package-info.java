/**
 * Session 领域 - 会话管理域
 *
 * <p>职责：用户交互会话管理、上下文维护</p>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>用户交互：管理用户与 Agent 的交互会话生命周期</li>
 *   <li>上下文维护：维护会话状态、消息历史、用户偏好</li>
 *   <li>会话隔离：不同用户的会话完全隔离</li>
 * </ul>
 *
 * <h3>聚合根</h3>
 * <ul>
 *   <li>{@link com.getoffer.domain.session.model.entity.AgentSessionEntity}</li>
 * </ul>
 *
 * <h3>核心实体</h3>
 * <ul>
 *   <li>AgentSession - 用户会话（主表）</li>
 *   <li>SessionMessage - 会话消息记录</li>
 * </ul>
 *
 * <h3>领域服务</h3>
 * <ul>
 *   <li>SessionManagementService - 会话生命周期管理</li>
 *   <li>ContextMaintenanceService - 上下文维护服务</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-30
 */
package com.getoffer.domain.session;
