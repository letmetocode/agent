import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import type { ShortcutMatchContext, ShortcutRegistration } from '@/shared/types/shortcut';
import { eventToCombo, isTextInputElement, normalizeCombo } from './keyboard';

interface ShortcutEntry {
  registrationId: number;
  registration: ShortcutRegistration;
}

interface ShortcutContextValue {
  registerShortcut: (registration: ShortcutRegistration) => () => void;
  isHelpOpen: boolean;
  openHelp: () => void;
  closeHelp: () => void;
  toggleHelp: () => void;
}

const ShortcutContext = createContext<ShortcutContextValue | null>(null);

let registrationSequence = 1;

export const ShortcutProvider = ({ children }: PropsWithChildren) => {
  const entriesRef = useRef<ShortcutEntry[]>([]);
  const [isHelpOpen, setIsHelpOpen] = useState(false);

  const openHelp = useCallback(() => setIsHelpOpen(true), []);
  const closeHelp = useCallback(() => setIsHelpOpen(false), []);
  const toggleHelp = useCallback(() => setIsHelpOpen((previous) => !previous), []);

  const registerShortcut = useCallback((registration: ShortcutRegistration) => {
    const registrationId = registrationSequence++;
    const combo = normalizeCombo(registration.definition.combo);
    const next: ShortcutRegistration = {
      ...registration,
      definition: {
        ...registration.definition,
        combo
      }
    };

    if (entriesRef.current.some((item) => item.registration.definition.id === next.definition.id)) {
      // duplicated ID may hide another shortcut by mistake.
      // eslint-disable-next-line no-console
      console.warn(`[shortcuts] duplicated shortcut id detected: ${next.definition.id}`);
    }

    entriesRef.current.push({ registrationId, registration: next });
    return () => {
      entriesRef.current = entriesRef.current.filter((item) => item.registrationId !== registrationId);
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.repeat) {
        return;
      }

      const combo = eventToCombo(event);
      if (!combo) {
        return;
      }

      const isInputFocused = isTextInputElement(event.target);
      const context: ShortcutMatchContext = {
        isTextInputFocused: isInputFocused,
        locationPathname: typeof window === 'undefined' ? '/' : window.location.pathname
      };

      const candidates = entriesRef.current
        .filter((item) => item.registration.definition.combo === combo)
        .filter((item) => (item.registration.enabled == null ? true : item.registration.enabled))
        .filter((item) => (item.registration.when ? item.registration.when(context) : true))
        .filter((item) => (isInputFocused ? item.registration.definition.allowInInput === true : true))
        .sort((left, right) => {
          const leftPriority = left.registration.definition.priority ?? 0;
          const rightPriority = right.registration.definition.priority ?? 0;
          if (leftPriority !== rightPriority) {
            return rightPriority - leftPriority;
          }
          return right.registrationId - left.registrationId;
        });

      if (!candidates.length) {
        return;
      }

      for (const candidate of candidates) {
        const result = candidate.registration.handler(event);
        if (result === false) {
          continue;
        }
        if (candidate.registration.definition.preventDefault !== false) {
          event.preventDefault();
        }
        event.stopPropagation();
        break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const value = useMemo<ShortcutContextValue>(
    () => ({
      registerShortcut,
      isHelpOpen,
      openHelp,
      closeHelp,
      toggleHelp
    }),
    [closeHelp, isHelpOpen, openHelp, registerShortcut, toggleHelp]
  );

  return <ShortcutContext.Provider value={value}>{children}</ShortcutContext.Provider>;
};

export const useShortcutContext = () => {
  const context = useContext(ShortcutContext);
  if (!context) {
    throw new Error('useShortcutContext must be used within ShortcutProvider');
  }
  return context;
};
