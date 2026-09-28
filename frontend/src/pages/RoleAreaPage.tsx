import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import type { CurrentUser, UserRole } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import ScheduleUploadForm from '../components/ScheduleUploadForm';
import ScheduleUploadList from '../components/ScheduleUploadList';

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
  const [uploadsVersion, setUploadsVersion] = useState(0);

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
      {loading ? <p role="status">Loading your area…</p> : error ? (
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
          <p>This area currently confirms your account access.</p>
        </div>
      )}
      {role === 'STUDENT' && home && !loading && !error && <>
        <p><Link to="/student/verified-courses">View Verified Courses</Link></p>
        <p><Link to="/student/reviews/new">Review a Course</Link></p>
        <ScheduleUploadForm key={`form-${currentUser?.id}`} onUploaded={() => setUploadsVersion(value => value + 1)} />
        <ScheduleUploadList key={`list-${currentUser?.id}`} refreshKey={uploadsVersion} />
      </>}
    </main>
  );
}
