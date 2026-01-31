package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.PlanStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 执行计划 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 会话 ID (关联 agent_sessions.id)
     */
    private Long sessionId;

    /**
     * SOP 模板 ID (关联 sop_templates.id，可空)
     */
    private Long sopTemplateId;

    /**
     * 计划目标
     */
    private String planGoal;

    /**
     * 执行图 (JSONB，运行时图谱副本)
     */
    private String executionGraph;

    /**
     * 全局上下文 (JSONB，黑板)
     */
    private String globalContext;

    /**
     * 状态
     */
    private PlanStatusEnum status;

    /**
     * 优先级 (数值越大优先级越高)
     */
    private Integer priority;

    /**
     * 错误摘要
     */
    private String errorSummary;

    /**
     * 版本号 (乐观锁)
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
