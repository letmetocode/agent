import { CompassOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Divider,
  Form,
  Input,
  List,
  Radio,
  Row,
  Select,
  Space,
  Steps,
  Typography,
  message
} from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';
import { agentApi } from '@/shared/api/agentApi';
import type { ActiveAgentDTO, SessionDetailDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

interface LaunchFormValues {
  goal: string;
  mode: 'use-existing' | 'quick-create';
  title?: string;
  agentKey?: string;
  quickAgentName?: string;
  quickAgentKey?: string;
  modelProvider?: string;
  modelName?: string;
}

const resolveDefaultAgentKey = (agents: ActiveAgentDTO[]) => {
  if (!agents || agents.length === 0) {
    return undefined;
  }
  const assistant = agents.find((item) => item.agentKey?.toLowerCase() === 'assistant');
  return assistant?.agentKey || agents[0].agentKey;
};

export const SessionListPage = () => {
  const navigate = useNavigate();
  const { userId, bookmarks, addBookmark } = useSessionStore();
  const [manualSessionId, setManualSessionId] = useState('');
  const [creating, setCreating] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [sessions, setSessions] = useState<SessionDetailDTO[]>([]);
  const [activeAgents, setActiveAgents] = useState<ActiveAgentDTO[]>([]);
  const [listKeyword, setListKeyword] = useState('');
  const [form] = Form.useForm<LaunchFormValues>();

  const launchMode = Form.useWatch('mode', form) || 'use-existing';
  const selectedAgentKey = Form.useWatch('agentKey', form) || '';
  const goalValue = Form.useWatch('goal', form) || '';

  const defaultAgentKey = useMemo(() => resolveDefaultAgentKey(activeAgents), [activeAgents]);
  const defaultAgent = useMemo(
    () => activeAgents.find((item) => item.agentKey === defaultAgentKey),
    [activeAgents, defaultAgentKey]
  );

  const canStartWithAgent = launchMode === 'quick-create' ? true : Boolean(selectedAgentKey.trim() || defaultAgentKey);
  const canStartChat = Boolean(userId && goalValue.trim() && canStartWithAgent);

  const loadSessions = useCallback(
    async (keyword?: string) => {
      if (!userId) {
        setSessions([]);
        return;
      }
      setListLoading(true);
      try {
        const pageData = await agentApi.getSessionsList({
          userId,
          page: 1,
          size: 50,
          keyword: keyword?.trim() || undefined
        });
        setSessions(pageData.items || []);
      } catch (err) {
        message.error(`加载会话列表失败: ${err instanceof Error ? err.message : String(err)}`);
      } finally {
        setListLoading(false);
      }
    },
    [userId]
  );

  const loadActiveAgents = useCallback(async () => {
    setAgentsLoading(true);
    try {
      const data = await agentApi.getActiveAgentsV2();
      const rows = data || [];
      setActiveAgents(rows);
      const preferredKey = resolveDefaultAgentKey(rows);
      if (preferredKey && !form.getFieldValue('agentKey')) {
        form.setFieldValue('agentKey', preferredKey);
      }
    } catch (err) {
      message.warning(`加载可用 Agent 失败: ${err instanceof Error ? err.message : String(err)}`);
      setActiveAgents([]);
    } finally {
      setAgentsLoading(false);
    }
  }, [form]);

  useEffect(() => {
    void loadSessions();
    void loadActiveAgents();
  }, [loadSessions, loadActiveAgents]);

  const createSession = async (values: LaunchFormValues) => {
    if (!userId) {
      message.warning('请先设置 userId');
      navigate('/login');
      return;
    }

    const goal = values.goal.trim();
    if (!goal) {
      message.warning('请输入本次对话目标');
      return;
    }

    setCreating(true);
    try {
      const mode = values.mode || 'use-existing';
      let agentKey = values.agentKey?.trim();
      if (mode === 'quick-create') {
        const quickName = values.quickAgentName?.trim();
        if (!quickName) {
          message.warning('请填写新 Agent 名称');
          return;
        }
        const created = await agentApi.createAgentV2({
          agentKey: values.quickAgentKey?.trim() || undefined,
          name: quickName,
          modelProvider: (values.modelProvider || 'openai').trim(),
          modelName: (values.modelName || 'gpt-4o-mini').trim(),
          active: true,
          modelOptions: { temperature: 0.2 },
          advisorConfig: {}
        });
        agentKey = created.agentKey;
        await loadActiveAgents();
      }

      if (mode === 'use-existing' && !agentKey) {
        agentKey = defaultAgentKey;
      }

      if (!agentKey) {
        setAdvancedOpen(true);
        message.warning('暂无可用 Agent，请在高级设置中快速创建 Agent');
        return;
      }

      const title = values.title?.trim() || goal;
      const session = await agentApi.createSessionV2({
        userId,
        title: title || undefined,
        agentKey,
        scenario: 'CONSOLE_LAUNCH',
        metaInfo: {
          launchMode: mode,
          source: 'session-list',
          entry: 'new-chat'
        }
      });

      const turn = await agentApi.createTurnV2(session.sessionId, {
        message: goal,
        contextOverrides: {
          launchMode: mode,
          launchFrom: 'session-list',
          entry: 'new-chat'
        }
      });

      addBookmark({ sessionId: session.sessionId, title: session.title || title, createdAt: new Date().toISOString() });
      message.success(`会话已创建并触发执行：Session #${session.sessionId} / Plan #${turn.planId}`);
      await loadSessions();
      navigate(`/sessions/${session.sessionId}?planId=${turn.planId}`);
    } catch (err) {
      message.error(`新聊天失败: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setCreating(false);
    }
  };

  const listSource =
    sessions.length > 0
      ? sessions
      : bookmarks.map((item) => ({
          sessionId: item.sessionId,
          userId: userId || '-',
          title: item.title,
          active: true,
          createdAt: item.createdAt
        }));

  const agentOptions = useMemo(
    () =>
      activeAgents.map((item) => ({
        label: `${item.name} (${item.agentKey})`,
        value: item.agentKey
      })),
    [activeAgents]
  );

  return (
    <div className="page-container">
      <PageHeader
        title="对话与执行"
        description="新聊天风格入口：输入目标后直接开始执行；高级设置按需展开。"
        primaryActionText="Workflow 治理"
        onPrimaryAction={() => navigate('/workflows/drafts')}
        extra={<Typography.Text type="secondary">当前 userId：{userId || '未设置'}</Typography.Text>}
      />

      {!userId ? (
        <Alert
          type="warning"
          showIcon
          message="尚未设置开发用户"
          description={
            <Space>
              <Typography.Text type="secondary">请先设置 userId，才能创建会话并触发执行。</Typography.Text>
              <Button type="link" onClick={() => navigate('/login')}>
                前往设置
              </Button>
            </Space>
          }
        />
      ) : null}

      <Row gutter={[16, 16]} className="page-section">
        <Col xs={24} xl={16}>
          <Card className="app-card" title="新聊天">
            <Form
              form={form}
              layout="vertical"
              initialValues={{ mode: 'use-existing', modelProvider: 'openai', modelName: 'gpt-4o-mini' }}
              onFinish={(values) => void createSession(values)}
            >
              <Form.Item label="你的目标" name="goal" rules={[{ required: true, message: '请输入本次对话目标' }]}>
                <Input.TextArea
                  rows={4}
                  placeholder="例如：生成一条小红书商品推荐文案，商品名为倍轻松back2f"
                />
              </Form.Item>

              <Space style={{ marginBottom: 12 }}>
                <Typography.Text type="secondary">
                  {agentsLoading
                    ? '正在加载默认 Agent...'
                    : defaultAgent
                    ? `默认 Agent：${defaultAgent.name} (${defaultAgent.agentKey})`
                    : '暂无默认 Agent'}
                </Typography.Text>
              </Space>

              {userId && launchMode === 'use-existing' && !agentsLoading && !selectedAgentKey.trim() && !defaultAgentKey ? (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="暂无可用 Agent"
                  description={
                    <Space>
                      <Typography.Text type="secondary">可在高级设置中快速创建 Agent 后再开始对话。</Typography.Text>
                      <Button type="link" onClick={() => setAdvancedOpen(true)}>
                        展开高级设置
                      </Button>
                    </Space>
                  }
                />
              ) : null}

              <Space wrap>
                <Button icon={<SendOutlined />} type="primary" htmlType="submit" loading={creating} disabled={!canStartChat}>
                  开始对话
                </Button>
                <Button
                  icon={<CompassOutlined />}
                  onClick={() => document.getElementById('recent-sessions')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                >
                  查看最近会话
                </Button>
                <Button icon={<ReloadOutlined />} onClick={() => void loadActiveAgents()} loading={agentsLoading}>
                  刷新 Agent
                </Button>
              </Space>

              <div style={{ marginTop: 16 }}>
                <Collapse
                  size="small"
                  activeKey={advancedOpen ? ['advanced'] : []}
                  onChange={(keys) => {
                    const opened = Array.isArray(keys) ? keys.includes('advanced') : keys === 'advanced';
                    setAdvancedOpen(opened);
                  }}
                  items={[
                    {
                      key: 'advanced',
                      label: '高级设置（可选）',
                      children: (
                        <>
                          <Row gutter={12}>
                            <Col xs={24} md={12}>
                              <Form.Item label="创建方式" name="mode">
                                <Radio.Group optionType="button" buttonStyle="solid">
                                  <Radio.Button value="use-existing">使用现有 Agent</Radio.Button>
                                  <Radio.Button value="quick-create">快速创建 Agent</Radio.Button>
                                </Radio.Group>
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="会话标题（可选）" name="title">
                                <Input placeholder="不填则默认使用目标文案" />
                              </Form.Item>
                            </Col>
                          </Row>

                          {launchMode === 'use-existing' ? (
                            <Form.Item label="覆盖默认 Agent（可选）" name="agentKey">
                              <Select
                                loading={agentsLoading}
                                placeholder="不选择时使用默认 Agent"
                                options={agentOptions}
                                allowClear
                                notFoundContent={agentsLoading ? '加载中...' : '暂无可用 Agent'}
                              />
                            </Form.Item>
                          ) : (
                            <Row gutter={12}>
                              <Col xs={24} md={8}>
                                <Form.Item
                                  label="新 Agent 名称"
                                  name="quickAgentName"
                                  rules={[{ required: true, message: '请输入 Agent 名称' }]}
                                >
                                  <Input placeholder="例如：content-writer" />
                                </Form.Item>
                              </Col>
                              <Col xs={24} md={8}>
                                <Form.Item label="Agent Key（可选）" name="quickAgentKey">
                                  <Input placeholder="不填则根据名称生成" />
                                </Form.Item>
                              </Col>
                              <Col xs={24} md={4}>
                                <Form.Item label="Provider" name="modelProvider" rules={[{ required: true, message: '请输入 provider' }]}>
                                  <Input />
                                </Form.Item>
                              </Col>
                              <Col xs={24} md={4}>
                                <Form.Item label="Model" name="modelName" rules={[{ required: true, message: '请输入 model' }]}>
                                  <Input />
                                </Form.Item>
                              </Col>
                            </Row>
                          )}
                        </>
                      )
                    }
                  ]}
                />
              </div>
            </Form>

            <Divider />

            <Space.Compact style={{ width: '100%' }}>
              <Input
                value={manualSessionId}
                onChange={(event) => setManualSessionId(event.target.value)}
                placeholder="输入 sessionId 打开已有会话"
              />
              <Button
                icon={<SendOutlined />}
                onClick={() => {
                  if (!manualSessionId.trim()) {
                    message.warning('请输入 sessionId');
                    return;
                  }
                  navigate(`/sessions/${manualSessionId.trim()}`);
                }}
              >
                打开
              </Button>
            </Space.Compact>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card className="app-card" title="主路径步骤">
            <Steps
              direction="vertical"
              size="small"
              items={[
                { title: '点击新聊天', description: '默认设置下输入目标即可开始执行' },
                { title: '按需展开高级设置', description: '仅在需要覆盖 Agent 或快速创建时使用' },
                { title: '执行与中途控制', description: '进入会话页查看计划与任务状态' },
                { title: '结果沉淀', description: '导出、分享、回溯与复盘' }
              ]}
            />
          </Card>
        </Col>

        <Col xs={24}>
          <div id="recent-sessions">
            <Card
              className="app-card"
              title="最近会话"
              extra={
                <Button onClick={() => void loadSessions(listKeyword)} loading={listLoading}>
                  刷新
                </Button>
              }
            >
              <Space style={{ marginBottom: 12 }}>
                <Input
                  placeholder="按标题或 sessionId 搜索"
                  value={listKeyword}
                  onChange={(event) => setListKeyword(event.target.value)}
                  onPressEnter={() => void loadSessions(listKeyword)}
                />
                <Button onClick={() => void loadSessions(listKeyword)} loading={listLoading}>
                  查询
                </Button>
              </Space>

              {listLoading ? <StateView type="loading" title="加载会话列表" /> : null}

              {!listLoading && listSource.length === 0 ? (
                <StateView
                  type="empty"
                  title="暂无会话"
                  description="点击新聊天后，会话会自动沉淀在这里。"
                  actionText="立即新聊天"
                  onAction={() => form.submit()}
                />
              ) : null}

              {!listLoading && listSource.length > 0 ? (
                <List
                  dataSource={listSource}
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
                        description={`ID: ${item.sessionId} · ${item.createdAt ? new Date(item.createdAt).toLocaleString() : '-'}`}
                      />
                    </List.Item>
                  )}
                />
              ) : null}
            </Card>
          </div>
        </Col>
      </Row>
    </div>
  );
};
