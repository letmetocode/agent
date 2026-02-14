import { create } from 'zustand';
import type { AuthLoginResponseDTO } from '@/shared/types/api';
import { storage } from '@/shared/utils/storage';

export type AuthStatus = 'UNAUTHENTICATED' | 'AUTHENTICATED' | 'EXPIRED';

export interface SessionBookmark {
  sessionId: number;
  title?: string;
  createdAt: string;
}

interface SessionState {
  userId: string;
  displayName: string;
  token: string;
  expiresAt?: string;
  lastLoginAt?: string;
  authStatus: AuthStatus;
  bookmarks: SessionBookmark[];
  setUserId: (userId: string) => void;
  setAuthSession: (session: AuthLoginResponseDTO) => void;
  clearAuth: () => void;
  addBookmark: (bookmark: SessionBookmark) => void;
}

export const SESSION_USER_KEY = 'agent:frontend:userId';
export const SESSION_DISPLAY_NAME_KEY = 'agent:frontend:displayName';
export const SESSION_TOKEN_KEY = 'agent:frontend:authToken';
export const SESSION_EXPIRES_AT_KEY = 'agent:frontend:authExpiresAt';
export const SESSION_LAST_LOGIN_AT_KEY = 'agent:frontend:lastLoginAt';
const BOOKMARKS_KEY = 'agent:frontend:sessionBookmarks';

const resolveAuthStatus = (token: string, expiresAt?: string): AuthStatus => {
  if (!token) {
    return 'UNAUTHENTICATED';
  }
  if (!expiresAt) {
    return 'AUTHENTICATED';
  }
  const epoch = Date.parse(expiresAt);
  if (Number.isFinite(epoch) && epoch <= Date.now()) {
    return 'EXPIRED';
  }
  return 'AUTHENTICATED';
};

const clearAuthStorage = () => {
  storage.remove(SESSION_TOKEN_KEY);
  storage.remove(SESSION_EXPIRES_AT_KEY);
  storage.remove(SESSION_LAST_LOGIN_AT_KEY);
  storage.remove(SESSION_DISPLAY_NAME_KEY);
  storage.remove(SESSION_USER_KEY);
};

export const useSessionStore = create<SessionState>((set, get) => {
  const userId = storage.get<string>(SESSION_USER_KEY, '');
  const displayName = storage.get<string>(SESSION_DISPLAY_NAME_KEY, '');
  const token = storage.get<string>(SESSION_TOKEN_KEY, '');
  const expiresAt = storage.get<string | undefined>(SESSION_EXPIRES_AT_KEY, undefined);
  const lastLoginAt = storage.get<string | undefined>(SESSION_LAST_LOGIN_AT_KEY, undefined);

  return {
    userId,
    displayName,
    token,
    expiresAt,
    lastLoginAt,
    authStatus: resolveAuthStatus(token, expiresAt),
    bookmarks: storage.get<SessionBookmark[]>(BOOKMARKS_KEY, []),
    setUserId: (nextUserId) => {
      storage.set(SESSION_USER_KEY, nextUserId);
      set({ userId: nextUserId });
    },
    setAuthSession: (session) => {
      const nextUserId = (session.userId || '').trim();
      const nextDisplayName = (session.displayName || nextUserId).trim();
      const nextToken = (session.token || '').trim();
      const nextExpiresAt = session.expiresAt || undefined;
      const status = resolveAuthStatus(nextToken, nextExpiresAt);
      const now = new Date().toISOString();

      storage.set(SESSION_USER_KEY, nextUserId);
      storage.set(SESSION_DISPLAY_NAME_KEY, nextDisplayName);
      storage.set(SESSION_TOKEN_KEY, nextToken);
      if (nextExpiresAt) {
        storage.set(SESSION_EXPIRES_AT_KEY, nextExpiresAt);
      } else {
        storage.remove(SESSION_EXPIRES_AT_KEY);
      }
      storage.set(SESSION_LAST_LOGIN_AT_KEY, now);

      set({
        userId: nextUserId,
        displayName: nextDisplayName,
        token: nextToken,
        expiresAt: nextExpiresAt,
        lastLoginAt: now,
        authStatus: status
      });
    },
    clearAuth: () => {
      clearAuthStorage();
      set({
        userId: '',
        displayName: '',
        token: '',
        expiresAt: undefined,
        lastLoginAt: undefined,
        authStatus: 'UNAUTHENTICATED'
      });
    },
    addBookmark: (bookmark) => {
      const exists = get().bookmarks.some((item) => item.sessionId === bookmark.sessionId);
      const next = exists ? get().bookmarks : [bookmark, ...get().bookmarks].slice(0, 50);
      storage.set(BOOKMARKS_KEY, next);
      set({ bookmarks: next });
    }
  };
});
