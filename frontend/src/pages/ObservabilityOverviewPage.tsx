import { Alert, Card, Col, List, Progress, Row, Space, Statistic, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { agentApi } from '@/shared/api/agentApi';
import type { DashboardOverviewDTO, TaskDetailDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

const { Text } = Typography;

const buildFailureSummary = (tasks: TaskDetailDTO[]) => {
  const failed = tasks.filter((item) => item.status === 'FAILED');
  return failed.slice(0, 6).map((item) => ({
    id: `#${item.taskId}`,
    reason: item.outputResult || item.name || item.nodeId,
    count: 1
  }));
};

export const ObservabilityOverviewPage = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [overview, setOverview] = useState<DashboardOverviewDTO>();

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      setLoading(true);
      setError(undefined);
      try {
        const rows = await agentApi.getDashboardOverview({ taskLimit: 50, planLimit: 20 });
        if (!canceled) {
          setOverview(rows);
        }
      } catch (err) {
        if (!canceled) {
          const text = err instanceof Error ? err.message : String(err);
          setError(text);
          message.error(`加载监控数据失败: ${text}`);
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
  }, []);

  const total = overview?.taskStats?.total || 0;
  const completed = overview?.taskStats?.completed || 0;
  const failed = overview?.taskStats?.failed || 0;
  const runningLike = overview?.taskStats?.runningLike || 0;
  const p95 = overview?.latencyStats?.p95 || 0;
  const p99 = overview?.latencyStats?.p99 || 0;
  const slowTaskCount = overview?.slowTaskCount || 0;
  const slaBreachCount = overview?.slaBreachCount || 0;

  const successRate = useMemo(() => {
    if (!total) {
      return 0;
    }
    return Number((((total - failed) / total) * 100).toFixed(1));
  }, [failed, total]);

  const failures = useMemo(() => buildFailureSummary((overview?.recentFailedTasks || []) as TaskDetailDTO[]), [overview?.recentFailedTasks]);

  if (loading) {
    return <StateView type="loading" title="加载监控数据中" />;
  }

  if (error) {
    return <StateView type="error" title="监控数据加载失败" description={error} />;
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <PageHeader title="监控总览" description="聚焦吞吐、成功率与慢任务，快速发现异常并进入日志下钻。" />

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="任务总量" value={total} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="成功率" value={successRate} suffix="%" />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="运行中" value={runningLike} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="失败数" value={failed} />
          </Card>
        </Col>

        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="P95 耗时" value={p95} suffix="ms" />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="P99 耗时" value={p99} suffix="ms" />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="慢任务数" value={slowTaskCount} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card">
            <Statistic title="SLA 超阈值" value={slaBreachCount} />
          </Card>
        </Col>

        <Col xs={24} xl={16}>
          <Card className="app-card" title="异常趋势（近 24 小时）">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text type="secondary">基于 dashboard 聚合接口统计任务状态分布。</Text>
              <Progress percent={total ? Number(((completed / total) * 100).toFixed(1)) : 0} status="active" />
              <Progress percent={total ? Number(((runningLike / total) * 100).toFixed(1)) : 0} strokeColor="#d97706" />
              <Progress percent={total ? Number(((failed / total) * 100).toFixed(1)) : 0} strokeColor="#dc2626" />
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card className="app-card" title="失败任务排行">
            <List
              dataSource={failures}
              locale={{ emptyText: '暂无失败任务' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta title={`${item.id} · ${item.reason}`} description={`出现次数：${item.count}`} />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Alert
        type="info"
        showIcon
        message={
          total > 0
            ? `建议操作：优先处理失败任务，再跟踪 P95/P99 与慢任务走势。`
            : '暂无可观测任务数据，请先发起会话执行。'
        }
      />
    </Space>
  );
};
