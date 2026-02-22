import { SearchOutlined } from '@ant-design/icons';
import { Button, Card, Input, Segmented, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { InputRef } from 'antd/es/input';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAriaLive } from '@/shared/a11y/AriaLiveProvider';
import { agentApi } from '@/shared/api/agentApi';
import { useHotkeys } from '@/shared/hotkeys/useHotkeys';
import type { TaskDetailDTO } from '@/shared/types/api';
import type { ShortcutMatchContext, ShortcutRegistration } from '@/shared/types/shortcut';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';
import { StatusTag } from '@/shared/ui/StatusTag';

interface TaskRow {
  taskId: number;
  goal: string;
  agent: string;
  status: string;
  updatedAt: string;
  duration: string;
  owner: string;
}

const statusOptions = ['ALL', 'RUNNING', 'COMPLETED', 'FAILED', 'READY', 'PENDING'];

const formatDuration = (durationMs?: number) => {
  if (!durationMs || durationMs <= 0) {
    return '-';
  }
  if (durationMs < 1000) {
    return `${durationMs}ms`;
  }
  if (durationMs < 60_000) {
    return `${(durationMs / 1000).toFixed(1)}s`;
  }
  return `${(durationMs / 60_000).toFixed(1)}m`;
};

const normalizeTaskRow = (task: TaskDetailDTO): TaskRow => ({
  taskId: task.taskId,
  goal: task.name || task.nodeId,
  agent: task.taskType || '-',
  status: task.status || 'UNKNOWN',
  updatedAt: task.updatedAt ? new Date(task.updatedAt).toLocaleString() : '-',
  duration: formatDuration(task.latestExecutionTimeMs),
  owner: task.claimOwner || '-'
});

