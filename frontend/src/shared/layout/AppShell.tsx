import {
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlusOutlined,
  SearchOutlined,
  UserOutlined
} from '@ant-design/icons';
import { Avatar, Badge, Breadcrumb, Button, Dropdown, Input, Layout, Menu, Space, Tag, Typography } from 'antd';
import type { BreadcrumbProps } from 'antd';
import type { MenuItemType } from 'antd/es/menu/interface';
import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useSessionStore } from '@/features/session/sessionStore';

const { Header, Sider, Content } = Layout;
const { Text, Title } = Typography;

interface AppNavItem {
  key: string;
  label: string;
  path?: string;
  children?: AppNavItem[];
}

const APP_NAV_ITEMS: AppNavItem[] = [
  { key: '/workspace', label: '工作台', path: '/workspace' },
  { key: '/sessions', label: '对话与执行', path: '/sessions' },
  { key: '/tasks', label: '任务中心', path: '/tasks' },
  { key: '/workflows/drafts', label: 'Workflow 治理', path: '/workflows/drafts' },
  {
    key: '/assets',
    label: '资产中心',
    children: [
      { key: '/assets/tools', label: '工具与插件', path: '/assets/tools' },
      { key: '/assets/knowledge', label: '知识库', path: '/assets/knowledge' }
    ]
  },
  {
    key: '/observability',
    label: '观测与日志',
    children: [
      { key: '/observability/overview', label: '监控总览', path: '/observability/overview' },
      { key: '/observability/logs', label: '日志检索', path: '/observability/logs' }
    ]
  },
  {
    key: '/settings',
    label: '设置',
    children: [
      { key: '/settings/profile', label: '个人设置', path: '/settings/profile' }
    ]
  }
];

const pathTitleMap: Record<string, string> = {
  '/workspace': '工作台',
  '/sessions': '对话与执行',
  '/tasks': '任务中心',
  '/workflows/drafts': 'Workflow 治理',
  '/assets/tools': '工具与插件',
  '/assets/knowledge': '知识库',
  '/observability/overview': '监控总览',
  '/observability/logs': '日志检索',
  '/settings/profile': '个人设置'
};

const flattenNavItems = (items: AppNavItem[]): Array<MenuItemType> =>
  items.map((item) => ({
    key: item.key,
    label: item.path ? <Link to={item.path}>{item.label}</Link> : item.label,
    children: item.children ? flattenNavItems(item.children) : undefined
  }));

const matchPathKey = (pathname: string) => {
  const orderedKeys = Object.keys(pathTitleMap).sort((a, b) => b.length - a.length);
  for (const key of orderedKeys) {
    if (pathname === key || pathname.startsWith(`${key}/`)) {
      return key;
    }
  }
  return '/workspace';
};

const buildBreadcrumbItems = (pathname: string): BreadcrumbProps['items'] => {
  const matchedKey = matchPathKey(pathname);
  const rootTitle = pathTitleMap[matchedKey] || '工作台';
  const items: NonNullable<BreadcrumbProps['items']> = [{ title: <Link to="/workspace">Agent 控制台</Link> }];
  items.push({ title: rootTitle });

  if (matchedKey === '/sessions' && /^\/sessions\/\d+/.test(pathname)) {
    items.push({ title: '会话详情' });
  }
  if (matchedKey === '/tasks' && /^\/tasks\/\d+/.test(pathname)) {
    items.push({ title: '任务详情' });
  }
  if (matchedKey === '/assets/knowledge' && /^\/assets\/knowledge\/\d+/.test(pathname)) {
    items.push({ title: '知识库详情' });
  }

  return items;
};

export const AppShell = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { userId, setUserId } = useSessionStore();
  const [collapsed, setCollapsed] = useState(false);

  const selectedKey = useMemo(() => matchPathKey(location.pathname), [location.pathname]);
  const breadcrumbItems = useMemo(() => buildBreadcrumbItems(location.pathname), [location.pathname]);

  const openKeys = useMemo(() => {
    const candidates = ['/assets', '/observability', '/settings'];
    return candidates.filter((key) => selectedKey.startsWith(key));
  }, [selectedKey]);

  return (
    <Layout className="app-shell">
      <Sider width={240} className="app-shell-sider" collapsed={collapsed} collapsible trigger={null}>
        <div className="app-shell-brand">
          <Title level={5} style={{ margin: 0 }}>
            {collapsed ? 'Agent' : 'Agent Console'}
          </Title>
          {!collapsed ? <Text type="secondary">任务编排与执行</Text> : null}
        </div>

        <Menu mode="inline" items={flattenNavItems(APP_NAV_ITEMS)} selectedKeys={[selectedKey]} defaultOpenKeys={openKeys} />

        {!collapsed ? (
          <div className="app-shell-footer-hint">
            <Tag color="cyan">Dev</Tag>
            <Text type="secondary">极简 · 高效 · 可观测</Text>
          </div>
        ) : null}
      </Sider>

      <Layout>
        <Header className="app-shell-header">
          <div className="app-shell-header-left">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((prev) => !prev)}
            />
            <Input prefix={<SearchOutlined />} placeholder="全局搜索（⌘K）" allowClear className="app-shell-search" />
          </div>

          <div className="app-shell-header-right">
            <Button icon={<PlusOutlined />} type="primary" onClick={() => navigate('/sessions')}>
              新聊天
            </Button>
            <Badge count={2} size="small">
              <Button type="text" icon={<BellOutlined />} />
            </Badge>

            <Dropdown
              menu={{
                items: [
                  { key: 'profile', label: '个人设置', onClick: () => navigate('/settings/profile') },
                  {
                    key: 'logout',
                    label: '清除 userId',
                    onClick: () => {
                      setUserId('');
                      navigate('/login');
                    }
                  }
                ]
              }}
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} />
                <Text>{userId || '未登录'}</Text>
              </Space>
            </Dropdown>
          </div>
        </Header>

        <Content className="app-shell-content">
          <Breadcrumb items={breadcrumbItems} className="app-shell-breadcrumb" />
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};
