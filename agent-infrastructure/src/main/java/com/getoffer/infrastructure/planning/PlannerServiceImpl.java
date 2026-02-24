package com.getoffer.infrastructure.planning;

import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.planning.adapter.gateway.IRootWorkflowDraftPlanner;
import com.getoffer.domain.planning.adapter.repository.IAgentPlanRepository;
import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDefinitionRepository;
import com.getoffer.domain.planning.adapter.repository.IWorkflowDraftRepository;
import com.getoffer.domain.planning.model.entity.AgentPlanEntity;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;
import com.getoffer.domain.planning.model.valobj.RoutingDecisionResult;
import com.getoffer.domain.planning.service.PlannerFallbackPolicyDomainService;
import com.getoffer.domain.planning.service.PlannerService;
import com.getoffer.domain.planning.service.WorkflowRoutingPolicyDomainService;
import com.getoffer.domain.task.adapter.repository.IAgentTaskRepository;
import com.getoffer.domain.task.model.entity.AgentTaskEntity;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.PlanStatusEnum;
import com.getoffer.types.enums.ResponseCode;
import com.getoffer.types.exception.AppException;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.DisposableBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Planner service implementation.
 *
 * @author getoffer
 * @since 2026-02-02
 */
@Slf4j
@Service
public class PlannerServiceImpl implements PlannerService, DisposableBean {

    private static final int DEFAULT_PRIORITY = 0;
    private static final long DEFAULT_ROOT_SOFT_TIMEOUT_MS = 15_000L;
    private static final String MISSING_REQUIRED_INPUT_PREFIX = "Missing required input:";
    private static final String ROUTE_REASON_PRODUCTION_INPUT_MISSING = "PRODUCTION_DEFINITION_INPUT_MISSING";
    private static final AtomicInteger ROOT_PLANNER_THREAD_SEQ = new AtomicInteger(1);

