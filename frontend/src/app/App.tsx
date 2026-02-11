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
          borderRadius: 10,
          fontFamily: 'Space Grotesk, Noto Sans SC, sans-serif'
        }
      }}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  );
};
