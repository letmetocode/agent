import { RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import { useEffect } from 'react';
import zhCN from 'antd/locale/zh_CN';
import { useSessionStore } from '@/features/session/sessionStore';
import { agentApi } from '@/shared/api/agentApi';
import { AriaLiveProvider } from '@/shared/a11y/AriaLiveProvider';
import { ShortcutHelpModal } from '@/shared/components/ShortcutHelpModal';
import { ShortcutProvider } from '@/shared/hotkeys/ShortcutProvider';
import { router } from './router';

export const App = () => {
  const authStatus = useSessionStore((state) => state.authStatus);
  const token = useSessionStore((state) => state.token);
  const setUserId = useSessionStore((state) => state.setUserId);
  const clearAuth = useSessionStore((state) => state.clearAuth);

  useEffect(() => {
    if (!token || (authStatus !== 'AUTHENTICATED' && authStatus !== 'EXPIRED')) {
      return;
    }
    let canceled = false;
    void (async () => {
      try {
        const profile = await agentApi.authMe();
        if (canceled) {
          return;
        }
        if (profile.userId) {
          setUserId(profile.userId);
        }
      } catch {
        if (canceled) {
          return;
        }
        clearAuth();
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.assign('/login');
        }
      }
    })();
    return () => {
      canceled = true;
    };
  }, [authStatus, clearAuth, setUserId, token]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#0f766e',
          colorSuccess: '#0e9f6e',
          colorWarning: '#d97706',
          colorError: '#dc2626',
          borderRadius: 10,
          fontSize: 14,
          lineHeight: 1.5715,
          fontFamily:
            "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans SC', sans-serif"
        },
        components: {
          Layout: {
            bodyBg: '#f5f7f9',
            headerBg: '#ffffff',
            siderBg: '#ffffff'
          },
          Card: {
            headerBg: '#ffffff'
          },
          Menu: {
            itemBg: 'transparent',
            itemSelectedBg: 'rgba(15, 118, 110, 0.1)',
            itemSelectedColor: '#115e59'
          }
        }
      }}
    >
      <AriaLiveProvider>
        <ShortcutProvider>
          <RouterProvider router={router} />
          <ShortcutHelpModal />
        </ShortcutProvider>
      </AriaLiveProvider>
    </ConfigProvider>
  );
};
