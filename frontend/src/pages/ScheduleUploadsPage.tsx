import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import StudentBackLink from '../components/StudentBackLink';
import ScheduleUploadForm from '../components/ScheduleUploadForm';
import ScheduleUploadList from '../components/ScheduleUploadList';

export default function ScheduleUploadsPage() {
  const { currentUser } = useAuth();
  return <ScheduleUploads key={currentUser?.id} />;
}

function ScheduleUploads() {
  const [version, setVersion] = useState(0);
  return <main id="main" className="container role-area student-page">
    <StudentBackLink />
    <h1>Upload Schedule</h1>
    <p>Upload the Detailed/Full SDU Schedule PDF containing course and teacher details. Maximum file size: 10 MB.</p>
    <ScheduleUploadForm onUploaded={() => setVersion(value => value + 1)} />
    <ScheduleUploadList refreshKey={version} />
  </main>;
}
