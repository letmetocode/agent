import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { useWorkflowGovernanceStore } from '@/features/workflow/workflowGovernanceStore';
import type { WorkflowDraftSummaryDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';

const { Text } = Typography;

const STATUS_OPTIONS: Array<{ label: string; value?: string }> = [
  { label: '全部', value: undefined },
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'DISABLED', value: 'DISABLED' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'ARCHIVED', value: 'ARCHIVED' }
];

const statusColor = (status?: string) => {
  switch (status) {
    case 'DRAFT':
      return 'gold';
    case 'ACTIVE':
      return 'success';
    case 'DISABLED':
      return 'default';
    case 'ARCHIVED':
      return 'warning';
    default:
      return 'default';
  }
};

const stringifyJson = (value?: Record<string, unknown>) => JSON.stringify(value || {}, null, 2);

const toErrorMessage = (err: unknown) => (err instanceof Error ? err.message : String(err));

export const WorkflowDraftPage = () => {
  const navigate = useNavigate();
  const {
    loading,
    detailLoading,
    publishingId,
    statusFilter,
    list,
    selectedCandidate,
    error,
    setStatusFilter,
    clearSelectedCandidate,
    loadCandidates,
    loadCandidateDetail,
    publishCandidate
  } = useWorkflowGovernanceStore();

  const [detailOpen, setDetailOpen] = useState(false);
  const [publishTarget, setPublishTarget] = useState<WorkflowDraftSummaryDTO | null>(null);
  const [operator, setOperator] = useState('SYSTEM');

  useEffect(() => {
    void loadCandidates();
  }, [loadCandidates]);

  const columns: ColumnsType<WorkflowDraftSummaryDTO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'Draft Key', dataIndex: 'draftKey', width: 200, ellipsis: true },
      { title: '租户', dataIndex: 'tenantId', width: 120, ellipsis: true },
      { title: '分类', dataIndex: 'category', width: 140, ellipsis: true },
      { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (value: string) => <Tag color={statusColor(value)}>{value || 'UNKNOWN'}</Tag>
      },
      { title: '来源', dataIndex: 'sourceType', width: 170, ellipsis: true },
      { title: '创建人', dataIndex: 'createdBy', width: 120, ellipsis: true },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        width: 190,
        render: (value?: string) => (value ? new Date(value).toLocaleString() : '-')
      },
      {
        title: '操作',
        key: 'actions',
        width: 210,
        fixed: 'right',
        render: (_, record) => (
          <Space>
            <Button
              size="small"
              onClick={async () => {
                try {
                  await loadCandidateDetail(record.id);
                  setDetailOpen(true);
                } catch (err) {
                  message.error(`加载详情失败: ${toErrorMessage(err)}`);
                }
              }}
            >
              查看详情
            </Button>
            <Button
              size="small"
              type="primary"
              disabled={record.status === 'ARCHIVED' || record.status === 'PUBLISHED'}
              onClick={() => {
                setOperator('SYSTEM');
                setPublishTarget(record);
              }}
            >
              发布转正
            </Button>
          </Space>
        )
      }
    ],
    [loadCandidateDetail]
  );

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <PageHeader
        title="Workflow Draft 治理"
        description="管理候补草案并发布为生产 Definition，避免路由未命中的执行降级。"
        primaryActionText="返回对话与执行"
        onPrimaryAction={() => navigate('/sessions')}
        extra={<Button onClick={() => void loadCandidates(statusFilter)}>刷新</Button>}
      />

      <Card className="app-card">
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
              onChange={async (value) => {
                const normalized = value || undefined;
                setStatusFilter(normalized);
                try {
                  await loadCandidates(normalized);
                } catch (err) {
                  message.error(`加载候选列表失败: ${toErrorMessage(err)}`);
                }
              }}
            />
          </Space>

          <Table<WorkflowDraftSummaryDTO>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={list}
            scroll={{ x: 1650 }}
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
            locale={{ emptyText: <Empty description="暂无 Workflow Draft" /> }}
          />
        </Space>
      </Card>

      <Drawer
        width={900}
        title={selectedCandidate ? `候选详情 #${selectedCandidate.id}` : '候选详情'}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          clearSelectedCandidate();
        }}
      >
        {detailLoading ? (
          <div style={{ padding: '24px 0' }}>
            <Text type="secondary">正在加载详情...</Text>
          </div>
        ) : selectedCandidate ? (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="ID">{selectedCandidate.id}</Descriptions.Item>
              <Descriptions.Item label="Draft Key">{selectedCandidate.draftKey}</Descriptions.Item>
              <Descriptions.Item label="租户">{selectedCandidate.tenantId || 'DEFAULT'}</Descriptions.Item>
              <Descriptions.Item label="分类">{selectedCandidate.category}</Descriptions.Item>
              <Descriptions.Item label="名称">{selectedCandidate.name}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor(selectedCandidate.status)}>{selectedCandidate.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源">{selectedCandidate.sourceType || '-'}</Descriptions.Item>
              <Descriptions.Item label="输入版本">{selectedCandidate.inputSchemaVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="路由描述" span={2}>
                {selectedCandidate.routeDescription || '-'}
              </Descriptions.Item>
            </Descriptions>

            <div>
              <Text strong>graphDefinition</Text>
              <pre className="json-block">{stringifyJson(selectedCandidate.graphDefinition)}</pre>
            </div>
            <div>
              <Text strong>inputSchema</Text>
              <pre className="json-block">{stringifyJson(selectedCandidate.inputSchema)}</pre>
            </div>
            <div>
              <Text strong>defaultConfig</Text>
              <pre className="json-block">{stringifyJson(selectedCandidate.defaultConfig)}</pre>
            </div>
            <div>
              <Text strong>toolPolicy</Text>
              <pre className="json-block">{stringifyJson(selectedCandidate.toolPolicy)}</pre>
            </div>
            <div>
              <Text strong>constraints</Text>
              <pre className="json-block">{stringifyJson(selectedCandidate.constraints)}</pre>
            </div>
          </Space>
        ) : (
          <Empty description="未选择 Workflow Draft" />
        )}
      </Drawer>

      <Modal
        title="发布 Workflow Draft"
        open={Boolean(publishTarget)}
        confirmLoading={publishTarget ? publishingId === publishTarget.id : false}
        onCancel={() => setPublishTarget(null)}
        onOk={async () => {
          if (!publishTarget) {
            return;
          }
          try {
            const result = await publishCandidate(publishTarget.id, operator.trim() || 'SYSTEM');
            message.success(
              `发布成功：draft #${result.draftId} -> definition #${result.definitionId} (v${result.definitionVersion})`
            );
            setPublishTarget(null);
          } catch (err) {
            message.error(`发布失败: ${toErrorMessage(err)}`);
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text type="secondary">
            发布后将基于当前 Draft 创建生产 Definition，并将 Draft 状态置为 PUBLISHED。Draft Key: {publishTarget?.draftKey || '-'}
          </Text>
          <Input value={operator} maxLength={64} onChange={(e) => setOperator(e.target.value)} placeholder="操作人（默认 SYSTEM）" />
        </Space>
      </Modal>
    </Space>
  );
};
