import { CHAT_HTTP_TIMEOUT_MS, http } from './http';
import type {
  AuthLoginRequestDTO,
  AuthLoginResponseDTO,
  AuthLogoutResponseDTO,
  AuthMeResponseDTO,
  AgentToolDTO,
  ApiResponse,
  ChatHistoryResponseV3,
  ChatMessageSubmitRequestV3,
  ChatMessageSubmitResponseV3,
  DashboardOverviewDTO,
  KnowledgeBaseDetailDTO,
  KnowledgeDocumentDTO,
  ObservabilityAlertCatalogItemDTO,
  ObservabilityAlertProbeStatusDTO,
  PageResult,
  PlanDetailDTO,
  PlanLogDTO,
  PlanTaskEventDTO,
  RetrievalTestResponseDTO,
  SessionDetailDTO,
  SharedTaskReadDTO,
  SopCompileResultDTO,
  SopValidateResultDTO,
  TaskDetailDTO,
  TaskExecutionDetailDTO,
  TaskExportDTO,
  TaskShareLinkDTO,
  TaskShareLinkItemDTO,
  TaskShareRevokeResultDTO,
  VectorStoreDTO,
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
  authLogin: async (payload: AuthLoginRequestDTO) =>
    unwrapWithCodeCheck(await http.post<ApiResponse<AuthLoginResponseDTO>>('/api/auth/login', payload)),

  authLogout: async () =>
    unwrapWithCodeCheck(await http.post<ApiResponse<AuthLogoutResponseDTO>>('/api/auth/logout')),

  authMe: async () =>
    unwrapWithCodeCheck(await http.get<ApiResponse<AuthMeResponseDTO>>('/api/auth/me')),

  submitChatMessageV3: async (
    payload: ChatMessageSubmitRequestV3,
    options?: { timeoutMs?: number; signal?: AbortSignal }
  ) =>
    unwrapWithCodeCheck(
      await http.post<ApiResponse<ChatMessageSubmitResponseV3>>('/api/v3/chat/messages', payload, {
        timeout: options?.timeoutMs ?? CHAT_HTTP_TIMEOUT_MS,
        signal: options?.signal
      })
    ),

  getChatHistoryV3: async (sessionId: number) =>
    unwrapWithCodeCheck(await http.get<ApiResponse<ChatHistoryResponseV3>>(`/api/v3/chat/sessions/${sessionId}/history`)),

  getSessionsList: async (params: {
    userId: string;
    activeOnly?: boolean;
    keyword?: string;
    page?: number;
    size?: number;
  }) => unwrap(await http.get<ApiResponse<PageResult<SessionDetailDTO>>>('/api/sessions/list', { params })),

  getPlan: async (planId: number) =>
    unwrap(await http.get<ApiResponse<PlanDetailDTO>>(`/api/plans/${planId}`)),

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

  compileWorkflowDraftSopSpec: async (id: number, sopSpec?: Record<string, unknown>) =>
    unwrapWithCodeCheck(
      await http.post<ApiResponse<SopCompileResultDTO>>(`/api/workflows/sop-spec/drafts/${id}/compile`, sopSpec ? { sopSpec } : {})
    ),

  validateWorkflowDraftSopSpec: async (id: number, sopSpec?: Record<string, unknown>) =>
    unwrapWithCodeCheck(
      await http.post<ApiResponse<SopValidateResultDTO>>(`/api/workflows/sop-spec/drafts/${id}/validate`, sopSpec ? { sopSpec } : {})
    ),

  getAgentTools: async () => unwrap(await http.get<ApiResponse<AgentToolDTO[]>>('/api/agents/tools')),

  getVectorStores: async () => unwrap(await http.get<ApiResponse<VectorStoreDTO[]>>('/api/agents/vector-stores')),

  getKnowledgeBaseDetail: async (kbId: number) =>
    unwrap(await http.get<ApiResponse<KnowledgeBaseDetailDTO>>(`/api/knowledge-bases/${kbId}`)),

  getKnowledgeBaseDocuments: async (kbId: number) =>
    unwrap(await http.get<ApiResponse<KnowledgeDocumentDTO[]>>(`/api/knowledge-bases/${kbId}/documents`)),

  testKnowledgeRetrieval: async (kbId: number, query: string) =>
    unwrap(await http.post<ApiResponse<RetrievalTestResponseDTO>>(`/api/knowledge-bases/${kbId}/retrieval-tests`, { query })),

  getLogsPaged: async (params?: {
    planId?: number;
    taskId?: number;
    level?: string;
    traceId?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }) => unwrap(await http.get<ApiResponse<PageResult<PlanLogDTO>>>('/api/logs/paged', { params })),

  getObservabilityAlertCatalog: async () =>
    unwrap(await http.get<ApiResponse<ObservabilityAlertCatalogItemDTO[]>>('/api/observability/alerts/catalog')),

  getObservabilityAlertProbeStatus: async (params?: { window?: number }) =>
    unwrap(await http.get<ApiResponse<ObservabilityAlertProbeStatusDTO>>('/api/observability/alerts/probe-status', { params })),

  getDashboardOverview: async (params?: { taskLimit?: number; planLimit?: number }) =>
    unwrap(await http.get<ApiResponse<DashboardOverviewDTO>>('/api/dashboard/overview', { params }))
};
