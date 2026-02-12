import {
  BranchesOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  RedoOutlined,
  ShareAltOutlined,
  StopOutlined
} from '@ant-design/icons';
import { Button, Card, Col, Descriptions, Divider, List, Row, Space, Steps, Timeline, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { PlanTaskEventDTO, TaskDetailDTO, TaskExecutionDetailDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';
import { StatusTag } from '@/shared/ui/StatusTag';

const { Paragraph, Text, Title } = Typography;

interface ReferenceItem {
  title: string;
  type: string;
  score?: number;
  source?: string;
}

const parseObject = (value: unknown): Record<string, unknown> | null => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
};

const parseNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return undefined;
};

const normalizeReferences = (source: unknown, fallbackType: string): ReferenceItem[] => {
  if (source == null) {
    return [];
  }
  if (Array.isArray(source)) {
    return source.flatMap((item) => normalizeReferences(item, fallbackType));
  }
  if (typeof source === 'string') {
    const title = source.trim();
    return title ? [{ title, type: fallbackType }] : [];
  }
  const value = parseObject(source);
  if (!value) {
    return [];
  }
  const titleCandidate = [value.title, value.name, value.source, value.id]
    .map((item) => (item == null ? '' : String(item).trim()))
    .find((item) => item.length > 0);
  if (!titleCandidate) {
    return [];
  }
  const type = value.type ? String(value.type) : fallbackType;
  const score = parseNumber(value.score);
  const sourceText = value.source ? String(value.source) : undefined;
  return [{ title: titleCandidate, type, score, source: sourceText }];
};

const deduplicateReferences = (references: ReferenceItem[]): ReferenceItem[] => {
  const index = new Map<string, ReferenceItem>();
  references.forEach((item) => {
    const key = `${item.type}::${item.title}`;
    if (!index.has(key)) {
      index.set(key, item);
    }
  });
  return Array.from(index.values());
};

const toTimelineEntry = (event: PlanTaskEventDTO) => {
  const eventName = event.eventName || event.eventType || '事件';
  const eventData = parseObject(event.eventData) || {};
  const status = eventData.status ? String(eventData.status).toUpperCase() : '';
  const messageText = eventData.message || eventData.outputResult || eventData.errorMessage;
  const detail = messageText == null ? '' : String(messageText);

  let title = eventName;
  if (eventName === 'TaskStarted') {
    title = '任务开始';
  } else if (eventName === 'TaskCompleted') {
    title = status ? `任务结束 · ${status}` : '任务结束';
  } else if (eventName === 'TaskLog') {
    title = '任务日志';
  }

  let color = 'blue';
  if (status === 'FAILED' || eventName.includes('Fail')) {
    color = 'red';
  } else if (status === 'COMPLETED' || eventName === 'TaskCompleted') {
    color = 'green';
  }

  return { title, detail, color };
};

const toStepIndex = (status?: string) => {
  const value = (status || '').toUpperCase();
  if (value === 'PENDING' || value === 'READY') {
    return 0;
  }
  if (value === 'RUNNING' || value === 'REFINING') {
    return 2;
  }
  if (value === 'VALIDATING') {
    return 3;
  }
  if (value === 'COMPLETED' || value === 'FAILED' || value === 'SKIPPED' || value === 'CANCELLED') {
    return 4;
  }
  return 1;
};

