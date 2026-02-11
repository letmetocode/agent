package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.infrastructure.dao.WorkflowDefinitionDao;
import com.getoffer.infrastructure.dao.po.WorkflowDefinitionPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow Definition 仓储实现。
 */
@Repository
public class WorkflowDefinitionRepositoryImpl implements IWorkflowDefinitionRepository {

    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final JsonCodec jsonCodec;

    public WorkflowDefinitionRepositoryImpl(WorkflowDefinitionDao workflowDefinitionDao,
                                            JsonCodec jsonCodec) {
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public WorkflowDefinitionEntity save(WorkflowDefinitionEntity entity) {
        entity.validate();
        WorkflowDefinitionPO po = toPO(entity);
        workflowDefinitionDao.insert(po);
        return toEntity(po);
    }

    @Override
    public WorkflowDefinitionEntity update(WorkflowDefinitionEntity entity) {
        entity.validate();
        WorkflowDefinitionPO po = toPO(entity);
        workflowDefinitionDao.update(po);
        return toEntity(po);
    }

    @Override
    public WorkflowDefinitionEntity findById(Long id) {
        WorkflowDefinitionPO po = workflowDefinitionDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    @Override
    public List<WorkflowDefinitionEntity> findAll() {
        return workflowDefinitionDao.selectAll().stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<WorkflowDefinitionEntity> findByStatus(WorkflowDefinitionStatusEnum status) {
        return workflowDefinitionDao.selectByStatus(status).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public WorkflowDefinitionEntity findLatestVersionByDefinitionKey(String tenantId, String definitionKey) {
        WorkflowDefinitionPO po = workflowDefinitionDao.selectLatestByTenantAndDefinitionKey(tenantId, definitionKey);
        return po == null ? null : toEntity(po);
    }

    private WorkflowDefinitionEntity toEntity(WorkflowDefinitionPO po) {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setId(po.getId());
        entity.setDefinitionKey(po.getDefinitionKey());
        entity.setTenantId(po.getTenantId());
        entity.setCategory(po.getCategory());
        entity.setName(po.getName());
        entity.setVersion(po.getVersion());
        entity.setRouteDescription(po.getRouteDescription());
        entity.setGraphDefinition(jsonCodec.readMap(po.getGraphDefinition()));
        entity.setInputSchema(jsonCodec.readMap(po.getInputSchema()));
        entity.setDefaultConfig(jsonCodec.readMap(po.getDefaultConfig()));
        entity.setToolPolicy(jsonCodec.readMap(po.getToolPolicy()));
        entity.setInputSchemaVersion(po.getInputSchemaVersion());
        entity.setConstraints(jsonCodec.readMap(po.getConstraints()));
        entity.setNodeSignature(po.getNodeSignature());
        entity.setStatus(po.getStatus());
        entity.setPublishedFromDraftId(po.getPublishedFromDraftId());
        entity.setIsActive(po.getIsActive());
        entity.setCreatedBy(po.getCreatedBy());
        entity.setApprovedBy(po.getApprovedBy());
        entity.setApprovedAt(po.getApprovedAt());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    private WorkflowDefinitionPO toPO(WorkflowDefinitionEntity entity) {
        WorkflowDefinitionPO po = WorkflowDefinitionPO.builder()
                .id(entity.getId())
                .definitionKey(entity.getDefinitionKey())
                .tenantId(entity.getTenantId())
                .category(entity.getCategory())
                .name(entity.getName())
                .version(entity.getVersion())
                .routeDescription(entity.getRouteDescription())
                .inputSchemaVersion(entity.getInputSchemaVersion())
                .nodeSignature(entity.getNodeSignature())
                .status(entity.getStatus())
                .publishedFromDraftId(entity.getPublishedFromDraftId())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(entity.getApprovedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        po.setGraphDefinition(jsonCodec.writeValue(entity.getGraphDefinition()));
        po.setInputSchema(jsonCodec.writeValue(entity.getInputSchema()));
        po.setDefaultConfig(jsonCodec.writeValue(entity.getDefaultConfig()));
        po.setToolPolicy(jsonCodec.writeValue(entity.getToolPolicy()));
        po.setConstraints(jsonCodec.writeValue(entity.getConstraints()));
        return po;
    }
}

