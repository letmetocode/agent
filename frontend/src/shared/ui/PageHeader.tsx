import { Button, Space, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Paragraph, Title } = Typography;

interface PageHeaderProps {
  title: string;
  description?: string;
  primaryActionText?: string;
  onPrimaryAction?: () => void;
  extra?: ReactNode;
}

export const PageHeader = ({ title, description, primaryActionText, onPrimaryAction, extra }: PageHeaderProps) => {
  return (
    <div className="page-header">
      <div>
        <Title level={3} className="page-header-title">
          {title}
        </Title>
        {description ? (
          <Paragraph type="secondary" className="page-header-description">
            {description}
          </Paragraph>
        ) : null}
      </div>

      <Space>
        {extra}
        {primaryActionText && onPrimaryAction ? (
          <Button type="primary" onClick={onPrimaryAction}>
            {primaryActionText}
          </Button>
        ) : null}
      </Space>
    </div>
  );
};

