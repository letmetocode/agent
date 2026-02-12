import { Tag } from 'antd';

const STATUS_COLOR_MAP: Record<string, string> = {
  COMPLETED: 'success',
  SUCCESS: 'success',
  ACTIVE: 'success',
  EXPIRED: 'default',
  REVOKED: 'error',
  RUNNING: 'processing',
  VALIDATING: 'processing',
  REFINING: 'processing',
  READY: 'blue',
  DRAFT: 'gold',
  WARNING: 'warning',
  FAILED: 'error',
  ERROR: 'error',
  CANCELLED: 'default',
  PAUSED: 'warning',
  ARCHIVED: 'default',
  DISABLED: 'default'
};

interface StatusTagProps {
  status?: string;
  fallback?: string;
}

export const StatusTag = ({ status, fallback = '-' }: StatusTagProps) => {
  if (!status) {
    return <Tag>{fallback}</Tag>;
  }
  const normalized = status.toUpperCase();
  return <Tag color={STATUS_COLOR_MAP[normalized] || 'default'}>{status}</Tag>;
};

