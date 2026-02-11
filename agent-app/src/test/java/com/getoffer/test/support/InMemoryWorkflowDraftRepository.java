package com.getoffer.test.support;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 内存 Workflow Draft 仓储。
 */
public class InMemoryWorkflowDraftRepository implements IWorkflowDraftRepository {

    private final Map<Long, WorkflowDraftEntity> store = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public WorkflowDraftEntity save(WorkflowDraftEntity entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public WorkflowDraftEntity update(WorkflowDraftEntity entity) {
        if (entity == null || entity.getId() == null) {
            return entity;
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public WorkflowDraftEntity findById(Long id) {
        return store.get(id);
    }

    @Override
    public List<WorkflowDraftEntity> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<WorkflowDraftEntity> findByStatus(WorkflowDraftStatusEnum status) {
        return store.values().stream()
                .filter(item -> Objects.equals(status, item.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowDraftEntity findLatestDraftByDedupHash(String dedupHash) {
        return store.values().stream()
                .filter(item -> Objects.equals(dedupHash, item.getDedupHash()))
                .filter(item -> item.getStatus() == WorkflowDraftStatusEnum.DRAFT)
                .max(Comparator.comparing(WorkflowDraftEntity::getId, Comparator.nullsFirst(Long::compareTo)))
                .orElse(null);
    }
}

