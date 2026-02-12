import { Alert, Button, Card, Form, InputNumber, Select, Typography, message } from 'antd';
import { PageHeader } from '@/shared/ui/PageHeader';

const { Text } = Typography;

export const SystemSettingsPage = () => {
  const [form] = Form.useForm<{
    fallbackAgent: string;
    plannerRetries: number;
    executionTimeoutMs: number;
  }>();

  return (
    <div className="page-container">
      <PageHeader title="系统配置" description="配置默认执行策略与超时参数，影响全局任务执行。" />

      <Alert
        showIcon
        type="warning"
        message="该页面属于高权限区域"
        description="仅管理员可修改，变更后建议在低峰期灰度验证。"
      />

      <Card className="app-card page-section" title="执行基线参数">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ fallbackAgent: 'assistant', plannerRetries: 3, executionTimeoutMs: 120000 }}
          onFinish={() => message.success('系统配置已保存（演示）')}
        >
          <Form.Item label="候选节点默认 Agent" name="fallbackAgent" rules={[{ required: true, message: '请选择默认 Agent' }]}>
            <Select
              options={[
                { label: 'assistant', value: 'assistant' },
                { label: 'researcher', value: 'researcher' },
                { label: 'analyst', value: 'analyst' }
              ]}
            />
          </Form.Item>

          <Form.Item label="Root 草案重试次数" name="plannerRetries">
            <InputNumber min={1} max={5} style={{ width: 200 }} />
          </Form.Item>

          <Form.Item label="Task 执行超时（ms）" name="executionTimeoutMs">
            <InputNumber min={30000} max={600000} step={10000} style={{ width: 240 }} />
          </Form.Item>

          <Text type="secondary">建议：修改后在“监控总览”观察失败率和 P95 耗时至少 30 分钟。</Text>

          <div style={{ marginTop: 16 }}>
            <Button type="primary" htmlType="submit">
              保存配置
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
};
