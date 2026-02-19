package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.QualityEvaluationEventEntity;
import com.getoffer.domain.task.model.valobj.QualityExperimentSummary;

import java.util.Collections;
import java.util.List;

/**
 * 质量评估事件仓储。
 */
public interface IQualityEvaluationEventRepository {

    QualityEvaluationEventEntity save(QualityEvaluationEventEntity entity);

    default List<QualityEvaluationEventEntity> findByPlanId(Long planId, int limit) {
        return Collections.emptyList();
    }

    /**
     * 按过滤条件分页查询质量评估事件。
     */
    default List<QualityEvaluationEventEntity> findByFiltersPaged(Long planId,
                                                                  Long taskId,
                                                                  String experimentKey,
                                                                  String experimentVariant,
                                                                  String evaluatorType,
                                                                  Boolean pass,
                                                                  String keyword,
                                                                  int offset,
                                                                  int limit) {
        return Collections.emptyList();
    }

    /**
     * 按过滤条件统计质量评估事件数量。
     */
    default long countByFilters(Long planId,
                                Long taskId,
                                String experimentKey,
                                String experimentVariant,
                                String evaluatorType,
                                Boolean pass,
                                String keyword) {
        return 0L;
    }

    /**
     * 按实验 key + variant 聚合统计质量事件。
     */
    default List<QualityExperimentSummary> summarizeByExperiment(Long planId,
                                                                 String experimentKey,
                                                                 String evaluatorType,
                                                                 int limit) {
        return Collections.emptyList();
    }
}
