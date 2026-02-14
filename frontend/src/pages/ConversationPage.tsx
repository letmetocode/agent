import { MenuOutlined, PlusOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Collapse, Drawer, Empty, Input, List, Segmented, Select, Space, Spin, Switch, Tag, Timeline, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useSessionStore } from '@/features/session/sessionStore';
import { openChatSseV3 } from '@/features/sse/sseClient';
import { agentApi } from '@/shared/api/agentApi';
import { CHAT_HTTP_TIMEOUT_MS } from '@/shared/api/http';
import type { ChatHistoryResponseV3, ChatStreamEventV3, SessionDetailDTO } from '@/shared/types/api';

const { TextArea } = Input;

interface ProcessEventItem {
  id: string;
  type: string;
  time: string;
  text: string;
  nodeId?: string;
  taskId?: number;
  planId?: number;
  mergedCount?: number;
}

type EventGroupMode = 'TIME' | 'TYPE' | 'NODE';

interface OptimisticMessageItem {
  clientMessageId: string;
  content: string;
  createdAt: string;
  sessionId?: number;
  status: 'PENDING' | 'FAILED';
  errorMessage?: string;
}

interface ChatRenderMessageItem {
  key: string;
  messageId?: number;
  role: string;
  content: string;
  createdAt?: string;
  localStatus?: 'PENDING' | 'FAILED';
  localErrorMessage?: string;
  clientMessageId?: string;
}

type StreamState = 'idle' | 'connecting' | 'connected' | 'retrying' | 'polling';

interface HistorySyncResult {
  hasExecutingTurn: boolean;
  nextPlanId?: number;
  runningPlanId?: number;
}

const EXECUTING_TURN_STATUS = new Set(['CREATED', 'PLANNING', 'EXECUTING', 'SUMMARIZING']);
const SSE_MAX_RETRIES = 3;
const SSE_RETRY_BASE_MS = 1200;
const SSE_SIGNAL_STALE_MS = 22000;
const HISTORY_POLL_INTERVAL_MS = 1500;
const HISTORY_POLL_TIMEOUT_MS = 30000;
const STREAM_ERROR_DEDUP_MS = 10000;
const SEND_REQUEST_TIMEOUT_MS = 15000;
const SEND_REQUEST_HARD_TIMEOUT_MS = 17000;
const SEND_RECOVERY_INTERVAL_MS = 1200;
const SEND_RECOVERY_TIMEOUT_MS = 45000;
const SEND_RECOVERY_HISTORY_LOOKBACK_MS = 10 * 60 * 1000;
const ACTIVE_HISTORY_SYNC_INTERVAL_MS = 2500;

const safeTime = (value?: string) => {
  if (!value) {
    return '-';
  }
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return value;
  }
  return new Date(timestamp).toLocaleString();
};

const toChatMessageError = (err: unknown): string => {
  if (err instanceof Error) {
    if (err.message.includes('timeout')) {
      return `发送超时（>${Math.floor(CHAT_HTTP_TIMEOUT_MS / 1000)}秒），请稍后查看会话结果。`;
    }
    return err.message;
  }
  return String(err);
};

const toEpoch = (value?: string): number => {
  if (!value) {
    return 0;
  }
  const ts = new Date(value).getTime();
  return Number.isFinite(ts) ? ts : 0;
};

const isAbortRequestError = (err: unknown): boolean => {
  if (!err || typeof err !== 'object') {
    return false;
  }
  const maybe = err as { code?: string; name?: string; message?: string };
  return (
    maybe.code === 'ERR_CANCELED' ||
    maybe.code === 'ECONNABORTED' ||
    maybe.name === 'CanceledError' ||
    maybe.name === 'AbortError' ||
    (typeof maybe.message === 'string' && maybe.message.includes('SEND_HARD_TIMEOUT')) ||
    (typeof maybe.message === 'string' && maybe.message.toLowerCase().includes('timeout')) ||
    (typeof maybe.message === 'string' && maybe.message.toLowerCase().includes('canceled'))
  );
};

const parseObjectJson = (raw: string, fieldName: string): Record<string, unknown> | undefined => {
  const text = raw.trim();
  if (!text) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(text) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error(`${fieldName} 必须是 JSON 对象`);
    }
    return parsed as Record<string, unknown>;
  } catch (err) {
    const reason = err instanceof Error ? err.message : String(err);
    throw new Error(`${fieldName} 解析失败: ${reason}`);
  }
};

const resolveEventNodeId = (event: ChatStreamEventV3): string | undefined => {
  const metadata = event.metadata && typeof event.metadata === 'object' ? event.metadata : undefined;
  const rawCandidates = [
    metadata ? metadata.nodeId : undefined,
    metadata ? metadata.taskName : undefined,
    // 向后兼容历史事件字段（新事件应统一为 nodeId/taskName）
    metadata ? metadata.taskNodeId : undefined
  ];
  for (const value of rawCandidates) {
    if (typeof value !== 'string') {
      continue;
    }
    const text = value.trim();
    if (text) {
      return text;
    }
  }
  return undefined;
};

const hasSubmittedMessageInHistory = (
  data: ChatHistoryResponseV3,
  expectedMessage: string,
  startedAt: number,
  clientMessageId?: string
): boolean => {
  const normalized = expectedMessage.trim();
  if (!normalized) {
    return false;
  }
  const threshold = Math.max(0, startedAt - SEND_RECOVERY_HISTORY_LOOKBACK_MS);

  const normalizedClientMessageId = (clientMessageId || '').trim();
  if (normalizedClientMessageId) {
    const matchedByClientMessageId = data.messages?.some((item) => {
      const role = (item.role || '').toUpperCase();
      if (role !== 'USER') {
        return false;
      }
      const metaClientMessageId =
        item.metadata && typeof item.metadata.clientMessageId === 'string'
          ? item.metadata.clientMessageId.trim()
          : '';
      if (metaClientMessageId !== normalizedClientMessageId) {
        return false;
      }
      const createdAt = toEpoch(item.createdAt);
      return createdAt <= 0 || createdAt >= threshold;
    });
    if (matchedByClientMessageId) {
      return true;
    }
  }

  const matchedUserMessage = data.messages?.some((item) => {
    const role = (item.role || '').toUpperCase();
    if (role !== 'USER') {
      return false;
    }
    const content = (item.content || '').trim();
    if (content !== normalized) {
      return false;
    }
    const createdAt = toEpoch(item.createdAt);
    return createdAt <= 0 || createdAt >= threshold;
  });
  if (matchedUserMessage) {
    return true;
  }

  return data.turns?.some((turn) => {
    const messageText = (turn.userMessage || '').trim();
    if (messageText !== normalized) {
      return false;
    }
    const createdAt = toEpoch(turn.createdAt);
    return createdAt <= 0 || createdAt >= threshold;
  }) || false;
};

