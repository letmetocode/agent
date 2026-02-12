import { Card, List, Space, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { agentApi } from '@/shared/api/agentApi';
import type { SharedTaskReadDTO } from '@/shared/types/api';
import { StateView } from '@/shared/ui/StateView';
import { StatusTag } from '@/shared/ui/StatusTag';

const { Paragraph, Text, Title } = Typography;

export const ShareTaskPage = () => {
  const { taskId } = useParams();
  const location = useLocation();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [data, setData] = useState<SharedTaskReadDTO>();

  const queryParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const shareCode = queryParams.get('code') || '';
  const token = queryParams.get('token') || '';
  const normalizedTaskId = Number(taskId);

  useEffect(() => {
    let canceled = false;
    const load = async () => {
      if (!Number.isFinite(normalizedTaskId) || normalizedTaskId <= 0) {
        setError('无效任务链接');
        return;
      }
      if (!shareCode || !token) {
        setError('分享参数缺失，请检查链接是否完整');
        return;
      }

      setLoading(true);
      setError(undefined);
      try {
        const result = await agentApi.readSharedTask(normalizedTaskId, { code: shareCode, token });
        if (!canceled) {
          setData(result);
        }
      } catch (err) {
        if (!canceled) {
          setError(err instanceof Error ? err.message : String(err));
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
  }, [normalizedTaskId, shareCode, token]);

  if (loading) {
    return (
      <div className="page-center">
        <Card className="glass-card" style={{ width: 'min(920px, 100%)' }}>
          <StateView type="loading" title="加载分享内容中" />
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-center">
        <Card className="glass-card" style={{ width: 'min(920px, 100%)' }}>
          <StateView type="error" title="分享内容不可访问" description={error} />
        </Card>
      </div>
    );
  }

  return (
    <div className="page-center">
      <Card className="glass-card" style={{ width: 'min(920px, 100%)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Space direction="vertical" size={2}>
            <Title level={3} style={{ margin: 0 }}>
              {data?.taskName || `任务 #${data?.taskId || taskId}`}
            </Title>
            <Space>
              <StatusTag status={data?.status || 'UNKNOWN'} />
              {data?.expiresAt ? <Text type="secondary">链接过期时间：{new Date(data.expiresAt).toLocaleString()}</Text> : null}
            </Space>
          </Space>

          <Card type="inner" title="最终结果">
            <Paragraph style={{ marginBottom: 0 }}>{data?.outputResult || '暂无输出内容'}</Paragraph>
          </Card>

          <Card type="inner" title="引用来源">
            <List
              dataSource={data?.references || []}
              locale={{ emptyText: '暂无引用信息' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={item.title}
                    description={`${item.type || '引用'}${item.source ? ` · ${item.source}` : ''}${item.score != null ? ` · 相关性 ${Math.round(item.score * 100)}%` : ''}`}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Space>
      </Card>
    </div>
  );
};
