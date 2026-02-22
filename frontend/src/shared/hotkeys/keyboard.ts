const normalizeKey = (event: KeyboardEvent): string => {
  const raw = event.key;
  if (!raw) {
    return '';
  }
  if (raw === ' ') {
    return 'space';
  }
  if (raw === 'Escape') {
    return 'esc';
  }
  if (raw === 'ArrowUp') {
    return 'arrowup';
  }
  if (raw === 'ArrowDown') {
    return 'arrowdown';
  }
  if (raw === 'ArrowLeft') {
    return 'arrowleft';
  }
  if (raw === 'ArrowRight') {
    return 'arrowright';
  }
  if (raw === '?') {
    return '?';
  }
  return raw.toLowerCase();
};

const normalizeComboToken = (token: string): string => {
  const value = token.trim().toLowerCase();
  if (value === 'cmd' || value === 'ctrl' || value === 'meta') {
    return 'mod';
  }
  if (value === 'escape') {
    return 'esc';
  }
  return value;
};

export const normalizeCombo = (combo: string): string => {
  return combo
    .split('+')
    .map((item) => normalizeComboToken(item))
    .filter((item) => item.length > 0)
    .join('+');
};

export const eventToCombo = (event: KeyboardEvent): string => {
  const segments: string[] = [];
  const key = normalizeKey(event);
  const isQuestionMark = key === '?';
  if (event.metaKey || event.ctrlKey) {
    segments.push('mod');
  }
  if (event.altKey) {
    segments.push('alt');
  }
  if (event.shiftKey && !isQuestionMark) {
    segments.push('shift');
  }
  if (key) {
    segments.push(key);
  }
  return segments.join('+');
};

export const isTextInputElement = (target: EventTarget | null): boolean => {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName.toLowerCase();
  if (target.isContentEditable) {
    return true;
  }
  if (tag === 'textarea') {
    return true;
  }
  if (tag !== 'input') {
    return false;
  }
  const input = target as HTMLInputElement;
  const type = (input.type || 'text').toLowerCase();
  return !['checkbox', 'radio', 'button', 'submit', 'reset', 'file', 'color', 'range'].includes(type);
};
