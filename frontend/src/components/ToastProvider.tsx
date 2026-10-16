import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

// Fixed user-facing messages keep provider errors and sensitive responses out of notifications.
export const toastMessages = {
  uploadSuccess: { kind: 'success', text: 'Schedule uploaded successfully.' },
  uploadInvalid: { kind: 'info', text: 'File uploaded. Please check its validation result.' },
  uploadError: { kind: 'error', text: 'Could not confirm the upload. Please check the page before trying again.' },
  reviewSubmitted: { kind: 'success', text: 'Review submitted successfully.' },
  reviewUpdated: { kind: 'success', text: 'Review updated successfully.' },
  correctionSubmitted: { kind: 'success', text: 'Feedback correction submitted.' },
  retryCompleted: { kind: 'info', text: 'Moderation retry completed. Check the current review status.' },
  reviewError: { kind: 'error', text: 'Could not complete this action. Please check the page for next steps.' },
} as const;
export type ToastMessage = keyof typeof toastMessages;
const ToastContext = createContext<(message: ToastMessage) => void>(() => {});
export const useToast = () => useContext(ToastContext);

export function ToastNotice({ message, onClose }: { message: ToastMessage; onClose: () => void }) {
  const { kind, text } = toastMessages[message];
  return <div className={`toast toast-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
    <span>{text}</span>
    <button aria-label="Dismiss notification" onClick={onClose}>×</button>
  </div>;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const sequence = useRef(0);
  const [toast, setToast] = useState<{ message: ToastMessage; id: number } | null>(null);
  const notify = useCallback((message: ToastMessage) => setToast({ message, id: ++sequence.current }), []);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 6000);
    return () => window.clearTimeout(timer);
  }, [toast]);
  return <ToastContext.Provider value={notify}>
    {children}
    <div className="toast-region">{toast && <ToastNotice key={toast.id} message={toast.message} onClose={() => setToast(null)} />}</div>
  </ToastContext.Provider>;
}
