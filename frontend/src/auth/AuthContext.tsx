import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { getCurrentUser, signIn, signOut } from '../api/auth';
import type { CurrentUser } from '../api/auth';

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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [checking, setChecking] = useState(true);
  const [sessionError, setSessionError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setChecking(true);
    setSessionError(false);
    getCurrentUser(controller.signal)
      .then(user => { if (!controller.signal.aborted) setCurrentUser(user); })
      .catch(() => { if (!controller.signal.aborted) setSessionError(true); })
      .finally(() => { if (!controller.signal.aborted) setChecking(false); });
    return () => controller.abort();
  }, [attempt]);

  async function login(email: string, password: string) {
    setCurrentUser(await signIn(email, password));
  }

  async function logout() {
    await signOut();
    setCurrentUser(null);
  }

  const expireSession = useCallback(() => setCurrentUser(null), []);

  return (
    <AuthContext.Provider value={{ currentUser, checking, sessionError,
      retry: () => setAttempt(value => value + 1), login, logout, expireSession, updateCurrentUser: setCurrentUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth requires AuthProvider');
  return auth;
}
