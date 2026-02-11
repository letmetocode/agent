export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface SessionCreateRequest {
  userId: string;
  title?: string;
  metaInfo?: Record<string, unknown>;
}

export interface SessionCreateResponse {
  sessionId: number;
  userId: string;
  title?: string;
  active: boolean;
}

export interface ChatRequest {
  message: string;
  extraContext?: Record<string, unknown>;
}

export interface ChatResponse {
  sessionId: number;
  turnId: number;
  planId: number;
  planGoal: string;
  turnStatus: string;
  assistantMessage?: string;
}

export interface SessionDetailDTO {
  sessionId: number;
  userId: string;
  title?: string;
  active: boolean;
  metaInfo?: Record<string, unknown>;
  createdAt?: string;
}

export interface PlanSummaryDTO {
  planId: number;
  sessionId: number;
  planGoal: string;
  status: string;
  priority?: number;
  errorSummary?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PlanTaskStatsDTO {
  total: number;
  pending: number;
  ready: number;
  runningLike: number;
  completed: number;
  failed: number;
  skipped: number;
}

export interface TaskDetailDTO {
  taskId: number;
  planId: number;
  nodeId: string;
  name: string;
  taskType: string;
  status: string;
  dependencyNodeIds?: string[];
  inputContext?: Record<string, unknown>;
  configSnapshot?: Record<string, unknown>;
  outputResult?: string;
  maxRetries?: number;
  currentRetry?: number;
  claimOwner?: string;
  claimAt?: string;
  leaseUntil?: string;
  executionAttempt?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskExecutionDetailDTO {
  executionId: number;
  taskId: number;
  attemptNumber: number;
  promptSnapshot?: string;
  llmResponseRaw?: string;
  modelName?: string;
  tokenUsage?: Record<string, unknown>;
  executionTimeMs?: number;
  valid?: boolean;
  validationFeedback?: string;
  errorMessage?: string;
  errorType?: string;
  createdAt?: string;
}

export interface PlanDetailDTO {
  planId: number;
  sessionId: number;
  routeDecisionId?: number;
  workflowDefinitionId?: number;
  workflowDraftId?: number;
  planGoal: string;
  executionGraph?: Record<string, unknown>;
  definitionSnapshot?: Record<string, unknown>;
  globalContext?: Record<string, unknown>;
  status: string;
  priority?: number;
  errorSummary?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SessionTurnDTO {
  turnId: number;
  sessionId: number;
  planId?: number;
  status: string;
  userMessage: string;
  assistantSummary?: string;
  finalResponseMessageId?: number;
  createdAt?: string;
  updatedAt?: string;
  completedAt?: string;
}

export interface SessionMessageDTO {
  messageId: number;
  sessionId: number;
  turnId: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL' | string;
  content: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface SessionOverviewDTO {
  session: SessionDetailDTO;
  plans: PlanSummaryDTO[];
  latestPlanId?: number;
  latestPlanTaskStats?: PlanTaskStatsDTO;
  latestPlanTasks?: TaskDetailDTO[];
}

export interface PlanStreamEvent {
  event: string;
  id?: string;
  data?: unknown;
}

export interface WorkflowDraftSummaryDTO {
  id: number;
  draftKey: string;
  tenantId: string;
  category: string;
  name: string;
  status: string;
  dedupHash?: string;
  sourceType?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowDraftDetailDTO extends WorkflowDraftSummaryDTO {
  routeDescription?: string;
  inputSchemaVersion?: string;
  nodeSignature?: string;
  sourceDefinitionId?: number;
  approvedBy?: string;
  approvedAt?: string;
  graphDefinition?: Record<string, unknown>;
  inputSchema?: Record<string, unknown>;
  defaultConfig?: Record<string, unknown>;
  toolPolicy?: Record<string, unknown>;
  constraints?: Record<string, unknown>;
}

export interface WorkflowPublishRequestDTO {
  operator?: string;
  definitionKey?: string;
}

export interface WorkflowDraftUpdateRequestDTO {
  draftKey?: string;
  tenantId?: string;
  category?: string;
  name?: string;
  routeDescription?: string;
  graphDefinition?: Record<string, unknown>;
  inputSchema?: Record<string, unknown>;
  defaultConfig?: Record<string, unknown>;
  toolPolicy?: Record<string, unknown>;
  constraints?: Record<string, unknown>;
  inputSchemaVersion?: string;
  status?: string;
}

export interface WorkflowPublishResultDTO {
  draftId: number;
  definitionId: number;
  definitionKey: string;
  definitionVersion: number;
}
