import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  message,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import axios from 'axios';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import { CHAT_HTTP_TIMEOUT_MS } from '@/shared/api/http';
import { usePlanStore } from '@/features/plan/planStore';
import { openPlanSse } from '@/features/sse/sseClient';
import type {
  PlanTaskStatsDTO,
  PlanStreamEvent,
  WorkflowDraftDetailDTO,
  SessionMessageDTO,
  SessionTurnDTO,
  TaskDetailDTO
} from '@/shared/types/api';

const { TextArea } = Input;

interface CandidateWorkflowDraftFormValues {
  draftKey: string;
  tenantId: string;
  category: string;
  name: string;
  routeDescription: string;
  inputSchemaVersion: string;
  graphDefinitionText: string;
  inputSchemaText: string;
  defaultConfigText: string;
  toolPolicyText: string;
  constraintsText: string;
  operator: string;
}

interface ProcessEventEntry {
  id: string;
  time: string;
  type: string;
  summary: string;
  detail?: string;
  raw?: unknown;
}

const EMPTY_TASK_STATS: PlanTaskStatsDTO = {
  total: 0,
  pending: 0,
  ready: 0,
  runningLike: 0,
  completed: 0,
  failed: 0,
  skipped: 0
};

const normalizeTaskStats = (stats?: Partial<PlanTaskStatsDTO> | null): PlanTaskStatsDTO => ({
  total: Number(stats?.total ?? 0),
  pending: Number(stats?.pending ?? 0),
  ready: Number(stats?.ready ?? 0),
  runningLike: Number(stats?.runningLike ?? 0),
  completed: Number(stats?.completed ?? 0),
  failed: Number(stats?.failed ?? 0),
  skipped: Number(stats?.skipped ?? 0)
});

const toChatErrorMessage = (err: unknown): string => {
  if (axios.isAxiosError(err)) {
    if (err.code === 'ECONNABORTED') {
      return `发送超时（>${Math.floor(CHAT_HTTP_TIMEOUT_MS / 1000)}秒），后端可能仍在处理中，请稍后刷新会话查看结果。`;
    }
    const data = err.response?.data as { info?: string } | undefined;
    if (data?.info) {
      return `发送失败: ${data.info}`;
    }
    if (err.message) {
      return `发送失败: ${err.message}`;
    }
  }
  return `发送失败: ${String(err)}`;
};

const statusColor = (status?: string) => {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'RUNNING':
    case 'VALIDATING':
    case 'REFINING':
      return 'processing';
    case 'READY':
      return 'blue';
    case 'PAUSED':
    case 'CANCELLED':
      return 'warning';
    default:
      return 'default';
  }
};

const byTimeDesc = (a?: string, b?: string) => {
  const av = a ? new Date(a).getTime() : 0;
  const bv = b ? new Date(b).getTime() : 0;
  return bv - av;
};

const eventSummary = (event: PlanStreamEvent): ProcessEventEntry => {
  const now = new Date().toLocaleTimeString();
  const raw = event.data;
  const data = typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {};
  const taskId = data.taskId ? String(data.taskId) : '-';
  const status = data.status ? String(data.status) : '';
  const nodeName = data.taskName ? String(data.taskName) : data.nodeId ? String(data.nodeId) : '';

  let summary = event.event;
  let detail = '';

  if (event.event === 'TaskStarted') {
    summary = `任务开始 · #${taskId}${nodeName ? ` · ${nodeName}` : ''}`;
  } else if (event.event === 'TaskCompleted') {
    summary = `任务结束 · #${taskId}${status ? ` · ${status}` : ''}`;
    detail = data.outputResult ? String(data.outputResult) : '';
  } else if (event.event === 'TaskLog') {
    summary = `任务日志 · #${taskId}`;
    detail = data.message ? String(data.message) : typeof raw === 'string' ? raw : '';
  } else if (event.event === 'PlanFinished') {
    summary = `计划完成 · ${status || 'UNKNOWN'}`;
  } else if (event.event === 'PlanSnapshot') {
    summary = '计划快照更新';
  } else if (event.event === 'StreamReady') {
    summary = 'SSE 流已连接';
  } else if (event.event === 'Heartbeat') {
    summary = '心跳';
  }

  return {
    id: `${event.event}-${event.id || Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    time: now,
    type: event.event,
    summary,
    detail,
    raw
  };
};

const groupMessagesByTurn = (turns: SessionTurnDTO[], messages: SessionMessageDTO[]) => {
  const turnMap = new Map<number, SessionTurnDTO>();
  turns.forEach((turn) => turnMap.set(turn.turnId, turn));

  const grouped = new Map<number, SessionMessageDTO[]>();
  messages.forEach((m) => {
    if (!grouped.has(m.turnId)) {
      grouped.set(m.turnId, []);
    }
    grouped.get(m.turnId)!.push(m);
  });

  const rows = Array.from(grouped.entries()).map(([turnId, list]) => ({
    turnId,
    turn: turnMap.get(turnId),
    messages: list.sort((a, b) => byTimeDesc(b.createdAt, a.createdAt))
  }));

  return rows.sort((a, b) => {
    const ta = a.turn?.createdAt;
    const tb = b.turn?.createdAt;
    return byTimeDesc(ta, tb);
  });
};

const stringifyJson = (value?: Record<string, unknown>) => JSON.stringify(value || {}, null, 2);

const parseJsonObject = (text: string, fieldName: string): Record<string, unknown> => {
  const trimmed = text.trim();
  if (!trimmed) {
    return {};
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch (err) {
    const errText = err instanceof Error ? err.message : String(err);
    throw new Error(`${fieldName} 不是合法 JSON：${errText}`);
  }
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${fieldName} 必须是 JSON 对象`);
  }
  return parsed as Record<string, unknown>;
};

const toErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err));

const extractCandidateId = (
  planDetail: { workflowDraftId?: number; globalContext?: Record<string, unknown> } | null | undefined
) => {
  const context = planDetail?.globalContext;
  const candidates = [
    context?.workflowDraftId,
    context?.draftId,
    context?.candidateWorkflowDraftId,
    planDetail?.workflowDraftId
  ];
  for (const item of candidates) {
    const num = Number(item);
    if (Number.isFinite(num) && num > 0) {
      return num;
    }
  }
  return undefined;
};

const isFallbackCandidatePlan = (planDetail: { globalContext?: Record<string, unknown> } | null | undefined) => {
  const context = planDetail?.globalContext || {};
  const sourceType = String(context.sourceType || '').toUpperCase();
  const fallbackReason = String(context.fallbackReason || '').toUpperCase();
  const workflowCategory = String(context.workflowCategory || '').toLowerCase();
  const hasDraftKey = Boolean(context.workflowDraftId || context.draftId || context.candidateWorkflowDraftId);
  return sourceType.includes('AUTO_MISS') || fallbackReason.length > 0 || workflowCategory === 'draft' || hasDraftKey;
};

export const ConversationPage = () => {
  const navigate = useNavigate();
  const { sessionId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const sid = Number(sessionId);

  const {
    loading,
    overview,
    selectedPlanId,
    planDetail,
    planTasks,
    turns,
    messages,
    taskExecutions,
    setLoading,
    setOverview,
    setSelectedPlanId,
    setPlanDetail,
    setPlanTasks,
    setTurns,
    setMessages,
    setTaskExecutions
  } = usePlanStore();

  const [prompt, setPrompt] = useState('');
  const [processEvents, setProcessEvents] = useState<ProcessEventEntry[]>([]);
  const [sending, setSending] = useState(false);
  const [drawerTask, setDrawerTask] = useState<TaskDetailDTO | null>(null);
  const [candidateDrawerOpen, setCandidateDrawerOpen] = useState(false);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [candidateHintLoading, setCandidateHintLoading] = useState(false);
  const [candidateTemplateVerified, setCandidateTemplateVerified] = useState<boolean | null>(null);
  const [candidateSaving, setCandidateSaving] = useState(false);
  const [publishingCandidate, setPublishingCandidate] = useState(false);
  const [candidateDetail, setCandidateDetail] = useState<WorkflowDraftDetailDTO | null>(null);
  const [candidateForm] = Form.useForm<CandidateWorkflowDraftFormValues>();
  const sseRef = useRef<EventSource | null>(null);

  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8091';

  const loadSessionBase = async () => {
    if (!sid) return;
    setLoading(true);
    try {
      const [ov, ts, ms] = await Promise.all([
        agentApi.getSessionOverview(sid),
        agentApi.getSessionTurns(sid),
        agentApi.getSessionMessages(sid)
      ]);
      setOverview(ov);
      setTurns(ts);
      setMessages(ms);

      const queryPlanId = Number(searchParams.get('planId'));
      const targetPlanId = queryPlanId || ov.latestPlanId;
      if (targetPlanId) {
        await switchPlan(targetPlanId, false);
      }
    } catch (err) {
      message.error(`加载会话失败: ${String(err)}`);
    } finally {
      setLoading(false);
    }
  };

  const switchPlan = async (planId: number, syncQuery = true) => {
    setSelectedPlanId(planId);
    if (syncQuery) {
      setSearchParams({ planId: String(planId) }, { replace: true });
    }
    try {
      const [plan, tasks] = await Promise.all([agentApi.getPlan(planId), agentApi.getPlanTasks(planId)]);
      setPlanDetail(plan);
      setPlanTasks(tasks);
    } catch (err) {
      message.error(`加载计划失败: ${String(err)}`);
    }
  };

  const refreshPlanTasks = async (planId: number) => {
    try {
      const tasks = await agentApi.getPlanTasks(planId);
      setPlanTasks(tasks);
    } catch {
      // ignore
    }
  };

  const onSseEvent = (event: PlanStreamEvent) => {
    const entry = eventSummary(event);
    if (entry.type !== 'Heartbeat') {
      setProcessEvents((prev) => [entry, ...prev].slice(0, 120));
    }

    if (!selectedPlanId) return;
    if (['TaskStarted', 'TaskCompleted', 'TaskLog', 'PlanSnapshot'].includes(event.event)) {
      void refreshPlanTasks(selectedPlanId);
    }
    if (event.event === 'PlanFinished') {
      void Promise.all([
        loadSessionBase(),
        refreshPlanTasks(selectedPlanId),
        agentApi.getSessionMessages(sid).then(setMessages),
        agentApi.getSessionTurns(sid).then(setTurns)
      ]);
    }
  };

  const connectSse = (planId: number) => {
    sseRef.current?.close();
    sseRef.current = openPlanSse(planId, baseUrl, onSseEvent, () => {
      setProcessEvents((prev) => [
        {
          id: `reconnect-${Date.now()}`,
          time: new Date().toLocaleTimeString(),
          type: 'SSE',
          summary: 'SSE 连接中断，正在自动重连'
        },
        ...prev
      ].slice(0, 120));
    });
  };

  useEffect(() => {
    if (!sid) return;
    void loadSessionBase();
  }, [sid]);

  useEffect(() => {
    if (!selectedPlanId) return;
    connectSse(selectedPlanId);
    return () => {
      sseRef.current?.close();
      sseRef.current = null;
    };
  }, [selectedPlanId]);

  const sendChat = async () => {
    if (!sid || !prompt.trim()) return;
    setSending(true);
    try {
      const res = await agentApi.sendChat(sid, { message: prompt.trim() });
      setPrompt('');
      await loadSessionBase();
      await switchPlan(res.planId);
      connectSse(res.planId);
      message.success(`已触发新回合，Plan #${res.planId}`);
    } catch (err) {
      message.error(toChatErrorMessage(err));
    } finally {
      setSending(false);
    }
  };

  const currentStats = useMemo(() => {
    if (overview && overview.latestPlanId != null && selectedPlanId != null && overview.latestPlanId === selectedPlanId) {
      return normalizeTaskStats(overview.latestPlanTaskStats ?? EMPTY_TASK_STATS);
    }
    const total = planTasks.length;
    const pending = planTasks.filter((t) => t.status === 'PENDING').length;
    const ready = planTasks.filter((t) => t.status === 'READY').length;
    const runningLike = planTasks.filter((t) => ['RUNNING', 'VALIDATING', 'REFINING'].includes(t.status)).length;
    const completed = planTasks.filter((t) => t.status === 'COMPLETED').length;
    const failed = planTasks.filter((t) => t.status === 'FAILED').length;
    const skipped = planTasks.filter((t) => t.status === 'SKIPPED').length;
    return normalizeTaskStats({ total, pending, ready, runningLike, completed, failed, skipped });
  }, [overview, selectedPlanId, planTasks]);

  const groupedMessages = useMemo(() => groupMessagesByTurn(turns, messages), [turns, messages]);

  const failureTasks = useMemo(() => planTasks.filter((t) => t.status === 'FAILED'), [planTasks]);

  const fallbackCandidateHint = useMemo(
    () => isFallbackCandidatePlan(planDetail) || candidateTemplateVerified === true,
    [planDetail, candidateTemplateVerified]
  );
  const fallbackCandidateId = useMemo(() => extractCandidateId(planDetail), [planDetail]);

  useEffect(() => {
    const templateId = planDetail?.workflowDraftId;
    if (!templateId) {
      setCandidateTemplateVerified(null);
      setCandidateHintLoading(false);
      return;
    }
    setCandidateHintLoading(true);
    setCandidateTemplateVerified(null);
    let canceled = false;
    agentApi
      .getWorkflowDraftDetail(templateId)
      .then(() => {
        if (canceled) {
          return;
        }
        setCandidateTemplateVerified(true);
      })
      .catch(() => {
        if (canceled) {
          return;
        }
        setCandidateTemplateVerified(false);
      })
      .finally(() => {
        if (canceled) {
          return;
        }
        setCandidateHintLoading(false);
      });
    return () => {
      canceled = true;
    };
  }, [planDetail?.workflowDraftId]);

  const fillCandidateForm = (detail: WorkflowDraftDetailDTO) => {
    candidateForm.setFieldsValue({
      draftKey: detail.draftKey || '',
      tenantId: detail.tenantId || 'DEFAULT',
      category: detail.category || '',
      name: detail.name || '',
      routeDescription: detail.routeDescription || '',
      inputSchemaVersion: detail.inputSchemaVersion || 'v1',
      graphDefinitionText: stringifyJson(detail.graphDefinition),
      inputSchemaText: stringifyJson(detail.inputSchema),
      defaultConfigText: stringifyJson(detail.defaultConfig),
      toolPolicyText: stringifyJson(detail.toolPolicy),
      constraintsText: stringifyJson(detail.constraints),
      operator: 'SYSTEM'
    });
  };

  const ensureCandidateDetailLoaded = async () => {
    if (!fallbackCandidateId) {
      message.error('当前 Plan 未提供候补 Workflow Draft ID，暂无法打开编辑器');
      return;
    }
    setCandidateLoading(true);
    try {
      const detail = await agentApi.getWorkflowDraftDetail(fallbackCandidateId);
      setCandidateDetail(detail);
      fillCandidateForm(detail);
      setCandidateDrawerOpen(true);
    } catch (err) {
      message.error(`加载候补 Workflow Draft 失败：${toErrorMessage(err)}`);
    } finally {
      setCandidateLoading(false);
    }
  };

  const saveCandidateOnly = async () => {
    const id = candidateDetail?.id;
    if (!id) {
      throw new Error('候补 Workflow Draft 尚未加载完成');
    }
    const values = await candidateForm.validateFields();
    setCandidateSaving(true);
    try {
      const updated = await agentApi.updateWorkflowDraft(id, {
        draftKey: values.draftKey.trim(),
        tenantId: values.tenantId.trim(),
        category: values.category.trim(),
        name: values.name.trim(),
        routeDescription: values.routeDescription.trim(),
        inputSchemaVersion: values.inputSchemaVersion?.trim(),
        graphDefinition: parseJsonObject(values.graphDefinitionText, 'graphDefinition'),
        inputSchema: parseJsonObject(values.inputSchemaText, 'inputSchema'),
        defaultConfig: parseJsonObject(values.defaultConfigText, 'defaultConfig'),
        toolPolicy: parseJsonObject(values.toolPolicyText, 'toolPolicy'),
        constraints: parseJsonObject(values.constraintsText, 'constraints')
      });
      setCandidateDetail(updated);
      fillCandidateForm(updated);
      message.success(`候补 Workflow Draft #${updated.id} 已保存`);
      return updated;
    } catch (err) {
      message.error(`保存候补 Workflow Draft 失败：${toErrorMessage(err)}`);
      throw err;
    } finally {
      setCandidateSaving(false);
    }
  };

  const saveAndPublishCandidate = async () => {
    const id = candidateDetail?.id;
    if (!id) {
      throw new Error('候补 Workflow Draft 尚未加载完成');
    }
    const values = await candidateForm.validateFields();
    setPublishingCandidate(true);
    try {
      const updated = await saveCandidateOnly();
      const operator = values.operator?.trim() || 'SYSTEM';
      const result = await agentApi.publishWorkflowDraft(id, { operator });
      message.success(`升级成功：draft #${result.draftId} -> definition #${result.definitionId} (v${result.definitionVersion})`);
      setCandidateDrawerOpen(false);
      setCandidateDetail(null);
      Modal.success({
        title: '已升级为正式 Workflow Definition',
        content: `Draft Key：${updated.draftKey}，生产版本：v${result.definitionVersion}`
      });
    } catch (err) {
      message.error(`升级正式 Workflow Definition 失败：${toErrorMessage(err)}`);
    } finally {
      setPublishingCandidate(false);
    }
  };

  const openExecutionDrawer = async (task: TaskDetailDTO) => {
    setDrawerTask(task);
    try {
      const rows = await agentApi.getTaskExecutions(task.taskId);
      setTaskExecutions(task.taskId, rows);
    } catch (err) {
      message.warning(`加载执行记录失败: ${String(err)}`);
    }
  };

  const taskColumns: ColumnsType<TaskDetailDTO> = [
    {
      title: '任务',
      dataIndex: 'name',
      key: 'name',
      render: (_, row) => <Space><Typography.Text strong>{row.name || row.nodeId}</Typography.Text><Tag>{row.taskType}</Tag></Space>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      filters: [
        'PENDING',
        'READY',
        'RUNNING',
        'VALIDATING',
        'REFINING',
        'COMPLETED',
        'FAILED',
        'SKIPPED'
      ].map((s) => ({ text: s, value: s })),
      onFilter: (value, record) => record.status === value,
      render: (status: string) => <Tag color={statusColor(status)}>{status}</Tag>
    },
    {
      title: 'Attempt',
      dataIndex: 'executionAttempt',
      key: 'executionAttempt',
      sorter: (a, b) => (a.executionAttempt || 0) - (b.executionAttempt || 0),
      width: 90
    },
    {
      title: 'Owner',
      dataIndex: 'claimOwner',
      key: 'claimOwner',
      width: 120,
      ellipsis: true,
      render: (owner?: string) => owner || '-'
    },
    {
      title: '更新',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      sorter: (a, b) => byTimeDesc(b.updatedAt, a.updatedAt),
      width: 160,
      render: (v?: string) => (v ? new Date(v).toLocaleTimeString() : '-')
    },
    {
      title: '操作',
      key: 'action',
      width: 96,
      render: (_, row) => (
        <Button size="small" type="link" onClick={() => void openExecutionDrawer(row)}>
          详情
        </Button>
      )
    }
  ];

  if (!sid || Number.isNaN(sid)) {
    return <Empty description="无效 sessionId" />;
  }

  return (
    <div className="workspace-shell">
      <Spin spinning={loading}>
        <Row gutter={12} wrap={false}>
          <Col className="pane pane-left" flex="280px">
            <Card size="small" title={`Session #${sid}`} extra={<Button type="link" onClick={() => navigate('/sessions')}>返回</Button>}>
              <Typography.Text type="secondary">{overview?.session?.title || '未命名会话'}</Typography.Text>
              <Divider />
              <List
                size="small"
                header="回合/Plan"
                dataSource={overview?.plans || []}
                renderItem={(p) => (
                  <List.Item
                    onClick={() => void switchPlan(p.planId)}
                    style={{ cursor: 'pointer', background: selectedPlanId === p.planId ? '#edf6f6' : 'transparent', borderRadius: 8 }}
                  >
                    <List.Item.Meta
                      title={<Space><Tag color={statusColor(p.status)}>{p.status}</Tag><span>#{p.planId}</span></Space>}
                      description={<Typography.Text ellipsis={{ tooltip: p.planGoal }}>{p.planGoal}</Typography.Text>}
                    />
                  </List.Item>
                )}
              />
            </Card>
          </Col>

          <Col className="pane pane-center" flex="auto">
            <Card size="small" title="消息流（按回合）">
              <List
                dataSource={groupedMessages}
                locale={{ emptyText: '暂无消息' }}
                renderItem={(group) => (
                  <List.Item>
                    <div style={{ width: '100%' }}>
                      <Space style={{ marginBottom: 8 }}>
                        <Tag color={statusColor(group.turn?.status)}>{group.turn?.status || 'UNKNOWN'}</Tag>
                        <Typography.Text strong>Turn #{group.turnId}</Typography.Text>
                        <Typography.Text type="secondary">Plan #{group.turn?.planId || '-'}</Typography.Text>
                      </Space>
                      <List
                        size="small"
                        dataSource={group.messages}
                        renderItem={(m) => (
                          <List.Item>
                            <List.Item.Meta
                              title={<Space><Tag>{m.role}</Tag><Typography.Text type="secondary">{m.createdAt ? new Date(m.createdAt).toLocaleString() : '-'}</Typography.Text></Space>}
                              description={<Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{m.content}</Typography.Paragraph>}
                            />
                          </List.Item>
                        )}
                      />
                    </div>
                  </List.Item>
                )}
              />

              <Divider />
              <Space direction="vertical" style={{ width: '100%' }}>
                <TextArea value={prompt} onChange={(e) => setPrompt(e.target.value)} rows={4} placeholder="输入你的问题，触发一轮新的计划执行" />
                <Button type="primary" loading={sending} onClick={sendChat}>发送并执行</Button>
              </Space>

              <Divider />
              <Collapse
                size="small"
                items={[
                  {
                    key: 'events',
                    label: `过程事件（${processEvents.length}）`,
                    children: (
                      <Timeline
                        items={processEvents.map((e) => ({
                          color: e.type === 'TaskCompleted' && e.summary.includes('FAILED') ? 'red' : 'blue',
                          children: (
                            <Space direction="vertical" style={{ width: '100%' }}>
                              <Typography.Text strong>{e.summary}</Typography.Text>
                              <Typography.Text type="secondary">{e.time}</Typography.Text>
                              {e.detail ? (
                                <Typography.Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, expandable: true }}>
                                  {e.detail}
                                </Typography.Paragraph>
                              ) : null}
                            </Space>
                          )
                        }))}
                      />
                    )
                  }
                ]}
              />
            </Card>
          </Col>

          <Col className="pane pane-right" flex="420px">
            <Card size="small" title="执行面板">
              <Space style={{ width: '100%' }} wrap>
                <Statistic title="总任务" value={currentStats?.total || 0} />
                <Statistic title="运行中" value={currentStats?.runningLike || 0} />
                <Statistic title="失败" value={currentStats?.failed || 0} />
              </Space>

              <Divider />
              <Descriptions column={1} size="small" title="当前 Plan">
                <Descriptions.Item label="PlanId">{planDetail?.planId || '-'}</Descriptions.Item>
                <Descriptions.Item label="状态"><Tag color={statusColor(planDetail?.status)}>{planDetail?.status || '-'}</Tag></Descriptions.Item>
                <Descriptions.Item label="目标">{planDetail?.planGoal || '-'}</Descriptions.Item>
              </Descriptions>

              {fallbackCandidateHint ? (
                <>
                  <Divider />
                  <Alert
                    type="warning"
                    showIcon
                    message="未命中正式 Workflow Definition，当前使用候补 Draft"
                    description={
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <Typography.Text type="secondary">
                          当前 Plan 由候补 Workflow Draft 执行，你可以查看并编辑 Draft，保存后可直接升级为正式 Definition。
                        </Typography.Text>
                      <Space>
                        <Button onClick={() => void ensureCandidateDetailLoaded()} loading={candidateLoading}>
                          查看/编辑候补 Draft
                        </Button>
                          <Button type="link" onClick={() => navigate('/workflows/drafts')}>
                            进入 Workflow Draft 治理页
                          </Button>
                        </Space>
                        <Typography.Text type="secondary">
                          候补 Draft ID：{fallbackCandidateId || '-'}
                        </Typography.Text>
                        {candidateHintLoading ? <Typography.Text type="secondary">正在校验候补 Workflow Draft...</Typography.Text> : null}
                      </Space>
                    }
                  />
                </>
              ) : null}

              {planDetail?.errorSummary || failureTasks.length > 0 ? (
                <>
                  <Divider />
                  <Alert
                    type="error"
                    showIcon
                    message="错误面板"
                    description={
                      <Space direction="vertical" style={{ width: '100%' }}>
                        {planDetail?.errorSummary ? <Typography.Text>{planDetail.errorSummary}</Typography.Text> : null}
                        {failureTasks.slice(0, 3).map((t) => (
                          <Typography.Text key={t.taskId} type="secondary">
                            #{t.taskId} {t.name || t.nodeId}：{t.outputResult || '无错误详情'}
                          </Typography.Text>
                        ))}
                      </Space>
                    }
                  />
                </>
              ) : null}

              <Divider />
              <Table<TaskDetailDTO>
                size="small"
                rowKey="taskId"
                columns={taskColumns}
                dataSource={planTasks}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                scroll={{ x: 760 }}
              />
            </Card>
          </Col>
        </Row>
      </Spin>

      <Drawer
        width={680}
        title={drawerTask ? `Task #${drawerTask.taskId} 执行记录` : '执行记录'}
        open={Boolean(drawerTask)}
        onClose={() => setDrawerTask(null)}
      >
        <List
          dataSource={drawerTask ? taskExecutions[drawerTask.taskId] || [] : []}
          locale={{ emptyText: '暂无执行记录' }}
          renderItem={(row) => (
            <List.Item>
              <List.Item.Meta
                title={<Space><Tag>attempt {row.attemptNumber}</Tag><span>{row.modelName || 'unknown-model'}</span></Space>}
                description={
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Typography.Text type="secondary">耗时: {row.executionTimeMs || 0}ms · 错误类型: {row.errorType || '-'}</Typography.Text>
                    <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }} ellipsis={{ rows: 5, expandable: true }}>
                      {row.llmResponseRaw || row.errorMessage || '(无内容)'}
                    </Typography.Paragraph>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Drawer>

      <Drawer
        width={980}
        title={candidateDetail ? `候补 Workflow Draft #${candidateDetail.id}` : '候补 Workflow Draft'}
        open={candidateDrawerOpen}
        onClose={() => {
          setCandidateDrawerOpen(false);
          setCandidateDetail(null);
        }}
        extra={
          <Space>
            <Button onClick={() => void saveCandidateOnly()} loading={candidateSaving || publishingCandidate}>
              保存草案
            </Button>
            <Button type="primary" onClick={() => void saveAndPublishCandidate()} loading={publishingCandidate}>
              保存并升级正式 Definition
            </Button>
          </Space>
        }
      >
        {candidateDetail ? (
          <Form
            form={candidateForm}
            layout="vertical"
            initialValues={{
              operator: 'SYSTEM'
            }}
          >
            <Row gutter={12}>
              <Col span={8}>
                <Form.Item label="Draft Key" name="draftKey" rules={[{ required: true, message: '请输入 Draft Key' }]}>
                  <Input maxLength={128} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="租户" name="tenantId" rules={[{ required: true, message: '请输入租户' }]}> 
                  <Input maxLength={64} />
                </Form.Item>
              </Col>
              <Col span={8} />
              <Col span={12}>
                <Form.Item label="分类" name="category" rules={[{ required: true, message: '请输入分类' }]}> 
                  <Input maxLength={128} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
                  <Input maxLength={256} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label="路由描述" name="routeDescription" rules={[{ required: true, message: '请输入路由描述' }]}> 
              <TextArea rows={2} />
            </Form.Item>

            <Form.Item label="Input Schema Version" name="inputSchemaVersion">
              <Input maxLength={32} />
            </Form.Item>

            <Form.Item label="graphDefinition(JSON 对象)" name="graphDefinitionText" rules={[{ required: true, message: '请填写 graphDefinition' }]}>
              <TextArea rows={9} />
            </Form.Item>
            <Form.Item label="inputSchema(JSON 对象)" name="inputSchemaText" rules={[{ required: true, message: '请填写 inputSchema' }]}>
              <TextArea rows={6} />
            </Form.Item>
            <Form.Item label="defaultConfig(JSON 对象)" name="defaultConfigText" rules={[{ required: true, message: '请填写 defaultConfig' }]}>
              <TextArea rows={6} />
            </Form.Item>
            <Form.Item label="toolPolicy(JSON 对象)" name="toolPolicyText" rules={[{ required: true, message: '请填写 toolPolicy' }]}>
              <TextArea rows={6} />
            </Form.Item>
            <Form.Item label="constraints(JSON 对象)" name="constraintsText" rules={[{ required: true, message: '请填写 constraints' }]}>
              <TextArea rows={6} />
            </Form.Item>

            <Divider />
            <Form.Item
              label="操作人（用于升级正式 Definition）"
              name="operator"
              tooltip="升级为正式 Definition 时写入 approvedBy/createdBy"
              rules={[{ required: true, message: '请输入操作人' }]}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Form>
        ) : (
          <Empty description="未加载候补 Workflow Draft" />
        )}
      </Drawer>
    </div>
  );
};
