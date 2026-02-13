import { PlusOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Empty, Input, List, Space, Spin, Tag, Timeline, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useSessionStore } from '@/features/session/sessionStore';
import { openChatSseV3 } from '@/features/sse/sseClient';
import { agentApi } from '@/shared/api/agentApi';
import { CHAT_HTTP_TIMEOUT_MS } from '@/shared/api/http';
import type { ChatHistoryResponseV3, ChatStreamEventV3, SessionDetailDTO, SessionMessageDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';

const { TextArea } = Input;

interface ProcessEventItem {
  id: string;
  type: string;
  time: string;
  text: string;
}

const EXECUTING_TURN_STATUS = new Set(['PLANNING', 'EXECUTING']);

const safeTime = (value?: string) => {
  if (!value) {
    return '-';
  }
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return value;
  }
  return new Date(timestamp).toLocaleString();
};

const toChatMessageError = (err: unknown): string => {
  if (err instanceof Error) {
    if (err.message.includes('timeout')) {
      return `发送超时（>${Math.floor(CHAT_HTTP_TIMEOUT_MS / 1000)}秒），请稍后查看会话结果。`;
    }
    return err.message;
  }
  return String(err);
};

export const ConversationPage = () => {
  const navigate = useNavigate();
  const { sessionId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const sid = sessionId ? Number(sessionId) : undefined;

  const { userId, bookmarks, addBookmark } = useSessionStore();

  const [sessionListLoading, setSessionListLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [streaming, setStreaming] = useState(false);

  const [sessions, setSessions] = useState<SessionDetailDTO[]>([]);
  const [history, setHistory] = useState<ChatHistoryResponseV3 | null>(null);
  const [prompt, setPrompt] = useState('');
  const [activePlanId, setActivePlanId] = useState<number | undefined>();
  const [processEvents, setProcessEvents] = useState<ProcessEventItem[]>([]);

  const sseRef = useRef<EventSource | null>(null);
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8091';

  const messages = useMemo(() => {
    if (!history?.messages) {
      return [];
    }
    return [...history.messages].sort((a, b) => {
      const at = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bt = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      if (at !== bt) {
        return at - bt;
      }
      return (a.messageId || 0) - (b.messageId || 0);
    });
  }, [history?.messages]);

  const hasExecutingTurn = useMemo(() => {
    if (!history?.turns) {
      return false;
    }
    return history.turns.some((turn) => EXECUTING_TURN_STATUS.has((turn.status || '').toUpperCase()));
  }, [history?.turns]);

  const recentSessions = useMemo(() => {
    if (sessions.length > 0) {
      return sessions;
    }
    return bookmarks.map((item) => ({
      sessionId: item.sessionId,
      userId: userId || '-',
      title: item.title,
      active: true,
      createdAt: item.createdAt
    }));
  }, [bookmarks, sessions, userId]);

  const currentSessionTitle = history?.title || (sid ? `Session #${sid}` : '新聊天');

  const pushProcessEvent = useCallback((event: ChatStreamEventV3) => {
    const text = event.finalAnswer || event.message || event.taskStatus || event.type;
    const row: ProcessEventItem = {
      id: `${event.type}-${event.eventId || Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      type: event.type,
      text,
      time: new Date().toLocaleTimeString()
    };
    setProcessEvents((prev) => [row, ...prev].slice(0, 120));
  }, []);

  const stopStream = useCallback(() => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
    setStreaming(false);
  }, []);

  const connectStream = useCallback(
    (targetSessionId: number, targetPlanId: number) => {
      stopStream();
      setStreaming(true);
      sseRef.current = openChatSseV3(
        targetSessionId,
        targetPlanId,
        baseUrl,
        (event) => {
          if (event.type !== 'stream.heartbeat') {
            pushProcessEvent(event);
          }
          if (event.type === 'answer.final') {
            setStreaming(false);
            void agentApi
              .getChatHistoryV3(targetSessionId)
              .then((data) => setHistory(data))
              .catch(() => undefined);
          }
          if (event.type === 'stream.error') {
            setStreaming(false);
          }
        },
        () => {
          setStreaming(false);
          pushProcessEvent({ type: 'stream.error', message: '流连接中断，稍后可重连。' });
        }
      );
    },
    [baseUrl, pushProcessEvent, stopStream]
  );

  const loadSessions = useCallback(async () => {
    if (!userId) {
      setSessions([]);
      return;
    }
    setSessionListLoading(true);
    try {
      const pageData = await agentApi.getSessionsList({ userId, page: 1, size: 50 });
      setSessions(pageData.items || []);
    } catch (err) {
      message.error(`加载会话失败: ${toChatMessageError(err)}`);
    } finally {
      setSessionListLoading(false);
    }
  }, [userId]);

  const loadHistory = useCallback(
    async (targetSessionId: number) => {
      setHistoryLoading(true);
      try {
        const data = await agentApi.getChatHistoryV3(targetSessionId);
        setHistory(data);

        const queryPlanId = Number(searchParams.get('planId')) || undefined;
        const runningPlanId = data.turns
          .filter((turn) => EXECUTING_TURN_STATUS.has((turn.status || '').toUpperCase()) && turn.planId)
          .sort((a, b) => (b.turnId || 0) - (a.turnId || 0))[0]?.planId;

        const nextPlanId = queryPlanId || runningPlanId || data.latestPlanId || undefined;
        setActivePlanId(nextPlanId);

        if (nextPlanId && runningPlanId && nextPlanId === runningPlanId) {
          connectStream(targetSessionId, nextPlanId);
        }
      } catch (err) {
        message.error(`加载会话历史失败: ${toChatMessageError(err)}`);
      } finally {
        setHistoryLoading(false);
      }
    },
    [connectStream, searchParams]
  );

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (!sid || Number.isNaN(sid)) {
      setHistory(null);
      setActivePlanId(undefined);
      stopStream();
      return;
    }
    void loadHistory(sid);
  }, [loadHistory, sid, stopStream]);

  useEffect(
    () => () => {
      stopStream();
    },
    [stopStream]
  );

  const sendMessage = async () => {
    if (!userId) {
      message.warning('请先设置 userId');
      navigate('/login');
      return;
    }
    if (!prompt.trim()) {
      return;
    }

    const content = prompt.trim();
    setSending(true);
    try {
      const response = await agentApi.submitChatMessageV3({
        userId,
        sessionId: sid,
        message: content,
        scenario: 'CHAT_DEFAULT',
        metaInfo: {
          source: 'conversation-page',
          entry: sid ? 'continue-chat' : 'new-chat'
        }
      });

      setPrompt('');
      setActivePlanId(response.planId);
      pushProcessEvent({
        type: 'message.accepted',
        planId: response.planId,
        sessionId: response.sessionId,
        message: `已提交，Plan #${response.planId}`
      });

      addBookmark({
        sessionId: response.sessionId,
        title: response.sessionTitle || content,
        createdAt: new Date().toISOString()
      });

      if (!sid || sid !== response.sessionId) {
        navigate(`/sessions/${response.sessionId}?planId=${response.planId}`, { replace: true });
      } else {
        setSearchParams({ planId: String(response.planId) }, { replace: true });
      }

      connectStream(response.sessionId, response.planId);
      void Promise.all([loadSessions(), agentApi.getChatHistoryV3(response.sessionId).then(setHistory)]);
    } catch (err) {
      message.error(`发送失败: ${toChatMessageError(err)}`);
    } finally {
      setSending(false);
    }
  };

  const renderMessage = (item: SessionMessageDTO) => {
    const role = (item.role || '').toUpperCase();
    const isUser = role === 'USER';
    const bubbleClass = isUser ? 'chat-message-bubble user' : 'chat-message-bubble assistant';

    return (
      <div key={item.messageId} className={`chat-message-row ${isUser ? 'user' : 'assistant'}`}>
        <div className={bubbleClass}>
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Typography.Text strong>{isUser ? '你' : role === 'ASSISTANT' ? 'Agent' : role}</Typography.Text>
            <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Typography.Paragraph>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {safeTime(item.createdAt)}
            </Typography.Text>
          </Space>
        </div>
      </div>
    );
  };

  return (
    <div className="page-container">
      <PageHeader
        title="对话与执行"
        description="ChatGPT 风格入口：点击新聊天直接输入目标；高级能力保留在执行侧栏。"
        primaryActionText="新聊天"
        onPrimaryAction={() => navigate('/sessions')}
        extra={<Typography.Text type="secondary">当前 userId：{userId || '未设置'}</Typography.Text>}
      />

      {!userId ? (
        <Alert
          type="warning"
          showIcon
          message="尚未设置开发用户"
          description={
            <Space>
              <Typography.Text type="secondary">请先设置 userId，才能创建会话并执行任务。</Typography.Text>
              <Link to="/login">前往设置</Link>
            </Space>
          }
        />
      ) : null}

      <div className="chat-layout">
        <Card className="app-card chat-panel chat-left-panel" title="历史对话" extra={<Tag color="blue">{recentSessions.length}</Tag>}>
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Button type="primary" icon={<PlusOutlined />} block onClick={() => navigate('/sessions')}>
              新聊天
            </Button>
            {sessionListLoading ? <Spin /> : null}
            <List
              size="small"
              dataSource={recentSessions}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史会话" /> }}
              renderItem={(item) => (
                <List.Item
                  className={`conversation-plan-item ${sid === item.sessionId ? 'active' : ''}`}
                  onClick={() => navigate(`/sessions/${item.sessionId}`)}
                >
                  <List.Item.Meta
                    title={item.title || `Session #${item.sessionId}`}
                    description={`#${item.sessionId} · ${safeTime(item.createdAt)}`}
                  />
                </List.Item>
              )}
            />
          </Space>
        </Card>

        <Card
          className="app-card chat-panel"
          title={
            <Space>
              <Typography.Text strong>{currentSessionTitle}</Typography.Text>
              {activePlanId ? <Tag color="processing">Plan #{activePlanId}</Tag> : <Tag>未执行</Tag>}
              {streaming || hasExecutingTurn ? (
                <Tag color="blue" icon={<SyncOutlined spin />}>
                  执行中
                </Tag>
              ) : null}
            </Space>
          }
        >
          <div className="chat-scrollable" style={{ flex: 1, paddingRight: 8 }}>
            {historyLoading ? (
              <Spin />
            ) : messages.length === 0 ? (
              <Empty
                description={sid ? '当前会话还没有消息，先输入你的目标。' : '开始一个新聊天，例如：生成一条小红书商品推荐文案'}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ) : (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                {messages.map(renderMessage)}
              </Space>
            )}
          </div>

          <div className="chat-composer">
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              <TextArea
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                rows={4}
                placeholder="输入你的任务目标，按 Enter 发送，Shift+Enter 换行"
                onPressEnter={(event) => {
                  if (event.shiftKey) {
                    return;
                  }
                  event.preventDefault();
                  void sendMessage();
                }}
              />
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Typography.Text type="secondary" className="chat-composer-hint">
                  中间态仅用于展示执行进展，最终回复以 answer.final 为准。
                </Typography.Text>
                <Button icon={<SendOutlined />} type="primary" loading={sending} onClick={() => void sendMessage()}>
                  发送
                </Button>
              </Space>
            </Space>
          </div>
        </Card>

        <Card className="app-card chat-panel chat-right-panel" title="执行进度">
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Text type="secondary">仅展示流式执行状态与终态收敛，不把中间态落为最终回复。</Typography.Text>
            {processEvents.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="发送消息后，这里会实时显示执行进度" />
            ) : (
              <Timeline
                items={processEvents.map((item) => ({
                  color: item.type === 'stream.error' ? 'red' : item.type === 'answer.final' ? 'green' : 'blue',
                  children: (
                    <Space direction="vertical" size={0}>
                      <Typography.Text>{item.text}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {item.type} · {item.time}
                      </Typography.Text>
                    </Space>
                  )
                }))}
              />
            )}
          </Space>
        </Card>
      </div>
    </div>
  );
};
