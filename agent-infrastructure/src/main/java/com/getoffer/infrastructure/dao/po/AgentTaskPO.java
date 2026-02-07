package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.TaskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务 PO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskPO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 计划 ID (关联 agent_plans.id)
     */
    private Long planId;

    /**
     * 节点 ID (图谱中的节点ID)
     */
    private String nodeId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务类型 ('WORKER', 'CRITIC')
     */
    private String taskType;

    /**
     * 状态
     */
    private TaskStatusEnum status;

    /**
     * DAG 依赖节点 IDs (JSONB 数组)
     */
    private String dependencyNodeIds;

    /**
     * 输入上下文 (JSONB)
     */
    private String inputContext;

    /**
     * 配置快照 (JSONB)
     */
    private String configSnapshot;

    /**
     * 输出结果
     */
    private String outputResult;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 当前重试次数
     */
    private Integer currentRetry;

    /**
     * claim 持有者
     */
    private String claimOwner;

    /**
     * claim 时间
     */
    private LocalDateTime claimAt;

    /**
     * lease 过期时间
     */
    private LocalDateTime leaseUntil;

    /**
     * 执行代际（每次 claim 递增）
     */
    private Integer executionAttempt;

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
