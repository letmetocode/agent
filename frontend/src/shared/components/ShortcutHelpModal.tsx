import { KeyOutlined } from '@ant-design/icons';
import { Divider, Modal, Space, Tag, Typography } from 'antd';
import { SHORTCUT_GUIDE_GROUPS } from '@/shared/constants/shortcuts';
import { useShortcutContext } from '@/shared/hotkeys/ShortcutProvider';

const humanizeCombo = (combo: string) =>
  combo
    .split('+')
    .map((part) => {
      const token = part.trim().toLowerCase();
      if (token === 'mod') {
        return 'Ctrl/Cmd';
      }
      if (token === 'esc') {
        return 'Esc';
      }
      if (token.length === 1) {
        return token.toUpperCase();
      }
      return token.charAt(0).toUpperCase() + token.slice(1);
    })
    .join(' + ');

export const ShortcutHelpModal = () => {
  const { isHelpOpen, closeHelp } = useShortcutContext();

  return (
    <Modal
      title={
        <Space>
          <KeyOutlined />
          <span>快捷键帮助</span>
        </Space>
      }
      open={isHelpOpen}
      onCancel={closeHelp}
      onOk={closeHelp}
      okText="我知道了"
      cancelButtonProps={{ style: { display: 'none' } }}
      width={720}
      destroyOnClose
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        快捷键在输入框中默认不拦截普通输入，仅对必要组合键生效。
      </Typography.Paragraph>
      {SHORTCUT_GUIDE_GROUPS.map((group, index) => (
        <div key={group.title}>
          {index > 0 ? <Divider style={{ margin: '12px 0' }} /> : null}
          <Typography.Title level={5} style={{ marginBottom: 8 }}>
            {group.title}
          </Typography.Title>
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {group.items.map((item) => (
              <Space key={item.id} style={{ justifyContent: 'space-between', width: '100%' }}>
                <Typography.Text>{item.description}</Typography.Text>
                <Tag color="blue">{humanizeCombo(item.combo)}</Tag>
              </Space>
            ))}
          </Space>
        </div>
      ))}
    </Modal>
  );
};
