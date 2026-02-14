import { Alert, Button, Card, Col, List, Progress, Row, Select, Space, Statistic, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type {
  DashboardOverviewDTO,
  ObservabilityAlertCatalogItemDTO,
  ObservabilityAlertProbeStatusDTO,
  TaskDetailDTO
} from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

const { Text } = Typography;

interface FailureSummaryItem {
  id: string;
  reason: string;
  count: number;
  taskId?: number;
}

const buildFailureSummary = (tasks: TaskDetailDTO[]): FailureSummaryItem[] => {
  const failed = tasks.filter((item) => item.status === 'FAILED');
  return failed.slice(0, 6).map((item) => ({
    id: `#${item.taskId}`,
    reason: item.outputResult || item.name || item.nodeId,
    count: 1,
    taskId: item.taskId
  }));
};

const severityColor: Record<string, string> = {
  critical: 'red',
  warning: 'gold'
};

const probeStatusColor: Record<string, string> = {
  PASS: 'green',
  WARN: 'red',
  IDLE: 'default',
  DISABLED: 'default'
};

const probeTrendColor: Record<string, string> = {
  UP: 'red',
  DOWN: 'green',
  FLAT: 'blue',
  NA: 'default'
};

const PROBE_WINDOW_OPTIONS = [
  { label: '近 3 次', value: 3 },
  { label: '近 5 次', value: 5 },
  { label: '近 10 次', value: 10 },
  { label: '近 20 次', value: 20 }
];

const isHttpLink = (value?: string) => typeof value === 'string' && /^https?:\/\//i.test(value.trim());

const safeTime = (value?: string) => {
  if (!value) {
    return '-';
  }
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) {
    return value;
  }
  return new Date(timestamp).toLocaleString();
};

const parseAlertNameFromIssue = (issue: string): string => {
  const text = String(issue || '').trim();
  if (!text) {
    return '';
  }
  const index = text.indexOf('.');
  if (index <= 0) {
    return text;
  }
  return text.slice(0, index);
};

const formatRate = (value?: number) => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '0.00%';
  }
  return `${value.toFixed(2)}%`;
};

