import StatusBadge from './StatusBadge';
import EmptyState from './EmptyState';
import LoadingState from './LoadingState';
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
    {loading ? <LoadingState label="Loading your uploads…" /> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : uploads.length === 0 ? <EmptyState title="You have not uploaded a schedule yet." description="Choose your Detailed/Full Schedule PDF using the upload form above." /> : <ScheduleUploadCards uploads={uploads} />}
  </section>;
}

export function ScheduleUploadCards({ uploads }: { uploads: ScheduleUploadView[] }) {
  return <ul className="schedule-list">
        {uploads.map(upload => <li key={upload.id} className="course-card schedule-card">
          <h3>{upload.originalFilename}</h3>
          <p className="student-meta">Upload #{upload.id} · Uploaded: {new Date(upload.uploadedAt).toLocaleString()}</p>
          <p>Validation: <StatusBadge status={upload.status} /></p>
          {upload.academicTerms.map(term => <p key={term}>Academic Term: {term}</p>)}
          {upload.academicTerms.length === 0 && <p className="student-meta">Academic Term: Not available</p>}
          {upload.validationMessage && <p>{upload.validationMessage}</p>}
          <p>{upload.extractedCount} extracted entries</p>
          {upload.status === 'VALID' && <>
            <Link className="student-link-button" to={`/student/schedules/${upload.id}`}>View extracted information</Link>
          </>}
        </li>)}
      </ul>;
}
