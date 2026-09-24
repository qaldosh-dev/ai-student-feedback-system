import type { ReactNode } from 'react';
import type { UserRole } from '../api/auth';
import { useAuth } from '../auth/AuthContext';

export default function ProtectedRoute({ requiredRole, children }: { requiredRole?: UserRole; children: ReactNode }) {
  const { currentUser, checking, sessionError, retry } = useAuth();
  let message: ReactNode;
  if (checking) {
    message = <p role="status">Checking session…</p>;
  } else if (sessionError) {
    message = <><h1>Could not check your session.</h1><button className="account-button" onClick={retry}>Retry</button></>;
  } else if (!currentUser) {
    message = <><h1>Sign In required</h1><p>Use Sign In above to access this area. The catalog is still public.</p></>;
  } else if (!currentUser.emailVerified || (requiredRole && !currentUser.roles.includes(requiredRole))) {
    message = <><h1>Access Denied</h1><p>You do not have access to this area.</p></>;
  } else {
    return children;
  }
  return <main id="main" className="container role-area">{message}</main>;
}
