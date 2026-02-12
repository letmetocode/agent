import { FileTextOutlined, InboxOutlined } from '@ant-design/icons';
import { Button, Card, Col, List, Progress, Row, Space, Tag, Typography, Upload, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { VectorStoreDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

const { Dragger } = Upload;
const { Text } = Typography;

interface KnowledgeBaseRow {
  id: number;
  name: string;
  docs: number;
  status: string;
  indexing: number;
}

const normalizeKnowledge = (item: VectorStoreDTO): KnowledgeBaseRow => ({
  id: item.id,
  name: item.name,
  docs: Number(item.connectionConfig?.documentCount || item.connectionConfig?.docCount || 0),
  status: item.isActive ? 'ACTIVE' : 'DISABLED',
  indexing: item.isActive ? 100 : 0
});

export const KnowledgePage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseRow[]>([]);

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      setLoading(true);
      setError(undefined);
      try {
        const rows = await agentApi.getVectorStores();
        if (!canceled) {
          setKnowledgeBases((rows || []).map(normalizeKnowledge));
        }
      } catch (err) {
        if (!canceled) {
          const text = err instanceof Error ? err.message : String(err);
          setError(text);
          message.error(`加载知识库失败: ${text}`);
        }
      } finally {
        if (!canceled) {
          setLoading(false);
        }
      }
    };

    void load();
    return () => {
      canceled = true;
    };
  }, []);

  const firstKbId = useMemo(() => knowledgeBases[0]?.id, [knowledgeBases]);

  return (
    <div className="page-container">
      <PageHeader
        title="知识库"
        description="管理文档资产、索引状态与检索质量，保障 Agent 引用可追溯。"
        primaryActionText="新建知识库"
        onPrimaryAction={() => {
          if (firstKbId) {
            navigate(`/assets/knowledge/${firstKbId}`);
            return;
          }
          message.info('暂无知识库可进入，请先在后端注册向量存储');
        }}
      />

      <Row gutter={[16, 16]} className="page-section">
        <Col xs={24} xl={16}>
          <Card className="app-card" title="知识库列表">
            {loading ? <StateView type="loading" title="加载知识库中" /> : null}
            {!loading && error ? <StateView type="error" title="知识库加载失败" description={error} /> : null}
            {!loading && !error && knowledgeBases.length === 0 ? (
              <StateView type="empty" title="暂无知识库配置" description="后端未配置向量存储注册信息，请先在系统侧创建。" />
            ) : null}

            {!loading && !error && knowledgeBases.length > 0 ? (
              <List
                dataSource={knowledgeBases}
                renderItem={(item) => (
                  <List.Item
                    actions={[
                      <Button key="open" type="link" onClick={() => navigate(`/assets/knowledge/${item.id}`)}>
                        查看详情
                      </Button>
                    ]}
                  >
                    <List.Item.Meta
                      avatar={<FileTextOutlined />}
                      title={item.name}
                      description={
                        <Space direction="vertical" size={2} style={{ width: '100%' }}>
                          <Space>
                            <Tag color={item.status === 'ACTIVE' ? 'success' : 'default'}>{item.status}</Tag>
                            <Text type="secondary">文档数：{item.docs}</Text>
                          </Space>
                          <Progress percent={item.indexing} size="small" />
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : null}
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card className="app-card" title="文档上传">
            <Dragger multiple beforeUpload={() => false}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">拖拽或点击上传文档</p>
              <p className="ant-upload-hint">支持 PDF / Markdown / TXT。上传后自动分片和索引。</p>
            </Dragger>
          </Card>
        </Col>

        <Col xs={24}>
          <Card className="app-card" title="检索质量检查">
            <StateView
              type="empty"
              title="还未执行检索测试"
              description="输入查询问题并验证召回结果、相关性分数与引用片段。"
              actionText="前往知识库详情"
              onAction={() => {
                if (firstKbId) {
                  navigate(`/assets/knowledge/${firstKbId}`);
                  return;
                }
                message.info('暂无知识库可进入');
              }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};
