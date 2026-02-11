package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;

import java.util.List;

/**
 * Workflow Draft 仓储接口。
 */
public interface IWorkflowDraftRepository {

    WorkflowDraftEntity save(WorkflowDraftEntity entity);

    WorkflowDraftEntity update(WorkflowDraftEntity entity);

    WorkflowDraftEntity findById(Long id);

    List<WorkflowDraftEntity> findAll();

    List<WorkflowDraftEntity> findByStatus(WorkflowDraftStatusEnum status);

    WorkflowDraftEntity findLatestDraftByDedupHash(String dedupHash);
}

