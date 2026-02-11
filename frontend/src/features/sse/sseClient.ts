import { storage } from '@/shared/utils/storage';
import type { PlanStreamEvent } from '@/shared/types/api';

const eventCursorKey = (planId: number) => `agent:frontend:sse:lastEventId:${planId}`;

export const getLastEventId = (planId: number): string => storage.get<string>(eventCursorKey(planId), '0');

export const setLastEventId = (planId: number, eventId?: string) => {
  if (!eventId) return;
  const prev = Number(getLastEventId(planId)) || 0;
  const next = Number(eventId) || 0;
  if (next > prev) {
    storage.set(eventCursorKey(planId), String(next));
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
