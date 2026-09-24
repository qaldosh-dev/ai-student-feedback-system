import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired, claimTeacher, selectStudentRole } from '../api/auth';
import { getTeachers } from '../api/catalog';
import type { Teacher } from '../api/catalog';
import { useAuth } from '../auth/AuthContext';

export default function AccountSetupPage() {
  const { currentUser, updateCurrentUser, expireSession } = useAuth();
  const [choosingTeacher, setChoosingTeacher] = useState(false);
  const [query, setQuery] = useState('');
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [selected, setSelected] = useState<Teacher | null>(null);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!choosingTeacher || !query.trim()) { setTeachers([]); setLoading(false); setSearchError(''); return; }
    const controller = new AbortController();
    setLoading(true);
    setSearchError('');
    getTeachers(controller.signal, query)
      .then(results => { if (!controller.signal.aborted) setTeachers(results); })
      .catch(() => { if (!controller.signal.aborted) setSearchError('Could not load teachers. Please try searching again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [query, choosingTeacher]);

  async function completeSetup(teacher?: Teacher) {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const user = teacher ? await claimTeacher(teacher.id) : await selectStudentRole();
      updateCurrentUser(user);
      setSelected(null);
      if (teacher) setChoosingTeacher(false);
      setMessage(teacher ? 'Your teacher profile is now linked.' : 'Student Area is now available.');
    } catch (failure) {
      if (failure instanceof AccountSessionExpired) expireSession();
      setError(failure instanceof Error ? failure.message : 'Could not complete account setup. Please try again.');
    } finally { setBusy(false); }
  }

  return <main id="main" className="container role-area account-setup">
    <h1>Complete your account</h1>
    <p>Who are you? You can add both Student and Teacher roles if both apply to you.</p>
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
    <div className="setup-actions">
      {currentUser?.roles.includes('STUDENT')
        ? <Link to="/student">Go to Student Area</Link>
        : <button className="account-button" disabled={busy} onClick={() => completeSetup()}>I am a Student</button>}
      {currentUser?.linkedTeacher
        ? <p>Linked teacher: {currentUser.linkedTeacher.name}. <Link to="/teacher">Go to Teacher Area</Link></p>
        : <button className="account-button" disabled={busy} onClick={() => setChoosingTeacher(true)}>I am a Teacher</button>}
    </div>
    {choosingTeacher && !currentUser?.linkedTeacher && <section aria-label="Teacher selection">
      <h2>Find your teacher profile</h2>
      <div className="section-search">
        <label htmlFor="claim-teacher-search">Search teacher by name</label>
        <input id="claim-teacher-search" type="search" value={query} disabled={busy}
          placeholder="Search teacher by name..." onChange={event => { setQuery(event.target.value); setSelected(null); }} />
      </div>
      {loading ? <p role="status">Loading teachers…</p> : searchError ? <p role="alert">{searchError}</p>
        : !query.trim() ? <p>Enter your name to search the existing catalog.</p>
        : teachers.length === 0 ? <p>No teachers found.</p>
        : <ul className="teacher-choices">{teachers.map(teacher => <li key={teacher.id}>
          <span>{teacher.name}{teacher.department ? ` · ${teacher.department}` : ''}</span>
          <button className="account-button" disabled={busy} aria-label={`Select ${teacher.name}`}
            onClick={() => { setSelected(teacher); setError(''); }}>Select</button>
        </li>)}</ul>}
      {selected && <div className="claim-confirmation" role="region" aria-label="Confirm teacher profile">
        <p>You are about to link your account to: <strong>{selected.name}</strong></p>
        <p>This cannot be changed automatically later.</p>
        <button className="account-button" disabled={busy} onClick={() => completeSetup(selected)}>{busy ? 'Saving…' : 'Confirm'}</button>
        <button className="account-button" disabled={busy} onClick={() => setSelected(null)}>Cancel</button>
      </div>}
    </section>}
  </main>;
}
