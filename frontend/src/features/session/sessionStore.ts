import { create } from 'zustand';
import { storage } from '@/shared/utils/storage';

export interface SessionBookmark {
  sessionId: number;
  title?: string;
  createdAt: string;
}

interface SessionState {
  userId: string;
  bookmarks: SessionBookmark[];
  setUserId: (userId: string) => void;
  addBookmark: (bookmark: SessionBookmark) => void;
}

const USER_KEY = 'agent:frontend:userId';
const BOOKMARKS_KEY = 'agent:frontend:sessionBookmarks';

export const useSessionStore = create<SessionState>((set, get) => ({
  userId: storage.get<string>(USER_KEY, ''),
  bookmarks: storage.get<SessionBookmark[]>(BOOKMARKS_KEY, []),
  setUserId: (userId) => {
    storage.set(USER_KEY, userId);
    set({ userId });
  },
  addBookmark: (bookmark) => {
    const exists = get().bookmarks.some((item) => item.sessionId === bookmark.sessionId);
    const next = exists ? get().bookmarks : [bookmark, ...get().bookmarks].slice(0, 50);
    storage.set(BOOKMARKS_KEY, next);
    set({ bookmarks: next });
  }
}));
