export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
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
  latestExecutionTimeMs?: number;
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

export interface PlanTaskEventDTO {
  id: number;
  planId: number;
  taskId?: number;
  eventType?: string;
  eventName?: string;
  eventData?: Record<string, unknown>;
  createdAt?: string;
}

export interface AgentToolDTO {
  id: number;
  name: string;
  type?: string;
  description?: string;
  isActive?: boolean;
  toolConfig?: Record<string, unknown>;
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface VectorStoreDTO {
  id: number;
  name: string;
  storeType?: string;
  collectionName?: string;
  dimension?: number;
  isActive?: boolean;
  connectionConfig?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

export interface PlanLogDTO {
  id: number;
  planId: number;
  taskId?: number;
  eventType?: string;
  eventName?: string;
  level?: 'INFO' | 'WARN' | 'ERROR' | string;
  traceId?: string;
  eventData?: Record<string, unknown>;
  createdAt?: string;
}

export interface DashboardOverviewDTO {
  taskStats?: {
    total: number;
    pending: number;
    ready: number;
    runningLike: number;
    completed: number;
    failed: number;
    skipped: number;
  };
  planStats?: Record<string, number>;
  sessionStats?: Record<string, number>;
  recentTasks?: TaskDetailDTO[];
  recentFailedTasks?: TaskDetailDTO[];
  recentPlans?: PlanSummaryDTO[];
  latencyStats?: {
    p50: number;
    p95: number;
    p99: number;
  };
  slowTaskCount?: number;
  slaBreachCount?: number;
}

export interface TaskExportDTO {
  fileName: string;
  contentType: string;
  content: string;
  generatedAt?: string;
}

export interface TaskShareLinkDTO {
  shareId: number;
  taskId: number;
  shareCode: string;
  scope?: string;
  status?: string;
  shareUrl: string;
  token: string;
  expiresAt: string;
  revoked?: boolean;
  revokedAt?: string;
  revokedReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskShareLinkItemDTO {
  shareId: number;
  taskId: number;
  shareCode: string;
  scope?: string;
  status?: string;
  shareUrl?: string;
  expiresAt?: string;
  revoked?: boolean;
  revokedAt?: string;
  revokedReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskShareRevokeResultDTO {
  taskId: number;
  shareId?: number;
  revoked?: boolean;
  revokedCount?: number;
  revokedAt?: string;
}

export interface SharedTaskReferenceDTO {
  title: string;
  type?: string;
  source?: string;
  score?: number;
}

export interface SharedTaskReadDTO {
  taskId: number;
  taskName?: string;
  status?: string;
  outputResult?: string;
  references?: SharedTaskReferenceDTO[];
  scope?: string;
  shareId?: number;
  shareCode?: string;
  expiresAt?: string;
  sharedAt?: string;
}

export interface KnowledgeBaseDetailDTO {
  id: number;
  name: string;
  storeType?: string;
  collectionName?: string;
  dimension?: number;
  isActive?: boolean;
  connectionConfig?: Record<string, unknown>;
  documentCount?: number;
  chunkCount?: number;
  updatedAt?: string;
}

export interface KnowledgeDocumentDTO {
  id: number;
  name: string;
  chunks?: number;
  status?: string;
  updatedAt?: string;
}

export interface RetrievalTestResultDTO {
  title: string;
  snippet: string;
  score: number;
  source?: string;
}

export interface RetrievalTestResponseDTO {
  query: string;
  total: number;
  results: RetrievalTestResultDTO[];
  testedAt?: string;
}
