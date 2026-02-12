import { Button, Card, Col, List, Progress, Row, Space, Statistic, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { DashboardOverviewDTO, TaskDetailDTO } from '@/shared/types/api';
import { useSessionStore } from '@/features/session/sessionStore';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';
import { StatusTag } from '@/shared/ui/StatusTag';

const { Paragraph, Text } = Typography;

interface RunningTaskCard {
  id: number;
  goal: string;
  progress: number;
  status: string;
  eta: string;
}

const toRunningTask = (task: TaskDetailDTO): RunningTaskCard => {
  const status = task.status || 'UNKNOWN';
  const progress = status === 'COMPLETED' ? 100 : status === 'FAILED' ? 100 : status === 'RUNNING' ? 60 : status === 'VALIDATING' ? 85 : 25;
  return {
    id: task.taskId,
    goal: task.name || task.nodeId,
    progress,
    status,
    eta: status === 'RUNNING' || status === 'VALIDATING' ? '进行中' : status === 'COMPLETED' ? '已完成' : '待执行'
  };
};

export const WorkspacePage = () => {
  const navigate = useNavigate();
  const { bookmarks } = useSessionStore();
  const [loading, setLoading] = useState(false);
  const [runningTasks, setRunningTasks] = useState<RunningTaskCard[]>([]);
  const [overview, setOverview] = useState<DashboardOverviewDTO>();

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      setLoading(true);
      try {
        const data = await agentApi.getDashboardOverview({ taskLimit: 12, planLimit: 12 });
        const tasks: TaskDetailDTO[] = data.recentTasks || [];
        if (!canceled) {
          setOverview(data);
          const runningLike = tasks
            .filter((item) => ['RUNNING', 'VALIDATING', 'REFINING', 'READY'].includes(item.status || ''))
            .slice(0, 6)
            .map(toRunningTask);
          setRunningTasks(runningLike);
        }
      } catch (err) {
        if (!canceled) {
          message.error(`加载工作台任务失败: ${err instanceof Error ? err.message : String(err)}`);
        }
      } finally {
        if (!canceled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      canceled = true;
    };
  }, [bookmarks]);

  const successRate = useMemo(() => {
    const total = overview?.taskStats?.total || 0;
    const failed = overview?.taskStats?.failed || 0;
    if (!total) {
      return 0;
    }
    return Number((((total - failed) / total) * 100).toFixed(1));
  }, [overview?.taskStats?.failed, overview?.taskStats?.total]);

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <PageHeader
        title="工作台"
        description="围绕一个目标快速创建执行，关注进行中任务并沉淀可复用结果。"
        primaryActionText="新建执行"
        onPrimaryAction={() => navigate('/sessions')}
        extra={<Button onClick={() => navigate('/tasks')}>查看全部任务</Button>}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="app-card" title="快捷启动" extra={<Text type="secondary">主路径入口</Text>}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                你可以从已有 Agent 或快速创建 Agent 启动一次新执行，默认进入“对话与执行”页面。
              </Paragraph>

              <Space wrap>
                <Button type="primary" onClick={() => navigate('/sessions')}>
                  使用现有 Agent 启动
                </Button>
                <Button onClick={() => navigate('/sessions')}>快速创建 Agent 并启动</Button>
                <Button type="link" onClick={() => navigate('/workflows/drafts')}>
                  前往 Workflow 治理
                </Button>
              </Space>
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card className="app-card" title="系统健康">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Row gutter={12}>
                <Col span={12}>
                  <Statistic title="成功率" value={successRate} suffix="%" />
                </Col>
                <Col span={12}>
                  <Statistic title="运行中任务" value={runningTasks.length} />
                </Col>
              </Row>
              <Button block onClick={() => navigate('/observability/overview')}>
                查看监控总览
              </Button>
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card className="app-card" title="最近会话" extra={<Button type="link" onClick={() => navigate('/sessions')}>更多</Button>}>
            {bookmarks.length === 0 ? (
              <StateView
                type="empty"
                title="还没有最近会话"
                description="创建你的第一个执行会话，后续会自动沉淀在这里。"
                actionText="立即创建"
                onAction={() => navigate('/sessions')}
              />
            ) : (
              <List
                dataSource={bookmarks.slice(0, 6)}
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
                      description={new Date(item.createdAt).toLocaleString()}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card className="app-card" title="运行中任务" extra={<Button type="link" onClick={() => navigate('/tasks')}>进入任务中心</Button>}>
            {loading ? <StateView type="loading" title="加载运行中任务" /> : null}
            {!loading && runningTasks.length === 0 ? (
              <StateView
                type="empty"
                title="暂无运行中任务"
                description="发起新会话后，运行中的任务会实时出现在这里。"
                actionText="前往对话与执行"
                onAction={() => navigate('/sessions')}
              />
            ) : null}

            {!loading && runningTasks.length > 0 ? (
              <List
                dataSource={runningTasks}
                renderItem={(task) => (
                  <List.Item>
                    <Space direction="vertical" style={{ width: '100%' }} size="small">
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <Text strong>
                          #{task.id} {task.goal}
                        </Text>
                        <StatusTag status={task.status} />
                      </Space>
                      <Progress percent={task.progress} showInfo={false} size="small" />
                      <Text type="secondary">状态：{task.eta}</Text>
                    </Space>
                  </List.Item>
                )}
              />
            ) : null}
          </Card>
        </Col>

        <Col xs={24}>
          <Card className="app-card" title="最近产出与可复用结果">
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card type="inner" title="竞品分析报告（Markdown）" extra={<StatusTag status="COMPLETED" />}>
                  <Paragraph type="secondary">来源：任务 #10012 · 引用 8 条知识片段 · 已分享给产品组</Paragraph>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card type="inner" title="客服问题聚类结果（JSON）" extra={<StatusTag status="COMPLETED" />}>
                  <Paragraph type="secondary">来源：任务 #10018 · 可直接导入 BI 看板</Paragraph>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card type="inner" title="投放诊断复盘（PDF）" extra={<StatusTag status="COMPLETED" />}>
                  <Paragraph type="secondary">来源：任务 #10026 · 包含执行日志摘要与结论</Paragraph>
                </Card>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>
    </Space>
  );
};
