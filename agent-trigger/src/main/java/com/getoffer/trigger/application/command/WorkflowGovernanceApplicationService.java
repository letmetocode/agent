package com.getoffer.trigger.application.command;

import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.domain.planning.service.WorkflowGraphPolicyKernel;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Workflow 治理应用服务：承载治理核心业务逻辑，Controller 仅处理协议层。
 */
@Service
@Slf4j
public class WorkflowGovernanceApplicationService {

    private static final String DEFAULT_OPERATOR = "SYSTEM";
    private static final String SOP_CONSTRAINT_KEY_SPEC = "sopSpec";
    private static final String SOP_CONSTRAINT_KEY_COMPILE_HASH = "compileHash";
    private static final String SOP_CONSTRAINT_KEY_COMPILE_STATUS = "compileStatus";
    private static final String SOP_CONSTRAINT_KEY_COMPILE_WARNINGS = "compileWarnings";
    private static final String SOP_COMPILE_STATUS_SUCCESS = "SUCCESS";

    private final IWorkflowDraftRepository workflowDraftRepository;
    private final IWorkflowDefinitionRepository workflowDefinitionRepository;
    private final SopSpecCompileService sopSpecCompileService;

    public WorkflowGovernanceApplicationService(IWorkflowDraftRepository workflowDraftRepository,
                                                IWorkflowDefinitionRepository workflowDefinitionRepository,
                                                SopSpecCompileService sopSpecCompileService) {
        this.workflowDraftRepository = workflowDraftRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.sopSpecCompileService = sopSpecCompileService;
    }

    public List<Map<String, Object>> listDrafts(String statusText) {
        WorkflowDraftStatusEnum status = parseDraftStatus(statusText);
        List<WorkflowDraftEntity> drafts = status == null
                ? workflowDraftRepository.findAll()
                : workflowDraftRepository.findByStatus(status);
        return drafts.stream().map(this::toDraftSummary).collect(Collectors.toList());
    }

