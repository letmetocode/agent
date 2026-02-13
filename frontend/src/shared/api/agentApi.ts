import { CHAT_HTTP_TIMEOUT_MS, http } from './http';
import type {
  ActiveAgentDTO,
  AgentCreateRequest,
  AgentCreateResponse,
  AgentToolDTO,
  ApiResponse,
  DashboardOverviewDTO,
  KnowledgeBaseDetailDTO,
  KnowledgeDocumentDTO,
  PageResult,
  PlanLogDTO,
  PlanTaskEventDTO,
  PlanDetailDTO,
  PlanSummaryDTO,
  RetrievalTestResponseDTO,
  SessionCreateRequest,
  SessionCreateResponse,
  SessionStartRequest,
  SessionStartResponse,
  SessionDetailDTO,
  SessionMessageDTO,
  SessionOverviewDTO,
  SessionTurnDTO,
  RoutingDecisionDTO,
  SharedTaskReadDTO,
  TaskDetailDTO,
  TaskExecutionDetailDTO,
  TaskExportDTO,
  TaskShareLinkDTO,
  TaskShareLinkItemDTO,
  TaskShareRevokeResultDTO,
  VectorStoreDTO,
  TurnCreateRequest,
  TurnCreateResponse,
  WorkflowDraftDetailDTO,
  WorkflowDraftSummaryDTO,
  WorkflowDraftUpdateRequestDTO,
  WorkflowPublishRequestDTO,
  WorkflowPublishResultDTO
} from '@/shared/types/api';

const unwrap = <T>(res: { data: ApiResponse<T> }): T => res.data.data;
const unwrapWithCodeCheck = <T>(res: { data: ApiResponse<T> }): T => {
  if (!res.data || res.data.code !== '0000') {
    throw new Error(res.data?.info || '请求失败');
  }
  return res.data.data;
};