    private final IAgentPlanRepository agentPlanRepository;
    private final IAgentTaskRepository agentTaskRepository;
    private final JsonCodec jsonCodec;
    private final ExecutorService rootPlanningExecutor;
    private final WorkflowTaskMaterializationService workflowTaskMaterializationService;
    private final WorkflowPlanSnapshotService workflowPlanSnapshotService;
    private final WorkflowDraftLifecycleService workflowDraftLifecycleService;
    private final WorkflowInputPreparationService workflowInputPreparationService;
    private final WorkflowRoutingResolveService workflowRoutingResolveService;
    private final WorkflowRoutingDecisionService workflowRoutingDecisionService;

    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec) {
        this(workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                null,
                null,
                true,
                "root",
                3,
                300L,
                true,
                "assistant",
                DEFAULT_ROOT_SOFT_TIMEOUT_MS,
                PlannerFallbackPolicyDomainService.defaultInstance(),
                WorkflowRoutingPolicyDomainService.defaultInstance());
    }

    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec,
                              IRootWorkflowDraftPlanner rootWorkflowDraftPlanner,
                              IAgentRegistryRepository agentRegistryRepository,
                              boolean rootPlannerEnabled,
                              String rootAgentKey,
                              int rootMaxAttempts,
                              long rootRetryBackoffMs,
                              boolean fallbackSingleNodeEnabled,
                              String fallbackAgentKey) {
        this(workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                rootWorkflowDraftPlanner,
                agentRegistryRepository,
                rootPlannerEnabled,
                rootAgentKey,
                rootMaxAttempts,
                rootRetryBackoffMs,
                fallbackSingleNodeEnabled,
                fallbackAgentKey,
                DEFAULT_ROOT_SOFT_TIMEOUT_MS,
                PlannerFallbackPolicyDomainService.defaultInstance(),
                WorkflowRoutingPolicyDomainService.defaultInstance());
    }

    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec,
                              IRootWorkflowDraftPlanner rootWorkflowDraftPlanner,
                              IAgentRegistryRepository agentRegistryRepository,
                              boolean rootPlannerEnabled,
                              String rootAgentKey,
                              int rootMaxAttempts,
                              long rootRetryBackoffMs,
                              boolean fallbackSingleNodeEnabled,
                              String fallbackAgentKey,
                              long rootSoftTimeoutMs) {
        this(workflowDefinitionRepository,
                workflowDraftRepository,
                routingDecisionRepository,
                agentPlanRepository,
                agentTaskRepository,
                jsonCodec,
                rootWorkflowDraftPlanner,
                agentRegistryRepository,
                rootPlannerEnabled,
                rootAgentKey,
                rootMaxAttempts,
                rootRetryBackoffMs,
                fallbackSingleNodeEnabled,
                fallbackAgentKey,
                rootSoftTimeoutMs,
                PlannerFallbackPolicyDomainService.defaultInstance(),
                WorkflowRoutingPolicyDomainService.defaultInstance());
    }

    @Autowired
    public PlannerServiceImpl(IWorkflowDefinitionRepository workflowDefinitionRepository,
                              IWorkflowDraftRepository workflowDraftRepository,
                              IRoutingDecisionRepository routingDecisionRepository,
                              IAgentPlanRepository agentPlanRepository,
                              IAgentTaskRepository agentTaskRepository,
                              JsonCodec jsonCodec,
                              IRootWorkflowDraftPlanner rootWorkflowDraftPlanner,
                              IAgentRegistryRepository agentRegistryRepository,
                              @Value("${planner.root.enabled:true}") boolean rootPlannerEnabled,
                              @Value("${planner.root.agent-key:root}") String rootAgentKey,
                              @Value("${planner.root.retry.max-attempts:3}") int rootMaxAttempts,
                              @Value("${planner.root.retry.backoff-ms:300}") long rootRetryBackoffMs,
                              @Value("${planner.root.fallback.single-node.enabled:true}") boolean fallbackSingleNodeEnabled,
                              @Value("${planner.root.fallback.agent-key:assistant}") String fallbackAgentKey,
                              @Value("${planner.root.timeout.soft-ms:15000}") long rootSoftTimeoutMs,
                              PlannerFallbackPolicyDomainService plannerFallbackPolicyDomainService,
                              WorkflowRoutingPolicyDomainService workflowRoutingPolicyDomainService) {
        this.agentPlanRepository = agentPlanRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.jsonCodec = jsonCodec;
        int normalizedRootMaxAttempts = Math.max(rootMaxAttempts, 1);
        int plannerThreadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.rootPlanningExecutor = Executors.newFixedThreadPool(plannerThreadPoolSize, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("planner-root-" + ROOT_PLANNER_THREAD_SEQ.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        if (plannerFallbackPolicyDomainService == null) {
            throw new IllegalArgumentException("plannerFallbackPolicyDomainService must not be null");
        }
        WorkflowRoutingPolicyDomainService resolvedRoutingPolicy = workflowRoutingPolicyDomainService == null
                ? WorkflowRoutingPolicyDomainService.defaultInstance()
                : workflowRoutingPolicyDomainService;
        this.workflowTaskMaterializationService = new WorkflowTaskMaterializationService(jsonCodec);
        this.workflowPlanSnapshotService = new WorkflowPlanSnapshotService(jsonCodec);
        this.workflowDraftLifecycleService = new WorkflowDraftLifecycleService(
                workflowDraftRepository,
                rootWorkflowDraftPlanner,
                agentRegistryRepository,
                rootPlannerEnabled,
                rootAgentKey,
                normalizedRootMaxAttempts,
                rootRetryBackoffMs,
                fallbackSingleNodeEnabled,
                fallbackAgentKey,
                rootSoftTimeoutMs,
                plannerFallbackPolicyDomainService,
                jsonCodec,
                rootPlanningExecutor
        );
        this.workflowInputPreparationService = new WorkflowInputPreparationService(jsonCodec);
        this.workflowRoutingResolveService = new WorkflowRoutingResolveService(
                workflowDefinitionRepository,
                this.workflowDraftLifecycleService,
                resolvedRoutingPolicy,
                plannerFallbackPolicyDomainService,
                jsonCodec,
                normalizedRootMaxAttempts
        );
        this.workflowRoutingDecisionService = new WorkflowRoutingDecisionService(
                routingDecisionRepository,
                Metrics.globalRegistry,
                plannerFallbackPolicyDomainService
        );
    }

    @Override
    public RoutingDecisionResult route(String userQuery) {
        return workflowRoutingResolveService.route(userQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanEntity createPlan(Long sessionId, String userQuery) {
        return createPlan(sessionId, userQuery, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanEntity createPlan(Long sessionId, String userQuery, Map<String, Object> extraContext) {
        if (sessionId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SessionId is required");
        }
        if (StringUtils.isBlank(userQuery)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "User query is required");
        }

        WorkflowRoutingResolveService.RoutedWorkflow routedWorkflow =
                workflowRoutingResolveService.resolve(sessionId, userQuery, extraContext);
        routedWorkflow = fallbackToCandidateWhenProductionInputMissing(
                sessionId,
                userQuery,
                extraContext,
                routedWorkflow
        );
        Map<String, Object> executionGraph = workflowDraftLifecycleService.normalizeAndValidateGraphDefinition(
                deepCopyMap(routedWorkflow.graphDefinition()),
                routedWorkflow.definition() != null ? "生产Definition" : "候选Draft",
                routedWorkflow.definition() == null
        );
        if (executionGraph.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Workflow graph definition is empty");
        }

        Map<String, Object> userInput = workflowInputPreparationService.parseUserInput(userQuery);
        workflowInputPreparationService.enrichRequiredInputFromQuery(routedWorkflow.inputSchema(), userInput, userQuery);

        Map<String, Object> globalContext = buildGlobalContext(sessionId, userQuery, routedWorkflow, userInput);
        if (extraContext != null && !extraContext.isEmpty()) {
            globalContext.putAll(extraContext);
        }
        workflowInputPreparationService.enrichRequiredInputFromContext(routedWorkflow.inputSchema(), userInput, globalContext);
        workflowInputPreparationService.validateInput(routedWorkflow.inputSchema(), userInput);

        RoutingDecisionEntity savedDecision = workflowRoutingDecisionService.saveAndRecord(sessionId, extraContext, routedWorkflow);
        log.info("ROUTING_DECIDED sessionId={}, turnId={}, routingDecisionId={}, decisionType={}, strategy={}, reason={}, score={}, definitionId={}, draftId={}",
                sessionId,
                savedDecision == null ? null : savedDecision.getTurnId(),
                savedDecision == null ? null : savedDecision.getId(),
                routedWorkflow.decisionType() == null ? null : routedWorkflow.decisionType().name(),
                routedWorkflow.strategy(),
                routedWorkflow.reason(),
                routedWorkflow.score(),
                routedWorkflow.definition() == null ? null : routedWorkflow.definition().getId(),
                routedWorkflow.draft() == null ? null : routedWorkflow.draft().getId());

        AgentPlanEntity plan = new AgentPlanEntity();
        plan.setSessionId(sessionId);
        plan.setRouteDecisionId(savedDecision.getId());
        plan.setWorkflowDefinitionId(routedWorkflow.definition() == null ? null : routedWorkflow.definition().getId());
        plan.setWorkflowDraftId(routedWorkflow.draft() == null ? null : routedWorkflow.draft().getId());
        plan.setPlanGoal(userQuery);
        plan.setExecutionGraph(executionGraph);
        plan.setDefinitionSnapshot(workflowPlanSnapshotService.buildDefinitionSnapshot(
                routedWorkflow.decisionType(),
                routedWorkflow.reason(),
                routedWorkflow.strategy(),
                routedWorkflow.score(),
                routedWorkflow.definition(),
                routedWorkflow.draft(),
                routedWorkflow.sourceType(),
                routedWorkflow.fallbackFlag(),
                routedWorkflow.fallbackReason(),
                routedWorkflow.plannerAttempts(),
                routedWorkflow.toolPolicy(),
                routedWorkflow.graphDefinition()
        ));
        plan.setGlobalContext(globalContext);
        plan.setStatus(PlanStatusEnum.PLANNING);
        plan.setPriority(resolvePriority(routedWorkflow.defaultConfig()));
        plan.setVersion(0);

        AgentPlanEntity savedPlan = agentPlanRepository.save(plan);

        List<AgentTaskEntity> tasks = workflowTaskMaterializationService.materializeTasks(
                savedPlan,
                routedWorkflow.defaultConfig(),
                routedWorkflow.toolPolicy(),
                executionGraph,
                globalContext
        );
        if (tasks.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "No executable nodes found in workflow graph");
        }
        agentTaskRepository.batchSave(tasks);

        savedPlan.ready();
        return agentPlanRepository.update(savedPlan);
    }

    private WorkflowRoutingResolveService.RoutedWorkflow fallbackToCandidateWhenProductionInputMissing(
            Long sessionId,
            String userQuery,
            Map<String, Object> extraContext,
            WorkflowRoutingResolveService.RoutedWorkflow routedWorkflow) {
        if (routedWorkflow == null || routedWorkflow.definition() == null) {
            return routedWorkflow;
        }

        Map<String, Object> probeUserInput = workflowInputPreparationService.parseUserInput(userQuery);
        workflowInputPreparationService.enrichRequiredInputFromQuery(routedWorkflow.inputSchema(), probeUserInput, userQuery);
        Map<String, Object> probeGlobalContext = buildGlobalContext(sessionId, userQuery, routedWorkflow, probeUserInput);
        if (extraContext != null && !extraContext.isEmpty()) {
            probeGlobalContext.putAll(extraContext);
        }
        workflowInputPreparationService.enrichRequiredInputFromContext(
                routedWorkflow.inputSchema(),
                probeUserInput,
                probeGlobalContext
        );
        try {
            workflowInputPreparationService.validateInput(routedWorkflow.inputSchema(), probeUserInput);
            return routedWorkflow;
        } catch (AppException ex) {
            if (!isMissingRequiredInput(ex)) {
                throw ex;
            }
            String missingInput = extractMissingRequiredInput(ex);
            log.warn("Production definition requires missing input. fallback to root candidate. sessionId={}, definitionId={}, definitionKey={}, missingInput={}",
                    sessionId,
                    routedWorkflow.definition().getId(),
                    routedWorkflow.definition().getDefinitionKey(),
                    missingInput);
            Map<String, Object> fallbackContext = extraContext == null
                    ? new HashMap<>()
                    : new HashMap<>(extraContext);
            fallbackContext.put("productionFallbackReason", ROUTE_REASON_PRODUCTION_INPUT_MISSING);
            if (StringUtils.isNotBlank(missingInput)) {
                fallbackContext.put("productionMissingRequiredInput", missingInput);
            }
            fallbackContext.put("productionDefinitionId", routedWorkflow.definition().getId());
            fallbackContext.put("productionDefinitionKey", routedWorkflow.definition().getDefinitionKey());

            WorkflowRoutingResolveService.RoutedWorkflow candidateRoutedWorkflow =
                    workflowRoutingResolveService.resolveCandidate(sessionId, userQuery, fallbackContext);
            return new WorkflowRoutingResolveService.RoutedWorkflow(
                    candidateRoutedWorkflow.decisionType(),
                    ROUTE_REASON_PRODUCTION_INPUT_MISSING,
                    candidateRoutedWorkflow.strategy(),
                    candidateRoutedWorkflow.score(),
                    candidateRoutedWorkflow.definition(),
                    candidateRoutedWorkflow.draft(),
                    candidateRoutedWorkflow.sourceType(),
                    candidateRoutedWorkflow.fallbackFlag(),
                    candidateRoutedWorkflow.fallbackReason(),
                    candidateRoutedWorkflow.plannerAttempts(),
                    candidateRoutedWorkflow.graphDefinition(),
                    candidateRoutedWorkflow.inputSchema(),
                    candidateRoutedWorkflow.defaultConfig(),
                    candidateRoutedWorkflow.toolPolicy()
            );
        }
    }

    private boolean isMissingRequiredInput(AppException ex) {
        if (ex == null) {
            return false;
        }
        if (!StringUtils.equals(ResponseCode.ILLEGAL_PARAMETER.getCode(), ex.getCode())) {
            return false;
        }
        String message = StringUtils.defaultIfBlank(ex.getInfo(), ex.getMessage());
        return StringUtils.startsWithIgnoreCase(StringUtils.defaultString(message), MISSING_REQUIRED_INPUT_PREFIX);
    }

    private String extractMissingRequiredInput(AppException ex) {
        if (ex == null) {
            return null;
        }
        String message = StringUtils.defaultIfBlank(ex.getInfo(), ex.getMessage());
        if (!StringUtils.startsWithIgnoreCase(StringUtils.defaultString(message), MISSING_REQUIRED_INPUT_PREFIX)) {
            return null;
        }
        return StringUtils.trim(message.substring(MISSING_REQUIRED_INPUT_PREFIX.length()));
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        String json = jsonCodec.writeValue(source);
        Map<String, Object> copy = jsonCodec.readMap(json);
        return copy == null ? new HashMap<>() : copy;
    }

    private Map<String, Object> buildGlobalContext(Long sessionId,
                                                   String userQuery,
                                                   WorkflowRoutingResolveService.RoutedWorkflow routedWorkflow,
                                                   Map<String, Object> userInput) {
        Map<String, Object> context = new HashMap<>();
        if (userInput != null) {
            context.putAll(userInput);
        }
        context.put("userQuery", userQuery);
        context.put("sessionId", sessionId);
        if (routedWorkflow != null) {
            context.put("routeType", routedWorkflow.decisionType() == null ? null : routedWorkflow.decisionType().name());
            context.put("routeReason", routedWorkflow.reason());
            if (routedWorkflow.toolPolicy() != null && !routedWorkflow.toolPolicy().isEmpty()) {
                context.put("toolPolicy", deepCopyMap(routedWorkflow.toolPolicy()));
            }
            if (routedWorkflow.definition() != null) {
                context.put("workflowDefinitionId", routedWorkflow.definition().getId());
                context.put("definitionKey", routedWorkflow.definition().getDefinitionKey());
                context.put("definitionVersion", routedWorkflow.definition().getVersion());
            }
            if (routedWorkflow.draft() != null) {
                context.put("workflowDraftId", routedWorkflow.draft().getId());
                context.put("draftKey", routedWorkflow.draft().getDraftKey());
            }
        }
        return context;
    }

    private Integer resolvePriority(Map<String, Object> defaultConfig) {
        Integer priority = getInteger(defaultConfig, "priority");
        return priority != null ? priority : DEFAULT_PRIORITY;
    }

    private Integer getInteger(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Override
    public void destroy() {
        rootPlanningExecutor.shutdownNow();
    }
}
