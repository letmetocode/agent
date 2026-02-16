package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.types.enums.PlanStatusEnum;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 执行计划仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentPlanRepository {

    /**
     * 保存执行计划
     */
    AgentPlanEntity save(AgentPlanEntity entity);

    /**
     * 更新执行计划 (带乐观锁)
     */
    AgentPlanEntity update(AgentPlanEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentPlanEntity findById(Long id);

    /**
     * 根据会话 ID 查询
     */
    List<AgentPlanEntity> findBySessionId(Long sessionId);

    /**
     * 根据状态查询
     */
    List<AgentPlanEntity> findByStatus(PlanStatusEnum status);

    /**
     * 根据状态和优先级查询 (用于调度器)
     */
    List<AgentPlanEntity> findByStatusAndPriority(PlanStatusEnum status);

    /**
     * 按状态分页查询。
     */
    List<AgentPlanEntity> findByStatusPaged(PlanStatusEnum status, int offset, int limit);

    /**
     * 查询所有计划
     */
    List<AgentPlanEntity> findAll();

    /**
     * 统计计划总数。
     */
    default long countAll() {
        List<AgentPlanEntity> plans = findAll();
        return plans == null ? 0L : plans.size();
    }

    /**
     * 按状态统计计划数量。
     */
    default long countByStatus(PlanStatusEnum status) {
        if (status == null) {
            return 0L;
        }
        List<AgentPlanEntity> plans = findByStatus(status);
        return plans == null ? 0L : plans.size();
    }

    /**
     * 查询最近更新计划。
     */
    default List<AgentPlanEntity> findRecent(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<AgentPlanEntity> plans = findAll();
        if (plans == null || plans.isEmpty()) {
            return Collections.emptyList();
        }
        return plans.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(AgentPlanEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentPlanEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 根据 Workflow Definition ID 查询
     */
    List<AgentPlanEntity> findByWorkflowDefinitionId(Long workflowDefinitionId);

    /**
     * 查询可执行的计划
     */
    List<AgentPlanEntity> findExecutablePlans();
}
