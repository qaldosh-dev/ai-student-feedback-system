import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { AccountSessionExpired } from '../api/auth';
import { getExtractedSchedule } from '../api/scheduleUploads';
import type { ExtractedSchedule } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';
import { summarizeVerification, verificationPresentation } from './verificationPresentation';

export default function ScheduleDetailsPage() {
  const { uploadId = '' } = useParams();
  const { currentUser } = useAuth();
  return <ScheduleDetails key={`${currentUser?.id}:${uploadId}`} uploadId={uploadId} />;
}

function ScheduleDetails({ uploadId }: { uploadId: string }) {
  const { expireSession } = useAuth();
  const [data, setData] = useState<ExtractedSchedule | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setData(null);
    if (!/^[1-9]\d*$/.test(uploadId)) {
      setError('Schedule upload not found.'); setLoading(false);
      return () => controller.abort();
    }
    getExtractedSchedule(uploadId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof AccountSessionExpired) expireSession();
        else setError(failure instanceof Error ? failure.message : 'Could not load schedule information.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [uploadId, attempt, expireSession]);

  const summary = summarizeVerification(data?.entries ?? []);

  return <main id="main" className="container role-area">
    <Link to="/student">Back to My Schedule Uploads</Link>
    <h1>Extracted Schedule Information</h1>
    {loading ? <p role="status">Loading extracted information…</p> : error ? <div className="state-message error-message" role="alert">
      <p>{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </div> : data && <>
      <div className="state-message area-details">
        <p><strong>{data.upload.originalFilename}</strong> · Upload #{data.upload.id}</p>
        <p>Uploaded: {new Date(data.upload.uploadedAt).toLocaleString()}</p>
        <p>PDF validation: {data.upload.status.charAt(0) + data.upload.status.slice(1).toLowerCase()}</p>
        {data.upload.academicTerms.map(term => <p key={term}><strong>Academic Term:</strong> {term}</p>)}
        {data.upload.validationMessage && <p>{data.upload.validationMessage}</p>}
      </div>
      {data.entries.length === 0 ? <p className="state-message">No extracted schedule information is available for this upload.</p> : <>
        <p>{summary.extracted} extracted entries · {summary.verified} verified · {summary.pending} pending
          {summary.notChecked > 0 && <> · {summary.notChecked} not checked</>}</p>
        <ul className="schedule-list">
          {data.entries.map(entry => {
            const verification = verificationPresentation(entry);
            return <li key={entry.id} className="course-card schedule-card">
            <h2>{entry.courseCode} — {entry.courseName}</h2>
            <p><strong>Teacher:</strong> {entry.teacherName}</p>
            <p><strong>Group / Section:</strong> {entry.groupSection}</p>
            <p><strong>Academic Term:</strong> {entry.academicTerm}</p>
            <p><strong>Verification:</strong> {verification.label}</p>
            {verification.reason && <p><strong>Reason:</strong> {verification.reason}</p>}
          </li>;
          })}
        </ul>
      </>}
    </>}
  </main>;
}
