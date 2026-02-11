import { create } from 'zustand';
import type {
  PlanDetailDTO,
  SessionMessageDTO,
  SessionOverviewDTO,
  SessionTurnDTO,
  TaskDetailDTO,
  TaskExecutionDetailDTO
} from '@/shared/types/api';

interface PlanState {
  loading: boolean;
  sessionId?: number;
  selectedPlanId?: number;
  overview?: SessionOverviewDTO;
  planDetail?: PlanDetailDTO;
  planTasks: TaskDetailDTO[];
  turns: SessionTurnDTO[];
  messages: SessionMessageDTO[];
  taskExecutions: Record<number, TaskExecutionDetailDTO[]>;
  setLoading: (loading: boolean) => void;
  setOverview: (overview: SessionOverviewDTO) => void;
  setSelectedPlanId: (planId?: number) => void;
  setPlanDetail: (detail: PlanDetailDTO) => void;
  setPlanTasks: (tasks: TaskDetailDTO[]) => void;
  setTurns: (turns: SessionTurnDTO[]) => void;
  setMessages: (messages: SessionMessageDTO[]) => void;
  setTaskExecutions: (taskId: number, rows: TaskExecutionDetailDTO[]) => void;
}

export const usePlanStore = create<PlanState>((set) => ({
  loading: false,
  planTasks: [],
  turns: [],
  messages: [],
  taskExecutions: {},
  setLoading: (loading) => set({ loading }),
  setOverview: (overview) =>
    set({
      overview,
      sessionId: overview.session?.sessionId,
      selectedPlanId: overview.latestPlanId
    }),
  setSelectedPlanId: (selectedPlanId) => set({ selectedPlanId }),
  setPlanDetail: (planDetail) => set({ planDetail }),
  setPlanTasks: (planTasks) => set({ planTasks }),
  setTurns: (turns) => set({ turns }),
  setMessages: (messages) => set({ messages }),
  setTaskExecutions: (taskId, rows) =>
    set((state) => ({ taskExecutions: { ...state.taskExecutions, [taskId]: rows } }))
}));
