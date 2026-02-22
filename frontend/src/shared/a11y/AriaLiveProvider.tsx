import { createContext, useCallback, useContext, useMemo, useRef, useState, type PropsWithChildren } from 'react';

interface AriaLiveContextValue {
  announce: (message: string, mode?: 'polite' | 'assertive') => void;
}

const AriaLiveContext = createContext<AriaLiveContextValue | null>(null);

export const AriaLiveProvider = ({ children }: PropsWithChildren) => {
  const [politeMessage, setPoliteMessage] = useState('');
  const [assertiveMessage, setAssertiveMessage] = useState('');
  const timerRef = useRef<number | null>(null);

  const announce = useCallback((message: string, mode: 'polite' | 'assertive' = 'polite') => {
    const text = (message || '').trim();
    if (!text) {
      return;
    }
    if (mode === 'assertive') {
      setAssertiveMessage('');
      setAssertiveMessage(text);
    } else {
      setPoliteMessage('');
      setPoliteMessage(text);
    }

    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
    }
    timerRef.current = window.setTimeout(() => {
      setPoliteMessage('');
      setAssertiveMessage('');
      timerRef.current = null;
    }, 1200);
  }, []);

  const value = useMemo<AriaLiveContextValue>(() => ({ announce }), [announce]);

  return (
    <AriaLiveContext.Provider value={value}>
      {children}
      <div className="sr-only" aria-live="polite" aria-atomic="true">
        {politeMessage}
      </div>
      <div className="sr-only" aria-live="assertive" aria-atomic="true">
        {assertiveMessage}
      </div>
    </AriaLiveContext.Provider>
  );
};

export const useAriaLive = () => {
  const context = useContext(AriaLiveContext);
  if (!context) {
    throw new Error('useAriaLive must be used within AriaLiveProvider');
  }
  return context;
};
