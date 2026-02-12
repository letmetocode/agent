import {
  Alert,
  Button,
  Card,
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
  Col,
  Row,
  Space,
  Spin,
  Tabs,
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
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';
import { StatusTag } from '@/shared/ui/StatusTag';
import type {
  PlanTaskStatsDTO,
  PlanStreamEvent,
  WorkflowDraftDetailDTO,
  SessionMessageDTO,
  SessionTurnDTO,
  TaskDetailDTO
} from '@/shared/types/api';

const { TextArea } = Input;
const { Text } = Typography;

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
      render: (status: string) => <StatusTag status={status} />
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
    return <StateView type="empty" title="无效会话 ID" description="请返回对话列表重新选择会话。" />;
  }

  return (
    <div className="page-container">
      <PageHeader
        title={`会话执行 #${sid}`}
        description="输入目标后持续执行，支持中途控制、引用追溯与任务下钻。"
        primaryActionText="返回会话列表"
        onPrimaryAction={() => navigate('/sessions')}
        extra={
          <Space>
            <Button onClick={() => navigate('/tasks')}>任务中心</Button>
            <Button onClick={() => navigate('/workflows/drafts')}>Workflow 治理</Button>
          </Space>
        }
      />

      <Spin spinning={loading} className="page-section">
        <div className="chat-layout">
          <Card className="app-card chat-panel chat-left-panel" size="small" title={`Session #${sid}`}>
            <Typography.Text type="secondary">{overview?.session?.title || '未命名会话'}</Typography.Text>
            <Divider />
            <div className="chat-scrollable">
              {(overview?.plans || []).length === 0 ? (
                <StateView
                  type="empty"
                  title="暂无 Plan"
                  description="发送一条消息后，系统会为你创建新的执行计划。"
                />
              ) : (
                <List
                  size="small"
                  dataSource={overview?.plans || []}
                  renderItem={(p) => (
                    <List.Item
                      className={`conversation-plan-item ${selectedPlanId === p.planId ? 'active' : ''}`}
                      onClick={() => void switchPlan(p.planId)}
                    >
                      <List.Item.Meta
                        title={
                          <Space>
                            <StatusTag status={p.status} />
                            <span>#{p.planId}</span>
                          </Space>
                        }
                        description={<Typography.Text ellipsis={{ tooltip: p.planGoal }}>{p.planGoal}</Typography.Text>}
                      />
                    </List.Item>
                  )}
                />
              )}
            </div>
          </Card>

          <Card className="app-card chat-panel" size="small" title="对话与流式输出">
            <div className="chat-scrollable">
              {groupedMessages.length === 0 ? (
                <StateView
                  type="empty"
                  title="开始你的第一次提问"
                  description="建议输入具体目标，例如：分析失败任务并给出修复清单。"
                />
              ) : (
                <List
                  dataSource={groupedMessages}
                  renderItem={(group) => (
                    <List.Item>
                      <div style={{ width: '100%' }}>
                        <Space style={{ marginBottom: 8 }}>
                          <StatusTag status={group.turn?.status} fallback="UNKNOWN" />
                          <Typography.Text strong>Turn #{group.turnId}</Typography.Text>
                          <Typography.Text type="secondary">Plan #{group.turn?.planId || '-'}</Typography.Text>
                        </Space>
                        <List
                          size="small"
                          dataSource={group.messages}
                          renderItem={(m) => (
                            <List.Item>
                              <List.Item.Meta
                                title={
                                  <Space>
                                    <Tag>{m.role}</Tag>
                                    <Typography.Text type="secondary">
                                      {m.createdAt ? new Date(m.createdAt).toLocaleString() : '-'}
                                    </Typography.Text>
                                  </Space>
                                }
                                description={
                                  <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                                    {m.content}
                                  </Typography.Paragraph>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </div>
                    </List.Item>
                  )}
                />
              )}
            </div>

            <div className="chat-composer">
              <Space direction="vertical" style={{ width: '100%' }}>
                <TextArea value={prompt} onChange={(e) => setPrompt(e.target.value)} rows={4} placeholder="输入你的问题，触发新一轮计划执行" />
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space>
                    <Button type="primary" loading={sending} onClick={sendChat}>
                      发送并执行
                    </Button>
                    <Button onClick={() => setProcessEvents([])} disabled={processEvents.length === 0}>
                      清空过程事件
                    </Button>
                  </Space>
                  <Text className="chat-composer-hint" type="secondary">
                    {sending ? '系统执行中，可在右侧查看实时进度' : '输入目标尽量具体，可显著提升执行质量'}
                  </Text>
                </Space>
              </Space>
            </div>
          </Card>

          <Card className="app-card chat-panel chat-right-panel" size="small" title="执行上下文">
            <Tabs
              defaultActiveKey="tasks"
              items={[
                {
                  key: 'progress',
                  label: `进度 (${processEvents.length})`,
                  children: (
                    <Space direction="vertical" style={{ width: '100%' }} size="middle">
                      <Descriptions column={1} size="small" title="当前 Plan">
                        <Descriptions.Item label="PlanId">{planDetail?.planId || '-'}</Descriptions.Item>
                        <Descriptions.Item label="状态">
                          <StatusTag status={planDetail?.status} />
                        </Descriptions.Item>
                        <Descriptions.Item label="目标">{planDetail?.planGoal || '-'}</Descriptions.Item>
                        <Descriptions.Item label="任务统计">
                          总 {currentStats.total} / 运行中 {currentStats.runningLike} / 失败 {currentStats.failed}
                        </Descriptions.Item>
                      </Descriptions>

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
                    </Space>
                  )
                },
                {
                  key: 'references',
                  label: '引用与异常',
                  children: (
                    <Space direction="vertical" style={{ width: '100%' }} size="middle">
                      {fallbackCandidateHint ? (
                        <Alert
                          type="warning"
                          showIcon
                          message="当前使用候补 Workflow Draft"
                          description={
                            <Space direction="vertical" style={{ width: '100%' }}>
                              <Text type="secondary">候补 Draft ID：{fallbackCandidateId || '-'}，可编辑后发布为正式 Definition。</Text>
                              <Space>
                                <Button onClick={() => void ensureCandidateDetailLoaded()} loading={candidateLoading}>
                                  查看/编辑 Draft
                                </Button>
                                <Button type="link" onClick={() => navigate('/workflows/drafts')}>
                                  进入治理页
                                </Button>
                              </Space>
                              {candidateHintLoading ? <Text type="secondary">正在校验候补 Workflow Draft...</Text> : null}
                            </Space>
                          }
                        />
                      ) : (
                        <StateView type="empty" title="暂无候补草案提示" description="当前回合命中了正式 Workflow Definition。" />
                      )}

                      {planDetail?.errorSummary || failureTasks.length > 0 ? (
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
                      ) : null}
                    </Space>
                  )
                },
                {
                  key: 'tasks',
                  label: `任务 (${planTasks.length})`,
                  children: (
                    <Table<TaskDetailDTO>
                      size="small"
                      rowKey="taskId"
                      columns={taskColumns}
                      dataSource={planTasks}
                      pagination={{ pageSize: 8, showSizeChanger: false }}
                      scroll={{ x: 760 }}
                    />
                  )
                }
              ]}
            />
          </Card>
        </div>
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
