import { Button, Empty, Result, Skeleton, Space, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Paragraph, Text } = Typography;

interface StateViewProps {
  type: 'empty' | 'loading' | 'error' | 'permission' | 'unavailable';
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  extra?: ReactNode;
}

const renderActions = (actionText?: string, onAction?: () => void, extra?: ReactNode) => {
  if (!actionText && !extra) {
    return undefined;
  }
  return (
    <Space>
      {actionText && onAction ? <Button onClick={onAction}>{actionText}</Button> : null}
      {extra}
    </Space>
  );
};

export const StateView = ({ type, title, description, actionText, onAction, extra }: StateViewProps) => {
  if (type === 'loading') {
    return (
      <div className="state-view-wrap state-view-loading">
        <Skeleton active paragraph={{ rows: 4 }} />
      </div>
    );
  }

  if (type === 'error') {
    return (
      <div className="state-view-result">
        <Result status="error" title={title} subTitle={description} extra={renderActions(actionText, onAction, extra)} />
      </div>
    );
  }

  if (type === 'permission' || type === 'unavailable') {
    return (
      <div className="state-view-result">
        <Result
          status={type === 'permission' ? '403' : 'warning'}
          title={title}
          subTitle={description}
          extra={renderActions(actionText, onAction, extra)}
        />
      </div>
    );
  }

  return (
    <div className="state-view-wrap">
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={
          <Space direction="vertical" size={2}>
            <Text strong>{title}</Text>
            {description ? (
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {description}
              </Paragraph>
            ) : null}
          </Space>
        }
      >
        {actionText && onAction ? (
          <Button type="primary" onClick={onAction}>
            {actionText}
          </Button>
        ) : (
          extra
        )}
      </Empty>
    </div>
  );
};
