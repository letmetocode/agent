import { Button, Card, Input, Space, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';

export const LoginPage = () => {
  const navigate = useNavigate();
  const setUserId = useSessionStore((s) => s.setUserId);
  const [value, setValue] = useState('dev-user');

  return (
    <div className="page-center">
      <Card className="glass-card" title="开发身份设置" style={{ width: 420 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">开发态先手工输入 userId，后续可接入统一登录。</Typography.Text>
          <Input value={value} onChange={(e) => setValue(e.target.value)} placeholder="请输入 userId" />
          <Button
            type="primary"
            onClick={() => {
              if (!value.trim()) return;
              setUserId(value.trim());
              navigate('/sessions');
            }}
          >
            进入会话列表
          </Button>
        </Space>
      </Card>
    </div>
  );
};
