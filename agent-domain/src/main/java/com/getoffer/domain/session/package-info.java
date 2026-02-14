/**
 * Session 领域：承载会话生命周期、回合状态与消息沉淀。
 *
 * <p>核心聚合根：{@link com.getoffer.domain.session.model.entity.AgentSessionEntity}</p>
 *
 * <p>关键实体：SessionTurn / SessionMessage。</p>
 *
 * <p>关键领域服务：
 * {@link com.getoffer.domain.session.service.SessionConversationDomainService}</p>
 *
 * <p>当前部署为单机模式，用户与治理者同账号，不涉及多租户隔离语义。</p>
 */
package com.getoffer.domain.session;
