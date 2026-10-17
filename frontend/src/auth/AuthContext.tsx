import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { getCurrentUser, signIn, signOut } from '../api/auth';
import type { CurrentUser } from '../api/auth';
import { clearReviewDrafts } from '../pages/reviewDraft';

interface AuthState {
  currentUser: CurrentUser | null;
  checking: boolean;
  sessionError: boolean;
  retry: () => void;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  expireSession: () => void;
  updateCurrentUser: (user: CurrentUser) => void;
}

const AuthContext = createContext<AuthState | null>(null);
const AUTH_CHANGE_KEY = 'feedback-auth-change';

function notifyOtherTabs() {
  // A notification only, never an identity, session token or credential.
  try { localStorage.setItem(AUTH_CHANGE_KEY, crypto.randomUUID()); } catch { /* Focus recheck still works. */ }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [checking, setChecking] = useState(true);
  const [sessionError, setSessionError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const revision = useRef(0);
  const ownerId = useRef<number | null>(null);
  const backgroundCheck = useRef(false);

  const refresh = useCallback((background = false) => {
    revision.current++;
    backgroundCheck.current = background;
    if (!background) { setCurrentUser(null); setChecking(true); }
    setSessionError(false);
    setAttempt(value => value + 1);
  }, []);

  useEffect(() => {
    const changed = (event: StorageEvent) => { if (event.key === AUTH_CHANGE_KEY || event.key === null) refresh(); };
    // Returning from a file picker must not unmount forms when the account is unchanged.
    const focused = () => refresh(true);
    const visible = () => { if (document.visibilityState === 'visible') refresh(true); };
    window.addEventListener('storage', changed);
    window.addEventListener('focus', focused);
    document.addEventListener('visibilitychange', visible);
    return () => {
      window.removeEventListener('storage', changed);
      window.removeEventListener('focus', focused);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [refresh]);

  useEffect(() => {
    const controller = new AbortController();
    const started = revision.current;
    const active = () => !controller.signal.aborted && started === revision.current;
    if (!backgroundCheck.current) setChecking(true);
    setSessionError(false);
    getCurrentUser(controller.signal)
      .then(user => {
        if (!active()) return;
        if (!user || (ownerId.current !== null && ownerId.current !== user.id)) clearReviewDrafts();
        ownerId.current = user?.id ?? null;
        setCurrentUser(user);
      })
      .catch(() => { if (active()) { setCurrentUser(null); setSessionError(true); } })
      .finally(() => { if (active()) setChecking(false); });
    return () => controller.abort();
  }, [attempt]);

  async function login(email: string, password: string) {
    const started = ++revision.current;
    const user = await signIn(email, password);
    if (started !== revision.current) return;
    clearReviewDrafts();
    ownerId.current = user.id;
    setCurrentUser(user);
    setChecking(false); setSessionError(false);
    notifyOtherTabs();
  }

  async function logout() {
    await signOut();
    expireSession();
  }

  const expireSession = useCallback(() => {
    revision.current++; ownerId.current = null;
    clearReviewDrafts(); setCurrentUser(null); setChecking(false); setSessionError(false);
    notifyOtherTabs();
  }, []);

  return (
    <AuthContext.Provider value={{ currentUser, checking, sessionError,
      retry: () => refresh(), login, logout, expireSession,
      updateCurrentUser: () => { notifyOtherTabs(); refresh(); } }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth requires AuthProvider');
  return auth;
}
