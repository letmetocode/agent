import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Card, Drawer, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { PlanLogDTO, ToolPolicyLogDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

type LogMode = 'GENERAL' | 'TOOL_POLICY';

interface LogRow {
  key: string;
  time: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  traceId: string;
  taskId: string;
  message: string;
  raw: string;
  policyAction?: string;
  policyMode?: string;
  selectedAgentKey?: string;
}

const parsePositiveNumber = (value: string | null, fallback: number) => {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return Math.floor(parsed);
};

const parseLevelFilter = (value: string | null): 'ALL' | LogRow['level'] => {
  if (!value) {
    return 'ALL';
  }
  const normalized = value.trim().toUpperCase();
  if (normalized === 'INFO' || normalized === 'WARN' || normalized === 'ERROR') {
    return normalized;
  }
  return 'ALL';
};

const parseLogMode = (value: string | null): LogMode => {
  const normalized = (value || '').trim().toUpperCase();
  return normalized === 'TOOL_POLICY' ? 'TOOL_POLICY' : 'GENERAL';
};

const toLogLevel = (eventName?: string, eventData?: Record<string, unknown>): LogRow['level'] => {
  if (!eventName) {
    return 'INFO';
  }
  if (eventName === 'TaskCompleted') {
    const status = eventData?.status ? String(eventData.status).toUpperCase() : '';
    if (status === 'FAILED') {
      return 'ERROR';
    }
    return 'INFO';
  }
  if (eventName === 'TaskLog') {
    return 'WARN';
  }
  if (eventName === 'PlanFinished') {
    return 'ERROR';
  }
  return 'INFO';
};

const normalizeGeneralLogRow = (event: PlanLogDTO): LogRow => {
  const eventName = event.eventName || event.eventType || 'UnknownEvent';
  const eventData = (event.eventData || {}) as Record<string, unknown>;
  const raw = JSON.stringify(eventData, null, 2);
  const traceId = event.traceId ? String(event.traceId) : eventData.traceId ? String(eventData.traceId) : '-';
  return {
    key: String(event.id),
    time: event.createdAt ? new Date(event.createdAt).toLocaleString() : '-',
    level: (event.level as LogRow['level']) || toLogLevel(eventName, eventData),
    traceId,
    taskId: event.taskId ? String(event.taskId) : '-',
    message: eventName,
    raw
  };
};

const normalizeToolPolicyLogRow = (event: ToolPolicyLogDTO): LogRow => {
  const eventData = (event.eventData || {}) as Record<string, unknown>;
  const eventName = event.eventName || event.eventType || 'ToolPolicyAudit';
  const policyAction = event.policyAction || (eventData.policyAction ? String(eventData.policyAction) : '');
  const policyMode = event.policyMode || (eventData.policyMode ? String(eventData.policyMode) : '');
  const selectedAgentKey = event.selectedAgentKey || (eventData.selectedAgentKey ? String(eventData.selectedAgentKey) : '');
  const messageText = `${eventName}${policyAction ? ` · ${policyAction}` : ''}${policyMode ? `(${policyMode})` : ''}`;
  return {
    key: String(event.id),
    time: event.createdAt ? new Date(event.createdAt).toLocaleString() : '-',
    level: (event.level as LogRow['level']) || toLogLevel(eventName, eventData),
    traceId: event.traceId ? String(event.traceId) : eventData.traceId ? String(eventData.traceId) : '-',
    taskId: event.taskId ? String(event.taskId) : '-',
    message: messageText,
    raw: JSON.stringify(eventData, null, 2),
    policyAction,
    policyMode,
    selectedAgentKey
  };
};

const levelColor: Record<LogRow['level'], string> = {
  INFO: 'blue',
  WARN: 'warning',
  ERROR: 'error'
};

export const LogsPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedLog, setSelectedLog] = useState<LogRow | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [rows, setRows] = useState<LogRow[]>([]);
  const [total, setTotal] = useState(0);

  const [mode, setMode] = useState<LogMode>(() => parseLogMode(searchParams.get('mode')));
  const [page, setPage] = useState(() => parsePositiveNumber(searchParams.get('page'), 1));
  const [size, setSize] = useState(() => parsePositiveNumber(searchParams.get('size'), 10));
  const [levelFilter, setLevelFilter] = useState<'ALL' | LogRow['level']>(() => parseLevelFilter(searchParams.get('level')));
  const [traceFilter, setTraceFilter] = useState(() => searchParams.get('traceId') || '');
  const [taskFilter, setTaskFilter] = useState(() => searchParams.get('taskId') || '');
  const [keyword, setKeyword] = useState(() => searchParams.get('keyword') || '');
  const [policyActionFilter, setPolicyActionFilter] = useState(() => searchParams.get('policyAction') || '');
  const [policyModeFilter, setPolicyModeFilter] = useState(() => searchParams.get('policyMode') || '');

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const taskIdValue = taskFilter.trim() ? Number(taskFilter.trim()) : undefined;
      if (mode === 'GENERAL') {
        const pageData = await agentApi.getLogsPaged({
          page,
          size,
          level: levelFilter === 'ALL' ? undefined : levelFilter,
          traceId: traceFilter.trim() || undefined,
          taskId: taskIdValue && Number.isFinite(taskIdValue) ? taskIdValue : undefined,
          keyword: keyword.trim() || undefined
        });
        setRows((pageData.items || []).map(normalizeGeneralLogRow));
        setTotal(pageData.total || 0);
      } else {
        const pageData = await agentApi.getToolPolicyLogsPaged({
          page,
          size,
          taskId: taskIdValue && Number.isFinite(taskIdValue) ? taskIdValue : undefined,
          policyAction: policyActionFilter.trim() || undefined,
          policyMode: policyModeFilter.trim() || undefined,
          keyword: keyword.trim() || undefined
        });
        setRows((pageData.items || []).map(normalizeToolPolicyLogRow));
        setTotal(pageData.total || 0);
      }
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      setError(text);
      message.error(`加载日志失败: ${text}`);
    } finally {
      setLoading(false);
    }
  }, [keyword, levelFilter, mode, page, policyActionFilter, policyModeFilter, size, taskFilter, traceFilter]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (mode !== 'GENERAL') {
      params.set('mode', mode);
    }
    if (mode === 'GENERAL' && levelFilter !== 'ALL') {
      params.set('level', levelFilter);
    }
    if (mode === 'GENERAL' && traceFilter.trim()) {
      params.set('traceId', traceFilter.trim());
    }
    if (mode === 'TOOL_POLICY' && policyActionFilter.trim()) {
      params.set('policyAction', policyActionFilter.trim());
    }
    if (mode === 'TOOL_POLICY' && policyModeFilter.trim()) {
      params.set('policyMode', policyModeFilter.trim());
    }
    if (taskFilter.trim()) {
      params.set('taskId', taskFilter.trim());
    }
    if (keyword.trim()) {
      params.set('keyword', keyword.trim());
    }
    if (page > 1) {
      params.set('page', String(page));
    }
    if (size !== 10) {
      params.set('size', String(size));
    }
    setSearchParams(params, { replace: true });
  }, [keyword, levelFilter, mode, page, policyActionFilter, policyModeFilter, setSearchParams, size, taskFilter, traceFilter]);

  const columns: ColumnsType<LogRow> = [
    { title: '时间', dataIndex: 'time', key: 'time', width: 180 },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 100,
      render: (level: LogRow['level']) => <Tag color={levelColor[level]}>{level}</Tag>
    },
    { title: 'Trace ID', dataIndex: 'traceId', key: 'traceId', width: 180 },
    { title: 'Task ID', dataIndex: 'taskId', key: 'taskId', width: 100 },
    ...(mode === 'TOOL_POLICY'
      ? [
          {
            title: '策略命中',
            key: 'policy',
            width: 220,
            render: (_: unknown, row: LogRow) => (
              <Space size={4} wrap>
                <Tag color={row.policyAction ? 'processing' : 'default'}>{row.policyAction || '-'}</Tag>
                <Tag>{row.policyMode || '-'}</Tag>
                {row.selectedAgentKey ? <Tag color="blue">{row.selectedAgentKey}</Tag> : null}
              </Space>
            )
          }
        ]
      : []),
    { title: '摘要', dataIndex: 'message', key: 'message' },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, row) => (
        <Button type="link" onClick={() => setSelectedLog(row)}>
          详情
        </Button>
      )
    }
  ];

  return (
    <div className="page-container">
      <PageHeader
        title="日志检索"
        description="按 level、traceId、taskId 快速定位问题，并支持工具策略审计日志回放。"
      />

      <Card className="app-card page-section">
        <Space wrap>
          <Select<LogMode>
            value={mode}
            onChange={(value) => {
              setMode(value);
              setPage(1);
            }}
            style={{ width: 180 }}
            options={[
              { label: '通用日志', value: 'GENERAL' },
              { label: '工具策略日志', value: 'TOOL_POLICY' }
            ]}
          />
          {mode === 'GENERAL' ? (
            <>
              <Select
                value={levelFilter}
                onChange={(value) => setLevelFilter(value as 'ALL' | LogRow['level'])}
                style={{ width: 160 }}
                options={[
                  { label: '全部级别', value: 'ALL' },
                  { label: 'INFO', value: 'INFO' },
                  { label: 'WARN', value: 'WARN' },
                  { label: 'ERROR', value: 'ERROR' }
                ]}
              />
              <Input style={{ width: 200 }} placeholder="按 traceId 过滤" value={traceFilter} onChange={(event) => setTraceFilter(event.target.value)} />
            </>
          ) : (
            <>
              <Input
                style={{ width: 200 }}
                placeholder="policyAction，如 ALLOW/BLOCK"
                value={policyActionFilter}
                onChange={(event) => setPolicyActionFilter(event.target.value)}
              />
              <Input
                style={{ width: 200 }}
                placeholder="policyMode，如 STRICT/RELAXED"
                value={policyModeFilter}
                onChange={(event) => setPolicyModeFilter(event.target.value)}
              />
            </>
          )}
          <Input style={{ width: 200 }} placeholder="按 taskId 过滤" value={taskFilter} onChange={(event) => setTaskFilter(event.target.value)} />
          <Input
            style={{ width: 300 }}
            placeholder="关键词搜索"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              void loadLogs();
            }}
          >
            查询
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void loadLogs()}>
            刷新
          </Button>
          <Button
            onClick={() => {
              setMode('GENERAL');
              setLevelFilter('ALL');
              setTraceFilter('');
              setPolicyActionFilter('');
              setPolicyModeFilter('');
              setTaskFilter('');
              setKeyword('');
              setPage(1);
              setSize(10);
            }}
          >
            清空筛选
          </Button>
        </Space>

        <div style={{ marginTop: 16 }}>
          {loading ? <StateView type="loading" title="加载日志中" /> : null}
          {!loading && error ? <StateView type="error" title="日志加载失败" description={error} actionText="重试" onAction={() => void loadLogs()} /> : null}
          {!loading && !error && rows.length === 0 ? <StateView type="empty" title="暂无匹配日志" description="请先执行任务，或调整筛选条件。" /> : null}
          {!loading && !error && rows.length > 0 ? (
            <Table
              rowKey="key"
              columns={columns}
              dataSource={rows}
              pagination={{
                current: page,
                pageSize: size,
                total,
                showSizeChanger: true,
                onChange: (nextPage, nextSize) => {
                  setPage(nextPage);
                  setSize(nextSize || 10);
                }
              }}
              onChange={(pagination: TablePaginationConfig) => {
                setPage(pagination.current || 1);
                setSize(pagination.pageSize || 10);
              }}
            />
          ) : null}
        </div>
      </Card>

      <Drawer
        width={620}
        title={selectedLog ? `日志详情 · ${selectedLog.traceId}` : '日志详情'}
        open={Boolean(selectedLog)}
        onClose={() => setSelectedLog(null)}
      >
        {selectedLog ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text strong>{selectedLog.message}</Typography.Text>
            <Typography.Text type="secondary">{selectedLog.time}</Typography.Text>
            <pre className="json-block">{selectedLog.raw}</pre>
          </Space>
        ) : null}
      </Drawer>
    </div>
  );
};
