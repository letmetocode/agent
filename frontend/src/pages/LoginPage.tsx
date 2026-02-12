import { Button, Card, Input, Space, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';

export const LoginPage = () => {
  const navigate = useNavigate();
  const setUserId = useSessionStore((state) => state.setUserId);
  const [value, setValue] = useState('dev-user');

  return (
    <div className="page-center">
      <Card className="glass-card" title="开发身份设置" style={{ width: 460 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Typography.Text type="secondary">开发态先手工输入 userId，后续可接入统一登录。</Typography.Text>
          <Input value={value} onChange={(event) => setValue(event.target.value)} placeholder="请输入 userId" />
          <Button
            type="primary"
            onClick={() => {
              if (!value.trim()) {
                return;
              }
              setUserId(value.trim());
              navigate('/workspace');
            }}
          >
            进入工作台
          </Button>
        </Space>
      </Card>
    </div>
  );
};
