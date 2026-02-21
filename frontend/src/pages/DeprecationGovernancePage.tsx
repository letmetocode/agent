import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Input, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { agentApi } from '@/shared/api/agentApi';
import type { DeprecationRegistryItemDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

const { Text } = Typography;

type StatusFilter = 'ALL' | 'ANNOUNCED' | 'MIGRATING' | 'REMOVED';

const statusColor: Record<string, string> = {
  ANNOUNCED: 'gold',
  MIGRATING: 'processing',
  REMOVED: 'default'
};

const toErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err));

const safeDate = (value?: string) => {
  if (!value) {
    return '-';
  }
  const ts = new Date(value).getTime();
  if (!Number.isFinite(ts)) {
    return value;
  }
  return new Date(ts).toLocaleDateString();
};

export const DeprecationGovernancePage = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [rows, setRows] = useState<DeprecationRegistryItemDTO[]>([]);
  const [generatedAt, setGeneratedAt] = useState<string>();
  const [minNoticeWindowDays, setMinNoticeWindowDays] = useState<number>(30);
  const [statusSummary, setStatusSummary] = useState<Record<string, number>>({});
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [includeRemoved, setIncludeRemoved] = useState(true);
  const [keyword, setKeyword] = useState('');

  const loadData = async (nextStatus = statusFilter, nextIncludeRemoved = includeRemoved) => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await agentApi.getDeprecationRegistry({
        status: nextStatus === 'ALL' ? undefined : nextStatus,
        includeRemoved: nextIncludeRemoved
      });
      setRows(data.items || []);
      setGeneratedAt(data.generatedAt);
      setStatusSummary(data.statusSummary || {});
      setMinNoticeWindowDays(data.policy?.minNoticeWindowDays || 30);
    } catch (err) {
      const text = toErrorMessage(err);
      setError(text);
      message.error(`加载废弃治理注册表失败: ${text}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredRows = useMemo(() => {
    const key = keyword.trim().toLowerCase();
    if (!key) {
      return rows;
    }
    return rows.filter((item) => {
      const text = [
        item.id,
        item.legacyPath,
        item.replacementPath,
        item.owner,
        item.migrationDoc,
        item.notes
      ]
        .map((x) => (x == null ? '' : String(x)))
        .join(' ')
        .toLowerCase();
      return text.includes(key);
    });
  }, [keyword, rows]);

  const columns: ColumnsType<DeprecationRegistryItemDTO> = [
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (value?: string) => <Tag color={statusColor[value || ''] || 'default'}>{value || 'UNKNOWN'}</Tag>
    },
    {
      title: 'ID',
      dataIndex: 'id',
      width: 170,
      ellipsis: true
    },
    {
      title: '旧接口',
      dataIndex: 'legacyPath',
      width: 260,
      ellipsis: true
    },
    {
      title: '替代方案',
      dataIndex: 'replacementPath',
      width: 300,
      ellipsis: true
    },
    {
      title: '公告/下线',
      key: 'window',
      width: 180,
      render: (_, row) => `${safeDate(row.announcedAt)} -> ${safeDate(row.sunsetAt)}`
    },
    {
      title: '窗口天数',
      dataIndex: 'noticeWindowDays',
      width: 120,
      render: (value?: number) => (typeof value === 'number' ? value : '-')
    },
    {
      title: '距下线',
      dataIndex: 'daysToSunset',
      width: 100,
      render: (value?: number) => {
        if (typeof value !== 'number') {
          return '-';
        }
        return `${value}d`;
      }
    },
    {
      title: '有效性',
      dataIndex: 'valid',
      width: 220,
      render: (valid: boolean | undefined, row: DeprecationRegistryItemDTO) => {
        if (valid) {
          return <Tag color="success">VALID</Tag>;
        }
        const issues = row.issues || [];
        return (
          <Tooltip title={issues.join('\n') || 'INVALID'}>
            <Tag color="error">INVALID</Tag>
          </Tooltip>
        );
      }
    },
    {
      title: 'Owner',
      dataIndex: 'owner',
      width: 120
    }
  ];

  return (
    <div className="page-container">
      <PageHeader
        title="废弃治理注册表"
        description="统一查看旧接口公告窗口、迁移路径与下线状态，避免版本迁移失真。"
        primaryActionText="刷新"
        onPrimaryAction={() => void loadData()}
      />

      <Card className="app-card page-section">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {error ? <Alert type="error" showIcon message={error} /> : null}

          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space wrap>
              <Select<StatusFilter>
                style={{ width: 160 }}
                value={statusFilter}
                onChange={async (value) => {
                  setStatusFilter(value);
                  await loadData(value, includeRemoved);
                }}
                options={[
                  { label: '全部状态', value: 'ALL' },
                  { label: 'ANNOUNCED', value: 'ANNOUNCED' },
                  { label: 'MIGRATING', value: 'MIGRATING' },
                  { label: 'REMOVED', value: 'REMOVED' }
                ]}
              />
              <Select<'true' | 'false'>
                style={{ width: 160 }}
                value={includeRemoved ? 'true' : 'false'}
                onChange={async (value) => {
                  const next = value === 'true';
                  setIncludeRemoved(next);
                  await loadData(statusFilter, next);
                }}
                options={[
                  { label: '包含已下线', value: 'true' },
                  { label: '不含已下线', value: 'false' }
                ]}
              />
              <Input
                style={{ width: 320 }}
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="搜索 ID / 接口路径 / owner / 文档"
              />
            </Space>
            <Space>
              <Text type="secondary">最小公告窗口：{minNoticeWindowDays} 天</Text>
              <Text type="secondary">生成时间：{generatedAt ? new Date(generatedAt).toLocaleString() : '-'}</Text>
            </Space>
          </Space>

          <Space wrap>
            <Tag color="gold">ANNOUNCED: {statusSummary.ANNOUNCED || 0}</Tag>
            <Tag color="processing">MIGRATING: {statusSummary.MIGRATING || 0}</Tag>
            <Tag>REMOVED: {statusSummary.REMOVED || 0}</Tag>
            <Tag color="blue">TOTAL: {rows.length}</Tag>
          </Space>

          {loading ? (
            <StateView type="loading" title="加载废弃治理数据中" />
          ) : (
            <Table
              rowKey={(row) => row.id || `${row.legacyPath || ''}-${row.announcedAt || ''}`}
              columns={columns}
              dataSource={filteredRows}
              scroll={{ x: 1700 }}
              pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
              locale={{ emptyText: '暂无废弃治理记录' }}
            />
          )}
        </Space>
      </Card>
    </div>
  );
};
