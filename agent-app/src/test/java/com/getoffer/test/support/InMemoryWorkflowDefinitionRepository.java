package com.getoffer.test.support;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 内存 Workflow Definition 仓储。
 */
public class InMemoryWorkflowDefinitionRepository implements IWorkflowDefinitionRepository {

    private final Map<Long, WorkflowDefinitionEntity> store = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public WorkflowDefinitionEntity save(WorkflowDefinitionEntity entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public WorkflowDefinitionEntity update(WorkflowDefinitionEntity entity) {
        if (entity == null || entity.getId() == null) {
            return entity;
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public WorkflowDefinitionEntity findById(Long id) {
        return store.get(id);
    }

    @Override
    public List<WorkflowDefinitionEntity> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<WorkflowDefinitionEntity> findByStatus(WorkflowDefinitionStatusEnum status) {
        return store.values().stream()
                .filter(item -> Objects.equals(status, item.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowDefinitionEntity findLatestVersionByDefinitionKey(String tenantId, String definitionKey) {
        return store.values().stream()
                .filter(item -> Objects.equals(tenantId, item.getTenantId()))
                .filter(item -> Objects.equals(definitionKey, item.getDefinitionKey()))
                .max(Comparator.comparing(WorkflowDefinitionEntity::getVersion, Comparator.nullsFirst(Integer::compareTo)))
                .orElse(null);
    }
}

