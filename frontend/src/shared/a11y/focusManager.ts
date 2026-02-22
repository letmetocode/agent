let lastFocusedElement: HTMLElement | null = null;

export const isFocusableElement = (element: unknown): element is HTMLElement => {
  if (!(element instanceof HTMLElement)) {
    return false;
  }
  return !element.hasAttribute('disabled') && element.tabIndex >= -1;
};

export const rememberFocusedElement = () => {
  if (typeof document === 'undefined') {
    return;
  }
  const active = document.activeElement;
  if (isFocusableElement(active)) {
    lastFocusedElement = active;
  }
};

export const restoreFocusedElement = () => {
  if (!lastFocusedElement) {
    return;
  }
  try {
    lastFocusedElement.focus();
  } catch {
    // no-op
  }
};

export const focusFirstElement = (container: HTMLElement | null) => {
  if (!container) {
    return;
  }
  const target = container.querySelector<HTMLElement>(
    'button:not([disabled]),[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'
  );
  if (target) {
    target.focus();
  }
};
