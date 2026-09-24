import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';
import type { FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function AccountArea() {
  const { currentUser, checking, sessionError, retry, login, logout } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const location = useLocation();

  useEffect(() => {
    if (location.state?.signIn) setShowForm(true);
  }, [location.key, location.state]);

  async function handleSignIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const email = String(fields.get('email') ?? '');
    const password = String(fields.get('password') ?? '');
    (form.elements.namedItem('password') as HTMLInputElement).value = '';
    setBusy(true);
    setError('');
    try {
      await login(email, password);
      setShowForm(false);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not sign in. Please try again.');
    } finally { setBusy(false); }
  }

  async function handleSignOut() {
    setBusy(true);
    setError('');
    try {
      await logout();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not sign out. Please try again.');
    } finally { setBusy(false); }
  }

  if (checking) return <div className="account-area" role="status">Checking session…</div>;
  if (sessionError) return (
    <div className="account-area">
      <span role="alert">Could not check your session.</span>
      <button className="account-button" onClick={retry}>Retry</button>
    </div>
  );

  return (
    <div className="account-area">
      {currentUser ? (
        <>
          <div className="account-summary">
            <span>{currentUser.email}</span>
            <small>{currentUser.roles.join(' · ')}</small>
            {currentUser.roles.length === 0 && currentUser.emailVerified && (
              <small className="staff-notice">Your account is verified. <Link to="/account-setup">Complete your account</Link> to choose your roles.</small>
            )}
          </div>
          <button className="account-button" disabled={busy} onClick={handleSignOut}>
            {busy ? 'Signing out…' : 'Sign Out'}
          </button>
          {error && <p className="account-error" role="alert">{error}</p>}
        </>
      ) : (
        <>
          <Link to="/register" onClick={() => setShowForm(false)}>Register</Link>
          <button className="account-button" aria-expanded={showForm} aria-controls="sign-in-form"
            disabled={busy} onClick={() => { setShowForm(!showForm); setError(''); }}>
            {showForm ? 'Close' : 'Sign In'}
          </button>
          {showForm && (
            <form id="sign-in-form" className="sign-in-form" onSubmit={handleSignIn}>
              <h2>Sign In</h2>
              <label htmlFor="account-email">Email</label>
              <input id="account-email" name="email" type="email" autoComplete="username" required disabled={busy} />
              <label htmlFor="account-password">Password</label>
              <input id="account-password" name="password" type="password" autoComplete="current-password" required disabled={busy} />
              {error && <p className="account-error" role="alert">{error}</p>}
              <button className="account-button primary" type="submit" disabled={busy}>
                {busy ? 'Signing in…' : 'Sign In'}
              </button>
              <p className="verification-link"><Link to="/verify-email" onClick={() => setShowForm(false)}>Continue email verification</Link></p>
            </form>
          )}
        </>
      )}
    </div>
  );
}
