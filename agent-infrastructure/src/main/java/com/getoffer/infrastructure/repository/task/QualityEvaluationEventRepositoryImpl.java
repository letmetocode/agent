package com.getoffer.infrastructure.repository.task;

import com.getoffer.domain.task.adapter.repository.IQualityEvaluationEventRepository;
import com.getoffer.domain.task.model.entity.QualityEvaluationEventEntity;
import com.getoffer.infrastructure.dao.QualityEvaluationEventDao;
import com.getoffer.infrastructure.dao.po.QualityEvaluationEventPO;
import com.getoffer.infrastructure.util.JsonCodec;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 质量评估事件仓储实现。
 */
@Repository
public class QualityEvaluationEventRepositoryImpl implements IQualityEvaluationEventRepository {

    private final QualityEvaluationEventDao qualityEvaluationEventDao;
    private final JsonCodec jsonCodec;

    public QualityEvaluationEventRepositoryImpl(QualityEvaluationEventDao qualityEvaluationEventDao,
                                                JsonCodec jsonCodec) {
        this.qualityEvaluationEventDao = qualityEvaluationEventDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public QualityEvaluationEventEntity save(QualityEvaluationEventEntity entity) {
        entity.validate();
        QualityEvaluationEventPO po = toPO(entity);
        qualityEvaluationEventDao.insert(po);
        return toEntity(po);
    }

    @Override
    public List<QualityEvaluationEventEntity> findByPlanId(Long planId, int limit) {
        if (planId == null || limit <= 0) {
            return Collections.emptyList();
        }
        List<QualityEvaluationEventPO> rows = qualityEvaluationEventDao.selectByPlanId(planId, limit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private QualityEvaluationEventEntity toEntity(QualityEvaluationEventPO po) {
        if (po == null) {
            return null;
        }
        QualityEvaluationEventEntity entity = new QualityEvaluationEventEntity();
        entity.setId(po.getId());
        entity.setPlanId(po.getPlanId());
        entity.setTaskId(po.getTaskId());
        entity.setExecutionId(po.getExecutionId());
        entity.setEvaluatorType(po.getEvaluatorType());
        entity.setExperimentKey(po.getExperimentKey());
        entity.setExperimentVariant(po.getExperimentVariant());
        entity.setSchemaVersion(po.getSchemaVersion());
        entity.setScore(po.getScore());
        entity.setPass(po.getPass());
        entity.setFeedback(po.getFeedback());
        entity.setCreatedAt(po.getCreatedAt());
        if (po.getPayload() != null) {
            entity.setPayload(jsonCodec.readMap(po.getPayload()));
        }
        return entity;
    }

    private QualityEvaluationEventPO toPO(QualityEvaluationEventEntity entity) {
        if (entity == null) {
            return null;
        }
        QualityEvaluationEventPO po = QualityEvaluationEventPO.builder()
                .id(entity.getId())
                .planId(entity.getPlanId())
                .taskId(entity.getTaskId())
                .executionId(entity.getExecutionId())
                .evaluatorType(entity.getEvaluatorType())
                .experimentKey(entity.getExperimentKey())
                .experimentVariant(entity.getExperimentVariant())
                .schemaVersion(entity.getSchemaVersion())
                .score(entity.getScore())
                .pass(entity.getPass())
                .feedback(entity.getFeedback())
                .createdAt(entity.getCreatedAt())
                .build();
        if (entity.getPayload() != null) {
            po.setPayload(jsonCodec.writeValue(entity.getPayload()));
        }
        return po;
    }
}
