package com.getoffer.domain.task.adapter.repository;

import com.getoffer.domain.task.model.entity.QualityEvaluationEventEntity;

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
}
