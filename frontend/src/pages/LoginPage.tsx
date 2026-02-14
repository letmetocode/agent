import { Button, Card, Input, Space, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';
import { agentApi } from '@/shared/api/agentApi';

const toErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err));

export const LoginPage = () => {
  const navigate = useNavigate();
  const setAuthSession = useSessionStore((state) => state.setAuthSession);
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!username.trim() || !password.trim()) {
      message.warning('请输入用户名和密码');
      return;
    }
    setSubmitting(true);
    try {
      const session = await agentApi.authLogin({ username: username.trim(), password });
      setAuthSession(session);
      message.success('登录成功');
      navigate('/workspace');
    } catch (err) {
      message.error(`登录失败: ${toErrorMessage(err)}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page-center">
      <Card className="glass-card" title="登录" style={{ width: 460 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Typography.Text type="secondary">输入本地账号密码后进入控制台。</Typography.Text>
          <Input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="用户名" />
          <Input.Password value={password} onChange={(event) => setPassword(event.target.value)} placeholder="密码" />
          <Button type="primary" loading={submitting} onClick={() => void submit()}>
            登录并进入工作台
          </Button>
        </Space>
      </Card>
    </div>
  );
};
