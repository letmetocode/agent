import { RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { router } from './router';

export const App = () => {
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
      <RouterProvider router={router} />
    </ConfigProvider>
  );
};