export const TasksPage = () => {
  const navigate = useNavigate();
  const { announce } = useAriaLive();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('ALL');
  const [viewMode, setViewMode] = useState<'列表视图' | '看板视图'>('列表视图');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [sourceTasks, setSourceTasks] = useState<TaskRow[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const keywordInputRef = useRef<InputRef>(null);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await agentApi.getTasksPaged({
        page,
        size,
        status: status === 'ALL' ? undefined : status,
        keyword: keyword.trim() || undefined
      });
      setSourceTasks((data.items || []).map(normalizeTaskRow));
      setTotal(data.total || 0);
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      setError(text);
      message.error(`加载任务失败: ${text}`);
    } finally {
      setLoading(false);
    }
  }, [keyword, page, size, status]);

  useEffect(() => {
    void fetchTasks();
  }, [fetchTasks]);

  const runQuery = useCallback(() => {
    setPage((current) => {
      if (current === 1) {
        void fetchTasks();
      }
      return 1;
    });
    announce('已执行任务查询');
  }, [announce, fetchTasks]);

  const tasksShortcutRegistrations = useMemo<ShortcutRegistration[]>(
    () => [
      {
        definition: {
          id: 'tasks.focus-filter',
          combo: 'f',
          description: '聚焦任务关键词筛选输入',
          scope: 'tasks',
          priority: 40
        },
        when: (context: ShortcutMatchContext) => context.locationPathname === '/tasks',
        handler: (): boolean => {
          keywordInputRef.current?.focus({ cursor: 'all' });
          announce('已聚焦任务筛选输入');
          return true;
        }
      },
      {
        definition: {
          id: 'tasks.run-query',
          combo: 'mod+enter',
          description: '执行任务查询',
          scope: 'tasks',
          priority: 40,
          allowInInput: true
        },
        when: (context: ShortcutMatchContext) => context.locationPathname === '/tasks',
        handler: (): boolean => {
          runQuery();
          return true;
        }
      },
      {
        definition: {
          id: 'tasks.toggle-view-mode',
          combo: 'mod+shift+v',
          description: '切换列表/看板视图',
          scope: 'tasks',
          priority: 30,
          allowInInput: true
        },
        when: (context: ShortcutMatchContext) => context.locationPathname === '/tasks',
        handler: (): boolean => {
          setViewMode((previous) => (previous === '列表视图' ? '看板视图' : '列表视图'));
          announce('已切换任务视图');
          return true;
        }
      }
    ],
    [announce, runQuery]
  );

  useHotkeys(tasksShortcutRegistrations);

  const boardStats = useMemo(
    () => ({
      running: sourceTasks.filter((item) => item.status === 'RUNNING').length,
      completed: sourceTasks.filter((item) => item.status === 'COMPLETED').length,
      failed: sourceTasks.filter((item) => item.status === 'FAILED').length
    }),
    [sourceTasks]
  );

  const columns: ColumnsType<TaskRow> = [
    {
      title: '任务',
      dataIndex: 'goal',
      key: 'goal',
      render: (goal: string, row) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{goal}</Typography.Text>
          <Typography.Text type="secondary">#{row.taskId}</Typography.Text>
        </Space>
      )
    },
    {
      title: 'Agent',
      dataIndex: 'agent',
      key: 'agent'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: string) => <StatusTag status={value} />
    },
    {
      title: '负责人',
      dataIndex: 'owner',
      key: 'owner',
      width: 120,
      render: (owner: string) => <Tag>{owner}</Tag>
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      key: 'duration',
      width: 100
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, row) => (
        <Button type="link" onClick={() => navigate(`/tasks/${row.taskId}`)}>
          详情
        </Button>
      )
    }
  ];

  return (
    <div className="page-container">
      <PageHeader
        title="任务中心"
        description="按状态、时间和负责人筛选任务，快速定位异常并执行重试。"
        primaryActionText="新建执行"
        onPrimaryAction={() => navigate('/sessions')}
      />

      <Card className="app-card page-section">
        <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space wrap>
            <Input
              ref={keywordInputRef}
              allowClear
              placeholder="搜索任务目标 / Agent / 负责人"
              prefix={<SearchOutlined />}
              style={{ width: 320 }}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              aria-label="任务关键词筛选"
            />

            <Select
              style={{ width: 180 }}
              value={status}
              onChange={setStatus}
              options={statusOptions.map((item) => ({ label: item === 'ALL' ? '全部状态' : item, value: item }))}
              aria-label="任务状态筛选"
            />

            <Button
              type="primary"
              onClick={runQuery}
              aria-label="执行任务查询"
            >
              查询
            </Button>
          </Space>

          <Segmented
            value={viewMode}
            onChange={(value) => setViewMode(value as '列表视图' | '看板视图')}
            options={['列表视图', '看板视图']}
            aria-label="任务视图模式"
          />
        </Space>

        <div style={{ marginTop: 16 }}>
          {loading ? <StateView type="loading" title="加载任务中" /> : null}
          {!loading && error ? <StateView type="error" title="任务加载失败" description={error} actionText="重试" onAction={() => void fetchTasks()} /> : null}

          {!loading && !error && sourceTasks.length === 0 ? (
            <StateView
              type="empty"
              title="暂无可展示任务"
              description="请先在“对话与执行”页发起会话并触发 Plan 执行。"
              actionText="前往对话与执行"
              onAction={() => navigate('/sessions')}
            />
          ) : null}

          {!loading && !error && sourceTasks.length > 0
            ? viewMode === '列表视图'
              ? (
                <Table
                  rowKey="taskId"
                  columns={columns}
                  dataSource={sourceTasks}
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
                )
              : (
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Typography.Text type="secondary">看板视图用于快速查看当前页状态分布。</Typography.Text>
                  <Card type="inner" title="RUNNING">
                    {boardStats.running} 个任务
                  </Card>
                  <Card type="inner" title="COMPLETED">
                    {boardStats.completed} 个任务
                  </Card>
                  <Card type="inner" title="FAILED">
                    {boardStats.failed} 个任务
                  </Card>
                </Space>
                )
            : null}
        </div>
      </Card>
    </div>
  );
};
