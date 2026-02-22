export type ShortcutScope =
  | 'global'
  | 'conversation'
  | 'logs'
  | 'tasks'
  | 'workflows'
  | 'observability'
  | 'governance'
  | 'settings';

export interface ShortcutDefinition {
  id: string;
  combo: string;
  description: string;
  scope: ShortcutScope;
  priority?: number;
  allowInInput?: boolean;
  preventDefault?: boolean;
}

export interface ShortcutMatchContext {
  isTextInputFocused: boolean;
  locationPathname: string;
}

export type ShortcutHandlerResult = boolean | void;
export type ShortcutHandler = (event: KeyboardEvent) => ShortcutHandlerResult;

export interface ShortcutRegistration {
  definition: ShortcutDefinition;
  handler: ShortcutHandler;
  enabled?: boolean;
  when?: (context: ShortcutMatchContext) => boolean;
}

export interface ShortcutGroup {
  title: string;
  items: ShortcutDefinition[];
}
