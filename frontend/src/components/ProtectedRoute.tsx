import type { ReactNode } from 'react';
import type { UserRole } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { AccessDenied } from '../pages/ErrorPage';
import LoadingState from './LoadingState';

export default function ProtectedRoute({ requiredRole, children }: { requiredRole?: UserRole; children: ReactNode }) {
  const { currentUser, checking, sessionError, retry } = useAuth();
  let message: ReactNode;
  if (checking) {
    message = <LoadingState label="Checking session…" />;
  } else if (sessionError) {
    message = <><h1>Could not check your session.</h1><button className="account-button" onClick={retry}>Retry</button></>;
  } else if (!currentUser) {
    message = <><h1>Sign In required</h1><p>Use Sign In above to access this area. The catalog is still public.</p></>;
  } else if (!currentUser.emailVerified || (requiredRole && !currentUser.roles.includes(requiredRole))) {
    message = <AccessDenied />;
  } else {
    return children;
  }
  return <main id="main" className="container role-area">{message}</main>;
}
