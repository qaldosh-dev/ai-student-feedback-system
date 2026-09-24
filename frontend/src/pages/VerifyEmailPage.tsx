import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation } from 'react-router';
import { resendVerification, verifyEmail } from '../api/auth';

export default function VerifyEmailPage() {
  const location = useLocation();
  const [email, setEmail] = useState<string>(location.state?.email ?? '');
  const [message, setMessage] = useState<string>(location.state?.message ?? 'Enter your SDU email and the code from your inbox.');
  const [remaining, setRemaining] = useState(location.state?.codeRequested ? 60 : 0);
  const [verified, setVerified] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const timer = window.setInterval(() => setRemaining(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const code = String(new FormData(form).get('code') ?? '').trim();
    (form.elements.namedItem('code') as HTMLInputElement).value = '';
    setBusy(true); setError('');
    try {
      const result = await verifyEmail(email.trim().toLowerCase(), code);
      setVerified(result.verified);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not verify email.');
    } finally { setBusy(false); }
  }

  async function resend() {
    setBusy(true); setError('');
    try {
      const result = await resendVerification(email.trim().toLowerCase());
      setMessage(result.message); setRemaining(60);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not resend code.');
    } finally { setBusy(false); }
  }

  return (
    <main id="main" className="container role-area">
      <h1>{verified ? 'Email verified successfully' : 'Verify your email'}</h1>
      {verified ? (
        <div className="registration-form" role="status">
          <p>{email.trim().toLowerCase()}</p>
          <p>Your account is verified. Role setup is the next step.</p>
          <p className="verification-link"><Link to="/" state={{ signIn: true }}>Sign In</Link></p>
        </div>
      ) : (
        <form className="registration-form" onSubmit={submit}>
          <p role="status">{message}</p>
          <label htmlFor="verify-email">SDU Email</label>
          <input id="verify-email" type="email" autoComplete="email" maxLength={254} required disabled={busy}
            value={email} onChange={event => setEmail(event.target.value)} onBlur={() => setEmail(email.trim().toLowerCase())} />
          <label htmlFor="verification-code">Verification code</label>
          <input id="verification-code" name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required disabled={busy} />
          <p className="form-hint">Six digits. Codes expire after 10 minutes. Only the latest code works.</p>
          {error && <p className="account-error" role="alert">{error}</p>}
          <div className="verification-actions">
            <button className="account-button primary" disabled={busy}>{busy ? 'Please wait…' : 'Verify Email'}</button>
            <button className="account-button" type="button" disabled={busy || remaining > 0 || !email.trim()} onClick={resend}>
              {remaining > 0 ? `Resend in ${remaining}s` : 'Resend Code'}
            </button>
          </div>
          <p className="verification-link"><Link to="/" state={{ signIn: true }}>Already verified? Sign In</Link></p>
        </form>
      )}
    </main>
  );
}