export const ConversationPage = () => {
  const navigate = useNavigate();
  const { sessionId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const sid = sessionId ? Number(sessionId) : undefined;

  const { userId, authStatus, bookmarks, addBookmark } = useSessionStore();

  const [sessionListLoading, setSessionListLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [recoveringSubmission, setRecoveringSubmission] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [streamState, setStreamState] = useState<StreamState>('idle');

  const [sessions, setSessions] = useState<SessionDetailDTO[]>([]);
  const [history, setHistory] = useState<ChatHistoryResponseV3 | null>(null);
  const [optimisticMessages, setOptimisticMessages] = useState<OptimisticMessageItem[]>([]);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [activePlanId, setActivePlanId] = useState<number | undefined>();
  const [processEvents, setProcessEvents] = useState<ProcessEventItem[]>([]);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [scenario, setScenario] = useState('CHAT_DEFAULT');
  const [targetAgentKey, setTargetAgentKey] = useState('');
  const [sessionTitleDraft, setSessionTitleDraft] = useState('');
  const [contextOverridesDraft, setContextOverridesDraft] = useState('');
  const [metaInfoDraft, setMetaInfoDraft] = useState('');
  const [eventTypeFilter, setEventTypeFilter] = useState<string>('ALL');
  const [eventNodeFilter, setEventNodeFilter] = useState<string>('ALL');
  const [eventGroupMode, setEventGroupMode] = useState<EventGroupMode>('TIME');
  const [collapseEventDuplicates, setCollapseEventDuplicates] = useState(true);
  const [isMobileLayout, setIsMobileLayout] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.matchMedia('(max-width: 1023px)').matches : false
  );

  const sseRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const historyPollTimerRef = useRef<number | null>(null);
  const historyPollDeadlineRef = useRef<number>(0);
  const retryCountRef = useRef(0);
  const currentSubscriptionRef = useRef<string | null>(null);
  const lastFinalEventRef = useRef<{ key: string; eventId: number }>({ key: '', eventId: 0 });
  const lastSseSignalAtRef = useRef<number>(0);
  const streamErrorHintAtRef = useRef<Record<string, number>>({});
  const sendAbortControllerRef = useRef<AbortController | null>(null);
  const chatScrollableRef = useRef<HTMLDivElement | null>(null);
  const autoScrollEnabledRef = useRef(true);
  const mountedRef = useRef(true);

  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8091';

  const messages = useMemo<ChatRenderMessageItem[]>(() => {
    const serverMessages = (history?.messages || []).map((item) => ({
      key: `server-${item.messageId || `${item.turnId || 0}-${item.createdAt || ''}-${(item.content || '').slice(0, 12)}`}`,
      messageId: item.messageId,
      role: item.role || 'SYSTEM',
      content: item.content || '',
      createdAt: item.createdAt,
      clientMessageId:
        item.metadata && typeof item.metadata.clientMessageId === 'string'
          ? item.metadata.clientMessageId
          : undefined
    }));

    const serverClientMessageIds = new Set(
      serverMessages
        .map((item) => item.clientMessageId)
        .filter((item): item is string => typeof item === 'string' && item.length > 0)
    );

    const historySessionId = history?.sessionId;
    const optimisticRenderable = optimisticMessages
      .filter((item) => {
        if (serverClientMessageIds.has(item.clientMessageId)) {
          return false;
        }
        if (item.sessionId && historySessionId && item.sessionId !== historySessionId) {
          return false;
        }
        if (!serverClientMessageIds.size) {
          return true;
        }
        const fallbackMatched = (history?.messages || []).some((serverItem) => {
          const role = (serverItem.role || '').toUpperCase();
          if (role !== 'USER') {
            return false;
          }
          if ((serverItem.content || '').trim() !== item.content.trim()) {
            return false;
          }
          const serverAt = toEpoch(serverItem.createdAt);
          const localAt = toEpoch(item.createdAt);
          return serverAt > 0 && localAt > 0 && Math.abs(serverAt - localAt) <= SEND_RECOVERY_HISTORY_LOOKBACK_MS;
        });
        return !fallbackMatched;
      })
      .map((item) => ({
        key: `optimistic-${item.clientMessageId}`,
        messageId: undefined,
        role: 'USER',
        content: item.content,
        createdAt: item.createdAt,
        localStatus: item.status,
        localErrorMessage: item.errorMessage,
        clientMessageId: item.clientMessageId
      }));

    return [...serverMessages, ...optimisticRenderable].sort((a, b) => {
      const at = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bt = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      if (at !== bt) {
        return at - bt;
      }
      return (a.messageId || 0) - (b.messageId || 0);
    });
  }, [history?.messages, history?.sessionId, optimisticMessages]);

  const hasExecutingTurn = useMemo(() => {
    if (!history?.turns) {
      return false;
    }
    return history.turns.some((turn) => EXECUTING_TURN_STATUS.has((turn.status || '').toUpperCase()));
  }, [history?.turns]);

  const recentSessions = useMemo(() => {
    if (sessions.length > 0) {
      return sessions;
    }
    return bookmarks.map((item) => ({
      sessionId: item.sessionId,
      userId: userId || '-',
      title: item.title,
      active: true,
      createdAt: item.createdAt
    }));
  }, [bookmarks, sessions, userId]);

  const currentSessionTitle = history?.title || (sid ? `Session #${sid}` : '新聊天');

  const streamStatusHint = useMemo(() => {
    if (streamState === 'retrying') {
      return '连接轻微波动，正在自动恢复…';
    }
    if (streamState === 'polling') {
      return '重连失败，正在同步最终结果…';
    }
    if (streamState === 'connecting' || streamState === 'connected') {
      return '实时执行中，最终回复将自动刷新。';
    }
    return '仅展示流式执行状态与终态收敛，不把中间态落为最终回复。';
  }, [streamState]);

  const eventTypeOptions = useMemo(
    () =>
      ['ALL', ...Array.from(new Set(processEvents.map((item) => item.type).filter((item) => !!item))).sort()].map((item) => ({
        label: item === 'ALL' ? '全部类型' : item,
        value: item
      })),
    [processEvents]
  );

  const eventNodeOptions = useMemo(
    () =>
      ['ALL', ...Array.from(new Set(processEvents.map((item) => item.nodeId).filter((item): item is string => !!item))).sort()].map(
        (item) => ({
          label: item === 'ALL' ? '全部节点' : item,
          value: item
        })
      ),
    [processEvents]
  );

  const filteredProcessEvents = useMemo(() => {
    const byFilter = processEvents.filter((item) => {
      if (eventTypeFilter !== 'ALL' && item.type !== eventTypeFilter) {
        return false;
      }
      if (eventNodeFilter !== 'ALL' && item.nodeId !== eventNodeFilter) {
        return false;
      }
      return true;
    });
    if (!collapseEventDuplicates) {
      return byFilter;
    }
    const merged: ProcessEventItem[] = [];
    for (const item of byFilter) {
      const previous = merged[merged.length - 1];
      const canMerge =
        previous &&
        previous.type === item.type &&
        previous.text === item.text &&
        previous.nodeId === item.nodeId &&
        previous.taskId === item.taskId;
      if (canMerge) {
        previous.mergedCount = (previous.mergedCount || 1) + 1;
        continue;
      }
      merged.push({ ...item, mergedCount: item.mergedCount || 1 });
    }
    return merged;
  }, [collapseEventDuplicates, eventNodeFilter, eventTypeFilter, processEvents]);

  const groupedProcessEvents = useMemo(() => {
    if (eventGroupMode === 'TIME') {
      return [{ key: 'TIME', title: '时间序', items: filteredProcessEvents }];
    }
    const groups = new Map<string, ProcessEventItem[]>();
    for (const item of filteredProcessEvents) {
      const key = eventGroupMode === 'TYPE' ? item.type : item.nodeId || '未标注节点';
      const current = groups.get(key) || [];
      current.push(item);
      groups.set(key, current);
    }
    return Array.from(groups.entries())
      .map(([key, items]) => ({
        key,
        title: `${key}（${items.length}）`,
        items
      }))
      .sort((a, b) => b.items.length - a.items.length);
  }, [eventGroupMode, filteredProcessEvents]);

  const scrollChatToBottom = useCallback((behavior: ScrollBehavior = 'auto') => {
    if (!autoScrollEnabledRef.current) {
      return;
    }
    const panel = chatScrollableRef.current;
    if (!panel) {
      return;
    }
    panel.scrollTo({
      top: panel.scrollHeight,
      behavior
    });
  }, []);

  const handleChatScroll = useCallback(() => {
    const panel = chatScrollableRef.current;
    if (!panel) {
      return;
    }
    const distanceToBottom = panel.scrollHeight - panel.scrollTop - panel.clientHeight;
    autoScrollEnabledRef.current = distanceToBottom < 96;
  }, []);

  const pushProcessEvent = useCallback((event: ChatStreamEventV3) => {
    const text = event.finalAnswer || event.message || event.taskStatus || event.type;
    const nodeId = resolveEventNodeId(event);
    const row: ProcessEventItem = {
      id: `${event.type}-${event.eventId || Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      type: event.type,
      text,
      time: new Date().toLocaleTimeString(),
      nodeId,
      taskId: typeof event.taskId === 'number' ? event.taskId : undefined,
      planId: typeof event.planId === 'number' ? event.planId : undefined,
      mergedCount: 1
    };
    setProcessEvents((prev) => [row, ...prev].slice(0, 120));
  }, []);

  const pushStreamErrorHint = useCallback(
    (key: string, messageText: string) => {
      const now = Date.now();
      const lastAt = streamErrorHintAtRef.current[key] || 0;
      if (now - lastAt < STREAM_ERROR_DEDUP_MS) {
        return;
      }
      streamErrorHintAtRef.current[key] = now;
      pushProcessEvent({ type: 'stream.error', message: messageText });
    },
    [pushProcessEvent]
  );

  const upsertOptimisticMessage = useCallback((item: OptimisticMessageItem) => {
    setOptimisticMessages((previous) => {
      const index = previous.findIndex((candidate) => candidate.clientMessageId === item.clientMessageId);
      if (index < 0) {
        return [...previous, item];
      }
      const next = previous.slice();
      next[index] = { ...next[index], ...item };
      return next;
    });
  }, []);

  const markOptimisticMessageFailed = useCallback((clientMessageId: string, errorMessage: string) => {
    setOptimisticMessages((previous) =>
      previous.map((item) =>
        item.clientMessageId === clientMessageId
          ? {
              ...item,
              status: 'FAILED',
              errorMessage
            }
          : item
      )
    );
  }, []);

  const bindOptimisticMessageSession = useCallback((clientMessageId: string, sessionId: number) => {
    setOptimisticMessages((previous) =>
      previous.map((item) =>
        item.clientMessageId === clientMessageId
          ? {
              ...item,
              sessionId
            }
          : item
      )
    );
  }, []);

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current !== null) {
      window.clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const clearHistoryPollTimer = useCallback(() => {
    if (historyPollTimerRef.current !== null) {
      window.clearTimeout(historyPollTimerRef.current);
      historyPollTimerRef.current = null;
    }
    historyPollDeadlineRef.current = 0;
  }, []);

  const abortPendingSendRequest = useCallback(() => {
    if (sendAbortControllerRef.current) {
      sendAbortControllerRef.current.abort();
      sendAbortControllerRef.current = null;
    }
  }, []);

  const stopStream = useCallback(() => {
    clearReconnectTimer();
    clearHistoryPollTimer();
    retryCountRef.current = 0;
    currentSubscriptionRef.current = null;
    lastFinalEventRef.current = { key: '', eventId: 0 };
    lastSseSignalAtRef.current = 0;
    streamErrorHintAtRef.current = {};
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
    setStreaming(false);
    setStreamState('idle');
  }, [clearHistoryPollTimer, clearReconnectTimer]);

  const resolveHistoryState = useCallback(
    (data: ChatHistoryResponseV3): HistorySyncResult => {
      const queryPlanId = Number(searchParams.get('planId')) || undefined;
      const runningPlanId = data.turns
        .filter((turn) => EXECUTING_TURN_STATUS.has((turn.status || '').toUpperCase()) && turn.planId)
        .sort((a, b) => (b.turnId || 0) - (a.turnId || 0))[0]?.planId;

      const nextPlanId = runningPlanId || queryPlanId || data.latestPlanId || undefined;
      const nextHasExecutingTurn = data.turns.some((turn) => EXECUTING_TURN_STATUS.has((turn.status || '').toUpperCase()));

      return {
        hasExecutingTurn: nextHasExecutingTurn,
        nextPlanId,
        runningPlanId
      };
    },
    [searchParams]
  );

  const syncHistorySnapshot = useCallback(
    async (targetSessionId: number, options?: { silent?: boolean }): Promise<HistorySyncResult | null> => {
      try {
        const data = await agentApi.getChatHistoryV3(targetSessionId);
        setHistory(data);
        const state = resolveHistoryState(data);
        setActivePlanId(state.nextPlanId);
        return state;
      } catch (err) {
        if (!options?.silent) {
          message.error(`加载会话历史失败: ${toChatMessageError(err)}`);
        }
        return null;
      }
    },
    [resolveHistoryState]
  );

  const loadSessions = useCallback(async () => {
    if (!userId || authStatus !== 'AUTHENTICATED') {
      setSessions([]);
      return;
    }
    setSessionListLoading(true);
    try {
      const pageData = await agentApi.getSessionsList({ userId, page: 1, size: 50 });
      setSessions(pageData.items || []);
    } catch (err) {
      message.error(`加载会话失败: ${toChatMessageError(err)}`);
    } finally {
      setSessionListLoading(false);
    }
  }, [authStatus, userId]);

  const startHistoryPolling = useCallback(
    (targetSessionId: number) => {
      clearHistoryPollTimer();
      setStreamState('polling');
      setStreaming(false);
      historyPollDeadlineRef.current = Date.now() + HISTORY_POLL_TIMEOUT_MS;

      const poll = async () => {
        const result = await syncHistorySnapshot(targetSessionId, { silent: true });
        if (result && !result.hasExecutingTurn) {
          clearHistoryPollTimer();
          setStreamState('idle');
          return;
        }

        if (Date.now() >= historyPollDeadlineRef.current) {
          clearHistoryPollTimer();
          setStreamState('idle');
          pushStreamErrorHint('polling-timeout', '自动同步超时，请手动刷新查看最新结果。');
          return;
        }

        historyPollTimerRef.current = window.setTimeout(poll, HISTORY_POLL_INTERVAL_MS);
      };

      void poll();
    },
    [clearHistoryPollTimer, pushStreamErrorHint, syncHistorySnapshot]
  );

  const connectStream = useCallback(
    (targetSessionId: number, targetPlanId: number, options?: { force?: boolean; resetRetry?: boolean }) => {
      const subscriptionKey = `${targetSessionId}-${targetPlanId}`;
      if (!options?.force && currentSubscriptionRef.current === subscriptionKey && sseRef.current) {
        return;
      }

      clearReconnectTimer();
      clearHistoryPollTimer();
      if (options?.resetRetry !== false) {
        retryCountRef.current = 0;
        streamErrorHintAtRef.current = {};
      }

      if (sseRef.current) {
        sseRef.current.close();
        sseRef.current = null;
      }

      currentSubscriptionRef.current = subscriptionKey;
      if (lastFinalEventRef.current.key !== subscriptionKey) {
        lastFinalEventRef.current = { key: subscriptionKey, eventId: 0 };
      }
      setStreaming(true);
      lastSseSignalAtRef.current = Date.now();
      setStreamState(options?.force ? 'retrying' : 'connecting');

      const source = openChatSseV3(
        targetSessionId,
        targetPlanId,
        baseUrl,
        (event) => {
          if (currentSubscriptionRef.current !== subscriptionKey) {
            return;
          }

          lastSseSignalAtRef.current = Date.now();

          if (event.type !== 'stream.heartbeat') {
            if (event.type !== 'stream.completed') {
              setStreamState((previous) => (previous === 'connected' ? previous : 'connected'));
            }
            pushProcessEvent(event);
          }

          if (event.type === 'stream.completed') {
            clearReconnectTimer();
            clearHistoryPollTimer();
            retryCountRef.current = 0;
            currentSubscriptionRef.current = null;
            if (sseRef.current) {
              sseRef.current.close();
              sseRef.current = null;
            }
            setStreaming(false);
            setStreamState('idle');
            void Promise.all([loadSessions(), syncHistorySnapshot(targetSessionId, { silent: true })]);
            return;
          }

          if (event.type === 'answer.final') {
            const eventId = typeof event.eventId === 'number' ? event.eventId : 0;
            const lastFinal = lastFinalEventRef.current;
            if (eventId > 0 && lastFinal.key === subscriptionKey && eventId <= lastFinal.eventId) {
              return;
            }
            if (eventId > 0) {
              lastFinalEventRef.current = { key: subscriptionKey, eventId };
            }

            clearReconnectTimer();
            clearHistoryPollTimer();
            retryCountRef.current = 0;
            currentSubscriptionRef.current = null;
            if (sseRef.current) {
              sseRef.current.close();
              sseRef.current = null;
            }
            setStreaming(false);
            setStreamState('idle');

            void Promise.all([loadSessions(), syncHistorySnapshot(targetSessionId, { silent: true })]);
          }
        },
        (eventSource) => {
          const handleStreamError = async () => {
            if (currentSubscriptionRef.current !== subscriptionKey) {
              return;
            }

            const lastSignalAt = lastSseSignalAtRef.current || Date.now();
            const staleDuration = Date.now() - lastSignalAt;
            const isTransientReconnect = eventSource.readyState === EventSource.CONNECTING && staleDuration < SSE_SIGNAL_STALE_MS;
            if (isTransientReconnect) {
              setStreaming(true);
              setStreamState((previous) => (previous === 'polling' ? previous : 'connected'));
              return;
            }

            setStreaming(false);

            if (sseRef.current) {
              sseRef.current.close();
              sseRef.current = null;
            }

            const historyState = staleDuration >= SSE_SIGNAL_STALE_MS
              ? await syncHistorySnapshot(targetSessionId, { silent: true })
              : null;
            if (currentSubscriptionRef.current !== subscriptionKey) {
              return;
            }

            if (historyState && !historyState.hasExecutingTurn) {
              clearReconnectTimer();
              clearHistoryPollTimer();
              retryCountRef.current = 0;
              currentSubscriptionRef.current = null;
              setStreamState('idle');
              return;
            }

            if (retryCountRef.current >= SSE_MAX_RETRIES) {
              currentSubscriptionRef.current = null;
              pushStreamErrorHint('switch-polling', '流连接长期中断，已切换为自动同步模式。');
              startHistoryPolling(targetSessionId);
              return;
            }

            retryCountRef.current += 1;
            const delay = SSE_RETRY_BASE_MS * Math.pow(2, retryCountRef.current - 1);
            setStreamState('retrying');
            if (retryCountRef.current > 1 && staleDuration >= SSE_SIGNAL_STALE_MS) {
              pushStreamErrorHint(
                'retrying',
                `流连接中断，正在重连（${retryCountRef.current}/${SSE_MAX_RETRIES}）...`
              );
            }

            clearReconnectTimer();
            reconnectTimerRef.current = window.setTimeout(() => {
              connectStream(targetSessionId, targetPlanId, { force: true, resetRetry: false });
            }, delay);
          };

          void handleStreamError();
        }
      );

      source.onopen = () => {
        if (currentSubscriptionRef.current !== subscriptionKey) {
          return;
        }
        retryCountRef.current = 0;
        lastSseSignalAtRef.current = Date.now();
        setStreaming(true);
        setStreamState('connected');
      };

      sseRef.current = source;
    },
    [
      baseUrl,
      clearHistoryPollTimer,
      clearReconnectTimer,
      loadSessions,
      pushProcessEvent,
      pushStreamErrorHint,
      startHistoryPolling,
      syncHistorySnapshot
    ]
  );

  const recoverSubmissionFromHistory = useCallback(
    async (
      expectedMessage: string,
      options?: { preferredSessionId?: number; startedAt?: number; clientMessageId?: string }
    ): Promise<boolean> => {
      const normalized = expectedMessage.trim();
      if (!normalized) {
        return false;
      }
      const startedAt = options?.startedAt || Date.now();
      const deadline = Date.now() + SEND_RECOVERY_TIMEOUT_MS;

      while (Date.now() <= deadline) {
        const candidateSessionIds: number[] = [];
        if (options?.preferredSessionId) {
          candidateSessionIds.push(options.preferredSessionId);
        } else if (userId) {
          try {
            const pageData = await agentApi.getSessionsList({ userId, page: 1, size: 12 });
            const candidates = (pageData.items || [])
              .slice()
              .sort((a, b) => toEpoch(b.createdAt) - toEpoch(a.createdAt))
              .map((item) => item.sessionId)
              .filter((item): item is number => typeof item === 'number' && item > 0);
            candidateSessionIds.push(...candidates);
          } catch {
            // ignore transient list error and continue retry loop
          }
        }

        for (const candidateSessionId of candidateSessionIds) {
          try {
            const historyData = await agentApi.getChatHistoryV3(candidateSessionId);
            if (!hasSubmittedMessageInHistory(historyData, normalized, startedAt, options?.clientMessageId)) {
              continue;
            }

            setHistory(historyData);
            const state = resolveHistoryState(historyData);
            setActivePlanId(state.nextPlanId);

            addBookmark({
              sessionId: historyData.sessionId,
              title: historyData.title || normalized,
              createdAt: new Date().toISOString()
            });

            if (!sid || sid !== historyData.sessionId) {
              const query = state.nextPlanId ? `?planId=${state.nextPlanId}` : '';
              navigate(`/sessions/${historyData.sessionId}${query}`, { replace: true });
            } else if (state.nextPlanId) {
              setSearchParams({ planId: String(state.nextPlanId) }, { replace: true });
            }

            if (state.nextPlanId && state.hasExecutingTurn) {
              connectStream(historyData.sessionId, state.nextPlanId, { force: true, resetRetry: true });
            }

            void loadSessions();
            return true;
          } catch {
            // ignore history fetch error and continue retry loop
          }
        }

        await new Promise((resolve) => window.setTimeout(resolve, SEND_RECOVERY_INTERVAL_MS));
      }

      return false;
    },
    [addBookmark, authStatus, connectStream, loadSessions, navigate, resolveHistoryState, setSearchParams, sid, userId]
  );

  const loadHistory = useCallback(
    async (targetSessionId: number) => {
      setHistoryLoading(true);
      try {
        return await syncHistorySnapshot(targetSessionId);
      } finally {
        setHistoryLoading(false);
      }
    },
    [syncHistorySnapshot]
  );

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    autoScrollEnabledRef.current = true;
    setOptimisticMessages((previous) => previous.filter((item) => (sid ? item.sessionId === sid : !item.sessionId)));
  }, [sid]);

  useEffect(() => {
    if (!messages.length) {
      return;
    }
    scrollChatToBottom('smooth');
  }, [messages.length, scrollChatToBottom]);

  useEffect(() => {
    if (!history?.messages?.length) {
      return;
    }
    const settledClientMessageIds = new Set(
      history.messages
        .map((item) =>
          item.metadata && typeof item.metadata.clientMessageId === 'string'
            ? item.metadata.clientMessageId
            : undefined
        )
        .filter((item): item is string => Boolean(item))
    );
    if (!settledClientMessageIds.size) {
      return;
    }
    setOptimisticMessages((previous) =>
      previous.filter((item) => !settledClientMessageIds.has(item.clientMessageId))
    );
  }, [history?.messages]);

  useEffect(() => {
    if (!sid || !hasExecutingTurn || streamState === 'polling') {
      return;
    }

    let canceled = false;
    let timer: number | null = null;

    const tick = async () => {
      const state = await syncHistorySnapshot(sid, { silent: true });
      if (canceled || !state || !state.hasExecutingTurn) {
        return;
      }
      timer = window.setTimeout(tick, ACTIVE_HISTORY_SYNC_INTERVAL_MS);
    };

    timer = window.setTimeout(tick, ACTIVE_HISTORY_SYNC_INTERVAL_MS);

    return () => {
      canceled = true;
      if (timer !== null) {
        window.clearTimeout(timer);
      }
    };
  }, [hasExecutingTurn, sid, streamState, syncHistorySnapshot]);

  useEffect(() => {
    if (!sid || Number.isNaN(sid)) {
      abortPendingSendRequest();
      setRecoveringSubmission(false);
      setHistory(null);
      setActivePlanId(undefined);
      stopStream();
      return;
    }

    const hasSameSessionSubscription = Boolean(
      sseRef.current && currentSubscriptionRef.current && currentSubscriptionRef.current.startsWith(`${sid}-`)
    );

    if (!hasSameSessionSubscription) {
      abortPendingSendRequest();
      setRecoveringSubmission(false);
      stopStream();
    }

    void loadHistory(sid);
  }, [abortPendingSendRequest, loadHistory, sid, stopStream]);

  useEffect(() => {
    if (!sid || !activePlanId) {
      return;
    }

    const subscriptionKey = `${sid}-${activePlanId}`;
    const isCurrentPlanSubscribed = currentSubscriptionRef.current === subscriptionKey && Boolean(sseRef.current);

    if (!hasExecutingTurn) {
      if (!isCurrentPlanSubscribed) {
        stopStream();
      }
      return;
    }
    if (streamState === 'retrying') {
      return;
    }

    if (isCurrentPlanSubscribed) {
      return;
    }

    connectStream(sid, activePlanId);
  }, [activePlanId, connectStream, hasExecutingTurn, sid, stopStream, streamState]);

  useEffect(
    () => () => {
      abortPendingSendRequest();
      stopStream();
    },
    [abortPendingSendRequest, stopStream]
  );

  const buildClientMessageId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return `cm-${crypto.randomUUID()}`;
    }
    return `cm-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  };

  const sendMessage = async (options?: { content?: string; clientMessageId?: string; retry?: boolean }) => {
    if (!userId || authStatus !== 'AUTHENTICATED') {
      message.warning('请先登录');
      navigate('/login');
      return;
    }
    const rawContent = options?.content ?? prompt;
    if (!rawContent.trim() || sending || recoveringSubmission) {
      return;
    }

    const content = rawContent.trim();
    const clientMessageId = options?.clientMessageId || buildClientMessageId();
    const localCreatedAt = new Date().toISOString();
    const submitStartedAt = Date.now();
    let parsedContextOverrides: Record<string, unknown> | undefined;
    let parsedMetaInfo: Record<string, unknown> | undefined;
    try {
      parsedContextOverrides = parseObjectJson(contextOverridesDraft, 'contextOverrides');
      parsedMetaInfo = parseObjectJson(metaInfoDraft, 'metaInfo');
    } catch (err) {
      message.error(err instanceof Error ? err.message : String(err));
      return;
    }
    const controller = new AbortController();
    sendAbortControllerRef.current = controller;
    const abortTimeoutHandle = window.setTimeout(() => {
      if (!controller.signal.aborted) {
        controller.abort();
      }
    }, SEND_REQUEST_TIMEOUT_MS);
    let hardTimeoutHandle: number | null = null;

    autoScrollEnabledRef.current = true;
    setRecoveringSubmission(false);
    setPrompt('');
    upsertOptimisticMessage({
      clientMessageId,
      content,
      createdAt: localCreatedAt,
      sessionId: sid,
      status: 'PENDING',
      errorMessage: undefined
    });
    setSending(true);
    try {
      const response = await Promise.race([
        agentApi.submitChatMessageV3(
          {
            clientMessageId,
            userId,
            sessionId: sid,
            message: content,
            title: sid ? undefined : (sessionTitleDraft || '').trim() || undefined,
            agentKey: (targetAgentKey || '').trim() || undefined,
            scenario: (scenario || '').trim() || 'CHAT_DEFAULT',
            metaInfo: {
              source: 'conversation-page',
              entry: options?.retry ? 'retry-message' : sid ? 'continue-chat' : 'new-chat',
              ...parsedMetaInfo
            },
            contextOverrides: parsedContextOverrides
          },
          {
            timeoutMs: Math.min(CHAT_HTTP_TIMEOUT_MS, SEND_REQUEST_TIMEOUT_MS),
            signal: controller.signal
          }
        ),
        new Promise<never>((_, reject) => {
          hardTimeoutHandle = window.setTimeout(() => {
            if (!controller.signal.aborted) {
              controller.abort();
            }
            reject(new Error('SEND_HARD_TIMEOUT'));
          }, SEND_REQUEST_HARD_TIMEOUT_MS);
        })
      ]);

      bindOptimisticMessageSession(clientMessageId, response.sessionId);
      if (response.planId) {
        setActivePlanId(response.planId);
      }
      pushProcessEvent({
        type: 'message.accepted',
        planId: response.planId,
        sessionId: response.sessionId,
        message: response.planId ? `已提交，Plan #${response.planId}` : '已提交，等待异步规划生成 Plan…'
      });

      addBookmark({
        sessionId: response.sessionId,
        title: response.sessionTitle || content,
        createdAt: new Date().toISOString()
      });

      const planQuery = response.planId ? `?planId=${response.planId}` : '';
      if (!sid || sid !== response.sessionId) {
        navigate(`/sessions/${response.sessionId}${planQuery}`, { replace: true });
      } else {
        if (response.planId) {
          setSearchParams({ planId: String(response.planId) }, { replace: true });
        } else {
          setSearchParams({}, { replace: true });
        }
      }

      if (response.planId) {
        connectStream(response.sessionId, response.planId, { force: true, resetRetry: true });
      } else {
        startHistoryPolling(response.sessionId);
      }
      void Promise.all([loadSessions(), syncHistorySnapshot(response.sessionId, { silent: true })]);
    } catch (err) {
      if (isAbortRequestError(err)) {
        setRecoveringSubmission(true);
        pushProcessEvent({
          type: 'message.accepted',
          message: '请求响应超时，正在自动同步最新会话结果…'
        });

        void (async () => {
          try {
            const recovered = await recoverSubmissionFromHistory(content, {
              preferredSessionId: sid,
              startedAt: submitStartedAt,
              clientMessageId
            });
            if (recovered) {
              message.info('请求已恢复：会话结果已自动同步。');
            } else {
              markOptimisticMessageFailed(clientMessageId, '发送超时：未匹配到服务端受理结果');
              message.warning('发送超时，自动同步未命中，请稍后重试或手动刷新。');
            }
          } finally {
            if (mountedRef.current) {
              setRecoveringSubmission(false);
            }
          }
        })();
      } else {
        markOptimisticMessageFailed(clientMessageId, toChatMessageError(err));
        message.error(`发送失败: ${toChatMessageError(err)}`);
      }
    } finally {
      window.clearTimeout(abortTimeoutHandle);
      if (hardTimeoutHandle !== null) {
        window.clearTimeout(hardTimeoutHandle);
      }
      if (sendAbortControllerRef.current === controller) {
        sendAbortControllerRef.current = null;
      }
      setSending(false);
    }
  };

  const retryOptimisticMessage = (item: ChatRenderMessageItem) => {
    if (!item.clientMessageId || !item.content.trim()) {
      return;
    }
    void sendMessage({
      content: item.content,
      clientMessageId: item.clientMessageId,
      retry: true
    });
  };

  const renderMessage = (item: ChatRenderMessageItem) => {
    const role = (item.role || '').toUpperCase();
    const isUser = role === 'USER';
    const bubbleClass = isUser ? 'chat-message-bubble user' : 'chat-message-bubble assistant';
    const isPending = item.localStatus === 'PENDING';
    const isFailed = item.localStatus === 'FAILED';

    return (
      <div key={item.key} className={`chat-message-row ${isUser ? 'user' : 'assistant'}`}>
        <div className={bubbleClass}>
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Typography.Text strong>{isUser ? '你' : role === 'ASSISTANT' ? 'Agent' : role}</Typography.Text>
            <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Typography.Paragraph>
            {isPending ? (
              <Tag color="processing" style={{ width: 'fit-content' }}>
                发送中
              </Tag>
            ) : null}
            {isFailed ? (
              <Space size={8} wrap>
                <Tag color="error" style={{ width: 'fit-content' }}>
                  发送失败
                </Tag>
                <Button type="link" size="small" onClick={() => retryOptimisticMessage(item)}>
                  重试
                </Button>
              </Space>
            ) : null}
            {isFailed && item.localErrorMessage ? (
              <Typography.Text type="danger" style={{ fontSize: 12 }}>
                {item.localErrorMessage}
              </Typography.Text>
            ) : null}
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {safeTime(item.createdAt)}
            </Typography.Text>
          </Space>
        </div>
      </div>
    );
  };

  const handleStartNewChat = useCallback(() => {
    setHistoryDrawerOpen(false);
    navigate('/sessions');
  }, [navigate]);

  const handleSelectSession = useCallback(
    (targetSessionId: number) => {
      setHistoryDrawerOpen(false);
      navigate(`/sessions/${targetSessionId}`);
    },
    [navigate]
  );

  const renderHistorySessionList = () => (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Button type="primary" icon={<PlusOutlined />} block onClick={handleStartNewChat}>
        新聊天
      </Button>
      {sessionListLoading ? <Spin /> : null}
      <List
        size="small"
        dataSource={recentSessions}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史会话" /> }}
        renderItem={(item) => (
          <List.Item
            className={`conversation-plan-item ${sid === item.sessionId ? 'active' : ''}`}
            onClick={() => handleSelectSession(item.sessionId)}
          >
            <List.Item.Meta
              title={item.title || `Session #${item.sessionId}`}
              description={`#${item.sessionId} · ${safeTime(item.createdAt)}`}
            />
          </List.Item>
        )}
      />
    </Space>
  );

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    const mediaQuery = window.matchMedia('(max-width: 1023px)');
    const sync = (event?: MediaQueryListEvent) => setIsMobileLayout(event ? event.matches : mediaQuery.matches);
    sync();
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', sync);
      return () => mediaQuery.removeEventListener('change', sync);
    }
    mediaQuery.addListener(sync);
    return () => mediaQuery.removeListener(sync);
  }, []);

  useEffect(() => {
    if (!isMobileLayout) {
      setHistoryDrawerOpen(false);
    }
  }, [isMobileLayout]);

  return (
    <div className="page-container conversation-page">
      <div className="chat-toolbar glass-card">
        <Space direction="vertical" size={2} className="chat-toolbar-main">
          <Typography.Text strong className="chat-toolbar-title">
            {sid ? currentSessionTitle : '新聊天'}
          </Typography.Text>
          <Typography.Text type="secondary">ChatGPT 风格入口：直接发消息，复杂执行细节放在右侧进度栏。</Typography.Text>
        </Space>
        <Space size={12} align="center">
          <Typography.Text type="secondary">当前账号：{userId || '未登录'}</Typography.Text>
          {isMobileLayout ? (
            <Button icon={<MenuOutlined />} onClick={() => setHistoryDrawerOpen(true)}>
              历史
            </Button>
          ) : null}
          <Button type="primary" icon={<PlusOutlined />} onClick={handleStartNewChat}>
            新聊天
          </Button>
        </Space>
      </div>

      {!userId || authStatus !== 'AUTHENTICATED' ? (
        <Alert
          type="warning"
          showIcon
          message="尚未登录"
          description={
            <Space>
              <Typography.Text type="secondary">请先登录，才能创建会话并执行任务。</Typography.Text>
              <Link to="/login">前往登录</Link>
            </Space>
          }
        />
      ) : null}

      <Drawer
        className="chat-history-drawer"
        title="历史对话"
        placement="left"
        width={320}
        open={isMobileLayout && historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
      >
        {renderHistorySessionList()}
      </Drawer>

      <div className="chat-layout">
        <Card className="app-card chat-panel chat-left-panel" title="历史对话" extra={<Tag color="blue">{recentSessions.length}</Tag>}>
          {renderHistorySessionList()}
        </Card>

        <Card
          className="app-card chat-panel"
          title={
            <Space wrap>
              <Typography.Text strong>{currentSessionTitle}</Typography.Text>
              {activePlanId ? <Tag color="processing">Plan #{activePlanId}</Tag> : <Tag>未执行</Tag>}
              {streaming || hasExecutingTurn ? (
                <Tag color="blue" icon={<SyncOutlined spin />}>
                  执行中
                </Tag>
              ) : null}
              {streamState === 'retrying' ? <Tag color="warning">重连中</Tag> : null}
              {streamState === 'polling' ? <Tag color="purple">自动同步中</Tag> : null}
              {recoveringSubmission ? <Tag color="gold">请求恢复中</Tag> : null}
            </Space>
          }
        >
          {(streamState !== 'idle' || recoveringSubmission) && (
            <Alert
              type={streamState === 'polling' || recoveringSubmission ? 'warning' : 'info'}
              showIcon
              banner
              message={recoveringSubmission ? '请求超时后自动同步中，请稍候。' : streamStatusHint}
              style={{ marginBottom: 12 }}
            />
          )}

          <div ref={chatScrollableRef} className="chat-scrollable chat-main-scrollable" onScroll={handleChatScroll}>
            {historyLoading ? (
              <div className="chat-empty-state">
                <Spin />
              </div>
            ) : messages.length === 0 ? (
              <div className="chat-empty-state">
                <Empty
                  description={sid ? '当前会话还没有消息，先输入你的目标。' : '开始一个新聊天，例如：生成一条小红书商品推荐文案'}
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              </div>
            ) : (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {messages.map(renderMessage)}
              </Space>
            )}
          </div>

          <div className="chat-composer">
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              <TextArea
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                rows={3}
                placeholder="输入你的任务目标，按 Enter 发送，Shift+Enter 换行"
                onPressEnter={(event) => {
                  if (event.shiftKey) {
                    return;
                  }
                  event.preventDefault();
                  void sendMessage();
                }}
              />
              <Collapse
                className="chat-advanced-panel"
                activeKey={advancedOpen ? ['advanced'] : []}
                onChange={(keys) => setAdvancedOpen(Array.isArray(keys) ? keys.includes('advanced') : keys === 'advanced')}
                items={[
                  {
                    key: 'advanced',
                    label: '高级参数（可选）',
                    children: (
                      <Space direction="vertical" style={{ width: '100%' }} size={8}>
                        <Space wrap style={{ width: '100%' }}>
                          <Input
                            style={{ width: 220 }}
                            value={scenario}
                            onChange={(event) => setScenario(event.target.value)}
                            placeholder="scenario，例如 CHAT_DEFAULT"
                          />
                          <Input
                            style={{ width: 220 }}
                            value={targetAgentKey}
                            onChange={(event) => setTargetAgentKey(event.target.value)}
                            placeholder="agentKey（留空自动选择）"
                          />
                          <Input
                            style={{ width: 260 }}
                            value={sessionTitleDraft}
                            onChange={(event) => setSessionTitleDraft(event.target.value)}
                            placeholder="会话标题（新会话时生效）"
                          />
                        </Space>
                        <TextArea
                          rows={3}
                          value={contextOverridesDraft}
                          onChange={(event) => setContextOverridesDraft(event.target.value)}
                          placeholder='contextOverrides JSON（对象），例如 {"topic":"SOP","priority":"high"}'
                        />
                        <TextArea
                          rows={3}
                          value={metaInfoDraft}
                          onChange={(event) => setMetaInfoDraft(event.target.value)}
                          placeholder='metaInfo JSON（对象），例如 {"source":"manual","traceTag":"demo"}'
                        />
                      </Space>
                    )
                  }
                ]}
              />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text type="secondary" className="chat-composer-hint">
                  中间态仅用于展示执行进展，最终回复以 answer.final 为准。
                </Typography.Text>
                <Button
                  icon={<SendOutlined />}
                  type="primary"
                  loading={sending}
                  disabled={!prompt.trim() || sending || recoveringSubmission}
                  onClick={() => void sendMessage()}
                >
                  {recoveringSubmission ? '同步中' : '发送'}
                </Button>
              </Space>
            </Space>
          </div>
        </Card>

        <Card className="app-card chat-panel chat-right-panel" title="执行进度">
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Text type="secondary">{streamStatusHint}</Typography.Text>
            <Space direction="vertical" size={8} style={{ width: '100%' }} className="chat-events-controls">
              <Space wrap>
                <Select
                  style={{ minWidth: 170 }}
                  value={eventTypeFilter}
                  options={eventTypeOptions}
                  onChange={(value) => setEventTypeFilter(value)}
                />
                <Select
                  style={{ minWidth: 170 }}
                  value={eventNodeFilter}
                  options={eventNodeOptions}
                  onChange={(value) => setEventNodeFilter(value)}
                />
                <Segmented
                  value={eventGroupMode}
                  options={[
                    { label: '时间序', value: 'TIME' },
                    { label: '按类型', value: 'TYPE' },
                    { label: '按节点', value: 'NODE' }
                  ]}
                  onChange={(value) => setEventGroupMode(value as EventGroupMode)}
                />
              </Space>
              <Space>
                <Switch size="small" checked={collapseEventDuplicates} onChange={setCollapseEventDuplicates} />
                <Typography.Text type="secondary">折叠连续重复事件</Typography.Text>
              </Space>
            </Space>
            {filteredProcessEvents.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="发送消息后，这里会实时显示执行进度" />
            ) : eventGroupMode === 'TIME' ? (
              <Timeline
                items={filteredProcessEvents.map((item) => ({
                  color: item.type === 'stream.error' ? 'red' : item.type === 'answer.final' ? 'green' : 'blue',
                  children: (
                    <Space direction="vertical" size={0}>
                      <Typography.Text>
                        {item.text}
                        {(item.mergedCount || 1) > 1 ? (
                          <Tag style={{ marginInlineStart: 8 }} color="default">
                            x{item.mergedCount}
                          </Tag>
                        ) : null}
                      </Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.type}
                        {item.nodeId ? ` · ${item.nodeId}` : ''}
                        {` · ${item.time}`}
                      </Typography.Text>
                    </Space>
                  )
                }))}
              />
            ) : (
              <Collapse
                className="chat-events-group-collapse"
                items={groupedProcessEvents.map((group) => ({
                  key: group.key,
                  label: group.title,
                  children: (
                    <Timeline
                      items={group.items.map((item) => ({
                        color: item.type === 'stream.error' ? 'red' : item.type === 'answer.final' ? 'green' : 'blue',
                        children: (
                          <Space direction="vertical" size={0}>
                            <Typography.Text>
                              {item.text}
                              {(item.mergedCount || 1) > 1 ? (
                                <Tag style={{ marginInlineStart: 8 }} color="default">
                                  x{item.mergedCount}
                                </Tag>
                              ) : null}
                            </Typography.Text>
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              {item.type}
                              {item.nodeId ? ` · ${item.nodeId}` : ''}
                              {` · ${item.time}`}
                            </Typography.Text>
                          </Space>
                        )
                      }))}
                    />
                  )
                }))}
              />
            )}
          </Space>
        </Card>
      </div>
    </div>
  );
};
