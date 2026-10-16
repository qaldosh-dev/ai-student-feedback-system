import LoadingState from '../components/LoadingState';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import type { CurrentUser, UserRole } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import StudentDashboard from '../components/StudentDashboard';

interface AreaHome {
  area: UserRole;
  email: string;
  roles: UserRole[];
  linkedTeacher: CurrentUser['linkedTeacher'];
}

export default function RoleAreaPage({ role }: { role: UserRole }) {
  const { currentUser, expireSession } = useAuth();
  const [home, setHome] = useState<AreaHome | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setHome(null);
    setError('');
    async function load() {
      try {
        const response = await fetch(`/api/${role.toLowerCase()}/home`, {
          credentials: 'same-origin',
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]),
        });
        if (controller.signal.aborted) return;
        if (response.status === 401) { expireSession(); return; }
        if (response.status === 403) throw new Error('You do not have access to this area.');
        if (!response.ok) throw new Error('Could not load this area. Please try again.');
        const data: AreaHome = await response.json();
        if (!controller.signal.aborted) setHome(data);
      } catch (failure) {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Could not load this area.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [role, currentUser?.id, expireSession, attempt]);

  const title = `${role.charAt(0)}${role.slice(1).toLowerCase()} Area`;
  return (
    <main id="main" className="container role-area">
      <h1>{title}</h1>
      {loading ? <LoadingState label="Loading your area…" /> : error ? (
        <div className="state-message error-message" role="alert">
          <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
        </div>
      ) : home && (
        <div className="state-message area-details">
          <p><strong>Account:</strong> {home.email}</p>
          <p><strong>Roles:</strong> {home.roles.join(', ')}</p>
          {role === 'TEACHER' && (home.linkedTeacher
            ? <p><strong>Linked teacher:</strong> {home.linkedTeacher.name} (ID {home.linkedTeacher.id})</p>
            : <p>No teacher profile is linked to this account.</p>)}
          {role !== 'STUDENT' && <p>This area currently confirms your account access.</p>}
        </div>
      )}
      {role === 'STUDENT' && home && !loading && !error && <StudentDashboard />}
      {role === 'TEACHER' && home && !loading && !error && <p><Link to="/teacher/feedback">Course Feedback</Link></p>}
      {role === 'TEACHER' && home && !loading && !error && <MyProfileLink linkedTeacher={home.linkedTeacher} />}
      {role === 'TEACHER' && home && !loading && !error && <p><Link to="/teacher/analytics">Teacher Analytics</Link></p>}
    </main>
  );
}

export function MyProfileLink({ linkedTeacher }: { linkedTeacher: CurrentUser['linkedTeacher'] }) {
  // This value comes from authenticated /api/teacher/home, never a URL/query identity.
  return linkedTeacher ? <p><Link to={`/teachers/${linkedTeacher.id}`}>My Profile</Link></p> : null;
}
