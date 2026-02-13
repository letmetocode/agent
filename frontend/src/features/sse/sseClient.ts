import { storage } from '@/shared/utils/storage';
import type { ChatStreamEventV3, PlanStreamEvent } from '@/shared/types/api';

const eventCursorKey = (planId: number) => `agent:frontend:sse:lastEventId:${planId}`;
const chatEventCursorKey = (sessionId: number, planId: number) =>
  `agent:frontend:sse:v3:lastEventId:${sessionId}:${planId}`;

export const getLastEventId = (planId: number): string => storage.get<string>(eventCursorKey(planId), '0');

export const setLastEventId = (planId: number, eventId?: string) => {
  if (!eventId) return;
  const prev = Number(getLastEventId(planId)) || 0;
  const next = Number(eventId) || 0;
  if (next > prev) {
    storage.set(eventCursorKey(planId), String(next));
  }
};

const getChatLastEventId = (sessionId: number, planId: number): string =>
  storage.get<string>(chatEventCursorKey(sessionId, planId), '0');

const setChatLastEventId = (sessionId: number, planId: number, eventId?: string) => {
  if (!eventId) return;
  const prev = Number(getChatLastEventId(sessionId, planId)) || 0;
  const next = Number(eventId) || 0;
  if (next > prev) {
    storage.set(chatEventCursorKey(sessionId, planId), String(next));
  }
};

export const openPlanSse = (
  planId: number,
  baseUrl: string,
  onEvent: (event: PlanStreamEvent) => void,
  onError?: () => void
) => {
  const lastEventId = getLastEventId(planId);
  const endpoint = `${baseUrl.replace(/\/$/, '')}/api/plans/${planId}/stream?lastEventId=${encodeURIComponent(lastEventId)}`;
  const source = new EventSource(endpoint);

  const handler = (event: MessageEvent<string>) => {
    setLastEventId(planId, event.lastEventId);
    let payload: unknown = event.data;
    try {
      payload = event.data ? JSON.parse(event.data) : undefined;
    } catch {
      payload = event.data;
    }
    onEvent({ event: event.type, id: event.lastEventId, data: payload });
  };

  source.onmessage = handler;
  const events = ['StreamReady', 'PlanSnapshot', 'TaskStarted', 'TaskLog', 'TaskCompleted', 'PlanFinished', 'Heartbeat'];
  events.forEach((name) => source.addEventListener(name, handler as EventListener));
  source.onerror = () => {
    onError?.();
  };

  return source;
};

export const openChatSseV3 = (
  sessionId: number,
  planId: number,
  baseUrl: string,
  onEvent: (event: ChatStreamEventV3) => void,
  onError?: () => void
) => {
  const lastEventId = getChatLastEventId(sessionId, planId);
  const endpoint = `${baseUrl.replace(/\/$/, '')}/api/v3/chat/sessions/${sessionId}/stream?planId=${planId}&lastEventId=${encodeURIComponent(lastEventId)}`;
  const source = new EventSource(endpoint);

  const handler = (event: MessageEvent<string>) => {
    setChatLastEventId(sessionId, planId, event.lastEventId);
    let payload: unknown = event.data;
    try {
      payload = event.data ? JSON.parse(event.data) : undefined;
    } catch {
      payload = event.data;
    }

    const typedPayload =
      payload && typeof payload === 'object'
        ? ({ ...(payload as Record<string, unknown>), type: (payload as { type?: string }).type || event.type } as ChatStreamEventV3)
        : ({ type: event.type, message: String(payload || '') } as ChatStreamEventV3);
    onEvent(typedPayload);
  };

  source.onmessage = handler;
  const events = [
    'message.accepted',
    'planning.started',
    'task.progress',
    'task.completed',
    'answer.finalizing',
    'answer.final',
    'stream.heartbeat',
    'stream.error'
  ];
  events.forEach((name) => source.addEventListener(name, handler as EventListener));
  source.onerror = () => {
    onError?.();
  };

  return source;
};
