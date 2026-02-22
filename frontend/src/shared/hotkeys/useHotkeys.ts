import { useEffect } from 'react';
import type { ShortcutRegistration } from '@/shared/types/shortcut';
import { useShortcutContext } from './ShortcutProvider';

export const useHotkeys = (registrations: ShortcutRegistration[]) => {
  const { registerShortcut } = useShortcutContext();

  useEffect(() => {
    const disposers = registrations.map((registration) => registerShortcut(registration));
    return () => {
      disposers.forEach((dispose) => dispose());
    };
  }, [registerShortcut, registrations]);
};
