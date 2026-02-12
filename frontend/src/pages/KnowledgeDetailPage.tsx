import { SearchOutlined } from '@ant-design/icons';
import { Button, Card, Col, Input, List, Row, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type {
  KnowledgeBaseDetailDTO,
  KnowledgeDocumentDTO,
  RetrievalTestResultDTO
} from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

const { Text } = Typography;

interface DocRow {
  id: number;
  name: string;
  chunks: number;
  updatedAt: string;
  status: string;
}

const normalizeDoc = (item: KnowledgeDocumentDTO): DocRow => ({
  id: item.id,
  name: item.name,
  chunks: Number(item.chunks || 0),
  updatedAt: item.updatedAt ? new Date(item.updatedAt).toLocaleString() : '-',
  status: item.status || 'UNKNOWN'
});

export const KnowledgeDetailPage = () => {
  const { kbId } = useParams();
  const numericKbId = Number(kbId);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [kb, setKb] = useState<KnowledgeBaseDetailDTO>();
  const [docs, setDocs] = useState<DocRow[]>([]);
  const [query, setQuery] = useState('');
  const [testing, setTesting] = useState(false);
  const [retrievalResults, setRetrievalResults] = useState<RetrievalTestResultDTO[]>([]);

  const loadDetail = async () => {
    if (!Number.isFinite(numericKbId) || numericKbId <= 0) {
      setError('无效知识库 ID');
      return;
    }
    setLoading(true);
    setError(undefined);
    try {
      const [detail, documents] = await Promise.all([
        agentApi.getKnowledgeBaseDetail(numericKbId),
        agentApi.getKnowledgeBaseDocuments(numericKbId)
      ]);
      setKb(detail);
      setDocs((documents || []).map(normalizeDoc));
    } catch (err) {
      const text = err instanceof Error ? err.message : String(err);
      setError(text);
      message.error(`加载知识库详情失败: ${text}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadDetail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numericKbId]);

  const onTestRetrieval = async () => {
    if (!query.trim()) {
      message.warning('请输入检索问题');
      return;
    }
    if (!numericKbId || numericKbId <= 0) {
      return;
    }
    setTesting(true);
    try {
      const result = await agentApi.testKnowledgeRetrieval(numericKbId, query.trim());
      setRetrievalResults(result.results || []);
    } catch (err) {
      message.error(`检索测试失败: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setTesting(false);
    }
  };

  const columns: ColumnsType<DocRow> = [
    { title: '文档', dataIndex: 'name', key: 'name' },
    { title: '分片数', dataIndex: 'chunks', key: 'chunks', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => <Tag color={status === 'INDEXED' ? 'success' : 'processing'}>{status}</Tag>
    },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 }
  ];

  const kbDesc = useMemo(() => {
    if (!kb) {
      return '-';
    }
    return `向量库：${kb.name || '-'} · 集合：${kb.collectionName || '-'} · 类型：${kb.storeType || '-'} · 文档 ${kb.documentCount || 0} · 分片 ${kb.chunkCount || 0}`;
  }, [kb]);

  if (loading) {
    return <StateView type="loading" title="加载知识库详情中" />;
  }

  if (error) {
    return <StateView type="error" title="知识库详情加载失败" description={error} actionText="重试" onAction={() => void loadDetail()} />;
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <PageHeader
        title={`知识库详情 #${kbId || '-'}`}
        description={kbDesc}
        primaryActionText="刷新文档状态"
        onPrimaryAction={() => void loadDetail()}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="app-card" title="文档与索引">
            <Table rowKey="id" columns={columns} dataSource={docs} pagination={false} locale={{ emptyText: '暂无文档记录' }} />
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card className="app-card" title="检索测试">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input
                prefix={<SearchOutlined />}
                placeholder="输入问题测试召回效果，例如：失败任务如何回滚？"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onPressEnter={() => void onTestRetrieval()}
              />
              <Button type="primary" loading={testing} onClick={() => void onTestRetrieval()}>
                执行检索
              </Button>
              <List
                dataSource={retrievalResults}
                locale={{ emptyText: '暂无检索结果' }}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={item.title}
                      description={
                        <Space direction="vertical" size={2}>
                          <Text type="secondary">{item.snippet}</Text>
                          <Text type="secondary">相关性：{Math.round((item.score || 0) * 100)}%</Text>
                          {item.source ? <Text type="secondary">来源：{item.source}</Text> : null}
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
};
