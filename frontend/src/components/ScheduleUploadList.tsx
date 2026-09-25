import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { listScheduleUploads } from '../api/scheduleUploads';
import type { ScheduleUploadView } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';

export default function ScheduleUploadList({ refreshKey }: { refreshKey: number }) {
  const { expireSession } = useAuth();
  const [uploads, setUploads] = useState<ScheduleUploadView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setUploads([]);
    listScheduleUploads(controller.signal)
      .then(data => { if (!controller.signal.aborted) setUploads(data); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError(failure instanceof Error ? failure.message : 'Could not load your uploads.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [refreshKey, attempt, expireSession]);

  return <section className="schedule-upload" aria-labelledby="my-schedules-title">
    <h2 id="my-schedules-title">My Schedule Uploads</h2>
    {loading ? <p role="status">Loading your uploads…</p> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : uploads.length === 0 ? <p className="state-message">You have not uploaded a schedule yet.</p> :
      <ul className="schedule-list">
        {uploads.map(upload => <li key={upload.id} className="course-card schedule-card">
          <h3>{upload.originalFilename}</h3>
          <p>Upload #{upload.id} · {new Date(upload.uploadedAt).toLocaleString()}</p>
          <p>Status: {upload.status.charAt(0) + upload.status.slice(1).toLowerCase()}</p>
          {upload.academicTerms.map(term => <p key={term}>Academic Term: {term}</p>)}
          {upload.validationMessage && <p>{upload.validationMessage}</p>}
          {upload.status === 'VALID' && <>
            <p>{upload.extractedCount} extracted entries</p>
            <Link to={`/student/schedules/${upload.id}`}>View extracted information</Link>
          </>}
        </li>)}
      </ul>}
  </section>;
}
