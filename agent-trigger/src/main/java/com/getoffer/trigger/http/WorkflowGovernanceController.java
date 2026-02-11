package com.getoffer.trigger.http;

import com.getoffer.api.response.Response;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.WorkflowDefinitionEntity;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Workflow 治理 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
public class WorkflowGovernanceController {

    private static final String DEFAULT_OPERATOR = "SYSTEM";

    private final IWorkflowDraftRepository workflowDraftRepository;
    private final IWorkflowDefinitionRepository workflowDefinitionRepository;

    public WorkflowGovernanceController(IWorkflowDraftRepository workflowDraftRepository,
                                        IWorkflowDefinitionRepository workflowDefinitionRepository) {
        this.workflowDraftRepository = workflowDraftRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
    }

    @GetMapping("/drafts")
    public Response<List<Map<String, Object>>> listDrafts(
            @RequestParam(value = "status", required = false) String statusText) {
        WorkflowDraftStatusEnum status = parseDraftStatus(statusText);
        List<WorkflowDraftEntity> drafts = status == null
                ? workflowDraftRepository.findAll()
                : workflowDraftRepository.findByStatus(status);
        List<Map<String, Object>> data = drafts.stream().map(this::toDraftSummary).collect(Collectors.toList());
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    @GetMapping("/drafts/{id}")
    public Response<Map<String, Object>> getDraftDetail(@PathVariable("id") Long id) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            return failure("Workflow Draft不存在");
        }
        return success(toDraftDetail(draft));
    }

    @PutMapping("/drafts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Response<Map<String, Object>> updateDraft(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            return failure("Workflow Draft不存在");
        }
        if (draft.getStatus() == WorkflowDraftStatusEnum.ARCHIVED || draft.getStatus() == WorkflowDraftStatusEnum.PUBLISHED) {
            return failure("当前状态不允许编辑");
        }
        if (request == null || request.isEmpty()) {
            return failure("请求体不能为空");
        }
        try {
            applyDraftUpdates(draft, request);
            draft.validate();
            workflowDraftRepository.update(draft);
            WorkflowDraftEntity updated = workflowDraftRepository.findById(id);
            return success(toDraftDetail(updated == null ? draft : updated));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return failure(ex.getMessage());
        }
    }

    @PostMapping("/drafts/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Response<Map<String, Object>> publishDraft(@PathVariable("id") Long id,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        WorkflowDraftEntity draft = workflowDraftRepository.findById(id);
        if (draft == null) {
            return failure("Workflow Draft不存在");
        }
        if (draft.getStatus() == WorkflowDraftStatusEnum.ARCHIVED || draft.getStatus() == WorkflowDraftStatusEnum.PUBLISHED) {
            return failure("当前状态不允许发布");
        }

        String operator = parseOperator(request);
        String definitionKey = resolveDefinitionKey(draft, request);
        WorkflowDefinitionEntity latest = workflowDefinitionRepository
                .findLatestVersionByDefinitionKey(StringUtils.defaultIfBlank(draft.getTenantId(), "DEFAULT"), definitionKey);
        int nextVersion = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;

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
        return success(result);
    }

    @GetMapping("/definitions")
    public Response<List<Map<String, Object>>> listDefinitions(
            @RequestParam(value = "status", required = false) String statusText) {
        WorkflowDefinitionStatusEnum status = parseDefinitionStatus(statusText);
        List<WorkflowDefinitionEntity> definitions = status == null
                ? workflowDefinitionRepository.findAll()
                : workflowDefinitionRepository.findByStatus(status);
        List<Map<String, Object>> data = definitions.stream()
                .map(this::toDefinitionSummary)
                .collect(Collectors.toList());
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    @GetMapping("/definitions/{id}")
    public Response<Map<String, Object>> getDefinition(@PathVariable("id") Long id) {
        WorkflowDefinitionEntity definition = workflowDefinitionRepository.findById(id);
        if (definition == null) {
            return failure("Workflow Definition不存在");
        }
        return success(toDefinitionDetail(definition));
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
            draft.setGraphDefinition(parseObjectMap(request.get("graphDefinition"), "graphDefinition"));
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
        detail.put("inputSchema", copyMap(item.getInputSchema()));
        detail.put("defaultConfig", copyMap(item.getDefaultConfig()));
        detail.put("toolPolicy", copyMap(item.getToolPolicy()));
        detail.put("constraints", copyMap(item.getConstraints()));
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

    private Response<Map<String, Object>> failure(String info) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info)
                .build();
    }

    private Response<Map<String, Object>> success(Map<String, Object> data) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
