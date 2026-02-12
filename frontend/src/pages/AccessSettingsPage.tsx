import { Button, Card, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PageHeader } from '@/shared/ui/PageHeader';

interface MemberRow {
  key: string;
  name: string;
  role: 'ADMIN' | 'OPS' | 'VIEWER';
  scope: string;
  lastActive: string;
}

const data: MemberRow[] = [
  { key: 'u1', name: 'alice', role: 'ADMIN', scope: '全局', lastActive: '2026-02-11 20:40' },
  { key: 'u2', name: 'bob', role: 'OPS', scope: '任务中心/监控', lastActive: '2026-02-11 19:58' },
  { key: 'u3', name: 'carol', role: 'VIEWER', scope: '只读', lastActive: '2026-02-11 18:20' }
];

const roleColor: Record<MemberRow['role'], string> = {
  ADMIN: 'red',
  OPS: 'blue',
  VIEWER: 'default'
};

export const AccessSettingsPage = () => {
  const columns: ColumnsType<MemberRow> = [
    { title: '成员', dataIndex: 'name', key: 'name' },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 120,
      render: (role: MemberRow['role']) => <Tag color={roleColor[role]}>{role}</Tag>
    },
    { title: '权限范围', dataIndex: 'scope', key: 'scope' },
    { title: '最近活跃', dataIndex: 'lastActive', key: 'lastActive', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => <Button type="link">编辑权限</Button>
    }
  ];

  return (
    <div className="page-container">
      <PageHeader title="成员与权限" description="采用 RBAC 管理页面访问与操作权限，避免越权执行。" />

      <Card className="app-card page-section">
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Typography.Text type="secondary">权限变更将记录审计日志并同步到会话/任务操作控制。</Typography.Text>
          <Button type="primary">邀请成员</Button>
        </Space>

        <div style={{ marginTop: 16 }}>
          <Table rowKey="key" columns={columns} dataSource={data} pagination={false} />
        </div>
      </Card>
    </div>
  );
};