export const ObservabilityOverviewPage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [overview, setOverview] = useState<DashboardOverviewDTO>();
  const [alertCatalog, setAlertCatalog] = useState<ObservabilityAlertCatalogItemDTO[]>([]);
  const [alertProbeStatus, setAlertProbeStatus] = useState<ObservabilityAlertProbeStatusDTO>();
  const [probeWindow, setProbeWindow] = useState<number>(5);

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      setLoading(true);
      setError(undefined);
      try {
        const [overviewData, alertData] = await Promise.all([
          agentApi.getDashboardOverview({ taskLimit: 50, planLimit: 20 }),
          agentApi.getObservabilityAlertCatalog()
        ]);
        if (!canceled) {
          setOverview(overviewData);
          setAlertCatalog(alertData || []);
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

  useEffect(() => {
    let canceled = false;
    const loadProbeStatus = async () => {
      try {
        const probeData = await agentApi.getObservabilityAlertProbeStatus({ window: probeWindow });
        if (!canceled) {
          setAlertProbeStatus(probeData);
        }
      } catch (err) {
        if (!canceled) {
          const text = err instanceof Error ? err.message : String(err);
          message.error(`加载巡检状态失败: ${text}`);
        }
      }
    };
    void loadProbeStatus();
    return () => {
      canceled = true;
    };
  }, [probeWindow]);

  const drillToLogs = (params?: { level?: string; taskId?: number; traceId?: string; keyword?: string }) => {
    const search = new URLSearchParams();
    if (params?.level) {
      search.set('level', params.level);
    }
    if (params?.taskId) {
      search.set('taskId', String(params.taskId));
    }
    if (params?.traceId) {
      search.set('traceId', params.traceId);
    }
    if (params?.keyword) {
      search.set('keyword', params.keyword);
    }
    navigate(`/observability/logs${search.toString() ? `?${search.toString()}` : ''}`);
  };

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

  const failures = useMemo(
    () => buildFailureSummary((overview?.recentFailedTasks || []) as TaskDetailDTO[]),
    [overview?.recentFailedTasks]
  );

  const probeStatusText = alertProbeStatus?.status || 'IDLE';
  const probeIssuesPreview = (alertProbeStatus?.issues || []).slice(0, 4);
  const envBreakdown = Object.entries(alertProbeStatus?.envStats || {}).sort((a, b) => b[1].failureRate - a[1].failureRate);
  const moduleBreakdown = Object.entries(alertProbeStatus?.moduleStats || {}).sort((a, b) => b[1].failureRate - a[1].failureRate);
  const recentRuns = alertProbeStatus?.recentRuns || [];
  const runTrendText = useMemo(() => {
    if (recentRuns.length < 2) {
      return '趋势样本不足（至少 2 次巡检）';
    }
    const previous = recentRuns[recentRuns.length - 2];
    const latest = recentRuns[recentRuns.length - 1];
    return `${safeTime(previous.executedAt)} ${formatRate(previous.failureRate)} -> ${safeTime(latest.executedAt)} ${formatRate(latest.failureRate)}`;
  }, [recentRuns]);

  if (loading) {
    return <StateView type="loading" title="加载监控数据中" />;
  }

  if (error) {
    return <StateView type="error" title="监控数据加载失败" description={error} />;
  }

  return (
    <div className="page-container">
      <PageHeader title="监控总览" description="聚焦吞吐、成功率与慢任务，快速发现异常并进入日志下钻。" />

      <Row gutter={[16, 16]} className="page-section">
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
                <List.Item
                  actions={[
                    <Button
                      key="drill"
                      type="link"
                      size="small"
                      disabled={!item.taskId}
                      onClick={() => drillToLogs({ level: 'ERROR', taskId: item.taskId })}
                    >
                      查看日志
                    </Button>
                  ]}
                >
                  <List.Item.Meta title={`${item.id} · ${item.reason}`} description={`出现次数：${item.count}`} />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24}>
          <Card
            className="app-card"
            title="告警规则目录（运行闭环）"
            extra={
              <Button type="link" onClick={() => drillToLogs({ level: 'ERROR' })}>
                查看 ERROR 日志
              </Button>
            }
          >
            <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }} size={8}>
              <Space wrap>
                <Text strong>链接巡检</Text>
                <Select
                  size="small"
                  style={{ width: 120 }}
                  value={probeWindow}
                  options={PROBE_WINDOW_OPTIONS}
                  onChange={(value) => setProbeWindow(value)}
                />
                <Tag color={probeStatusColor[probeStatusText] || 'default'}>{probeStatusText}</Tag>
                <Tag color={probeTrendColor[alertProbeStatus?.failureRateTrend || 'NA'] || 'default'}>
                  趋势: {alertProbeStatus?.failureRateTrend || 'NA'}
                </Tag>
                <Tag>启用: {alertProbeStatus?.enabled ? '是' : '否'}</Tag>
                <Tag>检查链接: {alertProbeStatus?.checkedLinks || 0}</Tag>
                <Tag color={alertProbeStatus?.failedLinks ? 'red' : 'green'}>失败: {alertProbeStatus?.failedLinks || 0}</Tag>
                <Tag>失败率: {formatRate(alertProbeStatus?.failureRate)}</Tag>
              </Space>
              <Text type="secondary">最近巡检时间：{safeTime(alertProbeStatus?.lastRunAt)}</Text>
              <Text type="secondary">趋势对比（近 {probeWindow} 次）：{runTrendText}</Text>
              {envBreakdown.length > 0 ? (
                <Space wrap>
                  <Text type="secondary">按环境：</Text>
                  {envBreakdown.map(([key, value]) => (
                    <Tag key={`env-${key}`} color={value.failedLinks > 0 ? 'red' : 'green'}>
                      {key} {value.failedLinks}/{value.checkedLinks} ({formatRate(value.failureRate)})
                    </Tag>
                  ))}
                </Space>
              ) : null}
              {moduleBreakdown.length > 0 ? (
                <Space wrap>
                  <Text type="secondary">按模块：</Text>
                  {moduleBreakdown.map(([key, value]) => (
                    <Tag key={`module-${key}`} color={value.failedLinks > 0 ? 'red' : 'green'}>
                      {key} {value.failedLinks}/{value.checkedLinks} ({formatRate(value.failureRate)})
                    </Tag>
                  ))}
                </Space>
              ) : null}
              {probeIssuesPreview.length > 0 ? (
                <Alert
                  type="warning"
                  showIcon
                  message="巡检发现不可达链接（示例）"
                  description={
                    <Space direction="vertical" size={2}>
                      {probeIssuesPreview.map((item) => (
                        <Space key={item} wrap>
                          <Text code>{item}</Text>
                          <Button
                            size="small"
                            type="link"
                            onClick={() => {
                              const alertName = parseAlertNameFromIssue(item);
                              if (!alertName) {
                                return;
                              }
                              drillToLogs({ level: 'ERROR', keyword: alertName });
                            }}
                          >
                            下钻日志
                          </Button>
                        </Space>
                      ))}
                    </Space>
                  }
                />
              ) : null}
            </Space>
            <List
              dataSource={alertCatalog}
              locale={{ emptyText: '暂无告警目录，请检查 /api/observability/alerts/catalog' }}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Button
                      key="dashboard"
                      type="link"
                      size="small"
                      disabled={!isHttpLink(item.dashboard)}
                      onClick={() => {
                        if (!isHttpLink(item.dashboard)) {
                          return;
                        }
                        window.open(item.dashboard, '_blank', 'noopener,noreferrer');
                      }}
                    >
                      打开看板
                    </Button>,
                    <Button
                      key="drill"
                      type="link"
                      size="small"
                      onClick={() => drillToLogs({ level: item.severity === 'critical' ? 'ERROR' : 'WARN', keyword: item.alertName })}
                    >
                      日志下钻
                    </Button>
                  ]}
                >
                  <Space direction="vertical" size={2}>
                    <Space>
                      <Text strong>{item.alertName}</Text>
                      <Tag color={severityColor[item.severity] || 'blue'}>{item.severity?.toUpperCase()}</Tag>
                      <Tag>{item.module}</Tag>
                      <Tag>{item.env}</Tag>
                    </Space>
                    <Text type="secondary">{item.summary || '-'}</Text>
                    <Text type="secondary" copyable>
                      runbook: {item.runbook}
                    </Text>
                    <Text type="secondary" copyable={!!item.dashboard}>
                      dashboard: {item.dashboard || '-'}
                    </Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Alert
        type="info"
        showIcon
        message={total > 0 ? '建议操作：优先处理失败任务，再跟踪 P95/P99 与慢任务走势。' : '暂无可观测任务数据，请先发起会话执行。'}
        action={
          <Button size="small" type="link" onClick={() => drillToLogs({ level: 'ERROR' })}>
            查看异常日志
          </Button>
        }
      />
    </div>
  );
};
