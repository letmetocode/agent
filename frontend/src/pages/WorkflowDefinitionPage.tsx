import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { WorkflowDefinitionDetailDTO, WorkflowDefinitionSummaryDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';

const { Text } = Typography;

const STATUS_OPTIONS: Array<{ label: string; value?: string }> = [
  { label: '全部', value: undefined },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'DISABLED', value: 'DISABLED' },
  { label: 'ARCHIVED', value: 'ARCHIVED' }
];

const statusColor = (status?: string) => {
  switch ((status || '').toUpperCase()) {
    case 'ACTIVE':
      return 'success';
    case 'DISABLED':
      return 'warning';
    case 'ARCHIVED':
      return 'default';
    default:
      return 'processing';
  }
};

const toErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err));

const stringifyJson = (value?: unknown) => JSON.stringify(value || {}, null, 2);

export const WorkflowDefinitionPage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [list, setList] = useState<WorkflowDefinitionSummaryDTO[]>([]);
  const [error, setError] = useState<string>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [selected, setSelected] = useState<WorkflowDefinitionDetailDTO>();

  const loadDefinitions = useCallback(async (status?: string) => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await agentApi.getWorkflowDefinitions(status);
      setList(data || []);
    } catch (err) {
      const text = toErrorMessage(err);
      setError(text);
      message.error(`加载 Definition 列表失败: ${text}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDefinitions(statusFilter);
  }, [loadDefinitions, statusFilter]);

  const loadDefinitionDetail = useCallback(async (id: number) => {
    setDetailLoading(true);
    try {
      const data = await agentApi.getWorkflowDefinitionDetail(id);
      setSelected(data);
      setDetailOpen(true);
    } catch (err) {
      message.error(`加载 Definition 详情失败: ${toErrorMessage(err)}`);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const columns: ColumnsType<WorkflowDefinitionSummaryDTO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'Definition Key', dataIndex: 'definitionKey', width: 220, ellipsis: true },
      { title: '版本', dataIndex: 'version', width: 100 },
      { title: '租户', dataIndex: 'tenantId', width: 130, ellipsis: true },
      { title: '分类', dataIndex: 'category', width: 140, ellipsis: true },
      { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        render: (value?: string) => <Tag color={statusColor(value)}>{value || 'UNKNOWN'}</Tag>
      },
      {
        title: '来源 Draft',
        dataIndex: 'publishedFromDraftId',
        width: 120,
        render: (value?: number) => (value ? `#${value}` : '-')
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        width: 190,
        render: (value?: string) => (value ? new Date(value).toLocaleString() : '-')
      },
      {
        title: '操作',
        key: 'actions',
        width: 120,
        fixed: 'right',
        render: (_, record) => (
          <Button size="small" onClick={() => void loadDefinitionDetail(record.id)}>
            查看详情
          </Button>
        )
      }
    ],
    [loadDefinitionDetail]
  );

  return (
    <div className="page-container">
      <PageHeader
        title="Workflow Definition 管理"
        description="查看已发布 Definition 版本，核对发布来源与运行配置。"
        primaryActionText="前往 Draft 治理"
        onPrimaryAction={() => navigate('/workflows/drafts')}
        extra={<Button onClick={() => void loadDefinitions(statusFilter)}>刷新</Button>}
      />

      <Card className="app-card page-section">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {error ? <Alert type="error" showIcon message={error} /> : null}

          <Space wrap>
            <Text type="secondary">状态筛选</Text>
            <Select
              style={{ width: 180 }}
              value={statusFilter}
              allowClear
              placeholder="选择状态"
              options={STATUS_OPTIONS}
              onChange={(value) => {
                const normalized = value || undefined;
                setStatusFilter(normalized);
              }}
            />
          </Space>

          <Table<WorkflowDefinitionSummaryDTO>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={list}
            scroll={{ x: 1540 }}
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
            locale={{ emptyText: <Empty description="暂无 Workflow Definition" /> }}
          />
        </Space>
      </Card>

      <Drawer
        width={1160}
        title={selected ? `Definition 详情 #${selected.id}` : 'Definition 详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelected(undefined);
        }}
      >
        {detailLoading ? (
          <div style={{ padding: '24px 0' }}>
            <Text type="secondary">正在加载详情...</Text>
          </div>
        ) : selected ? (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="ID">{selected.id}</Descriptions.Item>
              <Descriptions.Item label="Definition Key">{selected.definitionKey}</Descriptions.Item>
              <Descriptions.Item label="版本">{selected.version}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor(selected.status)}>{selected.status || 'UNKNOWN'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="租户">{selected.tenantId || '-'}</Descriptions.Item>
              <Descriptions.Item label="分类">{selected.category || '-'}</Descriptions.Item>
              <Descriptions.Item label="名称">{selected.name || '-'}</Descriptions.Item>
              <Descriptions.Item label="来源 Draft">{selected.publishedFromDraftId || '-'}</Descriptions.Item>
              <Descriptions.Item label="路由描述" span={2}>
                {selected.routeDescription || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="输入版本">{selected.inputSchemaVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="节点签名">{selected.nodeSignature || '-'}</Descriptions.Item>
              <Descriptions.Item label="审批人">{selected.approvedBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="审批时间">
                {selected.approvedAt ? new Date(selected.approvedAt).toLocaleString() : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {selected.createdAt ? new Date(selected.createdAt).toLocaleString() : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {selected.updatedAt ? new Date(selected.updatedAt).toLocaleString() : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title="Graph Definition">
              <pre className="json-block">{stringifyJson(selected.graphDefinition)}</pre>
            </Card>
            <Card size="small" title="Input Schema">
              <pre className="json-block">{stringifyJson(selected.inputSchema)}</pre>
            </Card>
            <Card size="small" title="Default Config">
              <pre className="json-block">{stringifyJson(selected.defaultConfig)}</pre>
            </Card>
            <Card size="small" title="Tool Policy">
              <pre className="json-block">{stringifyJson(selected.toolPolicy)}</pre>
            </Card>
            <Card size="small" title="Constraints">
              <pre className="json-block">{stringifyJson(selected.constraints)}</pre>
            </Card>
          </Space>
        ) : (
          <Empty description="未选择 Definition" />
        )}
      </Drawer>
    </div>
  );
};
