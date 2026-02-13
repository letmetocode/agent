import { CheckCircleOutlined, CloseCircleOutlined, ToolOutlined } from '@ant-design/icons';
import { Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { AgentToolDTO } from '@/shared/types/api';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StateView } from '@/shared/ui/StateView';

interface ToolRow {
  key: string;
  name: string;
  category: string;
  status: 'ENABLED' | 'DISABLED';
  auth: string;
  calls: number;
  lastError?: string;
}

const normalizeToolRow = (tool: AgentToolDTO): ToolRow => {
  const auth = tool.isActive ? '已授权' : '未授权';
  const status: ToolRow['status'] = tool.isActive ? 'ENABLED' : 'DISABLED';
  return {
    key: String(tool.id),
    name: tool.name,
    category: tool.type || '-',
    status,
    auth,
    calls: 0,
    lastError: undefined
  };
};

export const ToolsPage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [data, setData] = useState<ToolRow[]>([]);

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      setLoading(true);
      setError(undefined);
      try {
        const rows = await agentApi.getAgentTools();
        if (!canceled) {
          setData((rows || []).map(normalizeToolRow));
        }
      } catch (err) {
        if (!canceled) {
          const text = err instanceof Error ? err.message : String(err);
          setError(text);
          message.error(`加载工具目录失败: ${text}`);
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

  const columns: ColumnsType<ToolRow> = [
    {
      title: '工具名称',
      dataIndex: 'name',
      key: 'name',
      render: (value: string) => (
        <Space>
          <ToolOutlined />
          {value}
        </Space>
      )
    },
    { title: '分类', dataIndex: 'category', key: 'category', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: ToolRow['status']) =>
        status === 'ENABLED' ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            ENABLED
          </Tag>
        ) : (
          <Tag color="default" icon={<CloseCircleOutlined />}>
            DISABLED
          </Tag>
        )
    },
    { title: '授权', dataIndex: 'auth', key: 'auth', width: 120 },
    { title: '调用量', dataIndex: 'calls', key: 'calls', width: 120 },
    {
      title: '最近异常',
      dataIndex: 'lastError',
      key: 'lastError',
      render: (value?: string) => (value ? <Typography.Text type="danger">{value}</Typography.Text> : '-')
    }
  ];

  return (
    <div className="page-container">
      <PageHeader
        title="工具与插件"
        description="统一管理 Agent 可调用能力：状态、授权与健康度。"
        primaryActionText="新建执行"
        onPrimaryAction={() => navigate('/sessions')}
      />

      <Card className="app-card page-section">
        <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
          <Space>
            <Button type="primary">添加工具</Button>
            <Button>批量授权</Button>
          </Space>
          <Button onClick={() => navigate('/observability/logs')}>查看工具日志</Button>
        </Space>

        <div style={{ marginTop: 16 }}>
          {loading ? <StateView type="loading" title="加载工具目录中" /> : null}
          {!loading && error ? <StateView type="error" title="工具目录加载失败" description={error} /> : null}
          {!loading && !error && data.length === 0 ? (
            <StateView type="empty" title="暂无工具配置" description="后端未配置可用工具，请先在系统侧完成工具注册。" />
          ) : null}
          {!loading && !error && data.length > 0 ? <Table rowKey="key" columns={columns} dataSource={data} pagination={false} /> : null}
        </div>
      </Card>
    </div>
  );
};
