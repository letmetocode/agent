import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { useWorkflowGovernanceStore } from '@/features/workflow/workflowGovernanceStore';
import { SopGraphEditor } from '@/features/workflow/SopGraphEditor';
import { graphToSopSpec, normalizeSopSpec, sopSpecToPayload, type SopSpecDocument } from '@/features/workflow/sopSpecModel';
import { agentApi } from '@/shared/api/agentApi';
import type { SopCompileResultDTO, WorkflowDraftSummaryDTO } from '@/shared/types/api';
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

const stringifyJson = (value?: unknown) => JSON.stringify(value || {}, null, 2);

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
    updateCandidate,
    publishCandidate
  } = useWorkflowGovernanceStore();

  const [detailOpen, setDetailOpen] = useState(false);
  const [publishTarget, setPublishTarget] = useState<WorkflowDraftSummaryDTO | null>(null);
  const [operator, setOperator] = useState('SYSTEM');
  const [editorSpec, setEditorSpec] = useState<SopSpecDocument>();
  const [activeTab, setActiveTab] = useState<'detail' | 'visual'>('detail');
  const [compiling, setCompiling] = useState(false);
  const [validating, setValidating] = useState(false);
  const [savingSpec, setSavingSpec] = useState(false);
  const [compilePreview, setCompilePreview] = useState<SopCompileResultDTO>();

  useEffect(() => {
    void loadCandidates();
  }, [loadCandidates]);

  useEffect(() => {
    if (!selectedCandidate) {
      setEditorSpec(undefined);
      setCompilePreview(undefined);
      return;
    }

    const spec = selectedCandidate.sopSpec
      ? normalizeSopSpec(selectedCandidate.sopSpec)
      : graphToSopSpec(selectedCandidate.graphDefinition, selectedCandidate.name);
    setEditorSpec(spec);
    setCompilePreview({
      draftId: selectedCandidate.id,
      sopSpec: selectedCandidate.sopSpec,
      sopRuntimeGraph: selectedCandidate.sopRuntimeGraph || selectedCandidate.graphDefinition,
      compileHash: selectedCandidate.compileHash,
      nodeSignature: selectedCandidate.nodeSignature,
      warnings: selectedCandidate.compileWarnings
    });
  }, [selectedCandidate]);

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
      {
        title: '编译状态',
        dataIndex: 'compileStatus',
        width: 120,
        render: (value?: string) => (value ? <Tag color="processing">{value}</Tag> : '-')
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
                  setActiveTab('detail');
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

  const saveSopSpec = async () => {
    if (!selectedCandidate || !editorSpec) {
      return;
    }
    setSavingSpec(true);
    try {
      const updated = await updateCandidate(selectedCandidate.id, {
        sopSpec: sopSpecToPayload(editorSpec)
      });
      setCompilePreview({
        draftId: updated.id,
        sopSpec: updated.sopSpec,
        sopRuntimeGraph: updated.sopRuntimeGraph,
        compileHash: updated.compileHash,
        nodeSignature: updated.nodeSignature,
        warnings: updated.compileWarnings
      });
      message.success('SOP 编排已保存并完成编译');
    } catch (err) {
      message.error(`保存失败: ${toErrorMessage(err)}`);
    } finally {
      setSavingSpec(false);
    }
  };

  const compileSopSpec = async () => {
    if (!selectedCandidate || !editorSpec) {
      return;
    }
    setCompiling(true);
    try {
      const result = await agentApi.compileWorkflowDraftSopSpec(selectedCandidate.id, sopSpecToPayload(editorSpec));
      setCompilePreview(result);
      message.success('编译完成，可预览 Runtime Graph');
    } catch (err) {
      message.error(`编译失败: ${toErrorMessage(err)}`);
    } finally {
      setCompiling(false);
    }
  };

  const validateSopSpec = async () => {
    if (!selectedCandidate || !editorSpec) {
      return;
    }
    setValidating(true);
    try {
      const result = await agentApi.validateWorkflowDraftSopSpec(selectedCandidate.id, sopSpecToPayload(editorSpec));
      if (result.pass) {
        message.success('校验通过');
      } else {
        message.warning(`校验未通过: ${(result.issues || []).join('; ') || '请检查SOP配置'}`);
      }
    } catch (err) {
      message.error(`校验失败: ${toErrorMessage(err)}`);
    } finally {
      setValidating(false);
    }
  };

  return (
    <div className="page-container">
      <PageHeader
        title="Workflow Draft 治理"
        description="管理候补草案并发布为生产 Definition，支持图形化 SOP 编排与编译预览。"
        primaryActionText="返回对话与执行"
        onPrimaryAction={() => navigate('/sessions')}
        extra={<Button onClick={() => void loadCandidates(statusFilter)}>刷新</Button>}
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
            scroll={{ x: 1720 }}
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
            locale={{ emptyText: <Empty description="暂无 Workflow Draft" /> }}
          />
        </Space>
      </Card>

      <Drawer
        width={1320}
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
          <Tabs
            activeKey={activeTab}
            onChange={(tab) => setActiveTab(tab as 'detail' | 'visual')}
            items={[
              {
                key: 'detail',
                label: '草案详情',
                children: (
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
                      <Descriptions.Item label="编译状态">{selectedCandidate.compileStatus || '-'}</Descriptions.Item>
                      <Descriptions.Item label="编译哈希">{selectedCandidate.compileHash || '-'}</Descriptions.Item>
                      <Descriptions.Item label="来源">{selectedCandidate.sourceType || '-'}</Descriptions.Item>
                      <Descriptions.Item label="输入版本">{selectedCandidate.inputSchemaVersion || '-'}</Descriptions.Item>
                      <Descriptions.Item label="路由描述" span={2}>
                        {selectedCandidate.routeDescription || '-'}
                      </Descriptions.Item>
                    </Descriptions>

                    <div>
                      <Text strong>SOP Spec</Text>
                      <pre className="json-block">{stringifyJson(selectedCandidate.sopSpec)}</pre>
                    </div>
                    <div>
                      <Text strong>Runtime Graph</Text>
                      <pre className="json-block">{stringifyJson(selectedCandidate.sopRuntimeGraph || selectedCandidate.graphDefinition)}</pre>
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
                )
              },
              {
                key: 'visual',
                label: 'SOP 可视化编排',
                children: (
                  <Space direction="vertical" style={{ width: '100%' }} size="middle">
                    <Alert
                      type="info"
                      showIcon
                      message="通过拖拽和连线维护 SOP Spec；保存时后端会编译为 Runtime Graph 并写回 Draft。"
                    />

                    <Space wrap>
                      <Button loading={validating} onClick={() => void validateSopSpec()}>
                        校验
                      </Button>
                      <Button loading={compiling} onClick={() => void compileSopSpec()}>
                        编译预览
                      </Button>
                      <Button type="primary" loading={savingSpec} onClick={() => void saveSopSpec()}>
                        保存编排
                      </Button>
                      <Text type="secondary">compileHash: {compilePreview?.compileHash || selectedCandidate.compileHash || '-'}</Text>
                    </Space>

                    {compilePreview?.warnings && compilePreview.warnings.length > 0 ? (
                      <Alert
                        type="warning"
                        showIcon
                        message={`编译警告：${compilePreview.warnings.join('；')}`}
                      />
                    ) : null}

                    {editorSpec ? <SopGraphEditor value={editorSpec} onChange={setEditorSpec} /> : <Empty description="暂无可编辑 SOP" />}

                    <div>
                      <Text strong>编译预览（Runtime Graph）</Text>
                      <pre className="json-block">{stringifyJson(compilePreview?.sopRuntimeGraph || selectedCandidate.sopRuntimeGraph)}</pre>
                    </div>
                  </Space>
                )
              }
            ]}
          />
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
            message.success(`发布成功：draft #${result.draftId} -> definition #${result.definitionId} (v${result.definitionVersion})`);
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
          <Input value={operator} maxLength={64} onChange={(event) => setOperator(event.target.value)} placeholder="操作人（默认 SYSTEM）" />
        </Space>
      </Modal>
    </div>
  );
};
