export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface CurrentUser {
  id: number;
  email: string;
  roles: UserRole[];
  emailVerified: boolean;
  linkedTeacher: { id: number; name: string } | null;
}

export async function getCurrentUser(signal: AbortSignal): Promise<CurrentUser | null> {
  const response = await fetch('/api/auth/me', {
    credentials: 'same-origin',
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error('Could not check your session. Please try again.');
  return response.json();
}

async function authPost(path: string, body?: Record<string, string | number>, timeoutMs = 10000): Promise<Response> {
  // A fresh token handles login/logout rotation and expired sessions without browser storage.
  const csrfResponse = await fetch('/api/auth/csrf', {
    credentials: 'same-origin', signal: AbortSignal.timeout(10000),
  });
  if (!csrfResponse.ok) throw new Error('Could not verify the request. Please try again.');
  const csrf: { headerName: string; token: string } = await csrfResponse.json();
  const response = await fetch(path, {
    method: 'POST', credentials: 'same-origin', signal: AbortSignal.timeout(timeoutMs),
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (response.status === 401) {
    if (path.startsWith('/api/account/')) throw new AccountSessionExpired();
    throw new Error('Invalid email or password.');
  }
  if (response.status === 403) throw new Error('Could not verify the request. Please try again.');
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(typeof error?.message === 'string' ? error.message : 'Account request failed. Please try again.');
  }
  return response;
}

export async function signIn(email: string, password: string): Promise<CurrentUser> {
  return (await authPost('/api/auth/login', { email, password })).json();
}

export class AccountSessionExpired extends Error {
  constructor() { super('Your session has expired. Please sign in again.'); }
}

export async function selectStudentRole(): Promise<CurrentUser> {
  return (await authPost('/api/account/roles/student', {})).json();
}

export async function claimTeacher(teacherId: number): Promise<CurrentUser> {
  return (await authPost('/api/account/teacher-claim', { teacherId })).json();
}

export async function signOut(): Promise<void> {
  await authPost('/api/auth/logout');
}

export async function register(email: string, password: string): Promise<{ message: string }> {
  return (await authPost('/api/auth/register', { email, password }, 30000)).json();
}

export async function verifyEmail(email: string, code: string): Promise<{ verified: boolean }> {
  return (await authPost('/api/auth/verify-email', { email, code })).json();
}

export async function resendVerification(email: string): Promise<{ message: string }> {
  return (await authPost('/api/auth/resend-verification', { email }, 30000)).json();
}
