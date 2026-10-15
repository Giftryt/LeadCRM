import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

type Toast = { id: number; message: string; error: boolean };
type ToastFn = (message: string, opts?: { error?: boolean }) => void;

const ToastContext = createContext<ToastFn>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null);
  const timer = useRef<number>();
  const show = useCallback<ToastFn>((message, opts) => {
    window.clearTimeout(timer.current);
    const t = { id: Date.now(), message, error: !!opts?.error };
    setToast(t);
    timer.current = window.setTimeout(() => setToast(null), t.error ? 6000 : 2500);
  }, []);
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div aria-live="polite" role="status">
        {toast && (
          <div key={toast.id} className={`toast${toast.error ? " err" : ""}`}>
            {toast.message}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);

/** Turns any thrown error into a message suitable for a toast. */
export const errorText = (e: unknown) =>
  e instanceof Error ? e.message : "Something went wrong. Try again.";
