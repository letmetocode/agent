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

export const StateView = ({ type, title, description, actionText, onAction, extra }: StateViewProps) => {
  if (type === 'loading') {
    return (
      <div className="state-view-wrap">
        <Skeleton active paragraph={{ rows: 4 }} />
      </div>
    );
  }

  if (type === 'error') {
    return (
      <Result
        status="error"
        title={title}
        subTitle={description}
        extra={
          <Space>
            {actionText && onAction ? <Button onClick={onAction}>{actionText}</Button> : null}
            {extra}
          </Space>
        }
      />
    );
  }

  if (type === 'permission' || type === 'unavailable') {
    return (
      <Result
        status={type === 'permission' ? '403' : 'warning'}
        title={title}
        subTitle={description}
        extra={actionText && onAction ? <Button onClick={onAction}>{actionText}</Button> : extra}
      />
    );
  }

  return (
    <div className="state-view-wrap">
      <Empty
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
        {actionText && onAction ? <Button type="primary" onClick={onAction}>{actionText}</Button> : extra}
      </Empty>
    </div>
  );
};

