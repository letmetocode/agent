import { CompassOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  List,
  Radio,
  Row,
  Space,
  Steps,
  Typography,
  message
} from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import { useSessionStore } from '@/features/session/sessionStore';
import { agentApi } from '@/shared/api/agentApi';
import type { SessionDetailDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

interface LaunchFormValues {
  goal: string;
  mode: 'use-existing' | 'quick-create';
  title?: string;
}

export const SessionListPage = () => {
  const navigate = useNavigate();
  const { userId, bookmarks, addBookmark } = useSessionStore();
  const [manualSessionId, setManualSessionId] = useState('');
  const [creating, setCreating] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [sessions, setSessions] = useState<SessionDetailDTO[]>([]);
  const [listKeyword, setListKeyword] = useState('');
  const [form] = Form.useForm<LaunchFormValues>();

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

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  const createSession = async (values: LaunchFormValues) => {
    if (!userId) {
      message.warning('请先设置 userId');
      navigate('/login');
      return;
    }

    const title = values.title?.trim() || values.goal.trim();
    setCreating(true);
    try {
      const session = await agentApi.createSession({ userId, title: title || undefined });
      addBookmark({ sessionId: session.sessionId, title: session.title, createdAt: new Date().toISOString() });
      message.success(`会话已创建 #${session.sessionId}`);
      await loadSessions();
      navigate(`/sessions/${session.sessionId}`);
    } catch (err) {
      message.error(`创建会话失败: ${String(err)}`);
    } finally {
      setCreating(false);
    }
  };

  const listSource = sessions.length > 0
    ? sessions
    : bookmarks.map((item) => ({
      sessionId: item.sessionId,
      userId: userId || '-',
      title: item.title,
      active: true,
      createdAt: item.createdAt
    }));

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
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

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="app-card" title="新建执行">
            <Form
              form={form}
              layout="vertical"
              initialValues={{ mode: 'use-existing' }}
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

              <Space>
                <Button icon={<PlusOutlined />} type="primary" htmlType="submit" loading={creating}>
                  创建并进入执行
                </Button>
                <Button icon={<CompassOutlined />} onClick={() => navigate('/tasks')}>
                  进入任务中心
                </Button>
              </Space>
            </Form>

            <Divider />

            <Space.Compact style={{ width: '100%' }}>
              <Input
                value={manualSessionId}
                onChange={(e) => setManualSessionId(e.target.value)}
                placeholder="输入 sessionId 打开已有会话"
              />
              <Button
                icon={<SendOutlined />}
                onClick={() => {
                  if (!manualSessionId.trim()) {
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
          <Card className="app-card" title="核心用户路径">
            <Steps
              direction="vertical"
              size="small"
              items={[
                { title: '新建 Agent / 任务', description: '从统一启动器选择创建方式' },
                { title: '输入目标', description: '明确目标、约束与引用范围' },
                { title: '执行与中途控制', description: '暂停/继续/取消/插入指令' },
                { title: '结果与引用', description: '查看结论、证据和中间产物' },
                { title: '导出/分享与回溯', description: '沉淀到任务中心与历史记录' }
              ]}
            />
          </Card>
        </Col>

        <Col xs={24}>
          <Card
            className="app-card"
            title="最近会话"
            extra={
              <Space>
                <Input
                  allowClear
                  style={{ width: 220 }}
                  placeholder="按标题过滤"
                  value={listKeyword}
                  onChange={(e) => setListKeyword(e.target.value)}
                  onPressEnter={() => void loadSessions(listKeyword)}
                />
                <Button icon={<ReloadOutlined />} onClick={() => void loadSessions(listKeyword)} />
              </Space>
            }
          >
            {listLoading ? <StateView type="loading" title="加载会话列表中" /> : null}

            {!listLoading && listSource.length === 0 ? (
              <StateView
                type="empty"
                title="暂无最近会话"
                description="创建一次执行后，会自动写入会话列表供快速回访。"
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
    </Space>
  );
};