    public Map<String, Object> getDraftDetail(Long id) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            throw new IllegalArgumentException("Workflow Draft不存在");
        }
        return toDraftDetail(draft);
    }

    public Map<String, Object> compileDraftSopSpec(Long id, Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            throw new IllegalArgumentException("Workflow Draft不存在");
        }
        Map<String, Object> sopSpec = resolveSopSpec(request, draft);
        SopSpecCompileService.CompileResult compileResult = sopSpecCompileService.compile(sopSpec);
        Map<String, Object> data = new HashMap<>();
        data.put("draftId", id);
        data.put("sopSpec", copyMap(sopSpec));
        data.put("sopRuntimeGraph", copyMap(compileResult.sopRuntimeGraph()));
        data.put("compileHash", compileResult.compileHash());
        data.put("nodeSignature", compileResult.nodeSignature());
        data.put("warnings", compileResult.warnings());
        return data;
    }

    public Map<String, Object> validateDraftSopSpec(Long id, Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            throw new IllegalArgumentException("Workflow Draft不存在");
        }
        try {
            Map<String, Object> sopSpec = resolveSopSpec(request, draft);
            SopSpecCompileService.ValidationResult validationResult = sopSpecCompileService.validate(sopSpec);
            Map<String, Object> data = new HashMap<>();
            data.put("draftId", id);
            data.put("pass", validationResult.pass());
            data.put("issues", validationResult.issues());
            data.put("warnings", validationResult.warnings());
            return data;
        } catch (IllegalArgumentException ex) {
            Map<String, Object> data = new HashMap<>();
            data.put("draftId", id);
            data.put("pass", false);
            data.put("issues", List.of(ex.getMessage()));
            data.put("warnings", List.of());
            return data;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateDraft(Long id, Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            throw new IllegalArgumentException("Workflow Draft不存在");
        }
        if (draft.getStatus() == WorkflowDraftStatusEnum.ARCHIVED || draft.getStatus() == WorkflowDraftStatusEnum.PUBLISHED) {
            throw new IllegalArgumentException("当前状态不允许编辑");
        }
        if (request == null || request.isEmpty()) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        applyDraftUpdates(draft, request);
        draft.validate();
        workflowDraftRepository.update(draft);
        WorkflowDraftEntity updated = workflowDraftRepository.findById(id);
        return toDraftDetail(updated == null ? draft : updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishDraft(Long id, Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            throw new IllegalArgumentException("Workflow Draft不存在");
        }
        if (draft.getStatus() == WorkflowDraftStatusEnum.ARCHIVED || draft.getStatus() == WorkflowDraftStatusEnum.PUBLISHED) {
            throw new IllegalArgumentException("当前状态不允许发布");
        }

        String operator = parseOperator(request);
        String definitionKey = resolveDefinitionKey(draft, request);
        WorkflowDefinitionEntity latest = workflowDefinitionRepository
                .findLatestVersionByDefinitionKey(StringUtils.defaultIfBlank(draft.getTenantId(), "DEFAULT"), definitionKey);
        int nextVersion = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;

        validateSopCompileStateForPublish(draft);
        validateGraphDslV2(draft.getGraphDefinition(), "graphDefinition");
        WorkflowDefinitionEntity definition = cloneAsDefinition(draft, definitionKey, nextVersion, operator);
        WorkflowDefinitionEntity saved = workflowDefinitionRepository.save(definition);

        draft.setStatus(WorkflowDraftStatusEnum.PUBLISHED);
        draft.setApprovedBy(operator);
        draft.setApprovedAt(LocalDateTime.now());
        workflowDraftRepository.update(draft);

        Map<String, Object> result = new HashMap<>();
        result.put("draftId", draft.getId());
        result.put("definitionId", saved.getId());
        result.put("definitionKey", saved.getDefinitionKey());
        result.put("definitionVersion", saved.getVersion());
        return result;
    }

    public List<Map<String, Object>> listDefinitions(String statusText) {
        WorkflowDefinitionStatusEnum status = parseDefinitionStatus(statusText);
        List<WorkflowDefinitionEntity> definitions = status == null
                ? workflowDefinitionRepository.findAll()
                : workflowDefinitionRepository.findByStatus(status);
        return definitions.stream()
                .map(this::toDefinitionSummary)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDefinition(Long id) {
        WorkflowDefinitionEntity definition = workflowDefinitionRepository.findById(id);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow Definition不存在");
        }
        return toDefinitionDetail(definition);
    }

    private WorkflowDefinitionEntity cloneAsDefinition(WorkflowDraftEntity draft,
                                                       String definitionKey,
                                                       int version,
                                                       String operator) {
        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setDefinitionKey(definitionKey);
        definition.setTenantId(StringUtils.defaultIfBlank(draft.getTenantId(), "DEFAULT"));
        definition.setCategory(draft.getCategory());
        definition.setName(draft.getName());
        definition.setVersion(version);
        definition.setRouteDescription(draft.getRouteDescription());
        definition.setGraphDefinition(copyMap(draft.getGraphDefinition()));
        definition.setInputSchema(copyMap(draft.getInputSchema()));
        definition.setDefaultConfig(copyMap(draft.getDefaultConfig()));
        definition.setToolPolicy(copyMap(draft.getToolPolicy()));
        definition.setInputSchemaVersion(draft.getInputSchemaVersion());
        definition.setConstraints(copyMap(draft.getConstraints()));
        definition.setNodeSignature(draft.getNodeSignature());
        definition.setStatus(WorkflowDefinitionStatusEnum.ACTIVE);
        definition.setPublishedFromDraftId(draft.getId());
        definition.setIsActive(true);
        definition.setCreatedBy(operator);
        definition.setApprovedBy(operator);
        definition.setApprovedAt(LocalDateTime.now());
        return definition;
    }

    private void applyDraftUpdates(WorkflowDraftEntity draft, Map<String, Object> request) {
        boolean containsSopSpecUpdate = request.containsKey(SOP_CONSTRAINT_KEY_SPEC);

        if (request.containsKey("graphDefinition")
                && !containsSopSpecUpdate
                && hasSopSpec(draft.getConstraints())) {
            throw new IllegalArgumentException("当前Draft已启用sopSpec，请更新sopSpec后由系统编译runtime graph");
        }

        if (request.containsKey("draftKey")) {
            draft.setDraftKey(readRequiredText(request.get("draftKey"), "draftKey"));
        }
        if (request.containsKey("tenantId")) {
            draft.setTenantId(readRequiredText(request.get("tenantId"), "tenantId"));
        }
        if (request.containsKey("category")) {
            draft.setCategory(readRequiredText(request.get("category"), "category"));
        }
        if (request.containsKey("name")) {
            draft.setName(readRequiredText(request.get("name"), "name"));
        }
        if (request.containsKey("routeDescription")) {
            draft.setRouteDescription(readRequiredText(request.get("routeDescription"), "routeDescription"));
        }
        if (request.containsKey("inputSchemaVersion")) {
            draft.setInputSchemaVersion(readOptionalText(request.get("inputSchemaVersion")));
        }
        if (request.containsKey("graphDefinition")) {
            Map<String, Object> graphDefinition = parseObjectMap(request.get("graphDefinition"), "graphDefinition");
            validateGraphDslV2(graphDefinition, "graphDefinition");
            draft.setGraphDefinition(graphDefinition);
        }
        if (request.containsKey("inputSchema")) {
            draft.setInputSchema(parseObjectMap(request.get("inputSchema"), "inputSchema"));
        }
        if (request.containsKey("defaultConfig")) {
            draft.setDefaultConfig(parseObjectMap(request.get("defaultConfig"), "defaultConfig"));
        }
        if (request.containsKey("toolPolicy")) {
            draft.setToolPolicy(parseObjectMap(request.get("toolPolicy"), "toolPolicy"));
        }
        if (request.containsKey("constraints")) {
            draft.setConstraints(parseObjectMap(request.get("constraints"), "constraints"));
        }
        if (containsSopSpecUpdate) {
            Map<String, Object> sopSpec = parseObjectMap(request.get(SOP_CONSTRAINT_KEY_SPEC), SOP_CONSTRAINT_KEY_SPEC);
            if (sopSpec.isEmpty()) {
                throw new IllegalArgumentException("sopSpec不能为空");
            }
            SopSpecCompileService.CompileResult compileResult = sopSpecCompileService.compile(sopSpec);
            draft.setGraphDefinition(copyMap(compileResult.sopRuntimeGraph()));
            draft.setNodeSignature(compileResult.nodeSignature());

            Map<String, Object> constraints = mergeConstraints(draft.getConstraints(), null);
            constraints.put(SOP_CONSTRAINT_KEY_SPEC, sopSpec);
            constraints.put(SOP_CONSTRAINT_KEY_COMPILE_HASH, compileResult.compileHash());
            constraints.put(SOP_CONSTRAINT_KEY_COMPILE_STATUS, SOP_COMPILE_STATUS_SUCCESS);
            constraints.put(SOP_CONSTRAINT_KEY_COMPILE_WARNINGS, compileResult.warnings());
            draft.setConstraints(constraints);
        }
        if (request.containsKey("status")) {
            WorkflowDraftStatusEnum status = WorkflowDraftStatusEnum.fromText(String.valueOf(request.get("status")));
            if (status != null) {
                draft.setStatus(status);
            }
        }
    }

    private String resolveDefinitionKey(WorkflowDraftEntity draft, Map<String, Object> request) {
        if (request != null && request.get("definitionKey") != null) {
            String key = String.valueOf(request.get("definitionKey")).trim();
            if (!key.isEmpty()) {
                return key;
            }
        }
        String draftKey = StringUtils.defaultIfBlank(draft.getDraftKey(), "");
        if (draftKey.startsWith("draft-")) {
            return "def-" + draftKey.substring("draft-".length());
        }
        return "def-" + UUID.randomUUID();
    }

    private String readRequiredText(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return text;
    }

    private String readOptionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> parseObjectMap(Object value, String fieldName) {
        if (value == null) {
            return new HashMap<>();
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(fieldName + "必须是JSON对象");
        }
        Map<String, Object> parsed = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            parsed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return parsed;
    }

    private Map<String, Object> resolveSopSpec(Map<String, Object> request,
                                               WorkflowDraftEntity draft) {
        if (request != null && request.containsKey(SOP_CONSTRAINT_KEY_SPEC)) {
            Map<String, Object> sopSpec = parseObjectMap(request.get(SOP_CONSTRAINT_KEY_SPEC), SOP_CONSTRAINT_KEY_SPEC);
            if (sopSpec.isEmpty()) {
                throw new IllegalArgumentException("sopSpec不能为空");
            }
            return sopSpec;
        }
        Map<String, Object> sopSpec = readConstraintMap(draft.getConstraints(), SOP_CONSTRAINT_KEY_SPEC);
        if (sopSpec == null || sopSpec.isEmpty()) {
            throw new IllegalArgumentException("sopSpec不能为空");
        }
        return sopSpec;
    }

    private void validateSopCompileStateForPublish(WorkflowDraftEntity draft) {
        Map<String, Object> sopSpec = readConstraintMap(draft.getConstraints(), SOP_CONSTRAINT_KEY_SPEC);
        if (sopSpec == null || sopSpec.isEmpty()) {
            return;
        }
        String compileHash = readConstraintText(draft.getConstraints(), SOP_CONSTRAINT_KEY_COMPILE_HASH);
        if (StringUtils.isBlank(compileHash)) {
            throw new IllegalArgumentException("存在sopSpec但缺少compileHash，请先编译并保存");
        }
        String runtimeGraphHash = sopSpecCompileService.hashRuntimeGraph(draft.getGraphDefinition());
        if (!StringUtils.equals(compileHash, runtimeGraphHash)) {
            throw new IllegalArgumentException("sopSpec编译结果已过期，请重新编译并保存后发布");
        }
    }

    private boolean hasSopSpec(Map<String, Object> constraints) {
        Map<String, Object> sopSpec = readConstraintMap(constraints, SOP_CONSTRAINT_KEY_SPEC);
        return sopSpec != null && !sopSpec.isEmpty();
    }

    private Map<String, Object> mergeConstraints(Map<String, Object> base,
                                                 Map<String, Object> override) {
        Map<String, Object> merged = copyMap(base);
        if (override != null && !override.isEmpty()) {
            merged.putAll(override);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConstraintMap(Map<String, Object> constraints,
                                                  String key) {
        if (constraints == null || constraints.isEmpty() || StringUtils.isBlank(key)) {
            return null;
        }
        Object value = constraints.get(key);
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String readConstraintText(Map<String, Object> constraints,
                                      String key) {
        if (constraints == null || constraints.isEmpty() || StringUtils.isBlank(key)) {
            return null;
        }
        Object value = constraints.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void validateGraphDslV2(Map<String, Object> graphDefinition, String fieldName) {
        WorkflowGraphPolicyKernel.validateGraphDslV2(graphDefinition, fieldName);
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new HashMap<>() : new HashMap<>(source);
    }

    private String parseOperator(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return DEFAULT_OPERATOR;
        }
        Object value = request.get("operator");
        if (value == null) {
            return DEFAULT_OPERATOR;
        }
        String operator = String.valueOf(value).trim();
        return operator.isEmpty() ? DEFAULT_OPERATOR : operator;
    }

    private WorkflowDraftStatusEnum parseDraftStatus(String statusText) {
        if (StringUtils.isBlank(statusText)) {
            return null;
        }
        try {
            return WorkflowDraftStatusEnum.fromText(statusText);
        } catch (Exception ex) {
            log.warn("Ignore unknown draft status filter: {}", statusText);
            return null;
        }
    }

    private WorkflowDefinitionStatusEnum parseDefinitionStatus(String statusText) {
        if (StringUtils.isBlank(statusText)) {
            return null;
        }
        try {
            return WorkflowDefinitionStatusEnum.fromText(statusText);
        } catch (Exception ex) {
            log.warn("Ignore unknown definition status filter: {}", statusText);
            return null;
        }
    }

    private Map<String, Object> toDraftSummary(WorkflowDraftEntity item) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", item.getId());
        summary.put("draftKey", item.getDraftKey());
        summary.put("tenantId", item.getTenantId());
        summary.put("category", item.getCategory());
        summary.put("name", item.getName());
        summary.put("status", item.getStatus());
        summary.put("dedupHash", item.getDedupHash());
        summary.put("sourceType", item.getSourceType());
        summary.put("createdBy", item.getCreatedBy());
        summary.put("createdAt", item.getCreatedAt());
        summary.put("updatedAt", item.getUpdatedAt());
        summary.put("compileStatus", readConstraintText(item.getConstraints(), SOP_CONSTRAINT_KEY_COMPILE_STATUS));
        return summary;
    }

    private Map<String, Object> toDraftDetail(WorkflowDraftEntity item) {
        Map<String, Object> detail = toDraftSummary(item);
        detail.put("routeDescription", item.getRouteDescription());
        detail.put("inputSchemaVersion", item.getInputSchemaVersion());
        detail.put("nodeSignature", item.getNodeSignature());
        detail.put("sourceDefinitionId", item.getSourceDefinitionId());
        detail.put("approvedBy", item.getApprovedBy());
        detail.put("approvedAt", item.getApprovedAt());
        detail.put("graphDefinition", copyMap(item.getGraphDefinition()));
        detail.put("sopRuntimeGraph", copyMap(item.getGraphDefinition()));
        detail.put("inputSchema", copyMap(item.getInputSchema()));
        detail.put("defaultConfig", copyMap(item.getDefaultConfig()));
        detail.put("toolPolicy", copyMap(item.getToolPolicy()));
        detail.put("constraints", copyMap(item.getConstraints()));
        detail.put("sopSpec", readConstraintMap(item.getConstraints(), SOP_CONSTRAINT_KEY_SPEC));
        detail.put("compileStatus", readConstraintText(item.getConstraints(), SOP_CONSTRAINT_KEY_COMPILE_STATUS));
        detail.put("compileHash", readConstraintText(item.getConstraints(), SOP_CONSTRAINT_KEY_COMPILE_HASH));
        detail.put("compileWarnings", item.getConstraints() == null ? List.of() :
                item.getConstraints().getOrDefault(SOP_CONSTRAINT_KEY_COMPILE_WARNINGS, List.of()));
        return detail;
    }

    private Map<String, Object> toDefinitionSummary(WorkflowDefinitionEntity item) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", item.getId());
        summary.put("definitionKey", item.getDefinitionKey());
        summary.put("tenantId", item.getTenantId());
        summary.put("category", item.getCategory());
        summary.put("name", item.getName());
        summary.put("version", item.getVersion());
        summary.put("status", item.getStatus());
        summary.put("publishedFromDraftId", item.getPublishedFromDraftId());
        summary.put("createdBy", item.getCreatedBy());
        summary.put("createdAt", item.getCreatedAt());
        summary.put("updatedAt", item.getUpdatedAt());
        return summary;
    }

    private Map<String, Object> toDefinitionDetail(WorkflowDefinitionEntity item) {
        Map<String, Object> detail = toDefinitionSummary(item);
        detail.put("routeDescription", item.getRouteDescription());
        detail.put("inputSchemaVersion", item.getInputSchemaVersion());
        detail.put("nodeSignature", item.getNodeSignature());
        detail.put("approvedBy", item.getApprovedBy());
        detail.put("approvedAt", item.getApprovedAt());
        detail.put("graphDefinition", copyMap(item.getGraphDefinition()));
        detail.put("inputSchema", copyMap(item.getInputSchema()));
        detail.put("defaultConfig", copyMap(item.getDefaultConfig()));
        detail.put("toolPolicy", copyMap(item.getToolPolicy()));
        detail.put("constraints", copyMap(item.getConstraints()));
        return detail;
    }
}