const downloadTextFile = (fileName: string, content: string, mimeType: string) => {
  const blob = new Blob([content], { type: `${mimeType};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
};

export const TaskDetailPage = () => {
  const navigate = useNavigate();
  const { taskId } = useParams();
  const normalizedTaskId = Number(taskId);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [task, setTask] = useState<TaskDetailDTO>();
  const [planStatus, setPlanStatus] = useState<string>();
  const [executions, setExecutions] = useState<TaskExecutionDetailDTO[]>([]);
  const [planEvents, setPlanEvents] = useState<PlanTaskEventDTO[]>([]);
  const [actionLoading, setActionLoading] = useState<string>();

  const loadTaskDetail = useCallback(async () => {
    if (!Number.isFinite(normalizedTaskId) || normalizedTaskId <= 0) {
      setError('无效 taskId');
      return;
    }

    setLoading(true);
    setError(undefined);
    try {
      const [hit, executionsRows] = await Promise.all([
        agentApi.getTask(normalizedTaskId),
        agentApi.getTaskExecutions(normalizedTaskId)
      ]);

      let eventRows: PlanTaskEventDTO[] = [];
      let currentPlanStatus: string | undefined;
      if (hit.planId) {
        const [plan, events] = await Promise.all([
          agentApi.getPlan(hit.planId),
          agentApi.getPlanEvents(hit.planId, { limit: 500 })
        ]);
        currentPlanStatus = plan.status;
        eventRows = events || [];
      }

      setTask(hit);
      setExecutions(executionsRows || []);
      setPlanEvents(eventRows || []);
      setPlanStatus(currentPlanStatus);
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      setError(text);
      message.error(`加载任务详情失败: ${text}`);
    } finally {
      setLoading(false);
    }
  }, [normalizedTaskId]);

  useEffect(() => {
    void loadTaskDetail();
  }, [loadTaskDetail]);

  const currentStep = useMemo(() => toStepIndex(task?.status), [task?.status]);

  const taskEvents = useMemo(
    () =>
      planEvents
        .filter((event) => Number(event.taskId) === normalizedTaskId)
        .sort((a, b) => {
          const left = a.createdAt ? new Date(a.createdAt).getTime() : 0;
          const right = b.createdAt ? new Date(b.createdAt).getTime() : 0;
          return left - right;
        }),
    [normalizedTaskId, planEvents]
  );

  const references = useMemo(() => {
    const rows: ReferenceItem[] = [];
    const inputContext = parseObject(task?.inputContext);
    const configSnapshot = parseObject(task?.configSnapshot);
    rows.push(...normalizeReferences(inputContext?.references, '输入上下文'));
    rows.push(...normalizeReferences(configSnapshot?.references, '配置引用'));

    taskEvents.forEach((event) => {
      const eventData = parseObject(event.eventData);
      rows.push(...normalizeReferences(eventData?.references, '执行引用'));
      if (eventData?.traceId) {
        rows.push({
          title: `trace-${String(eventData.traceId)}`,
          type: '日志链路',
          source: String(eventData.traceId)
        });
      }
    });

    return deduplicateReferences(rows).slice(0, 10);
  }, [task?.configSnapshot, task?.inputContext, taskEvents]);

  const normalizedPlanStatus = (planStatus || '').toUpperCase();
  const normalizedTaskStatus = (task?.status || '').toUpperCase();

  const canPause = normalizedPlanStatus === 'RUNNING';
  const canResume = normalizedPlanStatus === 'PAUSED';
  const canCancel = !['COMPLETED', 'FAILED', 'CANCELLED'].includes(normalizedPlanStatus);
  const canRetry = normalizedTaskStatus === 'FAILED' && !['COMPLETED', 'CANCELLED'].includes(normalizedPlanStatus);

  const handleControlAction = async (action: 'pause' | 'resume' | 'cancel' | 'retry') => {
    if (!task) {
      return;
    }
    setActionLoading(action);
    try {
      if (action === 'pause') {
        await agentApi.pauseTask(task.taskId);
        message.success('已暂停计划执行');
      } else if (action === 'resume') {
        await agentApi.resumeTask(task.taskId);
        message.success('已恢复计划执行');
      } else if (action === 'cancel') {
        await agentApi.cancelTask(task.taskId);
        message.success('已取消计划执行');
      } else {
        await agentApi.retryTaskFromFailed(task.taskId);
        message.success('已触发从失败节点重试');
      }
      await loadTaskDetail();
    } catch (err) {
      message.error(`操作失败: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleExport = async (format: 'markdown' | 'json') => {
    if (!task) {
      return;
    }
    setActionLoading(`export-${format}`);
    try {
      const data = await agentApi.exportTask(task.taskId, format);
      downloadTextFile(data.fileName, data.content, data.contentType);
      message.success(`已导出 ${data.fileName}`);
    } catch (err) {
      message.error(`导出失败: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleShare = async () => {
    if (!task) {
      return;
    }
    setActionLoading('share');
    try {
      const data = await agentApi.createTaskShareLink(task.taskId, 24);
      let copied = false;
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        try {
          await navigator.clipboard.writeText(data.shareUrl);
          copied = true;
        } catch (clipboardError) {
          copied = false;
        }
      }
      message.success(`分享链接已生成${copied ? '并复制到剪贴板' : ''}: ${data.shareUrl}`);
    } catch (err) {
      message.error(`生成分享链接失败: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setActionLoading(undefined);
    }
  };

  if (loading) {
    return <StateView type="loading" title="加载任务详情中" />;
  }

  if (error) {
    return (
      <StateView
        type="error"
        title="任务详情加载失败"
        description={error}
        actionText="返回任务中心"
        onAction={() => navigate('/tasks')}
      />
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <PageHeader
        title={`任务详情 #${taskId || '-'}`}
        description="完整追踪执行链路，支持中途控制、失败重试与结果导出。"
        primaryActionText="返回任务中心"
        onPrimaryAction={() => navigate('/tasks')}
        extra={
          <Space>
            <StatusTag status={task?.status || 'UNKNOWN'} />
            {planStatus ? <StatusTag status={planStatus} /> : null}
          </Space>
        }
      />

      <Card className="app-card">
        <Space wrap>
          <Button icon={<PauseCircleOutlined />} disabled={!canPause} loading={actionLoading === 'pause'} onClick={() => void handleControlAction('pause')}>
            暂停
          </Button>
          <Button icon={<PlayCircleOutlined />} disabled={!canResume} loading={actionLoading === 'resume'} onClick={() => void handleControlAction('resume')}>
            继续
          </Button>
          <Button danger icon={<StopOutlined />} disabled={!canCancel} loading={actionLoading === 'cancel'} onClick={() => void handleControlAction('cancel')}>
            取消
          </Button>
          <Button icon={<RedoOutlined />} disabled={!canRetry} loading={actionLoading === 'retry'} onClick={() => void handleControlAction('retry')}>
            从失败节点重试
          </Button>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="app-card" title="执行时间轴" extra={<Text type="secondary">{taskEvents.length} 条任务事件</Text>}>
            {taskEvents.length === 0 ? (
              <StateView type="empty" title="暂无任务事件" description="任务刚创建或尚未开始执行，事件到达后将自动展示。" />
            ) : (
              <Timeline
                items={taskEvents.map((event) => {
                  const entry = toTimelineEntry(event);
                  return {
                    color: entry.color,
                    children: (
                      <Space direction="vertical" size={2}>
                        <Text strong>{entry.title}</Text>
                        {entry.detail ? <Text type="secondary">{entry.detail}</Text> : null}
                        <Text type="secondary">{event.createdAt ? new Date(event.createdAt).toLocaleString() : '-'}</Text>
                      </Space>
                    )
                  };
                })}
              />
            )}

            <Divider />

            <Title level={5}>阶段进度</Title>
            <Steps
              current={currentStep}
              status={task?.status === 'FAILED' ? 'error' : 'process'}
              items={[
                { title: '排队' },
                { title: '规划' },
                { title: '执行中' },
                { title: '校验' },
                { title: '完成' }
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card className="app-card" title="执行图与资源">
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Task ID">{task?.taskId || '-'}</Descriptions.Item>
              <Descriptions.Item label="Plan ID">{task?.planId || '-'}</Descriptions.Item>
              <Descriptions.Item label="Plan 状态">{planStatus || '-'}</Descriptions.Item>
              <Descriptions.Item label="节点ID">{task?.nodeId || '-'}</Descriptions.Item>
              <Descriptions.Item label="任务类型">{task?.taskType || '-'}</Descriptions.Item>
              <Descriptions.Item label="执行次数">{task?.executionAttempt || 0}</Descriptions.Item>
              <Descriptions.Item label="最近执行耗时">
                {task?.latestExecutionTimeMs ? `${task.latestExecutionTimeMs}ms` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="最近更新">
                {task?.updatedAt ? new Date(task.updatedAt).toLocaleString() : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Divider />

            <Button icon={<BranchesOutlined />} block onClick={() => navigate('/observability/overview')}>
              查看执行拓扑与监控
            </Button>
          </Card>
        </Col>

        <Col xs={24}>
          <Card className="app-card" title="结果与引用">
            <Paragraph>
              {task?.outputResult || '任务尚未产生最终输出。'}
            </Paragraph>

            <List
              header="引用来源"
              dataSource={references}
              locale={{ emptyText: '暂无结构化引用信息' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={item.title}
                    description={`${item.type}${item.source ? ` · ${item.source}` : ''}${item.score != null ? ` · 相关性 ${Math.round(item.score * 100)}%` : ''}`}
                  />
                </List.Item>
              )}
            />

            <Divider />

            <List
              header="执行记录"
              dataSource={executions}
              locale={{ emptyText: '暂无执行记录' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={`attempt ${item.attemptNumber} · ${item.modelName || 'unknown-model'}`}
                    description={`耗时 ${item.executionTimeMs || 0}ms · ${item.errorType || '无错误类型'}`}
                  />
                </List.Item>
              )}
            />

            <Divider />

            <Space>
              <Button type="primary" loading={actionLoading === 'export-markdown'} onClick={() => void handleExport('markdown')}>
                导出 Markdown
              </Button>
              <Button loading={actionLoading === 'export-json'} onClick={() => void handleExport('json')}>
                导出 JSON
              </Button>
              <Button icon={<ShareAltOutlined />} loading={actionLoading === 'share'} onClick={() => void handleShare()}>
                生成分享链接
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
};
