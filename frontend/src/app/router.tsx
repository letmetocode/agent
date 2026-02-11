import { createBrowserRouter, Navigate } from 'react-router-dom';
import { LoginPage } from '@/pages/LoginPage';
import { SessionListPage } from '@/pages/SessionListPage';
import { ConversationPage } from '@/pages/ConversationPage';
import { WorkflowDraftPage } from '@/pages/WorkflowDraftPage';

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/sessions" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/sessions', element: <SessionListPage /> },
  { path: '/sessions/:sessionId', element: <ConversationPage /> },
  { path: '/workflows/drafts', element: <WorkflowDraftPage /> }
]);
