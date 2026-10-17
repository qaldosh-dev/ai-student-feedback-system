import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { register } from '../api/auth';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const email = String(fields.get('email') ?? '').trim().toLowerCase();
    const password = String(fields.get('password') ?? '');
    const confirm = String(fields.get('confirmPassword') ?? '');
    setError('');
    if (!['stu.sdu.edu.kz', 'sdu.edu.kz'].includes(email.split('@')[1]) || email.split('@').length !== 2) {
      setError('Use your @stu.sdu.edu.kz or @sdu.edu.kz email address.'); return;
    }
    if (!password.trim() || Array.from(password).length < 8 || new TextEncoder().encode(password).length > 72) {
      setError('Password must not be blank and must contain at least 8 characters and at most 72 UTF-8 bytes.'); return;
    }
    if (password !== confirm) { setError('Passwords do not match.'); return; }
    (form.elements.namedItem('password') as HTMLInputElement).value = '';
    (form.elements.namedItem('confirmPassword') as HTMLInputElement).value = '';
    setBusy(true);
    try {
      const result = await register(email, password);
      navigate('/verify-email', { state: { email, message: result.message, codeRequested: true } });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not register. Please try again.');
    } finally { setBusy(false); }
  }

  return (
    <main id="main" className="container role-area">
      <h1>Create your account</h1>
      <form className="registration-form" onSubmit={submit}>
        <p>Use your SDU email: @stu.sdu.edu.kz or @sdu.edu.kz</p>
        <label htmlFor="register-email">SDU Email</label>
        <input id="register-email" name="email" type="email" autoComplete="email" maxLength={254} required disabled={busy} />
        <label htmlFor="register-password">Password</label>
        <input id="register-password" name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required disabled={busy} aria-describedby="password-policy" />
        <p id="password-policy" className="form-hint">At least 8 characters, up to 72 UTF-8 bytes. Cannot contain only whitespace. Some characters use more than one byte.</p>
        <label htmlFor="register-confirm">Confirm Password</label>
        <input id="register-confirm" name="confirmPassword" type="password" autoComplete="new-password" minLength={8} maxLength={72} required disabled={busy} />
        {error && <p className="account-error" role="alert">{error}</p>}
        <button className="account-button primary" disabled={busy}>{busy ? 'Creating account…' : 'Create Account'}</button>
        <p className="verification-link"><Link to="/verify-email">Continue email verification</Link></p>
        <p><Link to="/" state={{ signIn: true }}>Already verified? Sign In</Link></p>
      </form>
    </main>
  );
}