export const agentApi = {
  createSession: async (payload: SessionCreateRequest) =>
    unwrap(await http.post<ApiResponse<SessionCreateResponse>>('/api/sessions', payload)),

  createSessionV2: async (payload: SessionStartRequest) =>
    unwrap(await http.post<ApiResponse<SessionStartResponse>>('/api/v2/sessions', payload)),

  getActiveAgentsV2: async () =>
    unwrap(await http.get<ApiResponse<ActiveAgentDTO[]>>('/api/v2/agents/active')),

  createAgentV2: async (payload: AgentCreateRequest) =>
    unwrap(await http.post<ApiResponse<AgentCreateResponse>>('/api/v2/agents', payload)),

  getSessionsList: async (params: {
    userId: string;
    activeOnly?: boolean;
    keyword?: string;
    page?: number;
    size?: number;
  }) => unwrap(await http.get<ApiResponse<PageResult<SessionDetailDTO>>>('/api/sessions/list', { params })),

  createTurnV2: async (sessionId: number, payload: TurnCreateRequest) =>
    unwrap(
      await http.post<ApiResponse<TurnCreateResponse>>(`/api/v2/sessions/${sessionId}/turns`, payload, {
        timeout: CHAT_HTTP_TIMEOUT_MS
      })
    ),

  getSession: async (sessionId: number) =>
    unwrap(await http.get<ApiResponse<SessionDetailDTO>>(`/api/sessions/${sessionId}`)),

  getSessionPlans: async (sessionId: number) =>
    unwrap(await http.get<ApiResponse<PlanSummaryDTO[]>>(`/api/sessions/${sessionId}/plans`)),

  getSessionOverview: async (sessionId: number) =>
    unwrap(await http.get<ApiResponse<SessionOverviewDTO>>(`/api/sessions/${sessionId}/overview`)),

  getPlan: async (planId: number) =>
    unwrap(await http.get<ApiResponse<PlanDetailDTO>>(`/api/plans/${planId}`)),

  getPlanRoutingV2: async (planId: number) =>
    unwrap(await http.get<ApiResponse<RoutingDecisionDTO | null>>(`/api/v2/plans/${planId}/routing`)),

  getPlanTasks: async (planId: number) =>
    unwrap(await http.get<ApiResponse<TaskDetailDTO[]>>(`/api/plans/${planId}/tasks`)),

  getTaskExecutions: async (taskId: number) =>
    unwrap(await http.get<ApiResponse<TaskExecutionDetailDTO[]>>(`/api/tasks/${taskId}/executions`)),

  getTask: async (taskId: number) => unwrap(await http.get<ApiResponse<TaskDetailDTO>>(`/api/tasks/${taskId}`)),

  pauseTask: async (taskId: number) =>
    unwrap(await http.post<ApiResponse<TaskDetailDTO>>(`/api/tasks/${taskId}/pause`)),

  resumeTask: async (taskId: number) =>
    unwrap(await http.post<ApiResponse<TaskDetailDTO>>(`/api/tasks/${taskId}/resume`)),

  cancelTask: async (taskId: number) =>
    unwrap(await http.post<ApiResponse<TaskDetailDTO>>(`/api/tasks/${taskId}/cancel`)),

  retryTaskFromFailed: async (taskId: number) =>
    unwrap(await http.post<ApiResponse<TaskDetailDTO>>(`/api/tasks/${taskId}/retry-from-failed`)),

  exportTask: async (taskId: number, format: 'markdown' | 'json') =>
    unwrap(await http.get<ApiResponse<TaskExportDTO>>(`/api/tasks/${taskId}/export`, { params: { format } })),

  createTaskShareLink: async (taskId: number, expiresHours?: number) =>
    unwrap(await http.post<ApiResponse<TaskShareLinkDTO>>(`/api/tasks/${taskId}/share-links`, undefined, { params: { expiresHours } })),

  getTaskShareLinks: async (taskId: number) =>
    unwrap(await http.get<ApiResponse<TaskShareLinkItemDTO[]>>(`/api/tasks/${taskId}/share-links`)),

  revokeTaskShareLink: async (taskId: number, shareId: number, reason?: string) =>
    unwrap(
      await http.post<ApiResponse<TaskShareRevokeResultDTO>>(
        `/api/tasks/${taskId}/share-links/${shareId}/revoke`,
        undefined,
        { params: reason ? { reason } : undefined }
      )
    ),

  revokeAllTaskShareLinks: async (taskId: number, reason?: string) =>
    unwrap(
      await http.post<ApiResponse<TaskShareRevokeResultDTO>>(
        `/api/tasks/${taskId}/share-links/revoke-all`,
        undefined,
        { params: reason ? { reason } : undefined }
      )
    ),

  readSharedTask: async (taskId: number, params: { code: string; token: string }) =>
    unwrap(await http.get<ApiResponse<SharedTaskReadDTO>>(`/api/share/tasks/${taskId}`, { params })),

  getTasks: async (params?: {
    status?: string;
    keyword?: string;
    planId?: number;
    sessionId?: number;
    limit?: number;
  }) => unwrap(await http.get<ApiResponse<TaskDetailDTO[]>>('/api/tasks', { params })),

  getTasksPaged: async (params?: {
    status?: string;
    keyword?: string;
    planId?: number;
    sessionId?: number;
    page?: number;
    size?: number;
  }) => unwrap(await http.get<ApiResponse<PageResult<TaskDetailDTO>>>('/api/tasks/paged', { params })),

  getPlanEvents: async (planId: number, params?: { afterEventId?: number; limit?: number }) =>
    unwrap(
      await http.get<ApiResponse<PlanTaskEventDTO[]>>(`/api/plans/${planId}/events`, {
        params
      })
    ),

  getSessionTurns: async (sessionId: number) =>
    unwrap(await http.get<ApiResponse<SessionTurnDTO[]>>(`/api/sessions/${sessionId}/turns`)),

  getSessionMessages: async (sessionId: number) =>
    unwrap(await http.get<ApiResponse<SessionMessageDTO[]>>(`/api/sessions/${sessionId}/messages`)),

  getWorkflowDrafts: async (status?: string) =>
    unwrapWithCodeCheck(
      await http.get<ApiResponse<WorkflowDraftSummaryDTO[]>>('/api/workflows/drafts', {
        params: status ? { status } : undefined
      })
    ),

  getWorkflowDraftDetail: async (id: number) =>
    unwrapWithCodeCheck(await http.get<ApiResponse<WorkflowDraftDetailDTO>>(`/api/workflows/drafts/${id}`)),

  updateWorkflowDraft: async (id: number, payload: WorkflowDraftUpdateRequestDTO) =>
    unwrapWithCodeCheck(await http.put<ApiResponse<WorkflowDraftDetailDTO>>(`/api/workflows/drafts/${id}`, payload)),

  publishWorkflowDraft: async (id: number, payload?: WorkflowPublishRequestDTO) =>
    unwrapWithCodeCheck(await http.post<ApiResponse<WorkflowPublishResultDTO>>(`/api/workflows/drafts/${id}/publish`, payload || {})),

  getAgentTools: async () => unwrap(await http.get<ApiResponse<AgentToolDTO[]>>('/api/agents/tools')),

  getVectorStores: async () => unwrap(await http.get<ApiResponse<VectorStoreDTO[]>>('/api/agents/vector-stores')),

  getKnowledgeBaseDetail: async (kbId: number) =>
    unwrap(await http.get<ApiResponse<KnowledgeBaseDetailDTO>>(`/api/knowledge-bases/${kbId}`)),

  getKnowledgeBaseDocuments: async (kbId: number) =>
    unwrap(await http.get<ApiResponse<KnowledgeDocumentDTO[]>>(`/api/knowledge-bases/${kbId}/documents`)),

  testKnowledgeRetrieval: async (kbId: number, query: string) =>
    unwrap(await http.post<ApiResponse<RetrievalTestResponseDTO>>(`/api/knowledge-bases/${kbId}/retrieval-tests`, { query })),

  getLogs: async (params?: {
    planId?: number;
    taskId?: number;
    level?: string;
    keyword?: string;
    limit?: number;
  }) => unwrap(await http.get<ApiResponse<PlanLogDTO[]>>('/api/logs', { params })),

  getLogsPaged: async (params?: {
    planId?: number;
    taskId?: number;
    level?: string;
    traceId?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }) => unwrap(await http.get<ApiResponse<PageResult<PlanLogDTO>>>('/api/logs/paged', { params })),

  getDashboardOverview: async (params?: { taskLimit?: number; planLimit?: number }) =>
    unwrap(await http.get<ApiResponse<DashboardOverviewDTO>>('/api/dashboard/overview', { params }))
};
