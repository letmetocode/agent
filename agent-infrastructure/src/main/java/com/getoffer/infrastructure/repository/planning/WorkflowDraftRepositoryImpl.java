package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.infrastructure.dao.WorkflowDraftDao;
import com.getoffer.infrastructure.dao.po.WorkflowDraftPO;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow Draft 仓储实现。
 */
@Repository
public class WorkflowDraftRepositoryImpl implements IWorkflowDraftRepository {

    private final WorkflowDraftDao workflowDraftDao;
    private final JsonCodec jsonCodec;

    public WorkflowDraftRepositoryImpl(WorkflowDraftDao workflowDraftDao,
                                       JsonCodec jsonCodec) {
        this.workflowDraftDao = workflowDraftDao;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public WorkflowDraftEntity save(WorkflowDraftEntity entity) {
        entity.validate();
        WorkflowDraftPO po = toPO(entity);
        workflowDraftDao.insert(po);
        return toEntity(po);
    }

    @Override
    public WorkflowDraftEntity update(WorkflowDraftEntity entity) {
        entity.validate();
        WorkflowDraftPO po = toPO(entity);
        workflowDraftDao.update(po);
        return toEntity(po);
    }

    @Override
    public WorkflowDraftEntity findById(Long id) {
        WorkflowDraftPO po = workflowDraftDao.selectById(id);
        return po == null ? null : toEntity(po);
    }

    @Override
    public List<WorkflowDraftEntity> findAll() {
        return workflowDraftDao.selectAll().stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<WorkflowDraftEntity> findByStatus(WorkflowDraftStatusEnum status) {
        return workflowDraftDao.selectByStatus(status).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public WorkflowDraftEntity findLatestDraftByDedupHash(String dedupHash) {
        WorkflowDraftPO po = workflowDraftDao.selectLatestByDedupHash(dedupHash);
        return po == null ? null : toEntity(po);
    }

    private WorkflowDraftEntity toEntity(WorkflowDraftPO po) {
        WorkflowDraftEntity entity = new WorkflowDraftEntity();
        entity.setId(po.getId());
        entity.setDraftKey(po.getDraftKey());
        entity.setTenantId(po.getTenantId());
        entity.setCategory(po.getCategory());
        entity.setName(po.getName());
        entity.setRouteDescription(po.getRouteDescription());
        entity.setGraphDefinition(jsonCodec.readMap(po.getGraphDefinition()));
        entity.setInputSchema(jsonCodec.readMap(po.getInputSchema()));
        entity.setDefaultConfig(jsonCodec.readMap(po.getDefaultConfig()));
        entity.setToolPolicy(jsonCodec.readMap(po.getToolPolicy()));
        entity.setInputSchemaVersion(po.getInputSchemaVersion());
        entity.setConstraints(jsonCodec.readMap(po.getConstraints()));
        entity.setNodeSignature(po.getNodeSignature());
        entity.setDedupHash(po.getDedupHash());
        entity.setSourceType(po.getSourceType());
        entity.setSourceDefinitionId(po.getSourceDefinitionId());
        entity.setStatus(po.getStatus());
        entity.setCreatedBy(po.getCreatedBy());
        entity.setApprovedBy(po.getApprovedBy());
        entity.setApprovedAt(po.getApprovedAt());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    private WorkflowDraftPO toPO(WorkflowDraftEntity entity) {
        WorkflowDraftPO po = WorkflowDraftPO.builder()
                .id(entity.getId())
                .draftKey(entity.getDraftKey())
                .tenantId(entity.getTenantId())
                .category(entity.getCategory())
                .name(entity.getName())
                .routeDescription(entity.getRouteDescription())
                .inputSchemaVersion(entity.getInputSchemaVersion())
                .nodeSignature(entity.getNodeSignature())
                .dedupHash(entity.getDedupHash())
                .sourceType(entity.getSourceType())
                .sourceDefinitionId(entity.getSourceDefinitionId())
                .status(entity.getStatus())
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

