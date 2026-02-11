import { CHAT_HTTP_TIMEOUT_MS, http } from './http';
import type {
  ApiResponse,
  ChatRequest,
  ChatResponse,
  PlanDetailDTO,
  PlanSummaryDTO,
  WorkflowDraftDetailDTO,
  WorkflowDraftSummaryDTO,
  WorkflowDraftUpdateRequestDTO,
  WorkflowPublishRequestDTO,
  WorkflowPublishResultDTO,
  SessionCreateRequest,
  SessionCreateResponse,
  SessionDetailDTO,
  SessionMessageDTO,
  SessionOverviewDTO,
  SessionTurnDTO,
  TaskDetailDTO,
  TaskExecutionDetailDTO
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

  sendChat: async (sessionId: number, payload: ChatRequest) =>
    unwrap(
      await http.post<ApiResponse<ChatResponse>>(`/api/sessions/${sessionId}/chat`, payload, {
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

  getPlanTasks: async (planId: number) =>
    unwrap(await http.get<ApiResponse<TaskDetailDTO[]>>(`/api/plans/${planId}/tasks`)),

  getTaskExecutions: async (taskId: number) =>
    unwrap(await http.get<ApiResponse<TaskExecutionDetailDTO[]>>(`/api/tasks/${taskId}/executions`)),

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
    unwrapWithCodeCheck(await http.post<ApiResponse<WorkflowPublishResultDTO>>(`/api/workflows/drafts/${id}/publish`, payload || {}))
};
