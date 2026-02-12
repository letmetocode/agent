import { Spin } from 'antd';
import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '@/shared/layout/AppShell';

const LoginPage = lazy(() => import('@/pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const WorkspacePage = lazy(() => import('@/pages/WorkspacePage').then((module) => ({ default: module.WorkspacePage })));
const SessionListPage = lazy(() => import('@/pages/SessionListPage').then((module) => ({ default: module.SessionListPage })));
const ConversationPage = lazy(() => import('@/pages/ConversationPage').then((module) => ({ default: module.ConversationPage })));
const TasksPage = lazy(() => import('@/pages/TasksPage').then((module) => ({ default: module.TasksPage })));
const TaskDetailPage = lazy(() => import('@/pages/TaskDetailPage').then((module) => ({ default: module.TaskDetailPage })));
const ToolsPage = lazy(() => import('@/pages/ToolsPage').then((module) => ({ default: module.ToolsPage })));
const KnowledgePage = lazy(() => import('@/pages/KnowledgePage').then((module) => ({ default: module.KnowledgePage })));
const KnowledgeDetailPage = lazy(() => import('@/pages/KnowledgeDetailPage').then((module) => ({ default: module.KnowledgeDetailPage })));
const ObservabilityOverviewPage = lazy(() =>
  import('@/pages/ObservabilityOverviewPage').then((module) => ({ default: module.ObservabilityOverviewPage }))
);
const LogsPage = lazy(() => import('@/pages/LogsPage').then((module) => ({ default: module.LogsPage })));
const ProfileSettingsPage = lazy(() => import('@/pages/ProfileSettingsPage').then((module) => ({ default: module.ProfileSettingsPage })));
const SystemSettingsPage = lazy(() => import('@/pages/SystemSettingsPage').then((module) => ({ default: module.SystemSettingsPage })));
const AccessSettingsPage = lazy(() => import('@/pages/AccessSettingsPage').then((module) => ({ default: module.AccessSettingsPage })));
const WorkflowDraftPage = lazy(() => import('@/pages/WorkflowDraftPage').then((module) => ({ default: module.WorkflowDraftPage })));

const withSuspense = (node: ReactNode) => (
  <Suspense
    fallback={
      <div className="page-center">
        <Spin />
      </div>
    }
  >
    {node}
  </Suspense>
);

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/workspace" replace /> },
  { path: '/login', element: withSuspense(<LoginPage />) },

  {
    path: '/',
    element: <AppShell />,
    children: [
      { path: '/workspace', element: withSuspense(<WorkspacePage />) },
      { path: '/sessions', element: withSuspense(<SessionListPage />) },
      { path: '/sessions/:sessionId', element: withSuspense(<ConversationPage />) },

      { path: '/tasks', element: withSuspense(<TasksPage />) },
      { path: '/tasks/:taskId', element: withSuspense(<TaskDetailPage />) },

      { path: '/assets/tools', element: withSuspense(<ToolsPage />) },
      { path: '/assets/knowledge', element: withSuspense(<KnowledgePage />) },
      { path: '/assets/knowledge/:kbId', element: withSuspense(<KnowledgeDetailPage />) },

      { path: '/observability/overview', element: withSuspense(<ObservabilityOverviewPage />) },
      { path: '/observability/logs', element: withSuspense(<LogsPage />) },

      { path: '/settings/profile', element: withSuspense(<ProfileSettingsPage />) },
      { path: '/settings/system', element: withSuspense(<SystemSettingsPage />) },
      { path: '/settings/access', element: withSuspense(<AccessSettingsPage />) },

      { path: '/workflows/drafts', element: withSuspense(<WorkflowDraftPage />) }
    ]
  },

  { path: '*', element: <Navigate to="/workspace" replace /> }
]);
