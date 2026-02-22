import type { ShortcutGroup } from '@/shared/types/shortcut';

export const SHORTCUT_GUIDE_GROUPS: ShortcutGroup[] = [
  {
    title: '全局',
    items: [
      {
        id: 'global.focus-primary',
        combo: 'mod+k',
        description: '聚焦主输入 / 搜索框',
        scope: 'global'
      },
      {
        id: 'global.new-chat',
        combo: 'mod+shift+n',
        description: '新建聊天',
        scope: 'global'
      },
      {
        id: 'global.help',
        combo: '?',
        description: '打开快捷键帮助',
        scope: 'global'
      },
      {
        id: 'global.escape-close',
        combo: 'esc',
        description: '关闭当前最上层面板',
        scope: 'global'
      }
    ]
  },
  {
    title: '会话页',
    items: [
      {
        id: 'conversation.send',
        combo: 'enter',
        description: '发送消息（输入框内）',
        scope: 'conversation'
      },
      {
        id: 'conversation.newline',
        combo: 'shift+enter',
        description: '换行（输入框内）',
        scope: 'conversation'
      },
      {
        id: 'conversation.send-force',
        combo: 'mod+enter',
        description: '强制发送消息',
        scope: 'conversation'
      },
      {
        id: 'conversation.toggle-history',
        combo: 'mod+shift+h',
        description: '打开/关闭历史抽屉',
        scope: 'conversation'
      },
      {
        id: 'conversation.toggle-progress',
        combo: 'mod+shift+p',
        description: '展开/收起执行进度参数区',
        scope: 'conversation'
      },
      {
        id: 'conversation.prev-session',
        combo: '[',
        description: '切换到上一条会话',
        scope: 'conversation'
      },
      {
        id: 'conversation.next-session',
        combo: ']',
        description: '切换到下一条会话',
        scope: 'conversation'
      },
      {
        id: 'conversation.refresh-history',
        combo: 'r',
        description: '刷新历史快照',
        scope: 'conversation'
      }
    ]
  },
  {
    title: '日志页',
    items: [
      {
        id: 'logs.focus-filter',
        combo: 'f',
        description: '聚焦关键词筛选输入',
        scope: 'logs'
      },
      {
        id: 'logs.run-query',
        combo: 'mod+enter',
        description: '执行查询',
        scope: 'logs'
      }
    ]
  },
  {
    title: '任务页',
    items: [
      {
        id: 'tasks.focus-filter',
        combo: 'f',
        description: '聚焦任务关键词筛选',
        scope: 'tasks'
      },
      {
        id: 'tasks.run-query',
        combo: 'mod+enter',
        description: '执行任务查询',
        scope: 'tasks'
      },
      {
        id: 'tasks.toggle-view-mode',
        combo: 'mod+shift+v',
        description: '切换列表/看板视图',
        scope: 'tasks'
      },
      {
        id: 'task-detail.refresh',
        combo: 'r',
        description: '刷新任务详情（详情页）',
        scope: 'tasks'
      },
      {
        id: 'task-detail.back-to-list',
        combo: 'b',
        description: '返回任务列表（详情页）',
        scope: 'tasks'
      }
    ]
  }
];
