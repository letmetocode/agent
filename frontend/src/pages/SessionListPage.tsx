import { CompassOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Divider, Form, Input, List, Radio, Row, Select, Space, Steps, Typography, message } from 'antd';
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

export const SessionListPage = () => {
  const navigate = useNavigate();
  const { userId, bookmarks, addBookmark } = useSessionStore();
  const [manualSessionId, setManualSessionId] = useState('');
  const [creating, setCreating] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [sessions, setSessions] = useState<SessionDetailDTO[]>([]);
  const [activeAgents, setActiveAgents] = useState<ActiveAgentDTO[]>([]);
  const [listKeyword, setListKeyword] = useState('');
  const [form] = Form.useForm<LaunchFormValues>();
  const launchMode = Form.useWatch('mode', form) || 'use-existing';

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
      setActiveAgents(data || []);
      if (data && data.length > 0 && !form.getFieldValue('agentKey')) {
        form.setFieldValue('agentKey', data[0].agentKey);
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
      message.warning('请输入本次执行目标');
      return;
    }

    setCreating(true);
    try {
      let agentKey = values.agentKey?.trim();
      if (values.mode === 'quick-create') {
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

      if (!agentKey) {
        message.warning('请选择或创建 Agent');
        return;
      }

      const title = values.title?.trim() || goal;
      const session = await agentApi.createSessionV2({
        userId,
        title: title || undefined,
        agentKey,
        scenario: 'CONSOLE_LAUNCH',
        metaInfo: {
          launchMode: values.mode,
          source: 'session-list'
        }
      });

      const turn = await agentApi.createTurnV2(session.sessionId, {
        message: goal,
        contextOverrides: {
          launchMode: values.mode,
          launchFrom: 'session-list'
        }
      });

      addBookmark({ sessionId: session.sessionId, title: session.title || title, createdAt: new Date().toISOString() });
      message.success(`会话已创建并触发执行：Session #${session.sessionId} / Plan #${turn.planId}`);
      await loadSessions();
      navigate(`/sessions/${session.sessionId}?planId=${turn.planId}`);
    } catch (err) {
      message.error(`创建执行失败: ${err instanceof Error ? err.message : String(err)}`);
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
        description="统一入口：新建 Agent / 新建任务 → 输入目标 → 执行与中途控制。"
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
          <Card className="app-card" title="新建执行">
            <Form
              form={form}
              layout="vertical"
              initialValues={{ mode: 'use-existing', modelProvider: 'openai', modelName: 'gpt-4o-mini' }}
              onFinish={(values) => void createSession(values)}
            >
              <Form.Item label="目标" name="goal" rules={[{ required: true, message: '请输入本次执行目标' }]}>
                <Input.TextArea rows={3} placeholder="例如：分析本周失败任务原因，并给出可执行修复清单" />
              </Form.Item>

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
                <Form.Item label="选择 Agent" name="agentKey" rules={[{ required: true, message: '请选择 Agent' }]}>
                  <Select
                    loading={agentsLoading}
                    placeholder="请选择可用 Agent"
                    options={agentOptions}
                    notFoundContent={agentsLoading ? '加载中...' : '暂无可用 Agent'}
                  />
                </Form.Item>
              ) : (
                <Row gutter={12}>
                  <Col xs={24} md={8}>
                    <Form.Item label="新 Agent 名称" name="quickAgentName" rules={[{ required: true, message: '请输入 Agent 名称' }]}>
                      <Input placeholder="例如：ops-planner" />
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

              <Space>
                <Button icon={<PlusOutlined />} type="primary" htmlType="submit" loading={creating}>
                  创建并进入执行
                </Button>
                <Button icon={<CompassOutlined />} onClick={() => navigate('/tasks')}>
                  进入任务中心
                </Button>
                <Button icon={<ReloadOutlined />} onClick={() => void loadActiveAgents()} loading={agentsLoading}>
                  刷新 Agent
                </Button>
              </Space>
            </Form>

            <Divider />

            <Space.Compact style={{ width: '100%' }}>
              <Input value={manualSessionId} onChange={(event) => setManualSessionId(event.target.value)} placeholder="输入 sessionId 打开已有会话" />
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
                { title: '新建 Agent / 任务', description: '从统一启动器选择创建方式' },
                { title: '输入目标', description: '明确可执行目标与期望输出' },
                { title: '执行与中途控制', description: '进入会话页查看计划与任务状态' },
                { title: '结果沉淀', description: '导出、分享、回溯与复盘' }
              ]}
            />
          </Card>
        </Col>

        <Col xs={24}>
          <Card className="app-card" title="最近会话" extra={<Button onClick={() => void loadSessions(listKeyword)} loading={listLoading}>刷新</Button>}>
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
                description="创建一次执行后，会话会自动沉淀在这里。"
                actionText="立即创建"
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
        </Col>
      </Row>
    </div>
  );
};
