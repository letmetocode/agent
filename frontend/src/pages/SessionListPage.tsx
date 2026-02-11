import { Button, Card, Input, List, Space, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';
import { agentApi } from '@/shared/api/agentApi';

export const SessionListPage = () => {
  const navigate = useNavigate();
  const { userId, bookmarks, addBookmark } = useSessionStore();
  const [title, setTitle] = useState('');
  const [manualSessionId, setManualSessionId] = useState('');
  const [creating, setCreating] = useState(false);

  const createSession = async () => {
    if (!userId) {
      message.warning('请先设置 userId');
      navigate('/login');
      return;
    }
    setCreating(true);
    try {
      const session = await agentApi.createSession({ userId, title: title || undefined });
      addBookmark({ sessionId: session.sessionId, title: session.title, createdAt: new Date().toISOString() });
      message.success(`会话已创建 #${session.sessionId}`);
      navigate(`/sessions/${session.sessionId}`);
    } catch (err) {
      message.error(`创建会话失败: ${String(err)}`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="page-shell">
      <Card
        className="glass-card"
        title="会话工作台"
        extra={
          <Space>
            <Typography.Text type="secondary">userId: {userId || '未设置'}</Typography.Text>
            <Button onClick={() => navigate('/workflows/drafts')}>Workflow治理</Button>
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space.Compact style={{ width: '100%' }}>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="新会话标题（可选）" />
            <Button type="primary" loading={creating} onClick={createSession}>
              创建并进入
            </Button>
          </Space.Compact>

          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={manualSessionId}
              onChange={(e) => setManualSessionId(e.target.value)}
              placeholder="输入 sessionId 进入已有会话"
            />
            <Button
              onClick={() => {
                if (!manualSessionId.trim()) return;
                navigate(`/sessions/${manualSessionId.trim()}`);
              }}
            >
              进入
            </Button>
          </Space.Compact>

          <List
            header="最近会话"
            dataSource={bookmarks}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button key="open" type="link" onClick={() => navigate(`/sessions/${item.sessionId}`)}>
                    打开
                  </Button>
                ]}
              >
                <List.Item.Meta
                  title={item.title || `Session #${item.sessionId}`}
                  description={`ID: ${item.sessionId} · ${new Date(item.createdAt).toLocaleString()}`}
                />
              </List.Item>
            )}
          />
        </Space>
      </Card>
    </div>
  );
};
