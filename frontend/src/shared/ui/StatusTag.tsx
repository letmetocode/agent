import { Tag } from 'antd';

const STATUS_COLOR_MAP: Record<string, string> = {
  SUCCESS: 'success',
  ACTIVE: 'success',
  COMPLETED: 'success',
  INDEXED: 'success',

  RUNNING: 'processing',
  VALIDATING: 'processing',
  REFINING: 'processing',
  PLANNING: 'processing',

  READY: 'blue',
  CREATED: 'blue',
  PENDING: 'default',

  PAUSED: 'warning',
  WARNING: 'warning',

  FAILED: 'error',
  ERROR: 'error',
  REVOKED: 'error',

  CANCELLED: 'default',
  SKIPPED: 'default',
  DISABLED: 'default',
  ARCHIVED: 'default',
  EXPIRED: 'default',
  UNKNOWN: 'default'
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
