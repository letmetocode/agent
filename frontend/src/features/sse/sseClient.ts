import { SESSION_TOKEN_KEY } from '@/features/session/sessionStore';
import { storage } from '@/shared/utils/storage';
import type { ChatStreamEventV3 } from '@/shared/types/api';

const chatEventCursorKey = (sessionId: number, planId: number) =>
  `agent:frontend:sse:v3:lastEventId:${sessionId}:${planId}`;

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

export const openChatSseV3 = (
  sessionId: number,
  planId: number,
  baseUrl: string,
  onEvent: (event: ChatStreamEventV3) => void,
  onError?: (source: EventSource) => void
) => {
  const lastEventId = getChatLastEventId(sessionId, planId);
  const token = storage.get<string>(SESSION_TOKEN_KEY, '');
  const accessToken = (token || '').trim();
  const endpoint = `${baseUrl.replace(/\/$/, '')}/api/v3/chat/sessions/${sessionId}/stream?planId=${planId}&lastEventId=${encodeURIComponent(lastEventId)}${accessToken ? `&accessToken=${encodeURIComponent(accessToken)}` : ''}`;
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
    'stream.completed',
    'stream.heartbeat',
    'stream.error'
  ];
  events.forEach((name) => source.addEventListener(name, handler as EventListener));
  source.onerror = () => {
    onError?.(source);
  };

  return source;
};
