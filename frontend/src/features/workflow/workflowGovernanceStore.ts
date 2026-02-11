import { create } from 'zustand';
import { agentApi } from '@/shared/api/agentApi';
import type {
  WorkflowDraftDetailDTO,
  WorkflowDraftSummaryDTO,
  WorkflowPublishResultDTO
} from '@/shared/types/api';

interface WorkflowGovernanceState {
  loading: boolean;
  detailLoading: boolean;
  publishingId?: number;
  statusFilter?: string;
  list: WorkflowDraftSummaryDTO[];
  selectedCandidate?: WorkflowDraftDetailDTO;
  error?: string;
  setStatusFilter: (status?: string) => void;
  clearSelectedCandidate: () => void;
  loadCandidates: (status?: string) => Promise<void>;
  loadCandidateDetail: (id: number) => Promise<void>;
  publishCandidate: (id: number, operator?: string) => Promise<WorkflowPublishResultDTO>;
}

const toErrorMessage = (err: unknown) => {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
};

export const useWorkflowGovernanceStore = create<WorkflowGovernanceState>((set, get) => ({
  loading: false,
  detailLoading: false,
  list: [],
  setStatusFilter: (statusFilter) => set({ statusFilter }),
  clearSelectedCandidate: () => set({ selectedCandidate: undefined }),
  loadCandidates: async (status) => {
    set({ loading: true, error: undefined, statusFilter: status });
    try {
      const list = await agentApi.getWorkflowDrafts(status);
      set({ list: list || [], loading: false });
    } catch (err) {
      const error = toErrorMessage(err);
      set({ loading: false, error });
      throw err;
    }
  },
  loadCandidateDetail: async (id) => {
    set({ detailLoading: true, error: undefined });
    try {
      const selectedCandidate = await agentApi.getWorkflowDraftDetail(id);
      set({ selectedCandidate, detailLoading: false });
    } catch (err) {
      const error = toErrorMessage(err);
      set({ detailLoading: false, error });
      throw err;
    }
  },
  publishCandidate: async (id, operator) => {
    set({ publishingId: id, error: undefined });
    try {
      const result = await agentApi.publishWorkflowDraft(id, { operator });
      await get().loadCandidates(get().statusFilter);
      set({ publishingId: undefined });
      return result;
    } catch (err) {
      const error = toErrorMessage(err);
      set({ publishingId: undefined, error });
      throw err;
    }
  }
}));
