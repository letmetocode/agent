import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Card, Drawer, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { PlanLogDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

interface LogRow {
  key: string;
  time: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  traceId: string;
  taskId: string;
  message: string;
  raw: string;
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

const normalizeLogRow = (event: PlanLogDTO): LogRow => {
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

  const [page, setPage] = useState(() => parsePositiveNumber(searchParams.get('page'), 1));
  const [size, setSize] = useState(() => parsePositiveNumber(searchParams.get('size'), 10));
  const [levelFilter, setLevelFilter] = useState<'ALL' | LogRow['level']>(() => parseLevelFilter(searchParams.get('level')));
  const [traceFilter, setTraceFilter] = useState(() => searchParams.get('traceId') || '');
  const [taskFilter, setTaskFilter] = useState(() => searchParams.get('taskId') || '');
  const [keyword, setKeyword] = useState(() => searchParams.get('keyword') || '');

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const taskIdValue = taskFilter.trim() ? Number(taskFilter.trim()) : undefined;
      const pageData = await agentApi.getLogsPaged({
        page,
        size,
        level: levelFilter === 'ALL' ? undefined : levelFilter,
        traceId: traceFilter.trim() || undefined,
        taskId: taskIdValue && Number.isFinite(taskIdValue) ? taskIdValue : undefined,
        keyword: keyword.trim() || undefined
      });
      setRows((pageData.items || []).map(normalizeLogRow));
      setTotal(pageData.total || 0);
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      setError(text);
      message.error(`加载日志失败: ${text}`);
    } finally {
      setLoading(false);
    }
  }, [keyword, levelFilter, page, size, taskFilter, traceFilter]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (levelFilter !== 'ALL') {
      params.set('level', levelFilter);
    }
    if (traceFilter.trim()) {
      params.set('traceId', traceFilter.trim());
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
  }, [keyword, levelFilter, page, setSearchParams, size, taskFilter, traceFilter]);

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
      <PageHeader title="日志检索" description="按 level、traceId、taskId 快速定位问题，支持结构化排障。" />

      <Card className="app-card page-section">
        <Space wrap>
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
              setLevelFilter('ALL');
              setTraceFilter('');
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
