package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.domain.planning.model.entity.WorkflowDraftEntity;
import com.getoffer.test.support.InMemoryWorkflowDefinitionRepository;
import com.getoffer.test.support.InMemoryWorkflowDraftRepository;
import com.getoffer.trigger.application.command.SopSpecCompileService;
import com.getoffer.trigger.application.command.WorkflowGovernanceApplicationService;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowGovernanceApplicationServiceTest {

    @Test
    public void shouldUpdateDraftWithSopSpecCompileMetadata() {
        InMemoryWorkflowDraftRepository draftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryWorkflowDefinitionRepository definitionRepository = new InMemoryWorkflowDefinitionRepository();
        SopSpecCompileService compileService = new SopSpecCompileService(new ObjectMapper());
        WorkflowGovernanceApplicationService service = new WorkflowGovernanceApplicationService(
                draftRepository,
                definitionRepository,
                compileService
        );

        WorkflowDraftEntity draft = baseDraft();
        draftRepository.save(draft);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sopSpec", buildSimpleSopSpec());
        request.put("name", "updated-name");

        Map<String, Object> result = service.updateDraft(draft.getId(), request);
        Assertions.assertEquals("updated-name", result.get("name"));
        Assertions.assertEquals("SUCCESS", result.get("compileStatus"));
        Assertions.assertNotNull(result.get("compileHash"));

        WorkflowDraftEntity updated = draftRepository.findById(draft.getId());
        Assertions.assertNotNull(updated);
        Assertions.assertEquals("SUCCESS", updated.getConstraints().get("compileStatus"));
        Assertions.assertTrue(updated.getConstraints().containsKey("compileHash"));
        Assertions.assertNotNull(updated.getNodeSignature());
        Assertions.assertEquals(2, ((Number) updated.getGraphDefinition().get("version")).intValue());
    }

    @Test
    public void shouldRejectPublishWhenSopCompileHashOutdated() {
        InMemoryWorkflowDraftRepository draftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryWorkflowDefinitionRepository definitionRepository = new InMemoryWorkflowDefinitionRepository();
        SopSpecCompileService compileService = new SopSpecCompileService(new ObjectMapper());
        WorkflowGovernanceApplicationService service = new WorkflowGovernanceApplicationService(
                draftRepository,
                definitionRepository,
                compileService
        );

        WorkflowDraftEntity draft = baseDraft();
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("sopSpec", buildSimpleSopSpec());
        constraints.put("compileHash", "stale-hash");
        constraints.put("compileStatus", "SUCCESS");
        draft.setConstraints(constraints);
        draftRepository.save(draft);

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishDraft(draft.getId(), Map.of("operator", "tester"))
        );
        Assertions.assertTrue(ex.getMessage().contains("编译结果已过期"));
    }

    @Test
    public void shouldReturnValidationIssueWhenSopSpecInvalid() {
        InMemoryWorkflowDraftRepository draftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryWorkflowDefinitionRepository definitionRepository = new InMemoryWorkflowDefinitionRepository();
        SopSpecCompileService compileService = new SopSpecCompileService(new ObjectMapper());
        WorkflowGovernanceApplicationService service = new WorkflowGovernanceApplicationService(
                draftRepository,
                definitionRepository,
                compileService
        );

        WorkflowDraftEntity draft = baseDraft();
        draftRepository.save(draft);

        Map<String, Object> invalidSpec = new LinkedHashMap<>();
        Map<String, Object> stepA = new LinkedHashMap<>();
        stepA.put("id", "a");
        stepA.put("dependsOn", List.of("b"));
        Map<String, Object> stepB = new LinkedHashMap<>();
        stepB.put("id", "b");
        stepB.put("dependsOn", List.of("a"));
        invalidSpec.put("steps", List.of(stepA, stepB));

        Map<String, Object> validateResult = service.validateDraftSopSpec(
                draft.getId(),
                Map.of("sopSpec", invalidSpec)
        );
        Assertions.assertEquals(Boolean.FALSE, validateResult.get("pass"));
        List<?> issues = (List<?>) validateResult.get("issues");
        Assertions.assertFalse(issues.isEmpty());
        Assertions.assertTrue(String.valueOf(issues.get(0)).contains("循环依赖"));
    }

    @Test
    public void shouldThrowWhenCompileWithoutSopSpec() {
        InMemoryWorkflowDraftRepository draftRepository = new InMemoryWorkflowDraftRepository();
        InMemoryWorkflowDefinitionRepository definitionRepository = new InMemoryWorkflowDefinitionRepository();
        SopSpecCompileService compileService = new SopSpecCompileService(new ObjectMapper());
        WorkflowGovernanceApplicationService service = new WorkflowGovernanceApplicationService(
                draftRepository,
                definitionRepository,
                compileService
        );

        WorkflowDraftEntity draft = baseDraft();
        draftRepository.save(draft);

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.compileDraftSopSpec(draft.getId(), Map.of())
        );
        Assertions.assertTrue(ex.getMessage().contains("sopSpec不能为空"));
    }

    private WorkflowDraftEntity baseDraft() {
        WorkflowDraftEntity draft = new WorkflowDraftEntity();
        draft.setDraftKey("draft-001");
        draft.setTenantId("DEFAULT");
        draft.setCategory("candidate");
        draft.setName("candidate-name");
        draft.setRouteDescription("route");
        draft.setGraphDefinition(buildBaseGraph());
        draft.setInputSchema(new LinkedHashMap<>());
        draft.setDefaultConfig(new LinkedHashMap<>());
        draft.setToolPolicy(new LinkedHashMap<>());
        draft.setInputSchemaVersion("v1");
        draft.setConstraints(new LinkedHashMap<>());
        draft.setNodeSignature("sig");
        draft.setStatus(WorkflowDraftStatusEnum.DRAFT);
        draft.setCreatedBy("SYSTEM");
        return draft;
    }

    private Map<String, Object> buildBaseGraph() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "worker");
        node.put("name", "worker");
        node.put("type", "WORKER");
        node.put("config", Map.of("agentKey", "assistant"));

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("version", 2);
        graph.put("nodes", List.of(node));
        graph.put("edges", List.of());
        graph.put("groups", List.of());
        return graph;
    }

    private Map<String, Object> buildSimpleSopSpec() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "worker");
        step.put("name", "worker");
        step.put("roleType", "WORKER");
        step.put("config", Map.of("agentKey", "assistant"));
        Map<String, Object> sopSpec = new LinkedHashMap<>();
        sopSpec.put("steps", List.of(step));
        return sopSpec;
    }
}
