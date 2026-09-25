import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { AccountSessionExpired } from '../api/auth';
import { uploadSchedule } from '../api/scheduleUploads';
import type { ScheduleUpload } from '../api/scheduleUploads';
import { useAuth } from '../auth/AuthContext';

export default function ScheduleUploadForm() {
  const { expireSession } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [upload, setUpload] = useState<ScheduleUpload | null>(null);
  const input = useRef<HTMLInputElement>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || busy) return;
    setError('');
    setUpload(null);
    if (file.size === 0 || file.size > 10 * 1024 * 1024 || !file.name.toLowerCase().endsWith('.pdf')) {
      setError('Choose a non-empty .pdf file, up to 10 MB.');
      return;
    }
    setBusy(true);
    try {
      setUpload(await uploadSchedule(file));
      setFile(null);
      if (input.current) input.current.value = '';
    } catch (failure) {
      if (failure instanceof AccountSessionExpired) expireSession();
      setError(failure instanceof Error ? failure.message : 'Upload failed. Please try again.');
    } finally { setBusy(false); }
  }

  return <section aria-labelledby="schedule-upload-title" className="schedule-upload">
    <h2 id="schedule-upload-title">Upload Schedule</h2>
    <p>Upload the Detailed/Full version of your SDU Schedule PDF, including course names, teachers and groups/sections. The Compact/Short version does not contain enough details. Course information is not extracted yet.</p>
    <form onSubmit={submit} className="registration-form">
      <label htmlFor="schedule-file">Schedule PDF</label>
      <input ref={input} id="schedule-file" type="file" accept=".pdf,application/pdf" disabled={busy}
        onChange={event => { setFile(event.target.files?.[0] ?? null); setError(''); setUpload(null); }} />
      <p className="form-hint">{file ? file.name : 'No file selected.'} Maximum 10 MB.</p>
      <button className="account-button primary" type="submit" disabled={!file || busy}>{busy ? 'Uploading and validating…' : 'Upload'}</button>
    </form>
    {busy && <p role="status">Uploading and validating your schedule…</p>}
    {error && <p className="account-error" role="alert">{error}</p>}
    {upload && <div className={`state-message area-details${upload.status === 'INVALID' ? ' error-message' : ''}`} role="status">
      <p><strong>{upload.status === 'VALID' ? 'Schedule uploaded successfully'
        : upload.status === 'INVALID' ? 'File uploaded, but validation failed.' : 'File uploaded successfully'}</strong></p>
      <p>{upload.originalFilename}</p>
      <p>Uploaded: {new Date(upload.uploadedAt).toLocaleString()}</p>
      <p>Status: {upload.status.charAt(0) + upload.status.slice(1).toLowerCase()}</p>
      {upload.status === 'VALID' && <p>This schedule is ready for information extraction.</p>}
      {upload.validationMessage && <p>{upload.validationMessage}</p>}
    </div>}
  </section>;
}
