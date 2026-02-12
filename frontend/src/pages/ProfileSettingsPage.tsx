import { Button, Card, Form, Input, Space, Switch, Typography, message } from 'antd';
import { useSessionStore } from '@/features/session/sessionStore';
import { PageHeader } from '@/shared/ui/PageHeader';

const { Text } = Typography;

export const ProfileSettingsPage = () => {
  const { userId, setUserId } = useSessionStore();
  const [form] = Form.useForm<{ userId: string; compactMode: boolean; autoFollowStream: boolean }>();

  return (
    <div className="page-container">
      <PageHeader title="个人设置" description="管理个人身份、偏好与执行体验设置。" />

      <Card className="app-card page-section" title="基础信息">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ userId: userId || 'dev-user', compactMode: true, autoFollowStream: true }}
          onFinish={(values) => {
            setUserId(values.userId.trim());
            message.success('个人设置已保存');
          }}
        >
          <Form.Item label="userId" name="userId" rules={[{ required: true, message: '请输入 userId' }]}>
            <Input />
          </Form.Item>

          <Form.Item label="界面偏好" style={{ marginBottom: 8 }}>
            <Space direction="vertical">
              <Form.Item name="compactMode" valuePropName="checked" noStyle>
                <Switch checkedChildren="紧凑" unCheckedChildren="舒适" />
              </Form.Item>
              <Text type="secondary">紧凑模式用于提升信息密度，适合桌面多任务场景。</Text>
            </Space>
          </Form.Item>

          <Form.Item label="流式输出" style={{ marginBottom: 8 }}>
            <Space direction="vertical">
              <Form.Item name="autoFollowStream" valuePropName="checked" noStyle>
                <Switch checkedChildren="自动跟随" unCheckedChildren="手动滚动" />
              </Form.Item>
              <Text type="secondary">关闭后可暂停自动滚动，便于阅读历史片段。</Text>
            </Space>
          </Form.Item>

          <Button type="primary" htmlType="submit">
            保存
          </Button>
        </Form>
      </Card>
    </div>
  );
};
