package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;

import java.util.List;

/**
 * Workflow Definition 仓储接口。
 */
public interface IWorkflowDefinitionRepository {

    WorkflowDefinitionEntity save(WorkflowDefinitionEntity entity);

    WorkflowDefinitionEntity update(WorkflowDefinitionEntity entity);

    WorkflowDefinitionEntity findById(Long id);

    List<WorkflowDefinitionEntity> findAll();

    List<WorkflowDefinitionEntity> findByStatus(WorkflowDefinitionStatusEnum status);

    default List<WorkflowDefinitionEntity> findProductionActive() {
        return findByStatus(WorkflowDefinitionStatusEnum.ACTIVE);
    }

    WorkflowDefinitionEntity findLatestVersionByDefinitionKey(String tenantId, String definitionKey);
}

